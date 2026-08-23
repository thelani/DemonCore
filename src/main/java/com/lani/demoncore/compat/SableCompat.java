package com.lani.demoncore.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public class SableCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(SableCompat.class);
    private static boolean initialized = false;
    private static boolean sableLoaded = false;
    private static Method getSubLevelMethod = null;
    private static Method getVelocityMethod = null;

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        sableLoaded = ModList.get().isLoaded("sable");

        if (sableLoaded) {
            LOGGER.info("Sable detected - enabling velocity tracking");
            try {
                Class<?> sableApiClass = Class.forName("dev.ryanhcode.sable.api.SableAPI");
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.api.SubLevel");
                
                getSubLevelMethod = sableApiClass.getDeclaredMethod("getContainingSubLevel", Entity.class);
                getVelocityMethod = subLevelClass.getDeclaredMethod("getVelocity");
                
                LOGGER.info("Sable velocity tracking initialized");
            } catch (Exception e) {
                LOGGER.warn("Failed to initialize Sable velocity tracking: {}", e.getMessage());
                sableLoaded = false;
            }
        }
    }

    public static boolean isSableLoaded() {
        return sableLoaded;
    }

    public static Vec3 getSableVelocity(Entity entity) {
        if (!sableLoaded || getSubLevelMethod == null || getVelocityMethod == null) {
            return Vec3.ZERO;
        }

        try {
            Object subLevel = getSubLevelMethod.invoke(null, entity);
            if (subLevel != null) {
                Object velocity = getVelocityMethod.invoke(subLevel);
                if (velocity instanceof Vec3) {
                    return (Vec3) velocity;
                }
            }
        } catch (Exception e) {
            LOGGER.trace("Could not get Sable velocity: {}", e.getMessage());
        }

        return Vec3.ZERO;
    }

    public static double getTotalSpeed(Entity entity) {
        Vec3 entityVelocity = entity.getDeltaMovement();
        Vec3 sableVelocity = getSableVelocity(entity);
        
        Vec3 totalVelocity = entityVelocity.add(sableVelocity);
        return totalVelocity.length() * 20.0;
    }
}
