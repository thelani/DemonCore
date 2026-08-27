package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class PerformanceMonitor {

    private PerformanceMonitor() {
    }

    public enum Level {
        EXCELLENT(1.00),
        GOOD(0.85),
        FAIR(0.65),
        POOR(0.45),
        CRITICAL(0.25);

        private final double budgetScale;

        Level(double budgetScale) {
            this.budgetScale = budgetScale;
        }

        
        public double budgetScale() {
            return budgetScale;
        }
    }

    

    private static final int MSPT_SAMPLES = 100;
    private static final long[] msptSamples = new long[MSPT_SAMPLES];
    private static int msptIndex;
    private static int msptCount;

    private static long tickStartNs;
    private static volatile double averageMspt;
    private static volatile double peakMspt;
    private static volatile long serverTicks;

    public static void onServerTickStart() {
        tickStartNs = System.nanoTime();
    }

    public static void onServerTickEnd() {
        if (tickStartNs == 0L) {
            return;
        }
        long elapsed = System.nanoTime() - tickStartNs;
        if (elapsed <= 0L || elapsed > 5_000_000_000L) {
            return;
        }

        serverTicks++;
        msptSamples[msptIndex] = elapsed;
        msptIndex = (msptIndex + 1) % MSPT_SAMPLES;
        if (msptCount < MSPT_SAMPLES) {
            msptCount++;
        }

        long sum = 0L;
        long peak = 0L;
        for (int i = 0; i < msptCount; i++) {
            sum += msptSamples[i];
            if (msptSamples[i] > peak) {
                peak = msptSamples[i];
            }
        }
        averageMspt = (sum / (double) msptCount) / 1_000_000.0;
        peakMspt = peak / 1_000_000.0;

        GCStutterGuard.sample();
    }

    public static double getAverageMspt() {
        return averageMspt;
    }

    public static double getPeakMspt() {
        return peakMspt;
    }

    public static double getTps() {
        double mspt = averageMspt;
        return mspt <= 50.0 ? 20.0 : Math.max(1.0, 1000.0 / mspt);
    }

    public static long getServerTicks() {
        return serverTicks;
    }

    
    public static Level getServerLevel() {
        double target = DemonCoreConfig.getDouble(DemonCoreConfig.TARGET_MSPT, 38.0);
        double mspt = averageMspt;

        if (mspt <= target * 0.5) {
            return Level.EXCELLENT;
        }
        if (mspt <= target * 0.75) {
            return Level.GOOD;
        }
        if (mspt <= target) {
            return Level.FAIR;
        }
        if (mspt <= target * 1.4) {
            return Level.POOR;
        }
        return Level.CRITICAL;
    }

    
    public static double getServerHeadroom() {
        double target = DemonCoreConfig.getDouble(DemonCoreConfig.TARGET_MSPT, 38.0);
        if (target <= 0.0) {
            return 1.0;
        }
        double headroom = (target - averageMspt) / target;
        return Math.max(0.0, Math.min(1.0, headroom));
    }

    

    
    public static Level getClientLevel() {
        double budget = FrameProfiler.targetFrameTimeMs();
        double frame = FrameProfiler.getFrameTimeMs();

        if (frame <= 0.001) {
            return Level.EXCELLENT;
        }
        double ratio = frame / budget;

        if (ratio <= 0.7) {
            return Level.EXCELLENT;
        }
        if (ratio <= 1.0) {
            return Level.GOOD;
        }
        if (ratio <= 1.35) {
            return Level.FAIR;
        }
        if (ratio <= 2.0) {
            return Level.POOR;
        }
        return Level.CRITICAL;
    }

    public static void reset() {
        msptIndex = 0;
        msptCount = 0;
        serverTicks = 0L;
        averageMspt = 0.0;
        peakMspt = 0.0;
    }

    public static String getServerStats() {
        return String.format("Server: %.2f ms/tick avg, %.2f ms peak (%.1f TPS) | %s | headroom %.0f%%",
                averageMspt, peakMspt, getTps(), getServerLevel(), getServerHeadroom() * 100.0);
    }

    public static String getClientStats() {
        return FrameProfiler.getStats();
    }

    public static String getDetailedStats() {
        return getServerStats() + "\n" + getClientStats();
    }
}
