package com.lani.demoncore.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class PreventEjectionMixin {
    
    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void preventHighSpeedEjection(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (!(entity instanceof Player)) {
            return;
        }

        if (entity.isPassenger()) {
            Entity vehicle = entity.getVehicle();
            
            if (vehicle != null) {
                double speed = vehicle.getDeltaMovement().length() * 20.0; // m/s

                if (speed > 10.0) {

                    ci.cancel();

                    if (Math.random() < 0.01) { // %1 chance
                        org.slf4j.LoggerFactory.getLogger("DemonCore").debug(
                            "☢ PREVENTED high-speed ejection at {}m/s", speed);
                    }
                }
            }
        }
    }
}
