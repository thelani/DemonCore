package com.lani.demoncore.mixin;

import com.lani.demoncore.optimization.FrameProfiler;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {

    @Inject(method = "updateDisplay", at = @At("HEAD"), require = 0)
    private void demoncore$onSwapStart(CallbackInfo ci) {
        FrameProfiler.onSwapStart();
    }

    @Inject(method = "updateDisplay", at = @At("RETURN"), require = 0)
    private void demoncore$onSwapEnd(CallbackInfo ci) {
        FrameProfiler.onSwapEnd();
    }
}
