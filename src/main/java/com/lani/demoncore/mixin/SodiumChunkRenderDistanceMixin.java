package com.lani.demoncore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Sodium render distance mixin - increases chunk limits in render pipeline
 * WARNING: This mixin is fragile and may break with Sodium updates
 * 
 * Targets specific constants in specific classes:
 * - GUI options: max slider values (32 -> 128)
 * - Render manager: buffer sizes need careful adjustment
 * 
 * Note: We use require=0 to gracefully handle Sodium updates/absence
 */
@Pseudo
@Mixin(targets = {
    "net.caffeinemc.mods.sodium.client.gui.options.OptionImpl",
    "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"
}, remap = false)
public class SodiumChunkRenderDistanceMixin {
    
    // GUI max chunk distance (32 -> 128)
    @ModifyConstant(
        method = {
            "lambda$*",  // Lambda methods in option creation
            "<init>",    // Constructor
            "<clinit>"   // Static initializer
        },
        constant = @Constant(intValue = 32),
        require = 0
    )
    private int increaseGuiMax32To128(int original) {
        return 128;
    }
    
    @ModifyConstant(
        method = {
            "lambda$*",
            "<init>",
            "<clinit>"
        },
        constant = @Constant(intValue = 32),
        require = 0
    )
    private static int increaseGuiMax32To128Static(int original) {
        return 128;
    }
    
    // Padding value (33 -> 129)
    @ModifyConstant(
        method = {
            "lambda$*",
            "<init>",
            "<clinit>"
        },
        constant = @Constant(intValue = 33),
        require = 0
    )
    private int increaseGuiPadding33To129(int original) {
        return 129;
    }
}
