package com.lani.demoncore.mixin;

import com.lani.demoncore.sable.SableTrackingManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 2000, remap = false)
public abstract class SableSpeedCheckBypassMixin {
    
    @Shadow
    public abstract ServerPlayer getPlayer();
    
    @WrapOperation(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isChangingDimension()Z"
        )
    )
    private boolean demoncore$bypassSpeedCheckDuringGracePeriod(
        ServerPlayer player, 
        Operation<Boolean> original
    ) {

        if (SableTrackingManager.withinGracePeriod(player)) {

            return true;
        }

        return original.call(player);
    }
}
