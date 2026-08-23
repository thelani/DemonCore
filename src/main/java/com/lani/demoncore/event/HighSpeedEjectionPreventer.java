package com.lani.demoncore.event;

import com.lani.demoncore.DemonCore;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.lang.reflect.Method;

@EventBusSubscriber(modid = DemonCore.MOD_ID)
public class HighSpeedEjectionPreventer {
    private static final double HIGH_SPEED_THRESHOLD = 1000.0; // 1000 m/s
    
    private static Method getVelocityMethod;
    private static boolean sableChecked = false;
    private static boolean sableAvailable = false;

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();

        if (!(entity instanceof Player player)) return;

        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        if (!sableChecked) {
            checkSableAvailability();
        }
        
        if (!sableAvailable) {

            double speed = vehicle.getDeltaMovement().length() * 20.0;
            if (speed > HIGH_SPEED_THRESHOLD) {
                preventEjection(player, vehicle, speed);
            }
            return;
        }

        try {
            Object velocityObj = getVelocityMethod.invoke(vehicle);
            if (velocityObj == null) return;

            double vx = (double) velocityObj.getClass().getMethod("x").invoke(velocityObj);
            double vy = (double) velocityObj.getClass().getMethod("y").invoke(velocityObj);
            double vz = (double) velocityObj.getClass().getMethod("z").invoke(velocityObj);
            
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz) * 20.0;
            
            if (speed > HIGH_SPEED_THRESHOLD) {
                preventEjection(player, vehicle, speed);
                DemonCore.LOGGER.info("☢️ SubLevel speed: {:.0f} m/s - Player protected", speed);
            }
        } catch (Exception e) {

            double speed = vehicle.getDeltaMovement().length() * 20.0;
            if (speed > HIGH_SPEED_THRESHOLD) {
                preventEjection(player, vehicle, speed);
            }
        }
    }
    
    private static void preventEjection(Player player, Entity vehicle, double speed) {

        if (player.getVehicle() != vehicle) {
            player.startRiding(vehicle, true);
            DemonCore.LOGGER.warn("☢️ EMERGENCY RE-MOUNT at {:.0f} m/s", speed);
        }

        if (!vehicle.getPassengers().contains(player)) {
            player.startRiding(vehicle, true);
            DemonCore.LOGGER.warn("☢️ FORCE RE-MOUNT (not in passenger list) at {:.0f} m/s", speed);
        }
    }
    
    private static void checkSableAvailability() {
        sableChecked = true;
        try {

            Class<?> subLevelEntityClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevelEntity");
            getVelocityMethod = subLevelEntityClass.getMethod("getVelocity");
            sableAvailable = true;
            DemonCore.LOGGER.info("☢️ Sable SubLevel API detected - using reflection");
        } catch (Exception e) {
            sableAvailable = false;
            DemonCore.LOGGER.warn("☢️ Sable SubLevel API not found - using vanilla velocity");
        }
    }
}
