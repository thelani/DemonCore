package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class FrameSpikeProtector {

    private FrameSpikeProtector() {
    }

    private static volatile long totalSpikes;
    private static volatile long lastSpikeMs;
    private static volatile long longestSpikeMs;
    private static volatile double spikeRatePerMinute;
    private static volatile int suppressionsApplied;

    private static long windowStartMs;
    private static long spikesInWindow;

    public static void onSpikeDetected(long spikeDurationMs) {
        totalSpikes++;
        long now = System.currentTimeMillis();
        lastSpikeMs = now;

        if (spikeDurationMs > longestSpikeMs) {
            longestSpikeMs = spikeDurationMs;
        }

        if (windowStartMs == 0L) {
            windowStartMs = now;
        }
        long elapsed = now - windowStartMs;
        if (elapsed >= 60_000L) {
            spikeRatePerMinute = (spikesInWindow * 60_000.0) / Math.max(1L, elapsed);
            windowStartMs = now;
            spikesInWindow = 0L;
        }
        spikesInWindow++;

        if (DemonCoreConfig.getBool(DemonCoreConfig.SPIKE_PROTECTION, true)) {
            suppressionsApplied++;
        }
    }

    public static boolean isSuppressionActive() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.SPIKE_PROTECTION, true)) {
            return false;
        }
        return System.currentTimeMillis() - lastSpikeMs < 2000L;
    }

    public static long getTotalSpikes() {
        return totalSpikes;
    }

    public static long getLongestSpikeMs() {
        return longestSpikeMs;
    }

    public static double getSpikeRatePerMinute() {
        return spikeRatePerMinute;
    }

    public static int getSuppressionsApplied() {
        return suppressionsApplied;
    }

    public static String getStats() {
        boolean protection = DemonCoreConfig.getBool(DemonCoreConfig.SPIKE_PROTECTION, true);
        return String.format("SpikeProtect: %s | %d spikes total | longest %d ms | %.1f spikes/min | %d suppressions",
                protection ? "ON" : "OFF", totalSpikes, longestSpikeMs, spikeRatePerMinute, suppressionsApplied);
    }
}
