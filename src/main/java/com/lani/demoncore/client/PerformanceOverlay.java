package com.lani.demoncore.client;

import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.optimization.BottleneckDetector;
import com.lani.demoncore.optimization.FrameProfiler;
import com.lani.demoncore.optimization.GCStutterGuard;
import com.lani.demoncore.optimization.GeometryCache;
import com.lani.demoncore.optimization.GpuRamBalancer;
import com.lani.demoncore.optimization.PerformanceMonitor;
import com.lani.demoncore.optimization.SilentChunkTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class PerformanceOverlay {

    private PerformanceOverlay() {
    }

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BG_COLOR = 0x99000000;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int HEADER_COLOR = 0xFF5555;
    private static final int FEATURE_COLOR = 0xFFAA55;

    public static void render(GuiGraphics graphics) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.SHOW_OVERLAY, false)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return;
        }
        if (mc.gui != null && mc.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }

        int x = PADDING;
        int y = PADDING;
        int line = 0;

        drawLine(graphics, x, y + line * LINE_HEIGHT, "DemonCore", HEADER_COLOR);
        line++;

        double fps = FrameProfiler.getFps();
        double frameMs = FrameProfiler.getFrameTimeMs();
        PerformanceMonitor.Level clientLevel = PerformanceMonitor.getClientLevel();
        drawLine(graphics, x, y + line * LINE_HEIGHT,
                String.format("FPS: %.1f (%.2f ms) %s", fps, frameMs, clientLevel),
                levelColor(clientLevel));
        line++;

        double mspt = PerformanceMonitor.getAverageMspt();
        double tps = PerformanceMonitor.getTps();
        PerformanceMonitor.Level serverLevel = PerformanceMonitor.getServerLevel();
        drawLine(graphics, x, y + line * LINE_HEIGHT,
                String.format("Server: %.2f ms/tick (%.1f TPS) %s", mspt, tps, serverLevel),
                levelColor(serverLevel));
        line++;

        double cpu = FrameProfiler.getCpuRenderMs();
        double gpu = FrameProfiler.getGpuWaitMs();
        BottleneckDetector.Bottleneck bn = BottleneckDetector.get();
        drawLine(graphics, x, y + line * LINE_HEIGHT,
                String.format("CPU %.2f | GPU %.2f | %s", cpu, gpu, bn.label()),
                TEXT_COLOR);
        line++;

        long usedMb = GCStutterGuard.getUsedHeapMb();
        long maxMb = GCStutterGuard.getMaxHeapMb();
        double heapPct = GCStutterGuard.getHeapUsage() * 100.0;
        int geoUsed = GeometryCache.getRetainedMbEstimate();
        int geoCap = DemonCoreConfig.getInt(DemonCoreConfig.GEOMETRY_CACHE_MB, 192);
        drawLine(graphics, x, y + line * LINE_HEIGHT,
                String.format("Heap: %d/%d MB (%.0f%%) | Geo %d/%d MB",
                        usedMb, maxMb, heapPct, geoUsed, geoCap),
                heapColor(heapPct));
        line++;

        double quality = FrameProfiler.getQuality() * 100.0;
        double balMult = GpuRamBalancer.getCacheMultiplier();
        int silent = SilentChunkTracker.silentCount();
        drawLine(graphics, x, y + line * LINE_HEIGHT,
                String.format("Q %.0f%% | Mult %.2f | GC %.1f%% | SilentC %d",
                        quality, balMult, GCStutterGuard.getGcTimeShare() * 100.0, silent),
                FEATURE_COLOR);
    }

    private static void drawLine(GuiGraphics graphics, int x, int y, String text, int color) {
        graphics.fill(x - 1, y - 1, x + 8 + graphics.guiWidth(), y + LINE_HEIGHT - 1, BG_COLOR);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.font != null) {
            graphics.drawString(mc.font, text, x, y, color, false);
        }
    }

    private static int levelColor(PerformanceMonitor.Level level) {
        return switch (level) {
            case EXCELLENT -> 0x55FF55;
            case GOOD -> 0xFFFF55;
            case FAIR -> 0xFFAA00;
            case POOR -> 0xFF5555;
            case CRITICAL -> 0xFF0000;
        };
    }

    private static int heapColor(double pct) {
        if (pct < 60.0) {
            return 0x55FF55;
        } else if (pct < 80.0) {
            return 0xFFFF55;
        } else if (pct < 90.0) {
            return 0xFFAA00;
        } else {
            return 0xFF5555;
        }
    }
}
