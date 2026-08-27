package com.lani.demoncore.mixin;

import com.lani.demoncore.optimization.ParticleBudget;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void demoncore$budgetParticle(
            ParticleOptions particleData,
            double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed,
            CallbackInfoReturnable<?> cir) {

        if (!ParticleBudget.allow(x, y, z)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void demoncore$onTick(CallbackInfo ci) {
        ParticleBudget.onEngineTick();
    }
}
