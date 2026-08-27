package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class EntityCuller {

    private EntityCuller() {
    }

    private static int renderedThisFrame;
    private static int culledThisFrame;

    private static volatile int lastRendered;
    private static volatile int lastCulled;

    private static double adaptiveDistance = 96.0;

    private static long totalCulled;

    
    public static void beginFrame() {
        lastRendered = renderedThisFrame;
        lastCulled = culledThisFrame;
        renderedThisFrame = 0;
        culledThisFrame = 0;

        updateAdaptiveDistance();
    }

    private static void updateAdaptiveDistance() {
        double configured = DemonCoreConfig.getInt(DemonCoreConfig.ENTITY_CULL_DISTANCE, 96);
        double target = configured;

        if (DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_QUALITY, true)) {
            
            
            if (lastRendered > 80) {
                double quality = FrameProfiler.getQuality();
                double crowdFactor = Math.min(1.0, 80.0 / lastRendered);
                double scale = Math.max(quality, 0.45 + 0.55 * crowdFactor);
                target = configured * scale;
            }
        }

        
        adaptiveDistance += 0.15 * (target - adaptiveDistance);
        adaptiveDistance = Math.max(24.0, adaptiveDistance);
    }

    
    public static boolean shouldCull(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (!DemonCoreConfig.isRenderOptimizationEnabled()
                || !DemonCoreConfig.getBool(DemonCoreConfig.ENTITY_CULLING, true)) {
            renderedThisFrame++;
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }

        
        Entity camera = mc.getCameraEntity();
        if (camera != null) {
            if (entity == camera
                    || entity == camera.getVehicle()
                    || entity.getVehicle() == camera
                    || entity.hasPassenger(camera)) {
                renderedThisFrame++;
                return false;
            }
        }
        if (entity == mc.player) {
            renderedThisFrame++;
            return false;
        }

        double maxDist = adaptiveDistance;
        double distSq = camera != null
                ? camera.distanceToSqr(entity.getX(), entity.getY(), entity.getZ())
                : 0.0;

        if (distSq > maxDist * maxDist) {
            culledThisFrame++;
            totalCulled++;
            return true;
        }

        renderedThisFrame++;
        return false;
    }

    
    public static boolean shouldCullShadow(Entity entity) {
        if (!DemonCoreConfig.isRenderOptimizationEnabled()) {
            return false;
        }

        int shadowDistance = DemonCoreConfig.getInt(DemonCoreConfig.ENTITY_SHADOW_DISTANCE, 24);
        if (shadowDistance <= 0) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || entity == null) {
            return false;
        }
        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            return false;
        }

        double effective = shadowDistance;
        if (DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_QUALITY, true)) {
            effective *= FrameProfiler.getQuality();
        }

        double distSq = camera.distanceToSqr(entity.getX(), entity.getY(), entity.getZ());
        return distSq > effective * effective;
    }

    public static int getRenderedLastFrame() {
        return lastRendered;
    }

    public static int getCulledLastFrame() {
        return lastCulled;
    }

    public static double getEffectiveDistance() {
        return adaptiveDistance;
    }

    public static long getTotalCulled() {
        return totalCulled;
    }

    public static void reset() {
        totalCulled = 0L;
        adaptiveDistance = DemonCoreConfig.getInt(DemonCoreConfig.ENTITY_CULL_DISTANCE, 96);
    }

    public static String getStats() {
        return String.format("Entities: %d rendered, %d culled | effective radius %.0f blocks",
                lastRendered, lastCulled, adaptiveDistance);
    }
}
