package com.lani.demoncore.detection;

import net.minecraft.world.entity.Entity;

public class VehicleDetector {
    
    public static boolean isCreateContraption(Entity entity) {
        if (entity == null) return false;
        
        String className = entity.getClass().getName();
        return className.contains("AbstractContraptionEntity") ||
               className.contains("OrientedContraptionEntity") ||
               className.contains("Carriage") ||
               className.contains("create");
    }
    
    public static boolean shouldOptimize(Entity entity, double speed, double threshold) {
        if (!isCreateContraption(entity)) {
            return false;
        }
        
        return speed >= threshold;
    }
}
