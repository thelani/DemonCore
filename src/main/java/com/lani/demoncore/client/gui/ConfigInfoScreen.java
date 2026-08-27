package com.lani.demoncore.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigInfoScreen extends Screen {
    private final Screen parent;

    public ConfigInfoScreen(Screen parent) {
        super(Component.literal("Config Info"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        
        this.addRenderableWidget(Button.builder(
            Component.literal("Back"),
            button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(parent);
                }
            })
            .bounds(this.width / 2 - 100, this.height - 40, 200, 20)
            .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        
        graphics.drawCenteredString(this.font, "DemonCore Configuration", this.width / 2, 30, 0xFF5555);
        
        int y = 60;
        int lineHeight = 12;
        
        graphics.drawString(this.font, "All settings are in:", 20, y, 0xFFFFFF, false);
        y += lineHeight;
        graphics.drawString(this.font, "config/demoncore-common.toml", 20, y, 0x55FF55, false);
        y += lineHeight * 2;
        
        graphics.drawString(this.font, "Key Features:", 20, y, 0xFFAA00, false);
        y += lineHeight;
        graphics.drawString(this.font, "• High-speed chunk preloading", 20, y, 0xAAAAAA, false);
        y += lineHeight;
        graphics.drawString(this.font, "• Smart RAM/CPU/GPU balancing", 20, y, 0xAAAAAA, false);
        y += lineHeight;
        graphics.drawString(this.font, "• LOD system for distant entities", 20, y, 0xAAAAAA, false);
        y += lineHeight;
        graphics.drawString(this.font, "• Particle & foliage optimization", 20, y, 0xAAAAAA, false);
        y += lineHeight;
        graphics.drawString(this.font, "• Tick throttling for distant mobs", 20, y, 0xAAAAAA, false);
        y += lineHeight;
        graphics.drawString(this.font, "• VULCAN MODE (experimental)", 20, y, 0xAAAAAA, false);
        y += lineHeight * 2;
        
        graphics.drawString(this.font, "After editing config, restart the game!", 20, y, 0xFF5555, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
