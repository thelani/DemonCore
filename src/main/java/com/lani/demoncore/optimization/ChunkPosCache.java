package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.world.level.ChunkPos;

public final class ChunkPosCache {

    private ChunkPosCache() {
    }

    private static final LongLinkedOpenHashSet CACHE = new LongLinkedOpenHashSet(1024);

    private static volatile long hits;
    private static volatile long misses;
    private static volatile long evictions;

    private static int capacity() {
        return DemonCoreConfig.getInt(DemonCoreConfig.CHUNK_CACHE_SIZE, 8192);
    }

    
    public static boolean markRequested(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        int cap = capacity();

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

    public static boolean isCached(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (CACHE) {
            return CACHE.contains(key);
        }
    }

    public static void forget(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (CACHE) {
            CACHE.remove(key);
        }
    }

    
    public static int trimToRatio(double ratio) {
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

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        hits = 0L;
        misses = 0L;
        evictions = 0L;
    }

    public static int size() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    public static double getHitRate() {
        long total = hits + misses;
        return total == 0L ? 0.0 : (double) hits / (double) total;
    }

    
    public static long getApproxKilobytes() {
        
        return (size() * 16L) / 1024L;
    }

    public static String getStats() {
        return String.format("Chunk cache: %d/%d entries (~%d KB) | hit rate %.1f%% | %d evictions",
                size(), capacity(), getApproxKilobytes(), getHitRate() * 100.0, evictions);
    }
}
