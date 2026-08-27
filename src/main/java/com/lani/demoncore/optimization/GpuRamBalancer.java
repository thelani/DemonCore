package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class GpuRamBalancer {

    private GpuRamBalancer() {
    }

    
    private static volatile double cacheMultiplier = 1.0;

    
    private static volatile double integralAccum;

    
    private static long lastEvalMs;

    
    private static volatile double lastGpuUtil;
    private static volatile double lastRamUtil;
    private static volatile double targetGpuUtil;
    private static volatile int upAdjustments;
    private static volatile int downAdjustments;
    private static volatile int holdCount;
    private static volatile long evaluations;

    public static final double MIN_MULTIPLIER = 0.4;
    public static final double MAX_MULTIPLIER = 1.6;

    
    private static final double KP = 0.08;
    
    private static final double KI = 0.005;

    
    public static void evaluate() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.GPU_RAM_BALANCER, true)) {
            cacheMultiplier = 1.0;
            return;
        }
        long now = System.currentTimeMillis();
        
        if (now - lastEvalMs < 500L) {
            return;
        }
        lastEvalMs = now;
        evaluations++;

        
        double budget = FrameProfiler.targetFrameTimeMs();
        double gpu = FrameProfiler.getGpuWaitMs();
        double gpuUtil = budget > 0.001 ? Math.min(1.0, gpu / budget) : 0.0;
        double ramUtil = GCStutterGuard.getHeapUsage();

        double target = DemonCoreConfig.getDouble(DemonCoreConfig.GPU_TARGET_UTIL, 0.60);
        double ramCap = DemonCoreConfig.getDouble(DemonCoreConfig.RAM_MAX_USAGE, 0.45);

        lastGpuUtil = gpuUtil;
        lastRamUtil = ramUtil;
        targetGpuUtil = target;

        
        
        double gpuError = gpuUtil - target;
        double ramError = ramUtil - (ramCap * 0.95); 

        double effectiveError;
        if (ramError > 0.0) {
            
            
            effectiveError = Math.min(gpuError, -ramError * 1.5);
        } else {
            effectiveError = gpuError;
        }

        
        integralAccum = Math.max(-0.5, Math.min(0.5, integralAccum + KI * effectiveError));
        double delta = KP * effectiveError + integralAccum;

        double prev = cacheMultiplier;
        double next = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, cacheMultiplier + delta));
        cacheMultiplier = next;

        
        if (Math.abs(next - prev) < 0.01) {
            holdCount++;
        } else if (next > prev) {
            upAdjustments++;
        } else {
            downAdjustments++;
        }

        
        GeometryCache.applyCacheMultiplier(next);
    }

    
    public static double getCacheMultiplier() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.GPU_RAM_BALANCER, true)) {
            return 1.0;
        }
        return cacheMultiplier;
    }

    public static double getLastGpuUtil() { return lastGpuUtil; }
    public static double getLastRamUtil() { return lastRamUtil; }
    public static double getTargetGpuUtil() { return targetGpuUtil; }
    public static int getUpAdjustments() { return upAdjustments; }
    public static int getDownAdjustments() { return downAdjustments; }
    public static int getHoldCount() { return holdCount; }
    public static long getEvaluations() { return evaluations; }

    public static String getStats() {
        boolean on = DemonCoreConfig.getBool(DemonCoreConfig.GPU_RAM_BALANCER, true);
        if (!on) {
            return "GpuRamBalancer: OFF";
        }
        return String.format("GpuRamBalancer: mult %.2f | GPU %.0f%%/%.0f%% target | RAM %.0f%%/%.0f%% cap | %d up / %d dn / %d hold | %d evals",
                cacheMultiplier,
                lastGpuUtil * 100.0, targetGpuUtil * 100.0,
                lastRamUtil * 100.0,
                DemonCoreConfig.getDouble(DemonCoreConfig.RAM_MAX_USAGE, 0.45) * 100.0,
                upAdjustments, downAdjustments, holdCount, evaluations);
    }
}
