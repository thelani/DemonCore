package com.lani.demoncore.optimization;

import com.lani.demoncore.compat.ModCompat;
import com.lani.demoncore.compat.chunk.ChunkModCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdaptiveTickBudget {

    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore/TickBudget");

    private AdaptiveTickBudget() {}

    private static volatile double currentBudgetScale = 1.0;
    private static volatile double emaScale = 1.0;
    private static volatile double integral;
    private static volatile double lastError;
    private static volatile int throttledEntities;
    private static volatile int totalEntities;
    private static volatile long lastAdjustmentMs;
    private static volatile long lastDebugLogMs;
    private static volatile int adjustmentsUp;
    private static volatile int adjustmentsDown;
    private static volatile int holdCount;
    private static volatile long evaluations;
    private static volatile int hardLimitDrops;
    private static volatile int gpuTriggeredDrops;
    private static volatile int ramTriggeredDrops;
    private static volatile int softLimitAdjustments;
    private static volatile int bottleneckAdjustments;
    private static volatile int settlingAdjustments;
    private static volatile double entityPressureFactor = 1.0;

    private static final double KP = 0.09;
    private static final double KI = 0.012;
    private static final double KD = 0.035;
    private static final double EMA_ALPHA = 0.28;
    private static final double ENTITY_PRESSURE_ALPHA = 0.18;

    private static final long DEBUG_LOG_THROTTLE_MS = 5000L;

    public static void adjustBudget() {
        if (DemonCoreConfig.isVulcanMode() || ModCompat.hasTickOptimizer()) {
            currentBudgetScale = 1.0;
            emaScale = 1.0;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAdjustmentMs < 500L) return;
        lastAdjustmentMs = now;
        evaluations++;

        PerformanceMonitor.Level level;
        try {
            level = PerformanceMonitor.getServerLevel();
            if (level == null) level = PerformanceMonitor.Level.FAIR;
        } catch (Exception e) {
            level = PerformanceMonitor.Level.FAIR;
        }

        double baseTarget = switch (level) {
            case EXCELLENT -> 1.00;
            case GOOD      -> 0.92;
            case FAIR      -> 0.78;
            case POOR      -> 0.58;
            case CRITICAL  -> 0.38;
        };

        double gpuUtil = GpuRamBalancer.getLastGpuUtil();
        double ramUtil = GpuRamBalancer.getLastRamUtil();
        double hardGpu = GpuRamBalancer.getHardGpuCap();
        double hardRam = GpuRamBalancer.getHardRamCap();
        double softGpu = GpuRamBalancer.getGpuSoftTarget();
        double softRam = GpuRamBalancer.getRamSoftTarget();
        double targetMspt = DemonCoreConfig.getDouble(DemonCoreConfig.TARGET_MSPT, 38.0);
        double actualMspt = PerformanceMonitor.getAverageMspt();

        double target = baseTarget;
        String trigger = "perfLevel";

        boolean overSoftGpu = gpuUtil > softGpu;
        boolean overSoftRam = ramUtil > softRam;
        boolean overHardGpu = gpuUtil > hardGpu;
        boolean overHardRam = ramUtil > hardRam;

        if (overSoftGpu || overSoftRam) {
            double softPenalty = 0.0;
            if (overSoftGpu) softPenalty += 0.10 + (gpuUtil - softGpu) * 0.35;
            if (overSoftRam) softPenalty += 0.08 + (ramUtil - softRam) * 0.28;
            double candidate = Math.max(0.55, baseTarget - softPenalty);
            if (candidate < target) {
                target = candidate;
                trigger = (overSoftGpu ? "GPU>SOFT " : "") + (overSoftRam ? "RAM>SOFT" : "");
                softLimitAdjustments++;
            }
        }

        if (overHardGpu) {
            target = Math.min(target, 0.75);
            gpuTriggeredDrops++;
            hardLimitDrops++;
            trigger = "GPU>HARD";
        }
        if (overHardRam) {
            target = Math.min(target, 0.70);
            ramTriggeredDrops++;
            hardLimitDrops++;
            trigger = "RAM>HARD";
        }
        if (overHardGpu && overHardRam) {
            target = Math.min(target, 0.55);
            trigger = "GPU+RAM>HARD";
        }

        BottleneckDetector.Bottleneck bn = BottleneckDetector.get();
        double bottleneckFactor = 1.0;
        String bnTag = "";
        if (bn != BottleneckDetector.Bottleneck.UNKNOWN && actualMspt > targetMspt) {
            switch (bn) {
                case CPU_LOGIC -> {
                    bottleneckFactor = 0.68;
                    bnTag = "CPU_LOGIC";
                }
                case CPU_RENDER -> {
                    bottleneckFactor = 0.78;
                    bnTag = "CPU_RENDER";
                }
                case GPU -> {
                    bottleneckFactor = 0.82;
                    bnTag = "GPU";
                }
                case MEMORY -> {
                    bottleneckFactor = 0.70;
                    bnTag = "MEMORY";
                }
                default -> { }
            }
            if (bottleneckFactor < 1.0) {
                target = Math.min(target, bottleneckFactor);
                trigger = bnTag + " bottleneck";
                bottleneckAdjustments++;
            }
        }

        if (DimensionChangeOptimizer.isSettling()) {
            target = Math.min(target, 0.65);
            trigger = "dimension-settling";
            settlingAdjustments++;
        }

        if (totalEntities > 0) {
            double ratio = (double) throttledEntities / (double) totalEntities;
            double instPressure = 1.0 - 0.35 * ratio;
            entityPressureFactor += ENTITY_PRESSURE_ALPHA * (instPressure - entityPressureFactor);
            target = Math.min(target, Math.max(0.50, target * entityPressureFactor));
        } else {
            entityPressureFactor = 1.0;
        }

        int compatLevel = ChunkModCompat.getCompatibilityLevel();
        if (compatLevel >= 2 && level == PerformanceMonitor.Level.FAIR) {
            target = Math.min(1.0, target + 0.04);
        }

        if (targetMspt > 0 && actualMspt > targetMspt * 1.15) {
            double ratio = targetMspt / actualMspt;
            target = Math.min(target, Math.max(0.35, ratio));
            trigger = "MSPT overbudget (" + String.format("%.1f", actualMspt) + "ms)";
        }

        double error = target - currentBudgetScale;
        double derivative = error - lastError;
        lastError = error;

        integral = Math.max(-0.30, Math.min(0.30, integral + KI * error));
        double delta = KP * error + integral + KD * derivative;

        double prev = currentBudgetScale;
        double next;
        if (overHardRam || overHardGpu) {
            next = Math.max(0.25, Math.min(target, currentBudgetScale + Math.min(delta, -0.05)));
        } else if (overSoftRam || overSoftGpu) {
            double upCap = delta > 0 ? 0.018 : delta;
            next = Math.max(0.40, Math.min(target, currentBudgetScale + upCap));
        } else {
            if (delta > 0.0) {
                next = Math.min(1.0, currentBudgetScale + Math.min(delta, 0.035));
            } else {
                next = Math.max(0.25, currentBudgetScale + Math.max(delta, -0.06));
            }
        }
        currentBudgetScale = next;
        emaScale += EMA_ALPHA * (next - emaScale);

        if (Math.abs(next - prev) < 0.005) {
            holdCount++;
        } else if (next > prev) {
            adjustmentsUp++;
        } else {
            adjustmentsDown++;
        }

        if ((DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false))
                && now - lastDebugLogMs >= DEBUG_LOG_THROTTLE_MS) {
            lastDebugLogMs = now;
            LOGGER.info("[TickBudget] Eval #{}: {} -> {} (EMA {}) | target {} (via {}) | err {} "
                            + "| GPU {}/{}[{}] | RAM {}/{}[{}] | {}u/{}d/{}h "
                            + "| hardDrops={}(G{} R{}) | softAdj={} bnAdj={} settlingAdj={} entityPres={}",
                    evaluations,
                    String.format("%.2f", prev), String.format("%.2f", next),
                    String.format("%.2f", emaScale),
                    String.format("%.2f", target), trigger,
                    String.format("%.3f", error),
                    Math.round(gpuUtil * 100.0), Math.round(softGpu * 100.0), Math.round(hardGpu * 100.0),
                    Math.round(ramUtil * 100.0), Math.round(softRam * 100.0), Math.round(hardRam * 100.0),
                    adjustmentsUp, adjustmentsDown, holdCount,
                    hardLimitDrops, gpuTriggeredDrops, ramTriggeredDrops,
                    softLimitAdjustments, bottleneckAdjustments, settlingAdjustments,
                    String.format("%.2f", entityPressureFactor));
        }
    }

    public static double getBudgetScale() {
        adjustBudget();
        return emaScale;
    }

    public static double getRawBudgetScale() {
        return currentBudgetScale;
    }

    public static void reportEntityCounts(int throttled, int total) {
        throttledEntities = throttled;
        totalEntities = total;
    }

    public static int getThrottledEntities() { return throttledEntities; }
    public static int getTotalEntities() { return totalEntities; }
    public static double getEmaScale() { return emaScale; }
    public static long getEvaluations() { return evaluations; }
    public static int getAdjustmentsUp() { return adjustmentsUp; }
    public static int getAdjustmentsDown() { return adjustmentsDown; }
    public static int getHoldCount() { return holdCount; }
    public static int getHardLimitDrops() { return hardLimitDrops; }
    public static int getGpuTriggeredDrops() { return gpuTriggeredDrops; }
    public static int getRamTriggeredDrops() { return ramTriggeredDrops; }
    public static int getSoftLimitAdjustments() { return softLimitAdjustments; }
    public static int getBottleneckAdjustments() { return bottleneckAdjustments; }
    public static int getSettlingAdjustments() { return settlingAdjustments; }
    public static double getEntityPressureFactor() { return entityPressureFactor; }

    public static String getStats() {
        boolean throttleEnabled = DemonCoreConfig.getBool(DemonCoreConfig.TICK_THROTTLE_ENABLED, true);
        boolean locked = DemonCoreConfig.isVulcanMode() || ModCompat.hasTickOptimizer();
        return String.format("TickBudget: EMA %d%% (raw %d%%) | %d/%d entities throttled (pres=%.2f) | "
                        + "%d up / %d dn / %d hold | eval=%d | hardDrops=%d(G%d R%d) | "
                        + "softAdj=%d bnAdj=%d settleAdj=%d | throttle %s",
                Math.round(emaScale * 100.0), Math.round(currentBudgetScale * 100.0),
                throttledEntities, totalEntities, entityPressureFactor,
                adjustmentsUp, adjustmentsDown, holdCount,
                evaluations, hardLimitDrops, gpuTriggeredDrops, ramTriggeredDrops,
                softLimitAdjustments, bottleneckAdjustments, settlingAdjustments,
                locked ? "LOCKED(Vulcan/Mod)" : (throttleEnabled ? "ON" : "OFF"));
    }
}
