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

@Mixin(Entity.class)
public abstract class PreventHighSpeedEjectionMixin {

    @Shadow
    public abstract Vec3 getDeltaMovement();
    
    @Shadow
    public abstract boolean isVehicle();
    
    @Shadow
    public abstract Entity getVehicle();
    
    @Shadow
    public abstract boolean isPassenger();

    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void demoncore$preventHighSpeedEjection(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (!(entity instanceof Player)) return;

        if (!entity.isPassenger()) return;
        
        Entity vehicle = entity.getVehicle();
        if (vehicle == null) return;

        double speed = SableCompat.getEntityVelocityMagnitude(vehicle);

        if (speed == 0 && !SableCompat.isEntityInSubLevel(vehicle)) {
            Vec3 vehicleMotion = vehicle.getDeltaMovement();
            speed = vehicleMotion.length() * 20.0; // blocks/tick -> m/s
        }

        if (speed >= 500.0) {
            ci.cancel(); // stopRiding() çağrısını iptal et
            
            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.05) {
                com.lani.demoncore.DemonCore.LOGGER.info(
                    "🛡️ EJECTION BLOCKED: Player {} protected from ejection at {:.0f} m/s",
                    ((Player) entity).getName().getString(),
                    speed
                );
            }
        }
    }

    @Inject(method = "removePassenger", at = @At("HEAD"), cancellable = true)
    private void demoncore$preventPassengerRemoval(Entity passenger, CallbackInfo ci) {
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
                com.lani.demoncore.DemonCore.LOGGER.warn(
                    "🛡️ PASSENGER REMOVAL BLOCKED: Vehicle at {:.0f} m/s tried to eject player",
                    speed
                );
            }
        }
    }

    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", 
            at = @At("HEAD"), cancellable = true)
    private void demoncore$safePositionRider(Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
        Entity vehicle = (Entity) (Object) this;

        if (!(passenger instanceof Player)) return;

        double speed = SableCompat.getEntityVelocityMagnitude(vehicle);

        if (speed == 0 && !SableCompat.isEntityInSubLevel(vehicle)) {
            Vec3 vehicleMotion = vehicle.getDeltaMovement();
            speed = vehicleMotion.length() * 20.0;
        }

        if (speed >= 2000.0) {

            Vec3 vehiclePos = vehicle.position();

            double yOffset = vehicle.getBbHeight();

            moveFunction.accept(passenger, vehiclePos.x, vehiclePos.y + yOffset, vehiclePos.z);
            
            ci.cancel(); // Normal positionRider() metodunu atla
            
            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.001) {
                com.lani.demoncore.DemonCore.LOGGER.debug(
                    "🛡️ EXTREME SPEED POSITIONING: Collision checks bypassed at {:.0f} m/s",
                    speed
                );
            }
        }
    }

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void demoncore$alwaysAllowPassengers(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        Entity vehicle = (Entity) (Object) this;

        if (!vehicle.isVehicle()) return;

        double speed = SableCompat.getEntityVelocityMagnitude(vehicle);

        if (speed == 0 && !SableCompat.isEntityInSubLevel(vehicle)) {
            Vec3 vehicleMotion = vehicle.getDeltaMovement();
            speed = vehicleMotion.length() * 20.0;
        }

        if (speed >= 500.0) {
            cir.setReturnValue(true); // "Evet, yolcu alabilirsin"
        }
    }
}
