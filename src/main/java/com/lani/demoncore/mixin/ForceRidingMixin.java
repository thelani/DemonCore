package com.lani.demoncore.mixin;

import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.compat.SableCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class, priority = 10000)
public abstract class ForceRidingMixin {

    @Shadow
    public abstract Vec3 getDeltaMovement();
    
    @Shadow
    public abstract boolean isVehicle();
    
    @Shadow
    public abstract Entity getVehicle();
    
    @Shadow
    public abstract boolean isPassenger();

    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$forceStayOnVehicle(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (!(entity instanceof Player player)) return;

        if (!entity.isPassenger()) return;
        
        Entity vehicle = entity.getVehicle();
        if (vehicle == null) return;

        double speed = SableCompat.getEntityVelocityMagnitude(vehicle);

        if (speed == 0 && !SableCompat.isEntityInSubLevel(vehicle)) {
            Vec3 vehicleMotion = vehicle.getDeltaMovement();
            speed = vehicleMotion.length() * 20.0;
        }

        if (speed >= 500.0) {
            ci.cancel();
            
            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.05) {
                com.lani.demoncore.DemonCore.LOGGER.warn(
                    "🔒 FORCED RIDING: {} locked to vehicle at {:.0f} m/s - EJECTION IMPOSSIBLE",
                    player.getName().getString(),
                    speed
                );
            }
        }
    }

    @Inject(method = "removePassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$forceKeepPassenger(Entity passenger, CallbackInfo ci) {
        Entity vehicle = (Entity) (Object) this;

        if (!(passenger instanceof Player)) return;

        double speed = SableCompat.getEntityVelocityMagnitude(vehicle);

        if (speed == 0 && !SableCompat.isEntityInSubLevel(vehicle)) {
            Vec3 vehicleMotion = vehicle.getDeltaMovement();
            speed = vehicleMotion.length() * 20.0;
        }

        if (speed >= 500.0) {
            ci.cancel();
            
            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.05) {
                com.lani.demoncore.DemonCore.LOGGER.error(
                    "🔒 REMOVAL BLOCKED: Vehicle at {:.0f} m/s cannot remove passenger!",
                    speed
                );
            }
        }
    }

    @Inject(method = "ejectPassengers", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$preventEjectPassengers(CallbackInfo ci) {
        Entity vehicle = (Entity) (Object) this;

        if (!vehicle.isVehicle()) return;

        double speed = SableCompat.getEntityVelocityMagnitude(vehicle);

        if (speed == 0 && !SableCompat.isEntityInSubLevel(vehicle)) {
            Vec3 vehicleMotion = vehicle.getDeltaMovement();
            speed = vehicleMotion.length() * 20.0;
        }

        if (speed >= 500.0) {
            ci.cancel();
            
            if (DemonCoreConfig.ENABLE_DEBUG.get()) {
                com.lani.demoncore.DemonCore.LOGGER.error(
                    "🔒 EJECT BLOCKED: Vehicle at {:.0f} m/s tried to eject all passengers!",
                    speed
                );
            }
        }
    }

    @Inject(method = "unRide", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$preventUnRide(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (!(entity instanceof Player)) return;

        if (!entity.isPassenger()) return;
        
        Entity vehicle = entity.getVehicle();
        if (vehicle == null) return;

        double speed = SableCompat.getEntityVelocityMagnitude(vehicle);

        if (speed == 0 && !SableCompat.isEntityInSubLevel(vehicle)) {
            Vec3 vehicleMotion = vehicle.getDeltaMovement();
            speed = vehicleMotion.length() * 20.0;
        }

        if (speed >= 500.0) {
            ci.cancel();
        }
    }

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$alwaysAllowPassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        Entity vehicle = (Entity) (Object) this;

        if (!vehicle.isVehicle()) return;

        double speed = SableCompat.getEntityVelocityMagnitude(vehicle);

        if (speed == 0 && !SableCompat.isEntityInSubLevel(vehicle)) {
            Vec3 vehicleMotion = vehicle.getDeltaMovement();
            speed = vehicleMotion.length() * 20.0;
        }

        if (speed >= 500.0) {
            cir.setReturnValue(true);
        }
    }
}
