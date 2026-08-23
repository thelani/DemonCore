package com.lani.demoncore.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.simibubi.create.content.contraptions.actors.seat.SeatEntity", remap = false)
public class ContraptionSeatMixin {
    
    @Inject(
        method = "ejectPassengers",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void preventNullEject(CallbackInfo ci) {

        try {

        } catch (Exception e) {
            ci.cancel();
        }
    }
}
