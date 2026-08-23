package com.lani.demoncore.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ResourceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);

    private static final int CORE_COUNT = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService executorService = createOptimalThreadPool();

    private static ResourceStrategy currentStrategy = ResourceStrategy.BALANCED;

    private static final AtomicInteger tasksQueued = new AtomicInteger(0);
    private static final AtomicInteger tasksCompleted = new AtomicInteger(0);
    private static final AtomicInteger tasksFailed = new AtomicInteger(0);

    private static long lastCleanup = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL = 30000; // 30 saniye
    
    public enum ResourceStrategy {
        RAM_SAVING(0.5, 1.5, 0.8, "RAM tasarrufu - CPU yoğun"),
        
        CPU_SAVING(1.5, 0.5, 1.2, "CPU tasarrufu - RAM yoğun"),
        
        BALANCED(1.0, 1.0, 1.0, "Dengeli mod"),
        
        MINIMAL(0.3, 0.3, 0.3, "Minimal kaynak"),
        
        AGGRESSIVE(2.0, 2.0, 1.5, "Agresif optimizasyon");
        
        public final double ramMultiplier;
        public final double cpuMultiplier;
        public final double gpuMultiplier;
        public final String description;
        
        ResourceStrategy(double ram, double cpu, double gpu, String desc) {
            this.ramMultiplier = ram;
            this.cpuMultiplier = cpu;
            this.gpuMultiplier = gpu;
            this.description = desc;
        }
    }
    
    private static ExecutorService createOptimalThreadPool() {
        int poolSize = Math.max(2, Math.min(CORE_COUNT / 2, 8)); // Min 2, max 8
        LOGGER.info("Creating thread pool with {} threads (CPU cores: {})", poolSize, CORE_COUNT);
        
        return new ThreadPoolExecutor(
            poolSize / 2,              // Core threads
            poolSize,                   // Max threads
            60L, TimeUnit.SECONDS,     // Keep alive
            new LinkedBlockingQueue<>(100), // Queue size
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "DemonCore-Worker-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1); // Biraz düşük öncelik
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // Overflow policy
        );
    }
    
    public static void updateStrategy() {
        ResourceStrategy oldStrategy = currentStrategy;
        PerformanceMonitor.PerformanceLevel level = PerformanceMonitor.getCurrentLevel();

        boolean ramPressure = PerformanceMonitor.isMemoryPressure();
        boolean cpuPressure = PerformanceMonitor.isCpuPressure();
        
        if (level == PerformanceMonitor.PerformanceLevel.CRITICAL) {
            currentStrategy = ResourceStrategy.MINIMAL;
        } else if (ramPressure && !cpuPressure) {

            currentStrategy = ResourceStrategy.RAM_SAVING;
        } else if (cpuPressure && !ramPressure) {

            currentStrategy = ResourceStrategy.CPU_SAVING;
        } else if (level == PerformanceMonitor.PerformanceLevel.EXCELLENT || level == PerformanceMonitor.PerformanceLevel.GOOD) {
            currentStrategy = ResourceStrategy.AGGRESSIVE;
        } else {
            currentStrategy = ResourceStrategy.BALANCED;
        }
        
        if (oldStrategy != currentStrategy) {
            LOGGER.info("Resource strategy changed: {} -> {}", 
                oldStrategy.description, currentStrategy.description);
        }
    }
    
    public static <T> Future<T> submitTask(Callable<T> task, String taskName) {
        tasksQueued.incrementAndGet();
        
        return executorService.submit(() -> {
            try {
                T result = task.call();
                tasksCompleted.incrementAndGet();
                return result;
            } catch (Exception e) {
                tasksFailed.incrementAndGet();
                LOGGER.error("Task failed: {}", taskName, e);
                throw e;
            }
        });
    }
    
    public static void submitTask(Runnable task, String taskName) {
        tasksQueued.incrementAndGet();
        
        executorService.submit(() -> {
            try {
                task.run();
                tasksCompleted.incrementAndGet();
            } catch (Exception e) {
                tasksFailed.incrementAndGet();
                LOGGER.error("Task failed: {}", taskName, e);
            }
        });
    }
    
    public static int calculateChunkLimit(int baseLimit) {

        int perfLimit = PerformanceMonitor.recommendChunkLimit(baseLimit);

        int finalLimit = (int) (perfLimit * currentStrategy.cpuMultiplier);

        return Math.max(4, Math.min(128, finalLimit));
    }
    
    public static int calculateCacheSize(int baseSize) {
        int size = (int) (baseSize * currentStrategy.ramMultiplier);

        if (PerformanceMonitor.isMemoryPressure()) {
            size /= 2;
        }
        
        return Math.max(10, Math.min(1000, size));
    }
    
    public static int calculateThreadCount(int baseCount) {
        int count = (int) (baseCount * currentStrategy.cpuMultiplier);

        if (PerformanceMonitor.isCpuPressure()) {
            count = Math.max(1, count / 2);
        }
        
        return Math.max(1, Math.min(CORE_COUNT, count));
    }
    
    public static void tick() {
        long now = System.currentTimeMillis();
        
        if (now - lastCleanup >= CLEANUP_INTERVAL) {
            cleanup();
            lastCleanup = now;
        }
    }
    
    private static void cleanup() {

        updateStrategy();

        if (PerformanceMonitor.isMemoryPressure()) {
            PerformanceMonitor.forceCleanup();
        }
        
        LOGGER.debug("Resource cleanup - Strategy: {}, Queue: {}, Completed: {}, Failed: {}",
            currentStrategy.description, tasksQueued.get(), tasksCompleted.get(), tasksFailed.get());
    }
    
    public static void emergencyCleanup() {

        ((ThreadPoolExecutor) executorService).getQueue().clear();

        PerformanceMonitor.forceCleanup();

        currentStrategy = ResourceStrategy.MINIMAL;
    }
    
    public static void shutdown() {
        LOGGER.info("Shutting down ResourceManager...");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("ResourceManager shutdown complete. Stats: Queued={}, Completed={}, Failed={}",
            tasksQueued.get(), tasksCompleted.get(), tasksFailed.get());
    }

    public static ResourceStrategy getCurrentStrategy() { return currentStrategy; }
    public static int getCoreCount() { return CORE_COUNT; }
    public static int getQueuedTasks() { return tasksQueued.get(); }
    public static int getCompletedTasks() { return tasksCompleted.get(); }
    public static int getFailedTasks() { return tasksFailed.get(); }
    public static int getActiveThreads() { 
        return ((ThreadPoolExecutor) executorService).getActiveCount(); 
    }
    
    public static String getStats() {
        return String.format(
            "Strategy: %s | Threads: %d/%d | Queue: %d | Completed: %d | Failed: %d",
            currentStrategy.description,
            getActiveThreads(),
            CORE_COUNT,
            getQueuedTasks(),
            getCompletedTasks(),
            getFailedTasks()
        );
    }
}
