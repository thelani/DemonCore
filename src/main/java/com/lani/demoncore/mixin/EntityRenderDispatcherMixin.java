package com.lani.demoncore.mixin;

import com.lani.demoncore.optimization.EntityLODSystem;
import com.lani.demoncore.optimization.GeometryCache;
import com.lani.demoncore.optimization.VisibilityLattice;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends Entity> void demoncore$cullEntity(
            E entity,
            double x, double y, double z,
            float rotationYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci) {

        // Visibility lattice check
        if (!VisibilityLattice.entityMaybeVisible(entity)) {
            ci.cancel();
            return;
        }

        // EntityCulling mod handles frustum culling, we only do LOD
        EntityLODSystem.LOD lod = EntityLODSystem.computeLOD(entity);
        if (lod == EntityLODSystem.LOD.DOT) {
            ci.cancel();
            return;
        }

        // Cache entity pose for geometry reuse
        if (!GeometryCache.isEntityPoseFresh(entity, rotationYaw, partialTicks)) {
            GeometryCache.putEntity(entity, rotationYaw, partialTicks);
        }
    }

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void demoncore$cullShadow(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Entity entity,
            float weight,
            float partialTicks,
            LevelReader level,
            float size,
            CallbackInfo ci) {

        // Disable shadows for entities using simplified LOD
        EntityLODSystem.LOD lod = EntityLODSystem.computeLOD(entity);
        if (lod.level >= EntityLODSystem.LOD.SIMPLE.level) {
            ci.cancel();
        }
    }
}
