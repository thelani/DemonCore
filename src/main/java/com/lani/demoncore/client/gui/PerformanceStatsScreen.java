package com.lani.demoncore.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PerformanceStatsScreen extends Screen {
    private final Screen parent;

    public PerformanceStatsScreen(Screen parent) {
        super(Component.literal("Performance Stats"));
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

        
        graphics.drawCenteredString(this.font, "Performance Statistics", this.width / 2, 30, 0xFF5555);
        
        int y = 60;
        int lineHeight = 14;
        
        
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        long maxMB = maxMemory / (1024 * 1024);
        long usedMB = usedMemory / (1024 * 1024);
        long totalMB = totalMemory / (1024 * 1024);
        
        int usagePercent = (int)((usedMemory * 100) / maxMemory);
        
        graphics.drawString(this.font, "Memory Status:", 20, y, 0xFFAA00, false);
        y += lineHeight;
        graphics.drawString(this.font, "  Used: " + usedMB + " MB / " + maxMB + " MB (" + usagePercent + "%)", 20, y, 0xFFFFFF, false);
        y += lineHeight;
        graphics.drawString(this.font, "  Allocated: " + totalMB + " MB", 20, y, 0xFFFFFF, false);
        y += lineHeight * 2;
        
        
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            int fps = mc.getFps();
            graphics.drawString(this.font, "Performance:", 20, y, 0xFFAA00, false);
            y += lineHeight;
            graphics.drawString(this.font, "  FPS: " + fps, 20, y, 0xFFFFFF, false);
            y += lineHeight * 2;
        }
        
        
        if (mc != null && mc.options != null) {
            int renderDistance = mc.options.renderDistance().get();
            graphics.drawString(this.font, "Render Settings:", 20, y, 0xFFAA00, false);
            y += lineHeight;
            graphics.drawString(this.font, "  Render Distance: " + renderDistance + " chunks", 20, y, 0xFFFFFF, false);
            y += lineHeight * 2;
        }
        
        
        graphics.drawString(this.font, "Tips:", 20, y, 0x55FF55, false);
        y += lineHeight;
        graphics.drawString(this.font, "• Low RAM usage? Enable 'aggressiveRAM' in config", 20, y, 0xAAAAAA, false);
        y += lineHeight;
        graphics.drawString(this.font, "• Low FPS? Enable more optimizations", 20, y, 0xAAAAAA, false);
        y += lineHeight;
        graphics.drawString(this.font, "• High speed travel? Increase 'maxChunks'", 20, y, 0xAAAAAA, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
