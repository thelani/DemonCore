package com.lani.demoncore.mixin;

import com.lani.demoncore.optimization.BlockEntityCuller;
import com.lani.demoncore.optimization.GeometryCache;
import com.lani.demoncore.optimization.VisibilityLattice;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends BlockEntity> void demoncore$cullBlockEntity(
            E blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci) {

        
        if (!VisibilityLattice.blockEntityMaybeVisible(blockEntity)) {
            ci.cancel();
            return;
        }

        if (BlockEntityCuller.shouldCull(blockEntity)) {
            ci.cancel();
            return;
        }

        
        
        
        if (GeometryCache.isBlockEntityFresh(blockEntity, partialTick)) {
            
        } else {
            GeometryCache.putBlockEntity(blockEntity, 0f, partialTick);
        }
    }
}
