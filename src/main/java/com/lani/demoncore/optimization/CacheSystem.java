package com.lani.demoncore.optimization;

import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CacheSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheSystem.class);

    private static final LRUCache<BlockPos, ChunkState> chunkCache = new LRUCache<>(100);

    private static final LRUCache<UUID, Double> speedCache = new LRUCache<>(50);

    private static final LRUCache<UUID, BlockPos> positionCache = new LRUCache<>(50);

    private static final AtomicInteger cacheHits = new AtomicInteger(0);
    private static final AtomicInteger cacheMisses = new AtomicInteger(0);

    private static long lastCleanup = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL = 10000; // 10 saniye
    
    public enum ChunkState {
        LOADING,      // Yükleniyor
        LOADED,       // Yüklendi
        UNLOADING,    // Kaldırılıyor
        UNKNOWN       // Bilinmiyor
    }
    
    private static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private int maxSize;
        private final Map<K, Long> accessTimes = new ConcurrentHashMap<>();
        
        public LRUCache(int maxSize) {
            super(maxSize, 0.75f, true); // accessOrder = true (LRU)
            this.maxSize = maxSize;
        }
        
        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            boolean shouldRemove = size() > maxSize;
            if (shouldRemove) {
                accessTimes.remove(eldest.getKey());
            }
            return shouldRemove;
        }
        
        @Override
        public V get(Object key) {
            accessTimes.put((K) key, System.currentTimeMillis());
            return super.get(key);
        }
        
        @Override
        public V put(K key, V value) {
            accessTimes.put(key, System.currentTimeMillis());
            return super.put(key, value);
        }
        
        public void updateMaxSize(int newSize) {
            this.maxSize = Math.max(10, newSize);

            if (size() > maxSize) {
                trimToSize();
            }
        }
        
        private void trimToSize() {
            while (size() > maxSize) {
                K oldest = getOldestKey();
                if (oldest != null) {
                    remove(oldest);
                    accessTimes.remove(oldest);
                }
            }
        }
        
        private K getOldestKey() {
            long oldestTime = Long.MAX_VALUE;
            K oldestKey = null;
            
            for (Map.Entry<K, Long> entry : accessTimes.entrySet()) {
                if (entry.getValue() < oldestTime) {
                    oldestTime = entry.getValue();
                    oldestKey = entry.getKey();
                }
            }
            
            return oldestKey;
        }
        
        public int getMaxSize() {
            return maxSize;
        }
        
        public double getHitRate() {
            int total = cacheHits.get() + cacheMisses.get();
            return total > 0 ? (double) cacheHits.get() / total : 0.0;
        }
    }
    
    public static void putChunkState(BlockPos chunkPos, ChunkState state) {
        chunkCache.put(chunkPos, state);
    }
    
    public static ChunkState getChunkState(BlockPos chunkPos) {
        ChunkState state = chunkCache.get(chunkPos);
        
        if (state != null) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
            state = ChunkState.UNKNOWN;
        }
        
        return state;
    }
    
    public static boolean isChunkLoaded(BlockPos chunkPos) {
        ChunkState state = getChunkState(chunkPos);
        return state == ChunkState.LOADED;
    }
    
    public static void putSpeed(UUID entityId, double speed) {
        speedCache.put(entityId, speed);
    }
    
    public static Double getSpeed(UUID entityId) {
        Double speed = speedCache.get(entityId);
        
        if (speed != null) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
        
        return speed;
    }
    
    public static void putPosition(UUID entityId, BlockPos pos) {
        positionCache.put(entityId, pos);
    }
    
    public static BlockPos getPosition(UUID entityId) {
        BlockPos pos = positionCache.get(entityId);
        
        if (pos != null) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
        
        return pos;
    }
    
    public static void clearEntity(UUID entityId) {
        speedCache.remove(entityId);
        positionCache.remove(entityId);
    }
    
    public static void clearChunkCache() {
        chunkCache.clear();
        LOGGER.debug("Chunk cache cleared");
    }
    
    public static void clearAll() {
        chunkCache.clear();
        speedCache.clear();
        positionCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        LOGGER.info("All caches cleared");
    }
    
    public static void updateCacheSizes() {

        int chunkCacheSize = ResourceManager.calculateCacheSize(100);
        int speedCacheSize = ResourceManager.calculateCacheSize(50);
        int posCacheSize = ResourceManager.calculateCacheSize(50);
        
        chunkCache.updateMaxSize(chunkCacheSize);
        speedCache.updateMaxSize(speedCacheSize);
        positionCache.updateMaxSize(posCacheSize);
        
        LOGGER.debug("Cache sizes updated - Chunk: {}, Speed: {}, Position: {}",
            chunkCacheSize, speedCacheSize, posCacheSize);
    }
    
    public static void tick() {
        long now = System.currentTimeMillis();
        
        if (now - lastCleanup >= CLEANUP_INTERVAL) {
            cleanup();
            lastCleanup = now;
        }
    }
    
    private static void cleanup() {

        updateCacheSizes();

        if (PerformanceMonitor.isMemoryPressure()) {
            int currentSize = chunkCache.size();
            int newMaxSize = Math.max(10, chunkCache.getMaxSize() / 2);
            chunkCache.updateMaxSize(newMaxSize);
            
            LOGGER.debug("Memory pressure - chunk cache reduced from {} to {}",
                currentSize, chunkCache.size());
        }

        double hitRate = getCacheHitRate();
        if (hitRate < 0.5 && getTotalCacheSize() > 50) {
            LOGGER.debug("Low cache hit rate: {:.1f}% - consider adjusting cache strategy", hitRate * 100);
        }
    }
    
    public static void emergencyCleanup() {

        chunkCache.updateMaxSize(10);
        speedCache.updateMaxSize(10);
        positionCache.updateMaxSize(10);

        chunkCache.trimToSize();
        speedCache.trimToSize();
        positionCache.trimToSize();
    }

    
    public static int getChunkCacheSize() {
        return chunkCache.size();
    }
    
    public static int getSpeedCacheSize() {
        return speedCache.size();
    }
    
    public static int getPositionCacheSize() {
        return positionCache.size();
    }
    
    public static int getTotalCacheSize() {
        return chunkCache.size() + speedCache.size() + positionCache.size();
    }
    
    public static int getChunkCacheMaxSize() {
        return chunkCache.getMaxSize();
    }
    
    public static double getCacheHitRate() {
        int total = cacheHits.get() + cacheMisses.get();
        return total > 0 ? (double) cacheHits.get() / total : 0.0;
    }
    
    public static int getCacheHits() {
        return cacheHits.get();
    }
    
    public static int getCacheMisses() {
        return cacheMisses.get();
    }
    
    public static String getStats() {
        return String.format(
            "Cache: Chunk=%d/%d Speed=%d/%d Pos=%d/%d | Hit Rate: %.1f%% (%d/%d)",
            chunkCache.size(), chunkCache.getMaxSize(),
            speedCache.size(), speedCache.getMaxSize(),
            positionCache.size(), positionCache.getMaxSize(),
            getCacheHitRate() * 100,
            cacheHits.get(),
            cacheHits.get() + cacheMisses.get()
        );
    }
}
