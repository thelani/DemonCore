package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class ResourceManager {

    private ResourceManager() {
    }

    private static volatile long totalAllocatedBytes;
    private static volatile long peakAllocatedBytes;
    private static volatile int activeAllocations;
    private static volatile long lastSampleMs;

    private static double smoothedUsageRatio;

    public static void sample() {
        long now = System.currentTimeMillis();
        if (now - lastSampleMs < 500L) {
            return;
        }
        lastSampleMs = now;

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        double ratio = max > 0L ? (double) used / (double) max : 0.0;

        smoothedUsageRatio += 0.15 * (ratio - smoothedUsageRatio);
        totalAllocatedBytes = used;
        if (used > peakAllocatedBytes) {
            peakAllocatedBytes = used;
        }
        activeAllocations = ChunkPosCache.size();
    }

    public static double getUsageRatio() {
        return smoothedUsageRatio;
    }

    public static long getUsedMb() {
        return totalAllocatedBytes / (1024L * 1024L);
    }

    public static long getPeakMb() {
        return peakAllocatedBytes / (1024L * 1024L);
    }

    public static int getActiveAllocations() {
        return activeAllocations;
    }

    public static String getStats() {
        sample();
        double balance = DemonCoreConfig.getDouble(DemonCoreConfig.RESOURCE_BALANCE, 0.7);
        return String.format("Resource: %d MB used (peak %d MB) | balance %.2f | live entries %d | usage %.0f%%",
                getUsedMb(), getPeakMb(), balance, activeAllocations, smoothedUsageRatio * 100.0);
    }
}
