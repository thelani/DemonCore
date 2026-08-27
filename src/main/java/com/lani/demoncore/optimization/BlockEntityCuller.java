package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class BlockEntityCuller {

    private BlockEntityCuller() {
    }

    private static int renderedThisFrame;
    private static int culledThisFrame;

    private static volatile int lastRendered;
    private static volatile int lastCulled;

    private static double adaptiveDistance = 48.0;

    private static long totalCulled;

    public static void beginFrame() {
        lastRendered = renderedThisFrame;
        lastCulled = culledThisFrame;
        renderedThisFrame = 0;
        culledThisFrame = 0;

        double configured = DemonCoreConfig.getInt(DemonCoreConfig.BLOCK_ENTITY_CULL_DISTANCE, 48);
        double target = configured;

        if (DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_QUALITY, true) && lastRendered > 150) {
            double crowdFactor = Math.min(1.0, 150.0 / lastRendered);
            double scale = Math.max(FrameProfiler.getQuality(), 0.4 + 0.6 * crowdFactor);
            target = configured * scale;
        }

        adaptiveDistance += 0.15 * (target - adaptiveDistance);
        adaptiveDistance = Math.max(16.0, adaptiveDistance);
    }

    
    public static boolean shouldCull(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        if (!DemonCoreConfig.isRenderOptimizationEnabled()
                || !DemonCoreConfig.getBool(DemonCoreConfig.BLOCK_ENTITY_CULLING, true)) {
            renderedThisFrame++;
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }
        net.minecraft.world.entity.Entity camera = mc.getCameraEntity();
        if (camera == null) {
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        double dx = (pos.getX() + 0.5) - camera.getX();
        double dy = (pos.getY() + 0.5) - camera.getY();
        double dz = (pos.getZ() + 0.5) - camera.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > adaptiveDistance * adaptiveDistance) {
            culledThisFrame++;
            totalCulled++;
            return true;
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

    public static double getEffectiveDistance() {
        return adaptiveDistance;
    }

    public static long getTotalCulled() {
        return totalCulled;
    }

    public static void reset() {
        totalCulled = 0L;
        adaptiveDistance = DemonCoreConfig.getInt(DemonCoreConfig.BLOCK_ENTITY_CULL_DISTANCE, 48);
    }

    public static String getStats() {
        return String.format("Block entities: %d rendered, %d culled | effective radius %.0f blocks",
                lastRendered, lastCulled, adaptiveDistance);
    }
}
