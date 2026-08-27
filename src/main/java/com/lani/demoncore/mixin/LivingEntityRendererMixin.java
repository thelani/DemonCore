package com.lani.demoncore.mixin;

import com.lani.demoncore.optimization.EntityLODSystem;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(
            method = "setupRotations",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <T extends LivingEntity> void demoncore$lodgeAnimFreeze(
            T entity,
            CallbackInfo ci) {
        if (!EntityLODSystem.animationAllowed(entity)) {
            ci.cancel();
        }
    }
}
