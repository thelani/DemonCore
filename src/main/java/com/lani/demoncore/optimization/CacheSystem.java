package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class CacheSystem {

    private CacheSystem() {
    }

    private static volatile long hits;
    private static volatile long misses;
    private static volatile long evictions;
    private static volatile int currentSize;
    private static volatile int maxSize;

    public static void init() {
        maxSize = DemonCoreConfig.getInt(DemonCoreConfig.CHUNK_CACHE_SIZE, 8192);
    }

    public static void recordHit() {
        hits++;
    }

    public static void recordMiss() {
        misses++;
    }

    public static void recordEviction() {
        evictions++;
    }

    public static void setSize(int size) {
        currentSize = size;
    }

    public static int size() {
        return currentSize;
    }

    public static double getHitRate() {
        long total = hits + misses;
        if (total == 0L) {
            return 0.0;
        }
        return (double) hits / (double) total;
    }

    public static long getHits() {
        return hits;
    }

    public static long getMisses() {
        return misses;
    }

    public static long getEvictions() {
        return evictions;
    }

    public static String getStats() {
        init();
        int chunkCacheSize = ChunkPosCache.size();
        currentSize = chunkCacheSize;
        return String.format("Cache: %d/%d entries | hit rate %.1f%% | %d hits, %d misses, %d evictions",
                currentSize, maxSize, getHitRate() * 100.0, hits, misses, evictions);
    }
}
