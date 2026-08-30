package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.world.level.ChunkPos;

final class ChunkPosCache {

    private ChunkPosCache() {}

    private static final LongLinkedOpenHashSet CACHE = new LongLinkedOpenHashSet(1024);
    private static volatile long hits;
    private static volatile long misses;
    private static volatile long evictions;
    private static volatile int baseCapacity = -1;
    private static volatile double effectiveMultiplier = 1.0;

    static int baseCapacity() {
        if (baseCapacity <= 0) {
            baseCapacity = DemonCoreConfig.getInt(DemonCoreConfig.CHUNK_CACHE_SIZE, 8192);
        }
        return baseCapacity;
    }

    static int effectiveCapacity() {
        return (int) Math.max(256L, Math.round(baseCapacity() * effectiveMultiplier));
    }

    static void setEffectiveMultiplier(double m) {
        effectiveMultiplier = Math.max(GpuRamBalancer.MIN_MULTIPLIER, Math.min(GpuRamBalancer.MAX_MULTIPLIER, m));
    }

    static double getEffectiveMultiplier() {
        return effectiveMultiplier;
    }

    static boolean recordRequestedInternal(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        int cap = effectiveCapacity();
        synchronized (CACHE) {
            if (CACHE.contains(key)) {
                CACHE.addAndMoveToLast(key);
                hits++;
                return false;
            }
            CACHE.addAndMoveToLast(key);
            misses++;
            while (CACHE.size() > cap) {
                CACHE.removeFirstLong();
                evictions++;
            }
            return true;
        }
    }

    static boolean isCachedInternal(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (CACHE) {
            return CACHE.contains(key);
        }
    }

    static void forgetInternal(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (CACHE) {
            CACHE.remove(key);
        }
    }

    static int trimToRatioInternal(double ratio) {
        synchronized (CACHE) {
            int target = (int) (CACHE.size() * Math.max(0.0, Math.min(1.0, ratio)));
            int removed = 0;
            while (CACHE.size() > target && !CACHE.isEmpty()) {
                CACHE.removeFirstLong();
                removed++;
            }
            evictions += removed;
            return removed;
        }
    }

    static int applyMultiplierAndTrim(double mult) {
        setEffectiveMultiplier(mult);
        int cap = effectiveCapacity();
        synchronized (CACHE) {
            int removed = 0;
            while (CACHE.size() > cap && !CACHE.isEmpty()) {
                CACHE.removeFirstLong();
                removed++;
                evictions++;
            }
            return removed;
        }
    }

    static void clearInternal() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        hits = 0L;
        misses = 0L;
        evictions = 0L;
    }

    static int sizeInternal() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    static long getHits() { return hits; }
    static long getMisses() { return misses; }
    static long getEvictions() { return evictions; }

    static double getHitRateInternal() {
        long total = hits + misses;
        return total == 0L ? 0.0 : (double) hits / (double) total;
    }

    static long getApproxKilobytesInternal() {
        return (sizeInternal() * 16L) / 1024L;
    }

    static String getStatsInternal() {
        return String.format("Chunk cache: %d/%d entries (~%d KB, mult %s) | hit rate %.1f%% | %d evictions",
                sizeInternal(), effectiveCapacity(),
                getApproxKilobytesInternal(),
                String.format("%.2f", effectiveMultiplier),
                getHitRateInternal() * 100.0,
                evictions);
    }
}
