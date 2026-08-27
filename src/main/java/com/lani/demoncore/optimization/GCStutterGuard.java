package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public final class GCStutterGuard {

    private GCStutterGuard() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore/GC");

    private static List<GarbageCollectorMXBean> beans;

    private static long lastCollectionCount;
    private static long lastCollectionTimeMs;
    private static long lastSampleWallMs;

    private static volatile double gcTimeShare;
    private static volatile double gcPerSecond;
    private static volatile long totalPauseMs;
    private static volatile long longestPauseMs;

    private static long lastTrimMs;

    public static void init() {
        beans = ManagementFactory.getGarbageCollectorMXBeans();
        lastSampleWallMs = System.currentTimeMillis();
        lastCollectionCount = readCount();
        lastCollectionTimeMs = readTimeMs();
        LOGGER.info("GC monitor active ({} collectors)", beans.size());
    }

    
    public static void sample() {
        if (beans == null) {
            return;
        }

        long nowWall = System.currentTimeMillis();
        long elapsed = nowWall - lastSampleWallMs;
        if (elapsed < 1000L) {
            return;
        }

        long count = readCount();
        long timeMs = readTimeMs();

        long deltaCount = Math.max(0L, count - lastCollectionCount);
        long deltaTime = Math.max(0L, timeMs - lastCollectionTimeMs);

        lastSampleWallMs = nowWall;
        lastCollectionCount = count;
        lastCollectionTimeMs = timeMs;

        totalPauseMs += deltaTime;
        if (deltaCount > 0) {
            long avg = deltaTime / deltaCount;
            if (avg > longestPauseMs) {
                longestPauseMs = avg;
            }
        }

        double share = (double) deltaTime / (double) elapsed;
        gcTimeShare += 0.3 * (share - gcTimeShare);
        gcPerSecond = (deltaCount * 1000.0) / elapsed;

        maybeTrim(nowWall);
    }

    private static void maybeTrim(long nowWall) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.AUTO_TRIM, true)) {
            return;
        }
        if (nowWall - lastTrimMs < 10_000L) {
            return;
        }

        double heapUsage = getHeapUsage();
        if (heapUsage < 0.85 && gcTimeShare < 0.08) {
            return;
        }

        lastTrimMs = nowWall;

        
        
        int freed = ChunkPosCache.trimToRatio(0.5);

        if (DemonCoreConfig.isDebug()) {
            LOGGER.info("Heap at {}% with {}% GC time - released {} cached chunk entries",
                    Math.round(heapUsage * 100), Math.round(gcTimeShare * 100), freed);
        }
    }

    private static long readCount() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : beans) {
            long c = bean.getCollectionCount();
            if (c > 0L) {
                total += c;
            }
        }
        return total;
    }

    private static long readTimeMs() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : beans) {
            long t = bean.getCollectionTime();
            if (t > 0L) {
                total += t;
            }
        }
        return total;
    }

    public static double getHeapUsage() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        if (max <= 0L) {
            return 0.0;
        }
        long used = rt.totalMemory() - rt.freeMemory();
        return (double) used / (double) max;
    }

    public static long getUsedHeapMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
    }

    public static long getMaxHeapMb() {
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    
    public static double getGcTimeShare() {
        return gcTimeShare;
    }

    public static double getCollectionsPerSecond() {
        return gcPerSecond;
    }

    public static long getTotalPauseMs() {
        return totalPauseMs;
    }

    public static String getStats() {
        return String.format("GC %.1f%% of wall time | %.1f collections/s | heap %d/%d MB (%.0f%%)",
                gcTimeShare * 100.0, gcPerSecond,
                getUsedHeapMb(), getMaxHeapMb(), getHeapUsage() * 100.0);
    }
}
