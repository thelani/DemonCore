package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory allocator that provides intelligent chunk position caching
 * instead of just allocating empty byte arrays
 */
public class MemoryAllocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryAllocator.class);
    private static boolean initialized = false;

    // Real chunk cache instead of empty byte arrays
    private static final Map<ChunkPos, ChunkCacheEntry> chunkCache = new ConcurrentHashMap<>();
    private static long lastAllocationTime = 0;
    private static int allocationLevel = 0;
    
    private static class ChunkCacheEntry {
        final long timestamp;
        final int priority;
        final boolean persistent;
        
        ChunkCacheEntry(int priority, boolean persistent) {
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
            this.persistent = persistent;
        }
        
        boolean isExpired(long maxAge) {
            return System.currentTimeMillis() - timestamp > maxAge;
        }
    }
    
    public static void init() {
        if (initialized) return;
        
        // Skip initialization on dedicated server
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        
        initialized = true;
        analyzeMemory();
        allocateAggressiveCache();
    }
    
    private static void analyzeMemory() {
        // Memory analysis for optimization
    }
    
    private static void allocateAggressiveCache() {
        if (!DemonCoreConfig.AGGRESSIVE_RAM_ALLOCATION.get()) {
            return;
        }
        
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long availableMemory = maxMemory - usedMemory;
        
        // Use config-specified target RAM usage
        double targetUsage = DemonCoreConfig.TARGET_RAM_USAGE.get();
        
        if (availableMemory > 2048) {
            // Calculate cache size based on config (chunks, not MB of empty arrays)
            int targetCacheSize = (int)(DemonCoreConfig.CACHE_SIZE.get() * targetUsage);
            allocationLevel = targetCacheSize;
        }
    }
    
    /**
     * Cache a chunk position for faster lookup
     */
    public static void cacheChunkPosition(ChunkPos pos, int priority, boolean persistent) {
        if (!initialized || !DemonCoreConfig.AGGRESSIVE_RAM_ALLOCATION.get()) {
            return;
        }
        
        chunkCache.put(pos, new ChunkCacheEntry(priority, persistent));
        
        // Limit cache size
        if (chunkCache.size() > allocationLevel) {
            cleanupOldEntries();
        }
    }
    
    /**
     * Check if a chunk position is in cache
     */
    public static boolean isCached(ChunkPos pos) {
        ChunkCacheEntry entry = chunkCache.get(pos);
        return entry != null && !entry.isExpired(60000); // 60s max age
    }
    
    /**
     * Get cache priority for a chunk
     */
    public static int getCachePriority(ChunkPos pos) {
        ChunkCacheEntry entry = chunkCache.get(pos);
        return entry != null ? entry.priority : 0;
    }
    
    /**
     * Remove old cache entries to keep size manageable
     */
    private static void cleanupOldEntries() {
        long maxAge = 60000; // 60 seconds
        chunkCache.entrySet().removeIf(entry -> 
            !entry.getValue().persistent && entry.getValue().isExpired(maxAge)
        );
    }
    
    public static void optimizeMemoryUsage(double speed) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get() || !initialized) return;
        
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAllocationTime > 10000) {
            checkAndAdjustMemory(speed);
            lastAllocationTime = currentTime;
        }
    }
    
    private static void checkAndAdjustMemory(double speed) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        float usage = (float) usedMemory / maxMemory;
        
        if (usage > 0.90f) {
            // High memory pressure - clean up old cache entries
            LOGGER.warn("☢ High RAM usage - cleaning cache");
            shrinkCache();
        } else if (usage < 0.40f && speed > 30.0) {
            // Low usage at high speed - we can expand cache size
            LOGGER.debug("☢ Low RAM usage at high speed - cache available");
        }
    }
    
    private static void shrinkCache() {
        int sizeBefore = chunkCache.size();
        
        // Remove non-persistent expired entries
        chunkCache.entrySet().removeIf(entry -> 
            !entry.getValue().persistent && entry.getValue().isExpired(30000)
        );
        
        int removed = sizeBefore - chunkCache.size();
        if (removed > 0) {
            System.gc();
            LOGGER.info("Shrunk cache: -{} entries (total: {})", removed, chunkCache.size());
        }
    }
    
    public static void logMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        
        LOGGER.info("☢ Memory Stats:");
        LOGGER.info("  - Used: {}MB / {}MB ({}%)", 
            usedMemory, maxMemory, (int)((float)usedMemory / maxMemory * 100));
        LOGGER.info("  - Chunk Cache Entries: {}", chunkCache.size());
        LOGGER.info("  - Target Cache Size: {}", allocationLevel);
    }
    
    public static void emergencyCleanup() {
        LOGGER.warn("☢ EMERGENCY MEMORY CLEANUP!");

        int sizeBefore = chunkCache.size();
        
        // Remove all non-persistent entries
        chunkCache.entrySet().removeIf(entry -> !entry.getValue().persistent);

        System.gc();
        
        int removed = sizeBefore - chunkCache.size();
        LOGGER.warn("Cleaned {} cache entries", removed);
        logMemoryStats();
    }
    
    /**
     * Clear all cached data
     */
    public static void clearCache() {
        chunkCache.clear();
        LOGGER.info("☢ Chunk cache cleared");
    }
}
