package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;

import java.util.Arrays;

public final class FrameProfiler {

    private FrameProfiler() {
    }

    private static final int SAMPLES = 256;
    private static final int FAST_SAMPLES = 16; // For spike detection

    private static final long[] frameTimes = new long[SAMPLES];
    private static final long[] fastFrameTimes = new long[FAST_SAMPLES];
    private static int sampleIndex;
    private static int sampleCount;
    private static int fastIndex;

    private static long lastFrameStartNs;
    private static long levelRenderStartNs;
    private static long swapStartNs;

    private static volatile double frameTimeMs;
    private static volatile double cpuRenderMs;
    private static volatile double gpuWaitMs;
    private static volatile double fps;
    private static volatile double onePercentLowFps;
    private static volatile double zeroPointOnePercentLowFps; // 0.1% low

    // Exponential moving averages with different time constants
    private static double emaFastFrameMs = 16.0;    // Fast response (α=0.3)
    private static double emaSlowFrameMs = 16.0;    // Slow response (α=0.05)
    private static double emaCpuMs = 8.0;
    private static double emaGpuWaitMs = 4.0;

    private static volatile double quality = 1.0;
    private static volatile boolean spiking;
    private static double qualityVelocity = 0.0; // For smoother quality transitions

    private static long lastPercentileNs;
    private static long spikeUntilNs;
    private static double spikeThreshold = 2.5; // Adaptive spike threshold

    private static long totalFrames;
    private static long spikeCount;
    private static long microStutters; // Frames 10-20% over budget
    
    // Frame pacing metrics
    private static double frameTimeVariance = 0.0;
    private static double avgFrameTimeDeviation = 0.0;

    
    public static long getTotalFrames() {
        return totalFrames;
    }

    public static long getSpikeCount() {
        return spikeCount;
    }
    
    public static long getMicroStutters() {
        return microStutters;
    }
    
    public static double getFrameTimeVariance() {
        return frameTimeVariance;
    }
    
    public static double getFramePacingScore() {
        // 0 = perfect pacing, 1 = terrible pacing
        double budget = targetFrameTimeMs();
        if (budget <= 0.001) return 0.0;
        return Math.min(1.0, avgFrameTimeDeviation / budget);
    }

    public static void onLevelRenderStart() {
        long now = System.nanoTime();

        if (lastFrameStartNs != 0L) {
            long delta = now - lastFrameStartNs;
            // Sanity check: 0.5ms to 2s
            if (delta > 500_000L && delta < 2_000_000_000L) {
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
        double cpuMs = cpu / 1_000_000.0;
        
        // Dual EMA for smoother CPU tracking
        emaCpuMs += 0.12 * (cpuMs - emaCpuMs);
        cpuRenderMs = emaCpuMs;
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
        double waitMs = wait / 1_000_000.0;
        emaGpuWaitMs += 0.10 * (waitMs - emaGpuWaitMs);
        gpuWaitMs = emaGpuWaitMs;
    }

    private static void recordFrame(long deltaNs) {
        totalFrames++;

        // Store in both ring buffers
        frameTimes[sampleIndex] = deltaNs;
        sampleIndex = (sampleIndex + 1) % SAMPLES;
        if (sampleCount < SAMPLES) {
            sampleCount++;
        }
        
        fastFrameTimes[fastIndex] = deltaNs;
        fastIndex = (fastIndex + 1) % FAST_SAMPLES;

        double ms = deltaNs / 1_000_000.0;
        
        // Dual exponential moving average for adaptive response
        emaFastFrameMs += 0.30 * (ms - emaFastFrameMs); // Quick response
        emaSlowFrameMs += 0.05 * (ms - emaSlowFrameMs); // Smooth trend
        
        // Use slow EMA for display, fast EMA for spike detection
        frameTimeMs = emaSlowFrameMs;
        fps = emaSlowFrameMs > 0.0001 ? 1000.0 / emaSlowFrameMs : 0.0;
        
        // Calculate frame time variance for pacing metrics
        updateFramePacingMetrics();

        long now = System.nanoTime();

        detectSpike(ms, now);

        // Update percentiles every 500ms
        if (now - lastPercentileNs > 500_000_000L) {
            lastPercentileNs = now;
            updatePercentiles();
            updateQuality();
            updateSpikeThreshold();
        }
    }
    
    private static void updateFramePacingMetrics() {
        if (sampleCount < 8) return;
        
        // Calculate variance over recent samples
        int count = Math.min(sampleCount, 60);
        double sum = 0.0;
        double sumSq = 0.0;
        
        for (int i = 0; i < count; i++) {
            int idx = (sampleIndex - 1 - i + SAMPLES) % SAMPLES;
            double ms = frameTimes[idx] / 1_000_000.0;
            sum += ms;
            sumSq += ms * ms;
        }
        
        double mean = sum / count;
        frameTimeVariance = (sumSq / count) - (mean * mean);
        avgFrameTimeDeviation = Math.sqrt(Math.max(0.0, frameTimeVariance));
    }
    
    private static void updateSpikeThreshold() {
        // Adaptive spike threshold based on frame pacing
        // Better pacing = lower tolerance, worse pacing = higher tolerance
        double pacingScore = getFramePacingScore();
        spikeThreshold = 2.0 + pacingScore * 1.5; // Range: 2.0 to 3.5
        if (DemonCoreConfig.isVulcanMode()) {
            spikeThreshold += 1.5; // Loosen spike detection in Vulcan mode
        }
    }

    private static void detectSpike(double ms, long nowNs) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.SPIKE_PROTECTION, true)) {
            spiking = false;
            return;
        }
        if (sampleCount < 16) {
            return;
        }

