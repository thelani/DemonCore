package com.lani.demoncore.mixin;

import com.lani.demoncore.DemonCore;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class, priority = Integer.MAX_VALUE)
public abstract class DebugEjectionMixin {
    private static final double HIGH_SPEED_THRESHOLD = 500.0; // 500 m/s - daha düşük threshold
    
    @Shadow
    public abstract Entity getVehicle();
    
    @Shadow
    public abstract boolean isVehicle();

    @Inject(method = "stopRiding()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$blockStopRiding(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof Player player)) return;
        
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        double speed = getEntitySpeed(vehicle);
        
        if (speed > HIGH_SPEED_THRESHOLD) {
            DemonCore.LOGGER.warn("☢️ BLOCKED stopRiding() at {:.0f} m/s - KEEPING PLAYER ON VEHICLE", speed);
            ci.cancel();
        }
    }

    @Inject(method = "removePassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$blockRemovePassenger(Entity passenger, CallbackInfo ci) {
        if (!(passenger instanceof Player)) return;
        
        Entity vehicle = (Entity) (Object) this;
        double speed = getEntitySpeed(vehicle);
        
        if (speed > HIGH_SPEED_THRESHOLD) {
            DemonCore.LOGGER.warn("☢️ BLOCKED removePassenger() at {:.0f} m/s", speed);
            ci.cancel();
        }
    }

    @Inject(method = "ejectPassengers", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$blockEjectPassengers(CallbackInfo ci) {
        Entity vehicle = (Entity) (Object) this;
        if (!vehicle.isVehicle()) return;
        
        double speed = getEntitySpeed(vehicle);
        boolean hasPlayer = vehicle.getPassengers().stream()
            .anyMatch(p -> p instanceof Player);
        
        if (hasPlayer && speed > HIGH_SPEED_THRESHOLD) {
            DemonCore.LOGGER.warn("☢️ BLOCKED ejectPassengers() at {:.0f} m/s", speed);
            ci.cancel();
        }
    }

    @Inject(method = "unRide", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$blockUnRide(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof Player player)) return;
        
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;
        
        double speed = getEntitySpeed(vehicle);
        
        if (speed > HIGH_SPEED_THRESHOLD) {
            DemonCore.LOGGER.warn("☢️ BLOCKED unRide() at {:.0f} m/s", speed);
            ci.cancel();
        }
    }

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$forceCanAddPassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        if (!(passenger instanceof Player)) return;
        
        Entity vehicle = (Entity) (Object) this;
        double speed = getEntitySpeed(vehicle);
        
        if (speed > HIGH_SPEED_THRESHOLD) {
            DemonCore.LOGGER.info("☢️ FORCE canAddPassenger=true at {:.0f} m/s", speed);
            cir.setReturnValue(true);
        }
    }
    
    private double getEntitySpeed(Entity entity) {
        try {

            var getVelocityMethod = entity.getClass().getMethod("getVelocity");
            Object velocityObj = getVelocityMethod.invoke(entity);
            
            if (velocityObj != null) {

                var xMethod = velocityObj.getClass().getMethod("x");
                var yMethod = velocityObj.getClass().getMethod("y");
                var zMethod = velocityObj.getClass().getMethod("z");
                
                double vx = (double) xMethod.invoke(velocityObj);
                double vy = (double) yMethod.invoke(velocityObj);
                double vz = (double) zMethod.invoke(velocityObj);
                
                double speed = Math.sqrt(vx * vx + vy * vy + vz * vz) * 20.0;
                return speed;
            }
        } catch (Exception ignored) {

        }

        return entity.getDeltaMovement().length() * 20.0;
    }
}
