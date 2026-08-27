package com.lani.demoncore.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

import java.io.File;
import java.nio.file.Path;

public class ConfigFileEditScreen extends Screen {
    private final Screen parent;
    private int leftPos;
    private int topPos;
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 180;

    public ConfigFileEditScreen(Screen parent) {
        super(Component.literal("Config File Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;

        int buttonWidth = 300;
        int buttonHeight = 20;
        int buttonX = this.leftPos + 30;
        int startY = this.topPos + 60;

        
        addRenderableWidget(Button.builder(
            Component.literal("📁 Open Config Folder"),
            button -> {
                if (this.minecraft != null) {
                    Path configPath = new File("config").toPath();
                    Util.getPlatform().openFile(configPath.toFile());
                }
            })
            .bounds(buttonX, startY, buttonWidth, buttonHeight)
            .build()
        );

        
        addRenderableWidget(Button.builder(
            Component.literal("📝 Open demoncore-common.toml"),
            button -> {
                if (this.minecraft != null) {
                    File configFile = new File("config/demoncore-common.toml");
                    if (configFile.exists()) {
                        Util.getPlatform().openFile(configFile);
                    }
                }
            })
            .bounds(buttonX, startY + 30, buttonWidth, buttonHeight)
            .build()
        );

        
        addRenderableWidget(Button.builder(
            Component.literal("Back"),
            button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(parent);
                }
            })
            .bounds(this.width / 2 - 50, this.topPos + PANEL_HEIGHT - 35, 100, 20)
            .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xCC000000);
        
        
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + 1, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos + PANEL_HEIGHT - 1, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + PANEL_HEIGHT, 0xFFFFFFFF);
        graphics.fill(leftPos + PANEL_WIDTH - 1, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xFFFFFFFF);

        
        String title = "Config File Editor";
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, leftPos + (PANEL_WIDTH - titleWidth) / 2, topPos + 20, 0xFFFFFF, true);

        
        String line1 = "Config file location:";
        String line2 = "config/demoncore-common.toml";
        String line3 = "Restart game after editing!";
        
        graphics.drawString(this.font, line1, leftPos + 30, topPos + 110, 0xAAAAAA, false);
        graphics.drawString(this.font, line2, leftPos + 30, topPos + 125, 0x55FF55, false);
        graphics.drawString(this.font, line3, leftPos + 30, topPos + 140, 0xFFAA00, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
