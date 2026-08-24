package com.lani.demoncore.optimization;

import com.lani.demoncore.DemonCore;
import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;

public class PerformanceMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore-Performance");

    private static int targetFps = 60;
    private static long lastFrameTime = System.nanoTime();
    private static int frameCount = 0;
    private static double currentFps = 60.0;

    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private static final Runtime runtime = Runtime.getRuntime();

    private static double currentCpuLoad = 0.0;
    private static boolean cpuMonitoringAvailable = false;

    private static final double HIGH_RAM_THRESHOLD = 0.85;
    private static final double MEDIUM_RAM_THRESHOLD = 0.70;
    private static final double LOW_FPS_THRESHOLD = 0.75;

    private static PerformanceLevel currentLevel = PerformanceLevel.NORMAL;
    private static long lastLevelCheck = 0;
    private static final long LEVEL_CHECK_INTERVAL = 1000;

    private static long totalFrames = 0;
    private static double avgFps = 60.0;
    private static double minFps = 60.0;
    private static double maxFps = 60.0;
    
    static {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                cpuMonitoringAvailable = true;
            }
        } catch (Exception e) {
        }
    }
    
    public enum PerformanceLevel {
        EXCELLENT(1.0, "Excellent"),
        GOOD(0.8, "Good"),
        NORMAL(0.6, "Normal"),
        POOR(0.4, "Poor"),
        CRITICAL(0.2, "Critical");
        
        public final double multiplier;
        public final String displayName;
        
        PerformanceLevel(double multiplier, String displayName) {
            this.multiplier = multiplier;
            this.displayName = displayName;
        }
    }
    
    public static void setTargetFps(int fps) {
        targetFps = Math.max(30, Math.min(fps, 300));
    }
    
    public static void tick() {
        frameCount++;
        totalFrames++;
        long currentTime = System.nanoTime();
        long elapsed = currentTime - lastFrameTime;

        if (elapsed >= 1_000_000_000L) {
            currentFps = frameCount / (elapsed / 1_000_000_000.0);

            if (currentFps < minFps) minFps = currentFps;
            if (currentFps > maxFps) maxFps = currentFps;
            avgFps = (avgFps * 0.95) + (currentFps * 0.05);
            
            updateCpuLoad();
            
            frameCount = 0;
            lastFrameTime = currentTime;
        }

        if (System.currentTimeMillis() - lastLevelCheck > LEVEL_CHECK_INTERVAL) {
            updatePerformanceLevel();
            lastLevelCheck = System.currentTimeMillis();
        }
    }
    
    private static void updateCpuLoad() {
        double cpuLoad = getRealCpuLoad();
        if (cpuLoad >= 0) {
            currentCpuLoad = cpuLoad;
        }
    }
    
    private static void updatePerformanceLevel() {
        double ramUsage = getMemoryUsagePercent();
        double fpsRatio = currentFps / targetFps;
        double cpuLoad = getCurrentCpuLoad();
        
        PerformanceLevel oldLevel = currentLevel;

        boolean cpuHigh = cpuMonitoringAvailable ? 
            cpuLoad > com.lani.demoncore.config.DemonCoreConfig.CPU_THRESHOLD.get() : 
            fpsRatio < 0.5;

        if (ramUsage > HIGH_RAM_THRESHOLD || cpuHigh) {
            currentLevel = PerformanceLevel.CRITICAL;
        } else if (ramUsage > MEDIUM_RAM_THRESHOLD || (cpuMonitoringAvailable ? cpuLoad > 0.70 : fpsRatio < LOW_FPS_THRESHOLD)) {
            currentLevel = PerformanceLevel.POOR;
        } else if (fpsRatio < 0.9) {
            currentLevel = PerformanceLevel.NORMAL;
        } else if (fpsRatio < 1.2) {
            currentLevel = PerformanceLevel.GOOD;
        } else {
            currentLevel = PerformanceLevel.EXCELLENT;
        }

        if (currentLevel != oldLevel) {
            if (cpuMonitoringAvailable) {
                LOGGER.info("Performance level changed: {} -> {} (FPS: {:.1f}/{}, RAM: {:.1f}%, CPU: {:.1f}%)",
                    oldLevel.displayName, currentLevel.displayName,
                    currentFps, targetFps, ramUsage * 100, cpuLoad * 100);
            } else {
                LOGGER.info("Performance level changed: {} -> {} (FPS: {:.1f}/{}, RAM: {:.1f}%)",
                    oldLevel.displayName, currentLevel.displayName,
                    currentFps, targetFps, ramUsage * 100);
            }
        }
    }
    
    public static double getCurrentFps() {
        return currentFps;
    }
    
    public static double getAverageFps() {
        return avgFps;
    }
    
    public static PerformanceLevel getPerformanceLevel() {
        return currentLevel;
    }
    
    public static double getChunkLoadMultiplier() {
        return currentLevel.multiplier;
    }
    
    public static boolean isCritical() {
        return currentLevel == PerformanceLevel.CRITICAL;
    }
    
    public static boolean isLowFps() {
        return currentFps < (targetFps * LOW_FPS_THRESHOLD);
    }
    
    public static boolean isCpuPressure() {
        double cpuLoad = getRealCpuLoad();
        if (cpuLoad > 0) {
            return cpuLoad > DemonCoreConfig.CPU_THRESHOLD.get();
        }
        return currentLevel == PerformanceLevel.POOR || currentLevel == PerformanceLevel.CRITICAL;
    }
    
    public static double getRealCpuLoad() {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                return sunBean.getProcessCpuLoad();
            }
        } catch (Exception e) {
        }
        return -1.0;
    }
    
    public static double getCurrentCpuLoad() {
        return currentCpuLoad;
    }
    
    public static boolean isCpuMonitoringAvailable() {
        return cpuMonitoringAvailable;
    }
    
    public static PerformanceLevel getCurrentLevel() {
        return currentLevel;
    }
    
    public static int recommendTickDelay() {
        switch (currentLevel) {
            case EXCELLENT: return 0;
            case GOOD: return 1;
            case NORMAL: return 2;
            case POOR: return 4;
            case CRITICAL: return 10;
            default: return 2;
        }
    }
    
    public static int recommendChunkLimit(int baseLimit) {
        return (int) (baseLimit * currentLevel.multiplier);
    }
    
    public static String getDetailedStats() {
        return getPerformanceReport();
    }
    
    public static double getMemoryUsagePercent() {
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        return (double) used / max;
    }
    
    public static long getUsedMemoryMB() {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    public static long getMaxMemoryMB() {
        return runtime.maxMemory() / (1024 * 1024);
    }
    
    public static boolean isMemoryPressure() {
        return getMemoryUsagePercent() > HIGH_RAM_THRESHOLD;
    }
    
    public static void forceCleanup() {
        if (isMemoryPressure()) {
            double ramUsagePercent = getMemoryUsagePercent();
            LOGGER.debug("Forcing garbage collection (RAM: {:.1f}%)", ramUsagePercent * 100);
            System.gc();

        }
    }
    
    public static String getPerformanceReport() {
        if (cpuMonitoringAvailable) {
            return String.format(
                "Performance Report:\n" +
                "  Level: %s (%.1f multiplier)\n" +
                "  FPS: %.1f / %d (avg: %.1f, min: %.1f, max: %.1f)\n" +
                "  CPU: %.1f%% (real measurement)\n" +
                "  RAM: %d / %d MB (%.1f%%)\n" +
                "  Total Frames: %d",
                currentLevel.displayName,
                currentLevel.multiplier,
                currentFps, targetFps, avgFps, minFps, maxFps,
                currentCpuLoad * 100,
                getUsedMemoryMB(), getMaxMemoryMB(), getMemoryUsagePercent() * 100,
                totalFrames
            );
        } else {
            return String.format(
                "Performance Report:\n" +
                "  Level: %s (%.1f multiplier)\n" +
                "  FPS: %.1f / %d (avg: %.1f, min: %.1f, max: %.1f)\n" +
                "  CPU: FPS-based estimation (real monitoring unavailable)\n" +
                "  RAM: %d / %d MB (%.1f%%)\n" +
                "  Total Frames: %d",
                currentLevel.displayName,
                currentLevel.multiplier,
                currentFps, targetFps, avgFps, minFps, maxFps,
                getUsedMemoryMB(), getMaxMemoryMB(), getMemoryUsagePercent() * 100,
                totalFrames
            );
        }
    }
    
    public static void resetStats() {
        totalFrames = 0;
        avgFps = currentFps;
        minFps = currentFps;
        maxFps = currentFps;
        LOGGER.info("Performance statistics reset");
    }
    
    public static boolean canLoadChunks() {

        if (currentLevel == PerformanceLevel.CRITICAL) {
            return false;
        }

        if (isMemoryPressure()) {
            return Math.random() < 0.3;
        }
        
        return true;
    }
    
    public static int getRecommendedChunksPerTick(int configured) {
        double multiplier = getChunkLoadMultiplier();
        int recommended = (int) (configured * multiplier);
        return Math.max(1, recommended);
    }
}
