package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

public final class EntityLODSystem {

    private EntityLODSystem() {
    }

    public enum LOD {
        FULL(1.0, 0),
        SIMPLE(0.55, 1),
        BILLBOARD(0.015, 2),
        DOT(0.001, 3);

        public final double costFactor;
        public final int level;

        LOD(double costFactor, int level) {
            this.costFactor = costFactor;
            this.level = level;
        }
    }
    
    // Per-entity-type LOD multipliers
    private static final double PLAYER_MULTIPLIER = 1.8;      // Players get full detail longer
    private static final double VILLAGER_MULTIPLIER = 1.3;    // Important NPCs
    private static final double MONSTER_MULTIPLIER = 1.1;     // Threats stay visible
    private static final double ANIMAL_MULTIPLIER = 0.8;      // Passive mobs reduce faster
    private static final double ITEM_MULTIPLIER = 0.4;        // Items reduce aggressively
    private static final double ORB_MULTIPLIER = 0.3;         // XP orbs reduce most

    private static volatile long totalFull;
    private static volatile long totalSimple;
    private static volatile long totalBillboard;
    private static volatile long totalDot;
    private static volatile double estimatedGpuSavedMs;
    
    // Frame-to-frame counters
    private static int fullThisFrame;
    private static int simpleThisFrame;
    private static int billboardThisFrame;
    private static int dotThisFrame;

    
    public static void beginFrame() {
        fullThisFrame = 0;
        simpleThisFrame = 0;
        billboardThisFrame = 0;
        dotThisFrame = 0;
    }
    
    /**
     * Get entity-type-specific distance multiplier
     */
    private static double getEntityTypeMultiplier(Entity entity) {
        if (entity instanceof Player) {
            return PLAYER_MULTIPLIER;
        } else if (entity instanceof Villager) {
            return VILLAGER_MULTIPLIER;
        } else if (entity instanceof Monster) {
            return MONSTER_MULTIPLIER;
        } else if (entity instanceof Animal) {
            return ANIMAL_MULTIPLIER;
        } else if (entity instanceof ItemEntity) {
            return ITEM_MULTIPLIER;
        } else if (entity instanceof ExperienceOrb) {
            return ORB_MULTIPLIER;
        }
        return 1.0; // Default for other entities
    }
    
    public static LOD computeLOD(Entity entity, double distSq) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.ENTITY_LOD_ENABLED, true)) {
            totalFull++;
            fullThisFrame++;
            return LOD.FULL;
        }

        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc != null ? mc.getCameraEntity() : null;
        
        // Always render player, camera, and camera vehicle at full detail
        if (cam != null) {
            if (entity == cam
                    || entity == cam.getVehicle()
                    || entity.getVehicle() == cam
                    || entity.hasPassenger(cam)
                    || entity == mc.player) {
                totalFull++;
                fullThisFrame++;
                return LOD.FULL;
            }
        }

        // Get quality multiplier from performance profiler
        double quality = DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_QUALITY, true)
                ? FrameProfiler.getQuality() : 1.0;

        // Base distances from config
        double full = DemonCoreConfig.getInt(DemonCoreConfig.LOD_FULL_DISTANCE, 24);
        double simple = DemonCoreConfig.getInt(DemonCoreConfig.LOD_SIMPLE_DISTANCE, 48);
        double billboard = DemonCoreConfig.getInt(DemonCoreConfig.LOD_BILLBOARD_DISTANCE, 72);

        // Apply entity-type multiplier
        double typeMultiplier = getEntityTypeMultiplier(entity);
        full *= typeMultiplier;
        simple *= typeMultiplier;
        billboard *= typeMultiplier;

        // Apply quality scaling (quality range: 0.35-1.0)
        // Use square root for gentler scaling at low quality
        double qualityScale = Math.sqrt(Math.max(0.4, quality));
        double vulcanBoost = DemonCoreConfig.isVulcanMode() ? 2.0 : 1.0;
        full *= qualityScale * vulcanBoost;
        simple *= qualityScale * vulcanBoost;
        billboard *= qualityScale * vulcanBoost;

        // Calculate actual distance
        double dist = Math.sqrt(distSq);
        
        LOD result;
        if (dist <= full) {
            result = LOD.FULL;
            totalFull++;
            fullThisFrame++;
        } else if (dist <= simple) {
            result = LOD.SIMPLE;
            totalSimple++;
            simpleThisFrame++;
        } else if (dist <= billboard) {
            result = LOD.BILLBOARD;
            totalBillboard++;
            billboardThisFrame++;
        } else {
            result = LOD.DOT;
            totalDot++;
            dotThisFrame++;
        }

        // Estimate GPU savings (rough approximation)
        double savedCost = (1.0 - result.costFactor);
        estimatedGpuSavedMs += savedCost * 0.001; // Very rough estimate
        
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
        return lod == LOD.FULL;
    }
    
    // Statistics
    public static long getFullCount() { return totalFull; }
    public static long getSimpleCount() { return totalSimple; }
    public static long getBillboardCount() { return totalBillboard; }
    public static long getDotCount() { return totalDot; }
    
    public static int getFullThisFrame() { return fullThisFrame; }
    public static int getSimpleThisFrame() { return simpleThisFrame; }
    public static int getBillboardThisFrame() { return billboardThisFrame; }
    public static int getDotThisFrame() { return dotThisFrame; }

    public static double getSavedMsEstimate() {
        return estimatedGpuSavedMs * 0.0008; // Calibrated estimate
    }
    
    public static void reset() {
        totalFull = 0;
        totalSimple = 0;
        totalBillboard = 0;
        totalDot = 0;
        estimatedGpuSavedMs = 0.0;
        fullThisFrame = 0;
        simpleThisFrame = 0;
        billboardThisFrame = 0;
        dotThisFrame = 0;
    }

    public static String getStats() {
        long total = totalFull + totalSimple + totalBillboard + totalDot;
        if (total == 0) {
            return "EntityLOD: idle";
        }
        int frameTotal = fullThisFrame + simpleThisFrame + billboardThisFrame + dotThisFrame;
        if (frameTotal == 0) {
            return "EntityLOD: no entities this frame";
        }
        
        double fullPct = 100.0 * fullThisFrame / frameTotal;
        double simplePct = 100.0 * simpleThisFrame / frameTotal;
        double billPct = 100.0 * billboardThisFrame / frameTotal;
        double dotPct = 100.0 * dotThisFrame / frameTotal;
        
        return String.format("EntityLOD: full %.0f%% (%d) | simple %.0f%% (%d) | billboard %.0f%% (%d) | dot %.0f%% (%d) | ~%.2f ms saved",
                fullPct, fullThisFrame, simplePct, simpleThisFrame, 
                billPct, billboardThisFrame, dotPct, dotThisFrame, getSavedMsEstimate());
    }
}
