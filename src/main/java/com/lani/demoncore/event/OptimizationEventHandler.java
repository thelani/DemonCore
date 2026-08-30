package com.lani.demoncore.event;

import com.lani.demoncore.DemonCore;
import com.lani.demoncore.compat.chunk.ChunkModCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.optimization.CacheSystem;
import com.lani.demoncore.optimization.GeometryCache;
import com.lani.demoncore.optimization.HardwareMonitor;
import com.lani.demoncore.optimization.PerformanceMonitor;
import com.lani.demoncore.optimization.TickThrottleSystem;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OptimizationEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore-OptEvents");

    private static volatile PerformanceMonitor.Level lastAppliedLevel = PerformanceMonitor.Level.FAIR;
    private static volatile HardwareMonitor.ComponentPressure lastCpuPressure = HardwareMonitor.ComponentPressure.IDLE;
    private static volatile HardwareMonitor.ComponentPressure lastRamPressure = HardwareMonitor.ComponentPressure.IDLE;
    private static volatile HardwareMonitor.ComponentPressure lastGpuPressure = HardwareMonitor.ComponentPressure.IDLE;
    private static volatile long lastLevelApplyMs;
    private static volatile long levelImprovements;
    private static volatile long levelDegradations;
    private static volatile long pressureEventsHandled;
    private static volatile double adaptiveChunkMultiplier = 1.0;
    private static volatile int adaptiveTickThrottleBonus = 0;

    private static volatile long cacheEventsHandled;
    private static volatile long predictiveEventsHandled;
    private static volatile long gpuRamEventsHandled;
    private static volatile long cacheAutoTrimsFromEvent;
    private static volatile long severeOverrunEvents;
    private static volatile long criticalBalancerEvents;

    private static volatile double lastGpuRamMultiplier = 1.0;
    private static volatile double lastCacheHitRate;
    private static volatile double lastPredictedBudgetUsage = 0.0;

    private OptimizationEventHandler() {
    }

    public static OptimizationEventHandler create() {
        return new OptimizationEventHandler();
    }

    @SubscribeEvent
    public void onLevelChange(OptimizationLevelChangeEvent event) {
        PerformanceMonitor.Level target = event.getCurrent();
        PerformanceMonitor.Level previous = event.getPrevious();

        if (event.isDegradation()) {
            levelDegradations++;
            applyDegradationPolicy(target, previous);
        } else if (event.isImprovement()) {
            levelImprovements++;
            applyImprovementPolicy(target, previous);
        }
        lastAppliedLevel = target;
        lastLevelApplyMs = System.currentTimeMillis();

        if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
            LOGGER.info("[OptEvent] Level {} -> {} ({}) | budgetScale {} -> {}",
                    previous.name(), target.name(),
                    event.isImprovement() ? "IMPROVE" : event.isDegradation() ? "DEGRADE" : "FLAT",
                    String.format("%.2f", previous.budgetScale()),
                    String.format("%.2f", target.budgetScale()));
        }
    }

    private void applyDegradationPolicy(PerformanceMonitor.Level target, PerformanceMonitor.Level previous) {
        double scale = target.budgetScale();
        if (target.ordinal() >= PerformanceMonitor.Level.POOR.ordinal()) {
            adaptiveChunkMultiplier = Math.max(0.3, scale - 0.1);
            adaptiveTickThrottleBonus = Math.min(2, previous.ordinal() - target.ordinal());
        } else if (target.ordinal() >= PerformanceMonitor.Level.FAIR.ordinal()) {
            adaptiveChunkMultiplier = Math.max(0.55, scale);
            adaptiveTickThrottleBonus = Math.min(1, previous.ordinal() - target.ordinal());
        } else {
            adaptiveChunkMultiplier = Math.max(0.75, scale);
            adaptiveTickThrottleBonus = 0;
        }
    }

    private void applyImprovementPolicy(PerformanceMonitor.Level target, PerformanceMonitor.Level previous) {
        double scale = target.budgetScale();
        if (target.ordinal() <= PerformanceMonitor.Level.GOOD.ordinal()) {
            adaptiveChunkMultiplier = Math.min(1.25, scale + 0.1);
            adaptiveTickThrottleBonus = Math.max(-1, target.ordinal() - previous.ordinal());
        } else {
            adaptiveChunkMultiplier = Math.min(1.1, scale);
            adaptiveTickThrottleBonus = 0;
        }
    }

    @SubscribeEvent
    public void onHardwarePressure(HardwarePressureEvent event) {
        pressureEventsHandled++;

        HardwareMonitor.ComponentPressure cpu = event.getCpuPressure();
        HardwareMonitor.ComponentPressure ram = event.getRamPressure();
        HardwareMonitor.ComponentPressure gpu = event.getGpuPressure();

        if (event.isCritical()) {
            handleCriticalPressure(event);
        } else if (event.isHighOrWorse()) {
            handleHighPressure(event);
        }

        lastCpuPressure = cpu;
        lastRamPressure = ram;
        lastGpuPressure = gpu;
    }

    private void handleCriticalPressure(HardwarePressureEvent event) {
        String dominant = event.getDominantComponentName();
        switch (dominant) {
            case "CPU" -> {
                adaptiveChunkMultiplier = Math.min(adaptiveChunkMultiplier, 0.35);
                adaptiveTickThrottleBonus = Math.max(adaptiveTickThrottleBonus, 2);
            }
            case "RAM" -> {
                GeometryCache.trimStale();
                CacheSystem.trimAll(0.5);
            }
            case "GPU" -> {
                GeometryCache.applyCacheMultiplier(
                        com.lani.demoncore.optimization.GpuRamBalancer.getCacheMultiplier()
                );
            }
        }
        if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
            LOGGER.warn("[OptEvent] CRITICAL pressure on {} | systemScore={}%",
                    dominant, String.format("%.0f", event.getSystemScore() * 100.0));
        }
    }

    private void handleHighPressure(HardwarePressureEvent event) {
        String dominant = event.getDominantComponentName();
        switch (dominant) {
            case "CPU" -> adaptiveChunkMultiplier = Math.min(adaptiveChunkMultiplier, 0.65);
            case "RAM" -> CacheSystem.trimAll(0.8);
            case "GPU" -> GeometryCache.applyCacheMultiplier(0.7);
        }
    }

    @SubscribeEvent
    public void onBottleneckChange(BottleneckChangedEvent event) {
        if (!event.isNewBottleneck()) {
            return;
        }
        if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
            LOGGER.info("[OptEvent] Bottleneck {} -> {} | advice: {}",
                    event.getPrevious() == null ? "?" : event.getPrevious().label(),
                    event.getCurrent().label(),
                    event.getAdvice());
        }

        if (event.isMemoryBound()) {
            GeometryCache.trimStale();
        }
    }

    @SubscribeEvent
    public void onVehicleSpeed(VehicleSpeedEvent event) {
        double threshold = DemonCoreConfig.getDouble(DemonCoreConfig.SPEED_THRESHOLD, 24.0);
        if (event.getPhase() == VehicleSpeedEvent.Phase.THRESHOLD_CROSSED
                && event.getSpeedBps() > threshold) {
            PerformanceMonitor.Level level = PerformanceMonitor.getLastOverallLevel();
            if (level.ordinal() >= PerformanceMonitor.Level.FAIR.ordinal()) {
                adaptiveChunkMultiplier = Math.min(1.15, adaptiveChunkMultiplier * 1.08);
            }
        }
        if (event.getPhase() == VehicleSpeedEvent.Phase.IDLE_DETECTED) {
            adaptiveChunkMultiplier = Math.max(0.85, adaptiveChunkMultiplier * 0.95);
        }
    }

    @SubscribeEvent
    public void onCacheEvent(CacheEvent event) {
        cacheEventsHandled++;
        CacheEvent.Action action = event.getAction();
        CacheEvent.CacheType type = event.getCacheType();
        lastCacheHitRate = event.getHitRate();

        if (event.isCriticalPressure()) {
            cacheAutoTrimsFromEvent++;
            CacheSystem.autoTrimIfNeeded();
            if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                LOGGER.info("[CacheEvent] CRITICAL {} -> {} | size {}/{} | hitRate {}% | {}",
                        type.name(), action.name(),
                        event.getCurrentSize(), event.getMaxSize(),
                        String.format("%.1f", event.getHitRate() * 100.0),
                        event.getDetail());
            }
        }

        if (action == CacheEvent.Action.AUTO_TRIM || action == CacheEvent.Action.TRIM) {
            cacheAutoTrimsFromEvent++;
            adaptiveTickThrottleBonus = Math.max(adaptiveTickThrottleBonus, 1);
            if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                LOGGER.info("[CacheEvent] TRIM/AUTO_TRIM {} | {} entries removed | keepFraction={} | newSize={} | {}",
                        type.name(), event.getEntriesAffected(),
                        String.format("%.2f", event.getKeepFraction()),
                        event.getCurrentSize(), event.getDetail());
            }
        }

        if (action == CacheEvent.Action.RESIZE && event.isCriticalPressure()) {
            GeometryCache.trimStale();
        }
    }

    @SubscribeEvent
    public void onPredictiveFrameEvent(PredictiveFrameEvent event) {
        predictiveEventsHandled++;
        PredictiveFrameEvent.Phase phase = event.getPhase();
        lastPredictedBudgetUsage = event.getBudgetUsagePct();

        if (phase == PredictiveFrameEvent.Phase.PREEMPTIVE_SLEEP_ISSUED) {
            if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                LOGGER.debug("[PredEvent] PREEMPTIVE_SLEEP | slept={}us | pred={}ms / budget={}ms | overrun={}ms | savedFrames={}/{}",
                        event.getSleepNs() / 1000L,
                        String.format("%.2f", event.getPredictedMs()),
                        String.format("%.2f", event.getBudgetMs()),
                        String.format("%.2f", event.getOverrunMs()),
                        event.getSavedFromOverrun(), event.getFramesPredicted());
            }
        } else if (event.isSevereOverrun()) {
            severeOverrunEvents++;
            if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                LOGGER.warn("[PredEvent] SEVERE OVERRUN | phase={} | pred={}ms / budget={}ms | CPU={}ms GPU={}ms | usage={}%",
                        phase.name(),
                        String.format("%.2f", event.getPredictedMs()),
                        String.format("%.2f", event.getBudgetMs()),
                        String.format("%.2f", event.getCpuMs()),
                        String.format("%.2f", event.getGpuMs()),
                        String.format("%.0f", event.getBudgetUsagePct() * 100.0));
            }
            adaptiveChunkMultiplier = Math.max(0.4, adaptiveChunkMultiplier * 0.9);
        } else if (phase == PredictiveFrameEvent.Phase.BUDGET_EXCEEDED) {
            double usage = event.getBudgetUsagePct();
            if (usage > 1.10) {
                adaptiveChunkMultiplier = Math.max(0.5, adaptiveChunkMultiplier * 0.95);
            }
        } else if (phase == PredictiveFrameEvent.Phase.DISABLED) {
            lastPredictedBudgetUsage = 0.0;
        }
    }

    @SubscribeEvent
    public void onGpuRamBalanceEvent(GpuRamBalanceEvent event) {
        gpuRamEventsHandled++;
        GpuRamBalanceEvent.Action action = event.getAction();
        lastGpuRamMultiplier = event.getCacheMultiplier();

        switch (action) {
            case ADJUST_DOWN -> {
                if (event.isSignificantChange()) {
                    GeometryCache.trimStale();
                }
            }
            case RAM_CAP_TRIGGERED -> {
                CacheSystem.trimAll(0.75);
                if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                    LOGGER.info("[GPURAM-Event] RAM_CAP triggered | mult={} RAM={}%/{}% cap | GPU={}%/{}% target",
                            String.format("%.2f", event.getCacheMultiplier()),
                            String.format("%.0f", event.getRamUtil() * 100.0),
                            String.format("%.0f", event.getRamCap() * 100.0),
                            String.format("%.0f", event.getGpuUtil() * 100.0),
                            String.format("%.0f", event.getTargetGpuUtil() * 100.0));
                }
            }
            case CRITICAL_PRESSURE -> {
                criticalBalancerEvents++;
                CacheSystem.trimAll(0.6);
                GeometryCache.trimStale();
                adaptiveTickThrottleBonus = Math.max(adaptiveTickThrottleBonus, 2);
                if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                    LOGGER.warn("[GPURAM-Event] CRITICAL_PRESSURE | mult={} | GPU={}% RAM={}% | delta={} | reason: {}",
                            String.format("%.2f", event.getCacheMultiplier()),
                            String.format("%.0f", event.getGpuUtil() * 100.0),
                            String.format("%.0f", event.getRamUtil() * 100.0),
                            String.format("%.3f", event.getMultiplierDelta()),
                            event.getReason());
                }
            }
            case CACHE_MULTIPLIER_APPLIED -> {
                if (event.isSignificantChange() && event.getMultiplierDelta() < 0) {
                    GeometryCache.trimStale();
                }
                if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                    LOGGER.info("[GPURAM-Event] MULTIPLIER_CHANGE | {} -> {} (delta {}) | reason: {}",
                            String.format("%.2f", event.getPreviousMultiplier()),
                            String.format("%.2f", event.getCacheMultiplier()),
                            String.format("%.3f", event.getMultiplierDelta()),
                            event.getReason());
                }
            }
            case ADJUST_UP, HOLD, EVALUATION, TARGET_HIT, DISABLED -> {
            }
        }
    }

    public static int getEffectiveChunksPerTick(int base) {
        double budget = PerformanceMonitor.getBudgetScale(PerformanceMonitor.AggregateDomain.OVERALL);
        double mult = Math.max(0.25, Math.min(1.6, adaptiveChunkMultiplier * budget));
        if (HardwareMonitor.getCpuPressure().ordinal() >= HardwareMonitor.ComponentPressure.HIGH.ordinal()) {
            mult *= 0.75;
        }
        int baseResult = Math.max(1, (int) Math.round(base * mult));

        int compatCap = ChunkModCompat.getChunksPerTickCap(DemonCoreConfig.getInt(DemonCoreConfig.CHUNKS_PER_TICK, 16));
        if (compatCap > 0) {
            baseResult = Math.min(baseResult, compatCap);
        }

        double compatMult = ChunkModCompat.getPrefetchMultiplier();
        if (compatMult > 0.0 && compatMult != 1.0) {
            baseResult = (int) Math.round(baseResult * compatMult);
            baseResult = Math.max(1, baseResult);
        }

        return baseResult;
    }

    public static int getEffectiveMaxChunks(int base) {
        double scale = lastAppliedLevel.budgetScale();
        double mult = Math.max(0.3, Math.min(1.4, adaptiveChunkMultiplier * scale));
        return Math.max(8, (int) Math.round(base * mult));
    }

    public static int getEffectiveThrottleDistance(int base) {
        double scale = lastAppliedLevel.budgetScale();
        double bonus = adaptiveTickThrottleBonus > 0 ? 1.0 - adaptiveTickThrottleBonus * 0.1 : 1.0;
        double mult = Math.max(0.4, Math.min(1.2, scale * bonus));
        return Math.max(16, (int) Math.round(base * mult));
    }

    public static PerformanceMonitor.Level getLastAppliedLevel() {
        return lastAppliedLevel;
    }

    public static HardwareMonitor.ComponentPressure getLastCpuPressure() { return lastCpuPressure; }
    public static HardwareMonitor.ComponentPressure getLastRamPressure() { return lastRamPressure; }
    public static HardwareMonitor.ComponentPressure getLastGpuPressure() { return lastGpuPressure; }

    public static long getLastLevelApplyMs() { return lastLevelApplyMs; }
    public static long getLevelImprovements() { return levelImprovements; }
    public static long getLevelDegradations() { return levelDegradations; }
    public static long getPressureEventsHandled() { return pressureEventsHandled; }
    public static double getAdaptiveChunkMultiplier() { return adaptiveChunkMultiplier; }
    public static int getAdaptiveTickThrottleBonus() { return adaptiveTickThrottleBonus; }

    public static long getCacheEventsHandled() { return cacheEventsHandled; }
    public static long getPredictiveEventsHandled() { return predictiveEventsHandled; }
    public static long getGpuRamEventsHandled() { return gpuRamEventsHandled; }
    public static long getCacheAutoTrimsFromEvent() { return cacheAutoTrimsFromEvent; }
    public static long getSevereOverrunEvents() { return severeOverrunEvents; }
    public static long getCriticalBalancerEvents() { return criticalBalancerEvents; }

    public static double getLastGpuRamMultiplier() { return lastGpuRamMultiplier; }
    public static double getLastCacheHitRate() { return lastCacheHitRate; }
    public static double getLastPredictedBudgetUsage() { return lastPredictedBudgetUsage; }

    public static void reset() {
        lastAppliedLevel = PerformanceMonitor.Level.FAIR;
        lastCpuPressure = HardwareMonitor.ComponentPressure.IDLE;
        lastRamPressure = HardwareMonitor.ComponentPressure.IDLE;
        lastGpuPressure = HardwareMonitor.ComponentPressure.IDLE;
        lastLevelApplyMs = 0L;
        levelImprovements = 0L;
        levelDegradations = 0L;
        pressureEventsHandled = 0L;
        adaptiveChunkMultiplier = 1.0;
        adaptiveTickThrottleBonus = 0;
        cacheEventsHandled = 0L;
        predictiveEventsHandled = 0L;
        gpuRamEventsHandled = 0L;
        cacheAutoTrimsFromEvent = 0L;
        severeOverrunEvents = 0L;
        criticalBalancerEvents = 0L;
        lastGpuRamMultiplier = 1.0;
        lastCacheHitRate = 0.0;
        lastPredictedBudgetUsage = 0.0;
    }

    public static String getStats() {
        return String.format(
                "OptEvents: applied=%s | %d improve / %d degrade | %d pressure | chunkMult=%.2f | throttleBonus=%+d | cacheEvts=%d predEvts=%d gpuRamEvts=%d severeOverruns=%d criticalBal=%d",
                lastAppliedLevel.name(), levelImprovements, levelDegradations,
                pressureEventsHandled, (double) adaptiveChunkMultiplier, adaptiveTickThrottleBonus,
                cacheEventsHandled, predictiveEventsHandled, gpuRamEventsHandled,
                severeOverrunEvents, criticalBalancerEvents);
    }
}
