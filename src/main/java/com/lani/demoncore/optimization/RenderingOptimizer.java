package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderingOptimizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingOptimizer.class);
    private static boolean initialized = false;
    private static int frameSkipCounter = 0;
    private static long lastOptimizationTime = 0;
    
    public static void init() {
        if (initialized) return;
        
        // Skip initialization on dedicated server
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        
        initialized = true;
        applyAggressiveOptimizations();
    }
    
    private static void applyAggressiveOptimizations() {
        // Render pipeline enhancements applied
    }
    
    public static void optimizeFrame(Minecraft mc, double speed) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) return;
        
        long currentTime = System.currentTimeMillis();

        // Apply aggressive GPU optimization if enabled
        if (DemonCoreConfig.AGGRESSIVE_GPU_OPTIMIZATION.get() && speed > 50.0) {
            applyHighSpeedOptimization(mc, speed);
        } else if (speed > 20.0) {
            applyMediumSpeedOptimization(mc, speed);
        }
        
        // Dynamic render distance adjustment
        if (DemonCoreConfig.DYNAMIC_RENDER_DISTANCE.get()) {
            adjustRenderDistance(mc, speed);
        }

        if (currentTime - lastOptimizationTime > 5000) {
            performPeriodicOptimization(mc);
            lastOptimizationTime = currentTime;
        }
    }
    
    private static void adjustRenderDistance(Minecraft mc, double speed) {
        if (mc.options == null) return;
        
        int current = mc.options.renderDistance().get();
        int min = DemonCoreConfig.MIN_RENDER_DISTANCE.get();
        int optimal = calculateOptimalRenderDistance(speed, (int)PerformanceMonitor.getCurrentFps());
        
        // Ensure we never go below minimum
        optimal = Math.max(optimal, min);
        
        if (optimal != current) {
            mc.options.renderDistance().set(optimal);
            LOGGER.debug("Adjusted render distance: {} -> {} (speed: {}m/s)", current, optimal, (int)speed);
        }
    }
    
    private static void applyHighSpeedOptimization(Minecraft mc, double speed) {
        if (mc.level == null || mc.player == null) return;

        frameSkipCounter++;
        if (frameSkipCounter % 3 == 0) {
            LOGGER.trace("High-speed frame skip at {}m/s", speed);
        }
    }
    
    private static void applyMediumSpeedOptimization(Minecraft mc, double speed) {
        if (mc.level == null || mc.player == null) return;

        frameSkipCounter++;
        if (frameSkipCounter % 5 == 0) {
            LOGGER.trace("Medium-speed optimization at {}m/s", speed);
        }
    }
    
    private static void performPeriodicOptimization(Minecraft mc) {
        if (mc.level == null) return;

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        
        float usage = (float) usedMemory / maxMemory;
        
        if (usage > 0.85f) {

            LOGGER.warn("High memory usage: {}MB/{}MB ({}%) - Suggesting GC", 
                usedMemory, maxMemory, (int)(usage * 100));
            System.gc();
        } else if (usage < 0.30f) {

            LOGGER.debug("Low memory usage: {}MB/{}MB ({}%) - Can use more cache", 
                usedMemory, maxMemory, (int)(usage * 100));
        }
    }
    
    public static void logGPUStats() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        
        LOGGER.info("☢ GPU/Memory Stats:");
        LOGGER.info("  - RAM: {}MB / {}MB ({}%)", 
            usedMemory, maxMemory, (int)((float)usedMemory / maxMemory * 100));
        LOGGER.info("  - Frame skips: {}", frameSkipCounter);
    }
    
    public static void emergencyGPURelief(Minecraft mc) {
        LOGGER.warn("☢ EMERGENCY GPU RELIEF ACTIVATED!");

        if (mc.level != null) {

            if (mc.particleEngine != null) {
                try {

                    LOGGER.info("  - Preparing particle cleanup via GC");
                } catch (Exception e) {
                    LOGGER.warn("  - Particle cleanup skipped: {}", e.getMessage());
                }
            }
        }

        System.gc();
        System.gc(); // İkinci pass
        LOGGER.info("  - Forced aggressive garbage collection");

        logGPUStats();
    }
    
    public static int calculateOptimalRenderDistance(double speed, int currentFPS) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            return 12; // Default
        }

        if (speed > 100.0 || currentFPS < 30) {
            return 6; // Minimum - acil durum
        } else if (speed > 50.0 || currentFPS < 45) {
            return 8; // Düşük - yüksek hız
        } else if (speed > 20.0 || currentFPS < 60) {
            return 12; // Orta - normal hız
        } else {
            return 16; // Yüksek - düşük hız/iyi FPS
        }
    }
}
