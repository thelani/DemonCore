package com.lani.demoncore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

@Pseudo
@Mixin(targets = {
    "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"
}, remap = false)
public class SodiumChunkDistanceMixin {
    
    @ModifyConstant(
        method = "*",
        constant = @Constant(intValue = 32),
        require = 0
    )
    private static int modifyChunkLimit(int value) {
        return 128;
    }
    
    @ModifyConstant(
        method = "*",
        constant = @Constant(intValue = 33),
        require = 0
    )
    private static int modifyChunkLimitPadding(int value) {
        return 129;
    }
}
