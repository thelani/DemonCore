package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;

import java.util.Arrays;

public final class FrameProfiler {

    private FrameProfiler() {
    }

    private static final int SAMPLES = 256;

    private static final long[] frameTimes = new long[SAMPLES];
    private static int sampleIndex;
    private static int sampleCount;

    private static long lastFrameStartNs;
    private static long levelRenderStartNs;
    private static long swapStartNs;

    private static volatile double frameTimeMs;
    private static volatile double cpuRenderMs;
    private static volatile double gpuWaitMs;
    private static volatile double fps;
    private static volatile double onePercentLowFps;

    private static double smoothedFrameMs = 16.0;
    private static double smoothedCpuMs = 8.0;
    private static double smoothedGpuWaitMs = 4.0;

    private static volatile double quality = 1.0;
    private static volatile boolean spiking;

    private static long lastPercentileNs;
    private static long spikeUntilNs;

    private static long totalFrames;
    private static long spikeCount;

    
    public static long getTotalFrames() {
        return totalFrames;
    }

    public static long getSpikeCount() {
        return spikeCount;
    }

    
    
    

    public static void onLevelRenderStart() {
        long now = System.nanoTime();

        if (lastFrameStartNs != 0L) {
            long delta = now - lastFrameStartNs;
            
            if (delta > 0L && delta < 2_000_000_000L) {
                recordFrame(delta);
            }
        }
        lastFrameStartNs = now;
        levelRenderStartNs = now;
    }

    public static void onLevelRenderEnd() {
        if (levelRenderStartNs == 0L) {
            return;
        }
        long cpu = System.nanoTime() - levelRenderStartNs;
        smoothedCpuMs += 0.08 * ((cpu / 1_000_000.0) - smoothedCpuMs);
        cpuRenderMs = smoothedCpuMs;
    }

    public static void onSwapStart() {
        swapStartNs = System.nanoTime();
    }

    public static void onSwapEnd() {
        if (swapStartNs == 0L) {
            return;
        }
        long wait = System.nanoTime() - swapStartNs;
        if (wait < 0L || wait > 1_000_000_000L) {
            return;
        }
        smoothedGpuWaitMs += 0.08 * ((wait / 1_000_000.0) - smoothedGpuWaitMs);
        gpuWaitMs = smoothedGpuWaitMs;
    }

    

    private static void recordFrame(long deltaNs) {
        totalFrames++;

        frameTimes[sampleIndex] = deltaNs;
        sampleIndex = (sampleIndex + 1) % SAMPLES;
        if (sampleCount < SAMPLES) {
            sampleCount++;
        }

        double ms = deltaNs / 1_000_000.0;
        smoothedFrameMs += 0.08 * (ms - smoothedFrameMs);
        frameTimeMs = smoothedFrameMs;
        fps = smoothedFrameMs > 0.0001 ? 1000.0 / smoothedFrameMs : 0.0;

        long now = System.nanoTime();

        detectSpike(deltaNs, now);

        if (now - lastPercentileNs > 500_000_000L) {
            lastPercentileNs = now;
            updatePercentile();
            updateQuality();
        }
    }

    private static void detectSpike(long deltaNs, long nowNs) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.SPIKE_PROTECTION, true)) {
            spiking = false;
            return;
        }
        if (sampleCount < 32) {
            return;
        }

        double ms = deltaNs / 1_000_000.0;
        double budget = targetFrameTimeMs();

        
        if (ms > smoothedFrameMs * 2.0 && ms > budget * 1.8) {
            spikeCount++;
            
            spikeUntilNs = nowNs + 250_000_000L;
        }
        spiking = nowNs < spikeUntilNs;
    }

    private static void updatePercentile() {
        if (sampleCount < 16) {
            onePercentLowFps = fps;
            return;
        }
        long[] sorted = Arrays.copyOf(frameTimes, sampleCount);
        Arrays.sort(sorted);
        int idx = Math.min(sorted.length - 1, (int) (sorted.length * 0.99));
        double worstMs = sorted[idx] / 1_000_000.0;
        onePercentLowFps = worstMs > 0.0001 ? 1000.0 / worstMs : 0.0;
    }

    private static void updateQuality() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_QUALITY, true)) {
            quality = 1.0;
            return;
        }

        double minQuality = DemonCoreConfig.getDouble(DemonCoreConfig.MIN_QUALITY, 0.35);
        if (minQuality >= 1.0) {
            quality = 1.0;
            return;
        }

        double budget = targetFrameTimeMs();
        double ratio = smoothedFrameMs / budget;
        double q = quality;

        if (ratio > 1.25) {
            q -= 0.08;
        } else if (ratio > 1.05) {
            q -= 0.03;
        } else if (ratio < 0.75) {
            q += 0.06;
        } else if (ratio < 0.92) {
            q += 0.02;
        }

        quality = Math.max(minQuality, Math.min(1.0, q));
    }

    
    public static double targetFrameTimeMs() {
        int configured = DemonCoreConfig.getInt(DemonCoreConfig.TARGET_FPS, 0);
        if (configured <= 0) {
            configured = 60;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) {
                int limit = mc.options.framerateLimit().get();
                
                configured = limit >= 260 ? 144 : limit;
            }
        }
        configured = Math.max(15, Math.min(1000, configured));
        return 1000.0 / configured;
    }

    
    
    

    
    public static double getQuality() {
        if (spiking) {
            return quality * 0.7;
        }
        return quality;
    }

    public static double getRawQuality() {
        return quality;
    }

    public static boolean isSpiking() {
        return spiking;
    }

    public static double getFps() {
        return fps;
    }

    public static double getFrameTimeMs() {
        return frameTimeMs;
    }

    public static double getCpuRenderMs() {
        return cpuRenderMs;
    }

    public static double getGpuWaitMs() {
        return gpuWaitMs;
    }

    public static double getOnePercentLowFps() {
        return onePercentLowFps;
    }

    public static void reset() {
        sampleIndex = 0;
        sampleCount = 0;
        totalFrames = 0;
        spikeCount = 0;
        quality = 1.0;
        spiking = false;
        smoothedFrameMs = 16.0;
        smoothedCpuMs = 8.0;
        smoothedGpuWaitMs = 4.0;
    }

    public static String getStats() {
        return String.format(
                "Frame: %.1f FPS (%.2f ms) | 1%% low %.1f FPS | CPU %.2f ms | GPU wait %.2f ms | quality %.0f%% | spikes %d",
                fps, frameTimeMs, onePercentLowFps, cpuRenderMs, gpuWaitMs, getQuality() * 100.0, spikeCount);
    }
}
