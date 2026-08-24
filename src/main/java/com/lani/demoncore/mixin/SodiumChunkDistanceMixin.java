package com.lani.demoncore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

/**
 * Sodium GUI mixin - increases max chunk distance slider limit
 * Targeted to specific methods to avoid breaking unrelated constants
 */
@Pseudo
@Mixin(targets = {
    "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"
}, remap = false)
public class SodiumChunkDistanceMixin {
    
    // Target only the render distance option creation/validation methods
    @ModifyConstant(
        method = {
            "lambda$static$*",  // Lambda methods that create options
            "<clinit>"          // Static initializer where options are defined
        },
        constant = @Constant(intValue = 32),
        require = 0
    )
    private static int modifyChunkLimit(int value) {
        return 128; // 4x increase: 32 -> 128 chunks
    }
    
    @ModifyConstant(
        method = {
            "lambda$static$*",
            "<clinit>"
        },
        constant = @Constant(intValue = 33),
        require = 0
    )
    private static int modifyChunkLimitPadding(int value) {
        return 129; // Maintain padding: 33 -> 129
    }
}
