package com.lani.demoncore.mixin;

import com.lani.demoncore.optimization.BatchRenderCoordinator;
import com.lani.demoncore.optimization.BottleneckDetector;
import com.lani.demoncore.optimization.EntityLODSystem;
import com.lani.demoncore.optimization.FrameProfiler;
import com.lani.demoncore.optimization.GeometryCache;
import com.lani.demoncore.optimization.PredictiveFrameScheduler;
import com.lani.demoncore.optimization.VisibilityLattice;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    private static long demoncore$frameSeq;

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void demoncore$onRenderLevelStart(CallbackInfo ci) {
        PredictiveFrameScheduler.onFrameStart();
        demoncore$frameSeq++;
        VisibilityLattice.rebuild(demoncore$frameSeq);
        FrameProfiler.onLevelRenderStart();
        GeometryCache.beginFrame();
        EntityLODSystem.beginFrame();
        BatchRenderCoordinator.beginFrame();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void demoncore$onRenderLevelEnd(CallbackInfo ci) {
        BatchRenderCoordinator.endFrame();
        FrameProfiler.onLevelRenderEnd();
        BottleneckDetector.update();
        PredictiveFrameScheduler.onFrameEnd();
    }
}
