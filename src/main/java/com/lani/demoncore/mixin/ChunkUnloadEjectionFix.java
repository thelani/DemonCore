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

@Mixin(Entity.class)
public abstract class ChunkUnloadEjectionFix {

    @Shadow
    public abstract boolean isPassenger();
    
    @Shadow
    public abstract Entity getVehicle();
    
    @Shadow
    public abstract Vec3 getDeltaMovement();
    
    @Shadow
    public abstract void ejectPassengers();

    private boolean demoncore$lastChunkLoadedState = true;
    private int demoncore$unloadedTicks = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void demoncore$preventChunkUnloadEjection(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (!entity.isVehicle()) return;

        Vec3 motion = entity.getDeltaMovement();
        double speed = motion.length() * 20.0;

        if (speed < 1000.0 && !SableCompat.isEntityInSubLevel(entity)) return;

        boolean chunkLoaded = entity.level().hasChunk(entity.chunkPosition().x, entity.chunkPosition().z);
        
        if (!chunkLoaded) {
            demoncore$unloadedTicks++;

            if (demoncore$unloadedTicks >= 5) {
                boolean hasPlayerPassenger = entity.getPassengers().stream()
                    .anyMatch(p -> p instanceof Player);
                
                if (hasPlayerPassenger && DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.01) {
                    com.lani.demoncore.DemonCore.LOGGER.warn(
                        "🛡️ CHUNK UNLOAD DETECTED: Vehicle at {} m/s in unloaded chunk for {} ticks - PROTECTING PASSENGER",
                        String.format("%.0f", speed),
                        demoncore$unloadedTicks
                    );
                }
            }
        } else {
            demoncore$unloadedTicks = 0;
        }
        
        demoncore$lastChunkLoadedState = chunkLoaded;
    }

    @Inject(method = "ejectPassengers", at = @At("HEAD"), cancellable = true)
    private void demoncore$preventEjectPassengers(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        
        Vec3 motion = entity.getDeltaMovement();
        double speed = motion.length() * 20.0;

        if (speed >= 2000.0) {
            boolean hasPlayerPassenger = entity.getPassengers().stream()
                .anyMatch(p -> p instanceof Player);
            
            if (hasPlayerPassenger) {
                ci.cancel(); // ejectPassengers() çağrısını iptal et
                
                if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.01) {
                    com.lani.demoncore.DemonCore.LOGGER.warn(
                        "🛡️ EJECT BLOCKED: ejectPassengers() prevented at {} m/s",
                        String.format("%.0f", speed)
                    );
                }
            }
            return;
        }

        if (SableCompat.isEntityInSubLevel(entity) && speed >= 1000.0) {
            boolean hasPlayerPassenger = entity.getPassengers().stream()
                .anyMatch(p -> p instanceof Player);
            
            if (hasPlayerPassenger) {
                ci.cancel();
            }
        }
    }
}
