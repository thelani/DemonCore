package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

public class MemoryAllocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryAllocator.class);
    private static boolean initialized = false;

    private static final List<byte[]> memoryReserve = new ArrayList<>();
    private static long lastAllocationTime = 0;
    private static int allocationLevel = 0;
    
    public static void init() {
        if (initialized) return;
        initialized = true;
        
        LOGGER.info("☢ ========================================");
        LOGGER.info("☢  MEMORY ALLOCATOR - RAM Optimizer");
        LOGGER.info("☢ ========================================");
        
        analyzeMemory();
        allocateAggressiveCache();
        
        LOGGER.info("☢ ========================================");
    }
    
    private static void analyzeMemory() {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long heapMax = heapUsage.getMax() / (1024 * 1024);
        long heapUsed = heapUsage.getUsed() / (1024 * 1024);
        
        LOGGER.info("☢ Memory Analysis:");
        LOGGER.info("  - Max Memory: {}MB", maxMemory);
        LOGGER.info("  - Current Used: {}MB", usedMemory);
        LOGGER.info("  - Current Free: {}MB", freeMemory);
        LOGGER.info("  - Heap Max: {}MB", heapMax);
        LOGGER.info("  - Heap Used: {}MB", heapUsed);
        LOGGER.info("  - Usage: {}%", (int)((float)usedMemory / maxMemory * 100));
        
        if (usedMemory < maxMemory * 0.5) {
            LOGGER.warn("☢ WARNING: Only using {}% of available RAM!", 
                (int)((float)usedMemory / maxMemory * 100));
            LOGGER.warn("☢ We can use MORE memory for better performance!");
        }
    }
    
    private static void allocateAggressiveCache() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long availableMemory = maxMemory - usedMemory;
        
        if (availableMemory > 2048) {

            int cacheSize = (int)(availableMemory * 0.6); // %60'ını kullan
            
            LOGGER.info("☢ Allocating aggressive cache: {}MB", cacheSize);
            
            try {

                allocateChunkCache(cacheSize);
                allocationLevel = cacheSize;
                
                LOGGER.info("☢ Cache allocated successfully!");
            } catch (OutOfMemoryError e) {
                LOGGER.error("☢ Failed to allocate cache - OOM", e);

                memoryReserve.clear();
                System.gc();
            }
        } else {
            LOGGER.warn("☢ Low available memory: {}MB - using conservative cache", availableMemory);
        }
    }
    
    private static void allocateChunkCache(int sizeMB) {

        int bufferCount = sizeMB / 10; // Her biri 10MB
        
        for (int i = 0; i < bufferCount && i < 100; i++) {
            try {

                byte[] buffer = new byte[10 * 1024 * 1024];
                memoryReserve.add(buffer);
            } catch (OutOfMemoryError e) {
                LOGGER.warn("Reached memory limit at buffer {}", i);
                break;
            }
        }
        
        LOGGER.info("Allocated {} cache buffers ({}MB)", 
            memoryReserve.size(), memoryReserve.size() * 10);
    }
    
    public static void optimizeMemoryUsage(double speed) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) return;
        
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
        
        if (usage < 0.40f && speed > 30.0) {

            LOGGER.info("☢ Low RAM usage at high speed - allocating more cache");
            expandCache();
        } else if (usage > 0.90f) {

            LOGGER.warn("☢ High RAM usage - shrinking cache");
            shrinkCache();
        }
    }
    
    private static void expandCache() {
        Runtime runtime = Runtime.getRuntime();
        long availableMemory = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024);
        
        if (availableMemory > 512 && memoryReserve.size() < 200) {
            try {

                for (int i = 0; i < 5; i++) {
                    byte[] buffer = new byte[10 * 1024 * 1024];
                    memoryReserve.add(buffer);
                }
                LOGGER.info("Expanded cache: +50MB (total: {}MB)", memoryReserve.size() * 10);
            } catch (OutOfMemoryError e) {
                LOGGER.warn("Cannot expand cache - memory limit reached");
            }
        }
    }
    
    private static void shrinkCache() {
        if (memoryReserve.size() > 10) {

            int removeCount = memoryReserve.size() / 5;
            for (int i = 0; i < removeCount; i++) {
                if (!memoryReserve.isEmpty()) {
                    memoryReserve.remove(memoryReserve.size() - 1);
                }
            }
            System.gc(); // GC'yi tetikle
            LOGGER.info("Shrunk cache: -{}MB (total: {}MB)", removeCount * 10, memoryReserve.size() * 10);
        }
    }
    
    public static void logMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        
        LOGGER.info("☢ Memory Stats:");
        LOGGER.info("  - Used: {}MB / {}MB ({}%)", 
            usedMemory, maxMemory, (int)((float)usedMemory / maxMemory * 100));
        LOGGER.info("  - Cache Buffers: {} ({}MB)", 
            memoryReserve.size(), memoryReserve.size() * 10);
        LOGGER.info("  - Allocation Level: {}MB", allocationLevel);
    }
    
    public static void emergencyCleanup() {
        LOGGER.warn("☢ EMERGENCY MEMORY CLEANUP!");

        int removeCount = memoryReserve.size() / 2;
        for (int i = 0; i < removeCount; i++) {
            if (!memoryReserve.isEmpty()) {
                memoryReserve.remove(memoryReserve.size() - 1);
            }
        }

        System.gc();
        
        LOGGER.warn("Cleaned {}MB cache", removeCount * 10);
        logMemoryStats();
    }
}
