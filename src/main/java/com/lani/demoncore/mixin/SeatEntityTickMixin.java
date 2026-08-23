package com.lani.demoncore.mixin;

import com.lani.demoncore.compat.SableCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.seat.SeatEntity", remap = false)
public abstract class SeatEntityTickMixin {

    @Inject(
        method = "tick()V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void demoncore$preventHighSpeedDiscard(CallbackInfo ci) {
        Entity seatEntity = (Entity) (Object) this;

        if (seatEntity.level().isClientSide) return;

        if (!seatEntity.isVehicle()) return;

        double speed = SableCompat.getEntityVelocityMagnitude(seatEntity);

        if (speed == 0 && !SableCompat.isEntityInSubLevel(seatEntity)) {
            speed = seatEntity.getDeltaMovement().length() * 20.0;
        }

        if (speed >= 500.0) {

            ci.cancel();

            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.01) {
                com.lani.demoncore.DemonCore.LOGGER.info(
                    "🔒 SEAT PROTECTED: SeatEntity tick() bypassed at {:.0f} m/s - preventing discard()",
                    speed
                );
            }
            return;
        }

        Block blockBelow = seatEntity.level().getBlockState(seatEntity.blockPosition()).getBlock();

        String blockClassName = blockBelow.getClass().getSimpleName();
        boolean isSeatBlock = blockClassName.equals("SeatBlock");
        
        if (!isSeatBlock && seatEntity.isVehicle()) {

            ci.cancel();
            
            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.001) {
                com.lani.demoncore.DemonCore.LOGGER.debug(
                    "🔒 SEAT WAITING: SeatBlock not loaded yet at {:.0f} m/s - delaying discard()",
                    speed
                );
            }
        }

    }
}
