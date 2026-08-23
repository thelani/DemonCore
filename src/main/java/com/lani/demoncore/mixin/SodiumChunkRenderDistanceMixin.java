package com.lani.demoncore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = {
    "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",
    "net.caffeinemc.mods.sodium.client.render.viewport.Viewport",
    "net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform",
    "net.caffeinemc.mods.sodium.client.gui.options.OptionImpl",
    "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"
}, remap = false)
public class SodiumChunkRenderDistanceMixin {
    
    @ModifyConstant(
        method = "*",
        constant = @Constant(intValue = 32),
        require = 0
    )
    private int increase32To128(int original) {
        return 128;
    }
    
    @ModifyConstant(
        method = "*",
        constant = @Constant(intValue = 32),
        require = 0
    )
    private static int increase32To128Static(int original) {
        return 128;
    }
    
    @ModifyConstant(
        method = "*",
        constant = @Constant(intValue = 33),
        require = 0
    )
    private int increase33To129(int original) {
        return 129;
    }
    
    @ModifyConstant(
        method = "*",
        constant = @Constant(intValue = 1024),
        require = 0
    )
    private int increase1024To16384(int original) {
        return 16384;
    }
    
    @ModifyConstant(
        method = "*",
        constant = @Constant(intValue = 64),
        require = 0
    )
    private int increase64To256(int original) {
        return 256;
    }
}
