package com.lani.demoncore.optimization;

import com.lani.demoncore.compat.chunk.ChunkModCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.event.GpuRamBalanceEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GpuRamBalancer {

    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore/GPURAM");

    private GpuRamBalancer() {}

    public static final double HARD_GPU_CAP = 0.85; // Raised slightly to allow dynamic range
    public static final double HARD_RAM_CAP = 0.85;

    // PID constants
    private static final double KP = 0.8;
    private static final double KI = 0.1;
    private static final double KD = 0.05;

    private static volatile double cacheMultiplier = 1.0;
    private static long lastEvalMs;
    private static volatile double lastGpuUtil;
    private static volatile double lastRamUtil;
    private static volatile int upAdjustments;
    private static volatile int downAdjustments;
    private static volatile int holdCount;
    private static volatile long evaluations;
    private static volatile long lastEventFireMs;
    private static volatile long lastDebugLogMs;
    private static volatile boolean initialized;
    private static volatile double previousMultiplier = 1.0;

    private static volatile double pidIntegral = 0.0;
    private static volatile double pidLastError = 0.0;
    
    private static volatile int ramCapTriggerCount;
    private static volatile int criticalPressureCount;
    private static volatile int hardLimitBreaches;
    private static volatile int gpuHardLimitBreaches;
    private static volatile int ramHardLimitBreaches;
    private static volatile double emaCacheMultiplier = 1.0;
    private static volatile double lastAppliedMultiplierToCaches = 1.0;

    private static volatile long gpuOffloadCycles;
    private static volatile int adaptiveBoosts;
    
    private static volatile double cumulativeRamUtilized = 0.0;

    public static final double MIN_MULTIPLIER = 0.20;
    public static final double MAX_MULTIPLIER = 3.00;

    private static final long EVENT_THROTTLE_MS = 1500L;
    private static final long DEBUG_LOG_THROTTLE_MS = 4000L;

    public static void evaluate() {
        boolean enabled = DemonCoreConfig.getBool(DemonCoreConfig.GPU_RAM_BALANCER, true);
        if (!enabled) {
            cacheMultiplier = 1.0;
            applyMultiplierToAllCaches(1.0, "disabled");
            if (shouldFireEvent()) {
                fireEvent(GpuRamBalanceEvent.builder()
                        .action(GpuRamBalanceEvent.Action.DISABLED)
                        .balancerEnabled(false)
                        .build());
            }
            return;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastEvalMs < 500L) return;
        
        double dt = (now - lastEvalMs) / 1000.0;
        lastEvalMs = now;
        evaluations++;

        boolean isVulcanMode = DemonCoreConfig.isVulcanMode();

        if (!initialized) {
            initialized = true;
            if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                LOGGER.info("[GPURAM] Balancer initialized | VulcanMode={} | targetGPU={}", 
                    isVulcanMode, DemonCoreConfig.getDouble(DemonCoreConfig.GPU_TARGET_UTIL, 0.40));
            }
        }

        double budget = FrameProfiler.targetFrameTimeMs();
        double gpu = FrameProfiler.getGpuWaitMs();
        double gpuUtil = budget > 0.001 ? Math.min(1.0, gpu / budget) : 0.0;
        double ramUtil = GCStutterGuard.getHeapUsage();

        double targetGpuUtil = DemonCoreConfig.getDouble(DemonCoreConfig.GPU_TARGET_UTIL, 0.40);
        double ramCap = Math.min(HARD_RAM_CAP, DemonCoreConfig.getDouble(DemonCoreConfig.RAM_MAX_USAGE, HARD_RAM_CAP));
        
        lastGpuUtil = gpuUtil;
        lastRamUtil = ramUtil;
        cumulativeRamUtilized += ramUtil * dt;

        double delta = 0.0;
        String reason = "";
        
        if (isVulcanMode) {
            cacheMultiplier = 2.0;
            delta = 0.0;
            reason = "Vulcan Mode ON -> FIXED 2.0x";
            pidIntegral = 0;
            pidLastError = 0;
        } else {
            // VULCAN OFF: Use PID controller to offload GPU to RAM
            boolean gpuHardLimit = gpuUtil > HARD_GPU_CAP;
            boolean ramHardLimit = ramUtil > ramCap;
            
            if (gpuHardLimit || ramHardLimit) {
                hardLimitBreaches++;
            }
            
            if (ramHardLimit) {
                ramCapTriggerCount++;
                ramHardLimitBreaches++;
                double overrun = ramUtil - ramCap;
                delta = -0.15 - (overrun * 1.5);
                reason = "RAM cap exceeded (" + Math.round(ramUtil * 100) + "%) -> SHRINK caches";
                pidIntegral = 0; // Reset integral on hard cap
                fireRamCapIfThrottled(gpuUtil, ramUtil, targetGpuUtil, ramCap, delta, reason);
            } else {
                // True PID Control
                double error = targetGpuUtil - gpuUtil; // Positive if GPU is underutilized (we want to shrink caches to let GPU work more? No.)
                // Actually, if GPU is high (error < 0), we want to offload to RAM (increase caches).
                // So error = gpuUtil - targetGpuUtil
                error = gpuUtil - targetGpuUtil; 
                
                pidIntegral += error * dt;
                // anti-windup
                pidIntegral = Math.max(-5.0, Math.min(5.0, pidIntegral));
                
                double derivative = (error - pidLastError) / dt;
                pidLastError = error;
                
                delta = (KP * error) + (KI * pidIntegral) + (KD * derivative);
                
                if (delta > 0) {
                    gpuOffloadCycles++;
                    adaptiveBoosts++;
                    reason = "GPU above target -> GROW caches (PID)";
                } else if (delta < 0) {
                    reason = "GPU below target -> SHRINK caches (PID)";
                } else {
                    reason = "GPU at target -> HOLD (PID)";
                }
            }
        }

        boolean critical = gpuUtil > HARD_GPU_CAP * 1.05 || ramUtil > HARD_RAM_CAP * 1.02;
        if (!isVulcanMode && critical) {
            criticalPressureCount++;
            if (shouldFireEvent()) {
                fireEvent(GpuRamBalanceEvent.builder()
                        .action(GpuRamBalanceEvent.Action.CRITICAL_PRESSURE)
                        .cacheMultiplier(cacheMultiplier)
                        .previousMultiplier(previousMultiplier)
                        .gpuUtil(gpuUtil)
                        .ramUtil(ramUtil)
                        .targetGpuUtil(targetGpuUtil)
                        .ramCap(ramCap)
                        .pidError(delta)
                        .pidIntegral(pidIntegral)
                        .evaluations(evaluations)
                        .reason("CRITICAL: GPU=" + Math.round(gpuUtil * 100) + "%/cap=" + Math.round(HARD_GPU_CAP * 100)
                                + "% RAM=" + Math.round(ramUtil * 100) + "%/cap=" + Math.round(HARD_RAM_CAP * 100) + "%")
                        .build());
            }
        }

        double prev = cacheMultiplier;
        previousMultiplier = prev;
        double next = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, cacheMultiplier + delta));
        cacheMultiplier = next;

        emaCacheMultiplier += 0.22 * (next - emaCacheMultiplier);

        GpuRamBalanceEvent.Action action;
        if (Math.abs(next - prev) < 0.005) {
            holdCount++;
            action = GpuRamBalanceEvent.Action.HOLD;
        } else if (next > prev) {
            upAdjustments++;
            action = GpuRamBalanceEvent.Action.ADJUST_UP;
        } else {
            downAdjustments++;
            action = GpuRamBalanceEvent.Action.ADJUST_DOWN;
        }

        applyMultiplierToAllCaches(next, reason);

        if (shouldFireEvent()) {
            boolean sigChange = Math.abs(next - prev) >= 0.04;
            fireEvent(GpuRamBalanceEvent.builder()
                    .action(sigChange && action != GpuRamBalanceEvent.Action.HOLD
                            ? action : GpuRamBalanceEvent.Action.EVALUATION)
                    .cacheMultiplier(next)
                    .previousMultiplier(prev)
                    .gpuUtil(gpuUtil)
                    .ramUtil(ramUtil)
                    .targetGpuUtil(isVulcanMode ? 1.0 : targetGpuUtil)
                    .ramCap(ramCap)
                    .pidError(delta)
                    .pidIntegral(pidIntegral)
                    .upAdjustments(upAdjustments)
                    .downAdjustments(downAdjustments)
                    .holdCount(holdCount)
                    .evaluations(evaluations)
                    .reason(reason)
                    .build());
        }

        if (Math.abs(next - prev) >= 0.04) {
            fireEvent(GpuRamBalanceEvent.builder()
                    .action(GpuRamBalanceEvent.Action.CACHE_MULTIPLIER_APPLIED)
                    .cacheMultiplier(next)
                    .previousMultiplier(prev)
                    .gpuUtil(gpuUtil)
                    .ramUtil(ramUtil)
                    .targetGpuUtil(isVulcanMode ? 1.0 : targetGpuUtil)
                    .ramCap(ramCap)
                    .evaluations(evaluations)
                    .reason(String.format("Mult %s -> %s | %s",
                            String.format("%.2f", prev), String.format("%.2f", next), reason))
                    .build());
        }

        debugLogEvaluation(next, prev, gpuUtil, ramUtil, ramCap, delta, action, reason, !isVulcanMode && critical);
    }

    private static void fireRamCapIfThrottled(double gpuUtil, double ramUtil,
                                              double target, double ramCap,
                                              double err, String reason) {
        if (!shouldFireEvent()) return;
        fireEvent(GpuRamBalanceEvent.builder()
                .action(GpuRamBalanceEvent.Action.RAM_CAP_TRIGGERED)
                .cacheMultiplier(cacheMultiplier)
                .previousMultiplier(previousMultiplier)
                .gpuUtil(gpuUtil)
                .ramUtil(ramUtil)
                .targetGpuUtil(target)
                .ramCap(ramCap)
                .pidError(err)
                .pidIntegral(pidIntegral)
                .upAdjustments(upAdjustments)
                .downAdjustments(downAdjustments)
                .holdCount(holdCount)
                .evaluations(evaluations)
                .reason(reason)
                .build());
    }

    private static void applyMultiplierToAllCaches(double mult, String reason) {
        if (Math.abs(mult - lastAppliedMultiplierToCaches) < 0.02) return;
        lastAppliedMultiplierToCaches = mult;
        try {
            GeometryCache.applyCacheMultiplier(mult);
        } catch (Exception e) {
            if (DemonCoreConfig.isDebug()) LOGGER.warn("[GPURAM] GeometryCache.applyCacheMultiplier failed: {}", e.getMessage());
        }
        try {
            CacheSystem.applyCacheMultiplier(mult);
        } catch (Exception e) {
            if (DemonCoreConfig.isDebug()) LOGGER.warn("[GPURAM] CacheSystem.applyCacheMultiplier failed: {}", e.getMessage());
        }
    }

    private static void debugLogEvaluation(double next, double prev, double gpuUtil, double ramUtil,
                                           double ramCap, double error,
                                           GpuRamBalanceEvent.Action action, String reason,
                                           boolean anyHardLimit) {
        if (!(DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false))) return;
        long now = System.currentTimeMillis();
        if (now - lastDebugLogMs < DEBUG_LOG_THROTTLE_MS) return;
        lastDebugLogMs = now;

        LOGGER.info("[GPURAM] Eval #{}. {} | mult {}->{} | GPU {}% | RAM {}% cap {}% | delta {} | "
                        + "{} up/{} dn/{} hold | offloadCycles={} boosts={} | hardBreaches={}(G{} R{}) | {}",
                evaluations, action.name(),
                String.format("%.2f", prev), String.format("%.2f", next),
                Math.round(gpuUtil * 100.0),
                Math.round(ramUtil * 100.0), Math.round(ramCap * 100.0),
                String.format("%.3f", error),
                upAdjustments, downAdjustments, holdCount,
                gpuOffloadCycles, adaptiveBoosts,
                hardLimitBreaches, gpuHardLimitBreaches, ramHardLimitBreaches,
                reason);
    }

    public static double getCacheMultiplier() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.GPU_RAM_BALANCER, true)) return 1.0;
        return cacheMultiplier;
    }

    public static double getEmaMultiplier() { return emaCacheMultiplier; }
    public static double getLastGpuUtil() { return lastGpuUtil; }
    public static double getLastRamUtil() { return lastRamUtil; }
    public static double getTargetGpuUtil() { return DemonCoreConfig.getDouble(DemonCoreConfig.GPU_TARGET_UTIL, 0.40); } 
    public static int getUpAdjustments() { return upAdjustments; }
    public static int getDownAdjustments() { return downAdjustments; }
    public static int getHoldCount() { return holdCount; }
    public static long getEvaluations() { return evaluations; }
    public static int getRamCapTriggerCount() { return ramCapTriggerCount; }
    public static int getCriticalPressureCount() { return criticalPressureCount; }
    public static int getHardLimitBreaches() { return hardLimitBreaches; }
    public static int getGpuHardLimitBreaches() { return gpuHardLimitBreaches; }
    public static int getRamHardLimitBreaches() { return ramHardLimitBreaches; }
    public static double getHardGpuCap() { return HARD_GPU_CAP; }
    public static double getHardRamCap() { return HARD_RAM_CAP; }
    public static double getGpuSoftTarget() { return getTargetGpuUtil(); } 
    public static double getRamSoftTarget() { return Math.min(HARD_RAM_CAP, DemonCoreConfig.getDouble(DemonCoreConfig.RAM_MAX_USAGE, HARD_RAM_CAP)); } 
    public static double getGpuOffloadRatio() { return cacheMultiplier; } 
    public static long getGpuOffloadCycles() { return gpuOffloadCycles; }
    public static double getCumulativeGpuSaved() { return gpuOffloadCycles * 0.05; } 
    public static double getCumulativeRamUtilized() { return cumulativeRamUtilized; }
    public static int getAdaptiveBoosts() { return adaptiveBoosts; }

    private static boolean shouldFireEvent() {
        long now = System.currentTimeMillis();
        if (now - lastEventFireMs >= EVENT_THROTTLE_MS) {
            lastEventFireMs = now;
            return true;
        }
        return false;
    }

    private static void fireEvent(GpuRamBalanceEvent event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (Exception e) {
            if (DemonCoreConfig.isDebug()) {
                LOGGER.warn("[GPURAM] Event post failed: {}", e.getMessage());
            }
        }
    }

    public static String getStats() {
        boolean on = DemonCoreConfig.getBool(DemonCoreConfig.GPU_RAM_BALANCER, true);
        if (!on) return "GpuRamBalancer: OFF";
        double configuredRamCap = DemonCoreConfig.getDouble(DemonCoreConfig.RAM_MAX_USAGE, HARD_RAM_CAP);
        boolean isVulcan = DemonCoreConfig.isVulcanMode();
        return String.format("GpuRamBalancer[%s]: mult %s (EMA %s) | "
                        + "GPU %d%%/target %d%% | RAM %d%%/%d%% cap | "
                        + "%dup/%ddn/%d hold | %d evals | offloadCycles=%d boosts=%d | "
                        + "RAMcaps=%d crit=%d hardBreaches=%d(G%d R%d)",
                isVulcan ? "VULCAN" : "PID",
                String.format("%.2f", cacheMultiplier), String.format("%.2f", emaCacheMultiplier),
                Math.round(lastGpuUtil * 100.0), Math.round((isVulcan ? 1.0 : getTargetGpuUtil()) * 100),
                Math.round(lastRamUtil * 100.0), Math.round(configuredRamCap * 100.0),
                upAdjustments, downAdjustments, holdCount, evaluations,
                gpuOffloadCycles, adaptiveBoosts,
                ramCapTriggerCount, criticalPressureCount,
                hardLimitBreaches, gpuHardLimitBreaches, ramHardLimitBreaches);
    }
}
