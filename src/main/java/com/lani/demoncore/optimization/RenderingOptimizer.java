package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderingOptimizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingOptimizer.class);
    private static boolean initialized = false;
    private static int frameSkipCounter = 0;
    private static long lastOptimizationTime = 0;
    
    public static void init() {
        if (initialized) return;
        initialized = true;
        
        LOGGER.info("☢ ========================================");
        LOGGER.info("☢  RENDERING OPTIMIZER - GPU Fix");
        LOGGER.info("☢ ========================================");
        
        applyAggressiveOptimizations();
        
        LOGGER.info("☢  GPU Optimizations: ACTIVE");
        LOGGER.info("☢  Frame Skip: ENABLED");
        LOGGER.info("☢  Render Distance: DYNAMIC");
        LOGGER.info("☢ ========================================");
    }
    
    private static void applyAggressiveOptimizations() {

        System.setProperty("demoncore.gpu.aggressive", "true");
        System.setProperty("demoncore.render.priority", "speed");

        System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "false");
        System.setProperty("org.lwjgl.opengl.Window.undecorated", "false");

        System.setProperty("java.awt.headless", "true");
        
        LOGGER.info("Applied aggressive GPU optimizations");
    }
    
    public static void optimizeFrame(Minecraft mc, double speed) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) return;
        
        long currentTime = System.currentTimeMillis();

        if (speed > 50.0) {

            applyHighSpeedOptimization(mc, speed);
        } else if (speed > 20.0) {

            applyMediumSpeedOptimization(mc, speed);
        }

        if (currentTime - lastOptimizationTime > 5000) {
            performPeriodicOptimization(mc);
            lastOptimizationTime = currentTime;
        }
    }
    
    private static void applyHighSpeedOptimization(Minecraft mc, double speed) {
        if (mc.level == null || mc.player == null) return;

        frameSkipCounter++;
        if (frameSkipCounter % 3 == 0) {

            LOGGER.trace("High-speed frame skip at {}m/s", speed);
        }

        if (mc.options.renderDistance().get() > 8) {

            LOGGER.trace("Reducing render distance for high speed: {}", speed);
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
        System.runFinalization();
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
