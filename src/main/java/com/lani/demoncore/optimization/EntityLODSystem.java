package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;

public final class EntityLODSystem {

    private EntityLODSystem() {
    }

    public enum LOD {
        FULL(1.0, 0),
        SIMPLE(0.6, 1),
        BILLBOARD(0.02, 2),
        DOT(0.001, 3);

        public final double costFactor;
        public final int level;

        LOD(double costFactor, int level) {
            this.costFactor = costFactor;
            this.level = level;
        }
    }

    private static volatile long totalFull;
    private static volatile long totalSimple;
    private static volatile long totalBillboard;
    private static volatile long totalDot;
    private static volatile double estimatedGpuSavedMs;

    
    public static LOD computeLOD(Entity entity, double distSq) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.ENTITY_LOD_ENABLED, true)) {
            return LOD.FULL;
        }

        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc != null ? mc.getCameraEntity() : null;
        if (cam != null) {
            if (entity == cam
                    || entity == cam.getVehicle()
                    || entity.getVehicle() == cam
                    || entity.hasPassenger(cam)
                    || entity == mc.player) {
                totalFull++;
                return LOD.FULL;
            }
        }

        double quality = DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_QUALITY, true)
                ? FrameProfiler.getQuality() : 1.0;

        double full = DemonCoreConfig.getInt(DemonCoreConfig.LOD_FULL_DISTANCE, 24);
        double simple = DemonCoreConfig.getInt(DemonCoreConfig.LOD_SIMPLE_DISTANCE, 48);
        double bill = DemonCoreConfig.getInt(DemonCoreConfig.LOD_BILLBOARD_DISTANCE, 72);
        double cull = DemonCoreConfig.getInt(DemonCoreConfig.ENTITY_CULL_DISTANCE, 96);

        
        full *= (0.5 + 0.5 * quality);
        simple *= (0.5 + 0.5 * quality);
        bill *= (0.5 + 0.5 * quality);
        cull *= (0.5 + 0.5 * quality);

        
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            full *= 0.5;
            simple *= 0.5;
            bill *= 0.6;
        }

        double dist = Math.sqrt(distSq);
        LOD result;
        if (dist <= full) {
            result = LOD.FULL;
            totalFull++;
        } else if (dist <= simple) {
            result = LOD.SIMPLE;
            totalSimple++;
        } else if (dist <= bill) {
            result = LOD.BILLBOARD;
            totalBillboard++;
        } else {
            result = LOD.DOT;
            totalDot++;
        }

        
        estimatedGpuSavedMs += (1.0 - result.costFactor);
        return result;
    }

    
    public static LOD computeLOD(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc != null ? mc.getCameraEntity() : null;
        double distSq = cam != null
                ? cam.distanceToSqr(entity.getX(), entity.getY(), entity.getZ())
                : 0.0;
        return computeLOD(entity, distSq);
    }

    public static boolean animationAllowed(Entity entity) {
        LOD lod = computeLOD(entity);
        if (lod == LOD.FULL) {
            return true;
        }
        return false;
    }

    public static long getFullCount() { return totalFull; }
    public static long getSimpleCount() { return totalSimple; }
    public static long getBillboardCount() { return totalBillboard; }
    public static long getDotCount() { return totalDot; }

    public static double getSavedMsEstimate() {
        return estimatedGpuSavedMs * 0.005;
    }

    public static String getStats() {
        long total = totalFull + totalSimple + totalBillboard + totalDot;
        if (total == 0) {
            return "EntityLOD: idle";
        }
        double simplePct = 100.0 * totalSimple / (double) total;
        double billPct = 100.0 * totalBillboard / (double) total;
        double dotPct = 100.0 * totalDot / (double) total;
        return String.format("EntityLOD: full %.0f%% | simple %.0f%% | billboard %.0f%% | dot %.0f%% | ~%.1f ms GPU saved",
                100.0 - simplePct - billPct - dotPct, simplePct, billPct, dotPct, getSavedMsEstimate());
    }
}
