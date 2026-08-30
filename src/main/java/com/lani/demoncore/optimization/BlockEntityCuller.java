package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class BlockEntityCuller {

    private BlockEntityCuller() {
    }

    private static int renderedThisFrame;
    private static int culledThisFrame;
    private static int frustumCulledThisFrame;
    private static int distanceCulledThisFrame;

    private static volatile int lastRendered;
    private static volatile int lastCulled;
    private static volatile int lastFrustumCulled;
    private static volatile int lastDistanceCulled;

    private static double adaptiveDistance = 48.0;
    private static double adaptiveDistanceVelocity = 0.0;

    private static long totalCulled;
    private static long totalFrustumCulled;

    public static void beginFrame() {
        lastRendered = renderedThisFrame;
        lastCulled = culledThisFrame;
        lastFrustumCulled = frustumCulledThisFrame;
        lastDistanceCulled = distanceCulledThisFrame;
        
        renderedThisFrame = 0;
        culledThisFrame = 0;
        frustumCulledThisFrame = 0;
        distanceCulledThisFrame = 0;

        updateAdaptiveDistance();
    }
    
    private static void updateAdaptiveDistance() {
        double configured = DemonCoreConfig.getInt(DemonCoreConfig.BLOCK_ENTITY_CULL_DISTANCE, 48);
        double target = configured;

        if (DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_QUALITY, true)) {
            double quality = FrameProfiler.getQuality();
            
            // Adjust based on load
            if (lastRendered > 200) {
                // Heavy load - reduce distance more aggressively
                double crowdFactor = Math.min(1.0, 200.0 / lastRendered);
                double scale = Math.max(quality * 0.8, 0.35 + 0.65 * crowdFactor);
                target = configured * scale;
            } else if (lastRendered > 100) {
                // Moderate load - gentle reduction
                double crowdFactor = Math.min(1.0, 100.0 / lastRendered);
                double scale = Math.max(quality * 0.9, 0.5 + 0.5 * crowdFactor);
                target = configured * scale;
            } else {
                // Light load - use quality directly
                target = configured * Math.max(quality, 0.7);
            }
        }

        // Smooth transition with velocity (prevents oscillation)
        double delta = target - adaptiveDistance;
        adaptiveDistanceVelocity = 0.6 * adaptiveDistanceVelocity + 0.4 * delta;
        adaptiveDistance += adaptiveDistanceVelocity * 0.15;
        adaptiveDistance = Math.max(16.0, Math.min(configured, adaptiveDistance));
    }
    
    /**
     * Simple frustum check using camera direction and FOV
     * More accurate than just distance, catches block entities behind player
     */
    private static boolean isInFrustum(BlockPos pos, Camera camera) {
        Vec3 camPos = camera.getPosition();
        Vector3f lookVec = camera.getLookVector();
        Vec3 camLook = new Vec3(lookVec.x(), lookVec.y(), lookVec.z());
        
        // Vector from camera to block entity
        double dx = (pos.getX() + 0.5) - camPos.x;
        double dy = (pos.getY() + 0.5) - camPos.y;
        double dz = (pos.getZ() + 0.5) - camPos.z;
        
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 4.0) {
            // Very close - always visible
            return true;
        }
        
        double dist = Math.sqrt(distSq);
        
        // Normalize direction vector
        dx /= dist;
        dy /= dist;
        dz /= dist;
        
        // Dot product with camera look vector
        double dot = dx * camLook.x + dy * camLook.y + dz * camLook.z;
        
        // FOV-based threshold (approximately 110° FOV total = ±55°)
        // cos(60°) ≈ 0.5, cos(70°) ≈ 0.34
        // Use adaptive threshold: closer = more lenient
        double threshold = dist < 16.0 ? -0.2 : (dist < 32.0 ? 0.0 : 0.15);
        
        return dot > threshold;
    }

    
    public static boolean shouldCull(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        if (!DemonCoreConfig.isRenderOptimizationEnabled()
                || !DemonCoreConfig.getBool(DemonCoreConfig.BLOCK_ENTITY_CULLING, true)
                || com.lani.demoncore.compat.ModCompat.hasEntityCulling()) {
            renderedThisFrame++;
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) {
            return false;
        }
        
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        Vec3 camPos = camera.getPosition();
        
        // Calculate distance
        double dx = (pos.getX() + 0.5) - camPos.x;
        double dy = (pos.getY() + 0.5) - camPos.y;
        double dz = (pos.getZ() + 0.5) - camPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        // Distance culling
        if (distSq > adaptiveDistance * adaptiveDistance) {
            culledThisFrame++;
            distanceCulledThisFrame++;
            totalCulled++;
            return true;
        }
        
        // Frustum culling (only for moderately distant block entities)
        if (distSq > 256.0) { // Beyond 16 blocks
            if (!isInFrustum(pos, camera)) {
                culledThisFrame++;
                frustumCulledThisFrame++;
                totalFrustumCulled++;
                return true;
            }
        }

        renderedThisFrame++;
        return false;
    }

    public static int getRenderedLastFrame() {
        return lastRendered;
    }

    public static int getCulledLastFrame() {
        return lastCulled;
    }
    
    public static int getFrustumCulledLastFrame() {
        return lastFrustumCulled;
    }
    
    public static int getDistanceCulledLastFrame() {
        return lastDistanceCulled;
    }

    public static double getEffectiveDistance() {
        return adaptiveDistance;
    }

    public static long getTotalCulled() {
        return totalCulled;
    }
    
    public static long getTotalFrustumCulled() {
        return totalFrustumCulled;
    }

    public static void reset() {
        totalCulled = 0L;
        totalFrustumCulled = 0L;
        adaptiveDistance = DemonCoreConfig.getInt(DemonCoreConfig.BLOCK_ENTITY_CULL_DISTANCE, 48);
        adaptiveDistanceVelocity = 0.0;
    }

    public static String getStats() {
        int total = lastRendered + lastCulled;
        if (total == 0) {
            return "BlockEntities: idle";
        }
        double cullRate = 100.0 * lastCulled / total;
        return String.format("BlockEntities: %d rendered, %d culled (%.0f%%) [%d frustum, %d distance] | radius %.0f blocks",
                lastRendered, lastCulled, cullRate, lastFrustumCulled, lastDistanceCulled, adaptiveDistance);
    }
}