        double budget = targetFrameTimeMs();
        
        // Detect major spike: frame time exceeds adaptive threshold
        boolean majorSpike = ms > emaSlowFrameMs * spikeThreshold && ms > budget * 1.6;
        
        // Detect micro-stutter: frame time 10-20% over budget
        boolean microStutter = !majorSpike && ms > budget * 1.10 && ms < budget * 1.20;
        
        if (majorSpike) {
            spikeCount++;
            // Extend protection window: 300ms for major spikes
            spikeUntilNs = nowNs + 300_000_000L;
        }
        
        if (microStutter) {
            microStutters++;
        }
        
        spiking = nowNs < spikeUntilNs;
    }

    private static void updatePercentiles() {
        if (sampleCount < 16) {
            onePercentLowFps = fps;
            zeroPointOnePercentLowFps = fps;
            return;
        }
        
        long[] sorted = Arrays.copyOf(frameTimes, sampleCount);
        Arrays.sort(sorted);
        
        // 1% low (99th percentile)
        int idx99 = Math.min(sorted.length - 1, (int) (sorted.length * 0.99));
        double worst1pct = sorted[idx99] / 1_000_000.0;
        onePercentLowFps = worst1pct > 0.0001 ? 1000.0 / worst1pct : 0.0;
        
        // 0.1% low (99.9th percentile) - most extreme frame times
        int idx999 = Math.min(sorted.length - 1, (int) (sorted.length * 0.999));
        double worst01pct = sorted[idx999] / 1_000_000.0;
        zeroPointOnePercentLowFps = worst01pct > 0.0001 ? 1000.0 / worst01pct : 0.0;
    }

    private static void updateQuality() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_QUALITY, true)) {
            quality = 1.0;
            qualityVelocity = 0.0;
            return;
        }

        double minQuality = DemonCoreConfig.getDouble(DemonCoreConfig.MIN_QUALITY, 0.35);
        if (minQuality >= 1.0) {
            quality = 1.0;
            qualityVelocity = 0.0;
            return;
        }

        double budget = targetFrameTimeMs();
        double ratio = emaSlowFrameMs / budget;
        
        // Continuous smooth quality adjustment
        double targetQuality = quality;
        double error = 1.0 - ratio;
        
        if (error < -0.10) {
            // Over budget (ratio > 1.10) -> error is negative, shrinks targetQuality proportionally
            targetQuality += error * 0.15;
        } else if (error > 0.15) {
            // Under budget (ratio < 0.85) -> error is positive, grows targetQuality proportionally
            targetQuality += error * 0.08;
        }
        
        // Apply velocity for momentum (prevents oscillation)
        qualityVelocity = 0.7 * qualityVelocity + 0.3 * (targetQuality - quality);
        quality += qualityVelocity;
        
        // Clamp to valid range
        quality = Math.max(minQuality, Math.min(1.0, quality));
        
        // Additional penalty during bad frame pacing
        double pacingPenalty = getFramePacingScore() * 0.15;
        if (pacingPenalty > 0.05) {
            quality = Math.max(minQuality, quality - pacingPenalty);
        }
    }

    
    public static double targetFrameTimeMs() {
        int configured = DemonCoreConfig.getInt(DemonCoreConfig.TARGET_FPS, 0);
        if (configured <= 0) {
            configured = 60;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.options != null && mc.options.framerateLimit() != null) {
                    Integer limit = mc.options.framerateLimit().get();
                    if (limit != null) {
                        configured = limit >= 260 ? 144 : limit;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        configured = Math.max(15, Math.min(1000, configured));
        return 1000.0 / configured;
    }

    
    public static double getQuality() {
        // During spike: apply aggressive reduction
        if (spiking) {
            return quality * 0.65;
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
    
    public static double getZeroPointOnePercentLowFps() {
        return zeroPointOnePercentLowFps;
    }

    public static void reset() {
        sampleIndex = 0;
        sampleCount = 0;
        fastIndex = 0;
        totalFrames = 0;
        spikeCount = 0;
        microStutters = 0;
        quality = 1.0;
        qualityVelocity = 0.0;
        spiking = false;
        emaFastFrameMs = 16.0;
        emaSlowFrameMs = 16.0;
        emaCpuMs = 8.0;
        emaGpuWaitMs = 4.0;
        frameTimeVariance = 0.0;
        avgFrameTimeDeviation = 0.0;
        spikeThreshold = 2.5;
    }

    public static String getStats() {
        return String.format(
                "Frame: %.1f FPS (%.2f ms) | 1%% %.1f | 0.1%% %.1f | CPU %.2f ms | GPU %.2f ms | Q %.0f%% | spikes %d | stutters %d | pacing %.1f%%",
                fps, frameTimeMs, onePercentLowFps, zeroPointOnePercentLowFps, 
                cpuRenderMs, gpuWaitMs, getQuality() * 100.0, spikeCount, microStutters,
                (1.0 - getFramePacingScore()) * 100.0);
    }
}
