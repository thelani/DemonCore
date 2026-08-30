package com.lani.demoncore.client.gui;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

public class DemonCoreConfigScreen extends Screen {
    private final Screen parent;
    private int activeTab = 0;
    private int scrollY = 0;
    private int maxScroll = 0;

    private static final String[] TABS = {
            "GENERAL", "CHUNK", "TICK",
            "RENDER", "CACHE", "ENTITY LOD",
            "GPU/RAM", "UNIQUE FEATURES"
    };

    private static final int TAB_COUNT = TABS.length;
    private static final int TAB_BAR_H = 32;
    private static final int SCROLLBAR_W = 6;
    private static final int ENTRY_H = 28;
    private static final int CONTROL_W = 160;

    private static final int COLOR_RED = 0xFFFF4B4B;
    private static final int COLOR_RED_DARK = 0xFF7A1A1A;
    private static final int COLOR_ORANGE = 0xFFFFB23D;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFF1F1F5;
    private static final int COLOR_TEXT_DIM = 0xFFB5B5C0;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_PANEL_BG = 0xFF15151F;
    private static final int COLOR_PANEL_BORDER = 0xFF8B1F25;
    private static final int COLOR_ROW_ALT = 0x14FFFFFF;
    private static final int COLOR_SCREEN_BG = 0xFF09090F;
    private static final int COLOR_TAB_INACTIVE_BG = 0xFF262633;
    private static final int COLOR_TAB_INACTIVE_TEXT = 0xFFD0D0DA;

    private final List<Entry> entries = new ArrayList<>();
    private int contentStartY;
    private int contentHeight;
    private int panelTop;
    private int panelBottom;
    private int panelLeft;
    private int panelRight;
    private int tabStartX;
    private int tabW;

    public DemonCoreConfigScreen(Screen parent) {
        super(Component.literal("DemonCore Configuration"));
        this.parent = parent;
    }

    public static Screen createScreen(Screen parent) {
        return new DemonCoreConfigScreen(parent);
    }

    private record Entry(Component label, int y, GuiEventListener control, String tooltip,
                         boolean isSection, int rowIndex, boolean hasValueWidget, double pctHint,
                         double pctHintMin, double pctHintMax) {
    }

    private int addBool(int x, int y, String nameKey, String tip,
                        ModConfigSpec.BooleanValue value, boolean def, int rowIdx) {
        Component label = Component.translatable(nameKey);
        boolean current = safeGet(value, def);
        CycleButton<Boolean> btn = CycleButton.onOffBuilder(current)
                .create(x, y + 2, CONTROL_W, 22, Component.empty(), (b, v) -> {
                    value.set(v);
                    value.save();
                });
        if (tip != null && !tip.isEmpty()) {
            btn.setTooltip(Tooltip.create(Component.literal(tip)));
        }
        addRenderableWidget(btn);
        entries.add(new Entry(label, y, btn, tip, false, rowIdx, true,
                current ? 1.0 : 0.0, 0.0, 1.0));
        return y + ENTRY_H;
    }

    private int addInt(int x, int y, String nameKey, String tip,
                       ModConfigSpec.IntValue value, int def, int min, int max, int rowIdx) {
        Component label = Component.translatable(nameKey);
        int current = safeGet(value, def);
        int sliderX = x;
        int sliderW = CONTROL_W;
        double pct01 = (double) (current - min) / (double) (max - min);
        PercentageSlider slider = new PercentageSlider(
                sliderX, y + 2, sliderW, 22,
                Math.max(0, Math.min(1, pct01)),
                newVal -> {
                    int intVal = min + (int) Math.round(newVal * (max - min));
                    intVal = Math.max(min, Math.min(max, intVal));
                    value.set(intVal);
                    value.save();
                },
                pct -> {
                    int v = min + (int) Math.round(pct * (max - min));
                    return Component.literal(String.valueOf(v));
                },
                () -> (double) (safeGet(value, def) - min) / (double) (max - min)
        );
        if (tip != null && !tip.isEmpty()) {
            slider.setTooltip(Tooltip.create(Component.literal(tip + " [" + min + " - " + max + "]")));
        }
        addRenderableWidget(slider);
        entries.add(new Entry(label, y, slider, tip, false, rowIdx, true, pct01, 0.0, 1.0));
        return y + ENTRY_H;
    }

    private int addDouble(int x, int y, String nameKey, String tip,
                          ModConfigSpec.DoubleValue value, double def,
                          double min, double max, String format, int rowIdx) {
        Component label = Component.translatable(nameKey);
        double current = safeGet(value, def);
        double pct01 = (current - min) / (max - min);
        PercentageSlider slider = new PercentageSlider(
                x, y + 2, CONTROL_W, 22,
                Math.max(0, Math.min(1, pct01)),
                newVal -> {
                    double v = min + newVal * (max - min);
                    value.set(v);
                    value.save();
                },
                pct -> {
                    double v = min + pct * (max - min);
                    return Component.literal(String.format(format, v));
                },
                () -> (safeGet(value, def) - min) / (max - min)
        );
        if (tip != null && !tip.isEmpty()) {
            slider.setTooltip(Tooltip.create(Component.literal(
                    tip + " [" + String.format(format, min) + " - " + String.format(format, max) + "]")));
        }
        addRenderableWidget(slider);
        entries.add(new Entry(label, y, slider, tip, false, rowIdx, true, pct01, 0.0, 1.0));
        return y + ENTRY_H;
    }

    private static boolean safeGet(ModConfigSpec.BooleanValue v, boolean def) {
        try { return v.get(); } catch (Exception e) { return def; }
    }
    private static int safeGet(ModConfigSpec.IntValue v, int def) {
        try { return v.get(); } catch (Exception e) { return def; }
    }
    private static double safeGet(ModConfigSpec.DoubleValue v, double def) {
        try { return v.get(); } catch (Exception e) { return def; }
    }

    @Override
    protected void init() {
        super.init();
        panelLeft = 18;
        panelRight = this.width - 18;
        panelTop = 5 + TAB_BAR_H + 42;
        panelBottom = this.height - 42;
        contentStartY = panelTop + 14;
        contentHeight = panelBottom - contentStartY - 10;

        tabW = Math.min((this.width - 56) / TAB_COUNT, 150);
        int tabTotalW = tabW * TAB_COUNT + (TAB_COUNT - 1) * 3;
        tabStartX = (this.width - tabTotalW) / 2;

        rebuildControls();

        addRenderableWidget(Button.builder(
                Component.literal("Done").withStyle(s -> s.withColor(COLOR_WHITE)),
                button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 32, 200, 22)
                .build()
        );

        addRenderableWidget(Button.builder(
                Component.literal("Reset All").withStyle(s -> s.withColor(COLOR_GRAY)),
                button -> resetAllToDefaults())
                .bounds(28, this.height - 32, 140, 22)
                .build()
        );

        for (int i = 0; i < TAB_COUNT; i++) {
            final int tab = i;
            Button tabBtn = Button.builder(Component.literal(TABS[i]), b -> {
                        activeTab = tab;
                        scrollY = 0;
                        clearDynamicControls();
                        rebuildControls();
                    })
                    .bounds(tabStartX + i * (tabW + 3), 8, tabW, TAB_BAR_H - 4)
                    .build();
            addRenderableWidget(tabBtn);
        }
    }

    private void clearDynamicControls() {
        List<GuiEventListener> toRemove = new ArrayList<>();
        for (Entry e : entries) {
            if (e.control != null && children().contains(e.control)) toRemove.add(e.control);
        }
        for (GuiEventListener l : toRemove) removeWidget(l);
        entries.clear();
    }

    private void rebuildControls() {
        clearDynamicControls();

        int xRight = panelRight - CONTROL_W - 24;
        int y = contentStartY - scrollY;

        String T = "demoncore.config.";

        switch (activeTab) {
            case 0 -> y = buildGeneral(xRight, y, T);
            case 1 -> y = buildChunk(xRight, y, T);
            case 2 -> y = buildTick(xRight, y, T);
            case 3 -> y = buildRender(xRight, y, T);
            case 4 -> y = buildCache(xRight, y, T);
            case 5 -> y = buildLOD(xRight, y, T);
            case 6 -> y = buildGPUBalancer(xRight, y, T);
            case 7 -> y = buildUniqueFeatures(xRight, y, T);
        }

        int totalContent = (y + scrollY) - contentStartY;
        maxScroll = Math.max(0, totalContent - contentHeight);
        if (scrollY > maxScroll) {
            int delta = scrollY - maxScroll;
            scrollY = maxScroll;
            for (Entry e : entries) {
                if (e.control instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                    w.setY(w.getY() - delta);
                }
            }
        }
    }

    private int buildGeneral(int x, int y, String T) {
        int row = 0;
        y = sectionTitle(y, "General / Master Switches", row++);
        y = addBool(x, y, T + "enabled", "Master switch for all DemonCore systems.",
                DemonCoreConfig.ENABLED, true, row++);
        y = addBool(x, y, T + "debugLogging",
                "Write verbose DemonCore diagnostics to latest.log.",
                DemonCoreConfig.DEBUG_LOGGING, false, row++);
        y = addBool(x, y, T + "enableDebug",
                "Enable debug mode for DemonCore commands and output.",
                DemonCoreConfig.ENABLE_DEBUG, false, row++);
        y = addBool(x, y, T + "enableOptimization",
                "Enable DemonCore optimization subsystems.",
                DemonCoreConfig.ENABLE_OPTIMIZATION, true, row++);
        y = addBool(x, y, T + "enableCache",
                "Enable DemonCore caching systems.",
                DemonCoreConfig.ENABLE_CACHE, true, row++);
        y = addDouble(x, y, T + "speedThreshold",
                "Speed threshold in blocks/second for activation.",
                DemonCoreConfig.SPEED_THRESHOLD, 24.0, 0.0, 2000.0, "%.0f", row++);
        y = addDouble(x, y, T + "resourceBalance",
                "Resource balance factor (1.0 = max performance).",
                DemonCoreConfig.RESOURCE_BALANCE, 0.7, 0.0, 1.0, "%.2f", row++);
        y = sectionTitle(y, "VULCAN MODE (Experimental)", row++);
        y = addBool(x, y, T + "vulcan.enabled",
                "VULCAN MODE - disables internal safety limits. Use at your own risk.",
                DemonCoreConfig.VULCAN_MODE, false, row++);
        return y;
    }

    private int buildChunk(int x, int y, String T) {
        int row = 0;
        y = sectionTitle(y, "Predictive Chunk Loading", row++);
        y = addBool(x, y, T + "chunkLoading.enabled",
                "Enable predictive chunk pre-loading for fast-moving vehicles.",
                DemonCoreConfig.CHUNK_LOADING_ENABLED, true, row++);
        y = addDouble(x, y, T + "chunkLoading.activationSpeed",
                "Minimum vehicle speed (blocks/s) before pre-loading kicks in.",
                DemonCoreConfig.ACTIVATION_SPEED, 24.0, 0.0, 2000.0, "%.0f", row++);
        y = addInt(x, y, T + "chunkLoading.maxChunks",
                "Max chunks tracked ahead of a single vehicle.",
                DemonCoreConfig.MAX_CHUNKS, 96, 8, 512, row++);
        y = addInt(x, y, T + "chunkLoading.chunksPerTick",
                "Baseline tickets per server tick.",
                DemonCoreConfig.CHUNKS_PER_TICK, 16, 1, 128, row++);
        y = addInt(x, y, T + "chunkLoading.ticketRadius",
                "0 = single chunk per ticket (recommended). 1 = 3x3 area.",
                DemonCoreConfig.TICKET_RADIUS, 0, 0, 2, row++);
        y = addInt(x, y, T + "chunkLoading.ticketLifetime",
                "How long a chunk ticket stays alive (ticks).",
                DemonCoreConfig.TICKET_LIFETIME_TICKS, 200, 40, 2400, row++);
        y = addInt(x, y, T + "chunkLoading.maxQueued",
                "Hard cap on the pending ticket queue.",
                DemonCoreConfig.MAX_QUEUED_TICKETS, 4096, 256, 65536, row++);
        y = addBool(x, y, T + "chunkLoading.adaptiveBackpressure",
                "Drive the chunk loading rate from measured server tick time.",
                DemonCoreConfig.ADAPTIVE_BACKPRESSURE, true, row++);
        y = addDouble(x, y, T + "chunkLoading.targetMspt",
                "Server tick budget (ms) adaptive backpressure aims to stay under.",
                DemonCoreConfig.TARGET_MSPT, 38.0, 10.0, 50.0, "%.1f", row++);
        y = sectionTitle(y, "Memory & Caching", row++);
        y = addInt(x, y, T + "memory.chunkCacheSize",
                "Max chunk positions kept in predictive cache.",
                DemonCoreConfig.CHUNK_CACHE_SIZE, 8192, 256, 131072, row++);
        y = addBool(x, y, T + "memory.autoTrim",
                "Automatically shrink caches when heap is tight.",
                DemonCoreConfig.AUTO_TRIM, true, row++);
        return y;
    }

    private int buildTick(int x, int y, String T) {
        int row = 0;
        y = sectionTitle(y, "Entity Tick Throttling (Server)", row++);
        y = addBool(x, y, T + "tickOptimization.enabled",
                "Enable distance-based entity tick throttling.",
                DemonCoreConfig.TICK_THROTTLE_ENABLED, true, row++);
        y = addInt(x, y, T + "tickOptimization.distance",
                "Entities closer than this always tick normally.",
                DemonCoreConfig.TICK_THROTTLE_DISTANCE, 64, 16, 256, row++);
        y = addInt(x, y, T + "tickOptimization.maxSkipFactor",
                "Max slowdown factor (4 = tick every 4th tick for distant entities).",
                DemonCoreConfig.TICK_THROTTLE_MAX_FACTOR, 4, 1, 8, row++);
        y = addBool(x, y, T + "tickOptimization.items",
                "Also throttle dropped items and XP orbs (very effective).",
                DemonCoreConfig.TICK_THROTTLE_ITEMS, true, row++);
        return y;
    }

    private int buildRender(int x, int y, String T) {
        int row = 0;
        y = sectionTitle(y, "Client Rendering Optimizations", row++);
        y = addBool(x, y, T + "rendering.enabled",
                "Master switch for client rendering optimizations.",
                DemonCoreConfig.RENDER_OPTIMIZATION, true, row++);
        y = addInt(x, y, T + "rendering.entityShadowDistance",
                "Max distance (blocks) for entity drop shadows (0 = disable).",
                DemonCoreConfig.ENTITY_SHADOW_DISTANCE, 24, 0, 64, row++);
        y = sectionTitle(y, "Particles", row++);
        y = addBool(x, y, T + "particles.enabled",
                "Enable the particle budget.",
                DemonCoreConfig.PARTICLE_LIMIT_ENABLED, true, row++);
        y = addInt(x, y, T + "particles.maxParticles",
                "Max live particles (vanilla has NO upper bound!).",
                DemonCoreConfig.MAX_PARTICLES, 4000, 250, 16000, row++);
        y = addInt(x, y, T + "particles.cullDistance",
                "Particles spawned further away than this are skipped.",
                DemonCoreConfig.PARTICLE_CULL_DISTANCE, 48, 8, 256, row++);
        y = sectionTitle(y, "Adaptive Quality", row++);
        y = addBool(x, y, T + "adaptive.enabled",
                "Automatically tighten budgets when frame time exceeds target.",
                DemonCoreConfig.ADAPTIVE_QUALITY, true, row++);
        y = addInt(x, y, T + "adaptive.targetFps",
                "Frame rate DemonCore aims to sustain (0 = follow video settings).",
                DemonCoreConfig.TARGET_FPS, 0, 0, 1000, row++);
        y = addBool(x, y, T + "adaptive.spikeProtection",
                "Briefly reduce optional work to smooth out slow frames.",
                DemonCoreConfig.SPIKE_PROTECTION, true, row++);
        y = addDouble(x, y, T + "adaptive.minQuality",
                "Lowest quality multiplier adaptive mode may drop to (1.0 = no reduction).",
                DemonCoreConfig.MIN_QUALITY, 0.35, 0.10, 1.0, "%.2f", row++);
        y = sectionTitle(y, "Render Batch Coalescing", row++);
        y = addBool(x, y, T + "batchRender.enabled",
                "Enable batched draw-call coalescing.",
                DemonCoreConfig.BATCH_RENDER_ENABLED, true, row++);
        y = addInt(x, y, T + "batchRender.bufferSize",
                "Max quads held in a single batch before flushing.",
                DemonCoreConfig.BATCH_BUFFER_SIZE, 2048, 256, 16384, row++);
        return y;
    }

    private int buildCache(int x, int y, String T) {
        int row = 0;
        y = sectionTitle(y, "Geometry & Visibility Caching", row++);
        y = addBool(x, y, T + "geometryCache.enabled",
                "Cache block entity geometry and entity pose data in heap RAM.",
                DemonCoreConfig.GEOMETRY_CACHE_ENABLED, true, row++);
        y = addInt(x, y, T + "geometryCache.sizeMb",
                "MB of heap RAM DemonCore may use for geometry caching.",
                DemonCoreConfig.GEOMETRY_CACHE_MB, 192, 32, 1024, row++);
        y = addBool(x, y, T + "geometryCache.visibilityLattice",
                "Coarse in-RAM visibility lattice.",
                DemonCoreConfig.VISIBILITY_LATTICE, true, row++);
        y = addInt(x, y, T + "geometryCache.cellSize",
                "Edge length of each lattice cell in blocks.",
                DemonCoreConfig.VISIBILITY_CELL_SIZE, 8, 4, 32, row++);
        return y;
    }

    private int buildLOD(int x, int y, String T) {
        int row = 0;
        y = sectionTitle(y, "Entity Level of Detail (LOD)", row++);
        y = addBool(x, y, T + "entityLOD.enabled",
                "Enable progressive entity LOD switching (fade out smoothly).",
                DemonCoreConfig.ENTITY_LOD_ENABLED, true, row++);
        y = addInt(x, y, T + "entityLOD.fullDistance",
                "Entities closer than this = full detail (model + animation).",
                DemonCoreConfig.LOD_FULL_DISTANCE, 24, 8, 128, row++);
        y = addInt(x, y, T + "entityLOD.simpleDistance",
                "Past this distance = static simplified model (no limb animation).",
                DemonCoreConfig.LOD_SIMPLE_DISTANCE, 48, 16, 192, row++);
        y = addInt(x, y, T + "entityLOD.billboardDistance",
                "Past this distance = axis-facing billboard sprite.",
                DemonCoreConfig.LOD_BILLBOARD_DISTANCE, 72, 24, 256, row++);
        return y;
    }

    private int buildGPUBalancer(int x, int y, String T) {
        int row = 0;
        y = sectionTitle(y, "GPU-RAM Dynamic Balancer (UNIQUE)", row++);
        y = addBool(x, y, T + "gpuRamBalancer.enabled",
                "Closed-loop controller: grows caches in RAM when GPU is hot, shrinks when not.",
                DemonCoreConfig.GPU_RAM_BALANCER, true, row++);
        y = addDouble(x, y, T + "gpuRamBalancer.targetGpu",
                "Target GPU utilisation to maintain.",
                DemonCoreConfig.GPU_TARGET_UTIL, 0.60, 0.20, 0.95, "%.2f", row++);
        y = addDouble(x, y, T + "gpuRamBalancer.maxRamFraction",
                "Hard cap: max fraction of heap DemonCore may consume for caches.",
                DemonCoreConfig.RAM_MAX_USAGE, 0.45, 0.10, 0.80, "%.2f", row++);
        return y;
    }

    private int buildUniqueFeatures(int x, int y, String T) {
        int row = 0;
        y = sectionTitle(y, "Predictive Frame Scheduler (UNIQUE)", row++);
        y = addBool(x, y, T + "predictiveScheduler.enabled",
                "Micro-sleep before slow frames to eliminate jitter.",
                DemonCoreConfig.PREDICTIVE_SCHEDULER, true, row++);
        y = addDouble(x, y, T + "predictiveScheduler.headroom",
                "Extra frame-budget margin kept in reserve.",
                DemonCoreConfig.PREDICTIVE_HEADROOM, 0.10, 0.02, 0.40, "%.2f", row++);
        y = sectionTitle(y, "Silent Chunk Tracker (UNIQUE)", row++);
        y = addBool(x, y, T + "silentChunkTracker.enabled",
                "Skip mesh re-validation for chunks that haven't changed in N ticks.",
                DemonCoreConfig.SILENT_CHUNK_TRACKER, true, row++);
        y = addInt(x, y, T + "silentChunkTracker.ticks",
                "Consecutive no-change ticks before marking a chunk 'silent'.",
                DemonCoreConfig.SILENT_CHUNK_TICKS, 20, 5, 200, row++);
        y = sectionTitle(y, "Diagnostics & Overlay", row++);
        y = addBool(x, y, T + "diagnostics.bottleneck",
                "Measure CPU vs GPU wait per frame to find the real bottleneck.",
                DemonCoreConfig.BOTTLENECK_DETECTION, true, row++);
        y = addBool(x, y, T + "diagnostics.overlay",
                "Draw the compact live performance overlay top-left.",
                DemonCoreConfig.SHOW_OVERLAY, false, row++);
        y = addInt(x, y, T + "diagnostics.logInterval",
                "Write performance summary every N seconds (0 = disable).",
                DemonCoreConfig.STATS_LOG_INTERVAL, 0, 0, 3600, row++);
        return y;
    }

    private int sectionTitle(int y, String text, int rowIdx) {
        entries.add(new Entry(Component.literal(text), y, null, null, true, rowIdx,
                false, 0, 0, 0));
        return y + ENTRY_H;
    }

    private void resetAllToDefaults() {
        trySet(DemonCoreConfig.ENABLED, true);
        trySet(DemonCoreConfig.DEBUG_LOGGING, false);
        trySet(DemonCoreConfig.ENABLE_DEBUG, false);
        trySet(DemonCoreConfig.ENABLE_OPTIMIZATION, true);
        trySet(DemonCoreConfig.ENABLE_CACHE, true);
        trySet(DemonCoreConfig.SPEED_THRESHOLD, 24.0);
        trySet(DemonCoreConfig.RESOURCE_BALANCE, 0.7);
        trySet(DemonCoreConfig.VULCAN_MODE, false);

        trySet(DemonCoreConfig.CHUNK_LOADING_ENABLED, true);
        trySet(DemonCoreConfig.ACTIVATION_SPEED, 24.0);
        trySet(DemonCoreConfig.MAX_CHUNKS, 96);
        trySet(DemonCoreConfig.CHUNKS_PER_TICK, 16);
        trySet(DemonCoreConfig.TICKET_RADIUS, 0);
        trySet(DemonCoreConfig.TICKET_LIFETIME_TICKS, 200);
        trySet(DemonCoreConfig.MAX_QUEUED_TICKETS, 4096);
        trySet(DemonCoreConfig.ADAPTIVE_BACKPRESSURE, true);
        trySet(DemonCoreConfig.TARGET_MSPT, 38.0);
        trySet(DemonCoreConfig.CHUNK_CACHE_SIZE, 8192);
        trySet(DemonCoreConfig.AUTO_TRIM, true);

        trySet(DemonCoreConfig.TICK_THROTTLE_ENABLED, true);
        trySet(DemonCoreConfig.TICK_THROTTLE_DISTANCE, 64);
        trySet(DemonCoreConfig.TICK_THROTTLE_MAX_FACTOR, 4);
        trySet(DemonCoreConfig.TICK_THROTTLE_ITEMS, true);

        trySet(DemonCoreConfig.RENDER_OPTIMIZATION, true);
        trySet(DemonCoreConfig.ENTITY_SHADOW_DISTANCE, 24);
        trySet(DemonCoreConfig.PARTICLE_LIMIT_ENABLED, true);
        trySet(DemonCoreConfig.MAX_PARTICLES, 4000);
        trySet(DemonCoreConfig.PARTICLE_CULL_DISTANCE, 48);
        trySet(DemonCoreConfig.ADAPTIVE_QUALITY, true);
        trySet(DemonCoreConfig.TARGET_FPS, 0);
        trySet(DemonCoreConfig.SPIKE_PROTECTION, true);
        trySet(DemonCoreConfig.MIN_QUALITY, 0.35);
        trySet(DemonCoreConfig.BATCH_RENDER_ENABLED, true);
        trySet(DemonCoreConfig.BATCH_BUFFER_SIZE, 2048);

        trySet(DemonCoreConfig.GEOMETRY_CACHE_ENABLED, true);
        trySet(DemonCoreConfig.GEOMETRY_CACHE_MB, 192);
        trySet(DemonCoreConfig.VISIBILITY_LATTICE, true);
        trySet(DemonCoreConfig.VISIBILITY_CELL_SIZE, 8);

        trySet(DemonCoreConfig.ENTITY_LOD_ENABLED, true);
        trySet(DemonCoreConfig.LOD_FULL_DISTANCE, 24);
        trySet(DemonCoreConfig.LOD_SIMPLE_DISTANCE, 48);
        trySet(DemonCoreConfig.LOD_BILLBOARD_DISTANCE, 72);

        trySet(DemonCoreConfig.GPU_RAM_BALANCER, true);
        trySet(DemonCoreConfig.GPU_TARGET_UTIL, 0.60);
        trySet(DemonCoreConfig.RAM_MAX_USAGE, 0.45);

        trySet(DemonCoreConfig.PREDICTIVE_SCHEDULER, true);
        trySet(DemonCoreConfig.PREDICTIVE_HEADROOM, 0.10);
        trySet(DemonCoreConfig.SILENT_CHUNK_TRACKER, true);
        trySet(DemonCoreConfig.SILENT_CHUNK_TICKS, 20);

        trySet(DemonCoreConfig.BOTTLENECK_DETECTION, true);
        trySet(DemonCoreConfig.SHOW_OVERLAY, false);
        trySet(DemonCoreConfig.STATS_LOG_INTERVAL, 0);

        clearDynamicControls();
        rebuildControls();
    }

    private static void trySet(ModConfigSpec.BooleanValue v, boolean d) { try { v.set(d); v.save(); } catch (Exception ignored) {} }
    private static void trySet(ModConfigSpec.IntValue v, int d) { try { v.set(d); v.save(); } catch (Exception ignored) {} }
    private static void trySet(ModConfigSpec.DoubleValue v, double d) { try { v.set(d); v.save(); } catch (Exception ignored) {} }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, COLOR_SCREEN_BG);

        drawTabBar(graphics);

        graphics.drawCenteredString(this.font, "DemonCore Configuration",
                this.width / 2, 5 + TAB_BAR_H + 14, COLOR_RED);
        graphics.drawCenteredString(this.font, "Tab: " + TABS[activeTab],
                this.width / 2, 5 + TAB_BAR_H + 26, COLOR_TEXT_DIM);

        drawPanel(graphics);

        int labelX = panelLeft + 24;
        for (Entry e : entries) {
            boolean inView = e.y >= panelTop - 6 && e.y <= panelBottom - 22;
            if (!inView) continue;
            if (e.isSection) {
                drawSectionHeader(graphics, e.label.getString(), e.y);
            } else if (e.control instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                if ((e.rowIndex & 1) == 1) {
                    graphics.fill(panelLeft + 14, w.getY() - 2, panelRight - 14, w.getY() + 24, COLOR_ROW_ALT);
                }
                drawValueHint(graphics, e, w.getX() - 6, w.getY() + 6, 4);
                graphics.drawString(this.font, e.label.getString(),
                        labelX, w.getY() + 7, COLOR_TEXT, false);
            }
        }

        if (maxScroll > 0) {
            int sbX = panelRight - SCROLLBAR_W - 8;
            int sbY = panelTop + 6;
            int sbH = panelBottom - panelTop - 12;
            float ratio = (float) scrollY / (float) maxScroll;
            int handleH = Math.max(30, (int) ((contentHeight / (float) (contentHeight + maxScroll)) * sbH));
            int handleY = sbY + (int) (ratio * (sbH - handleH));
            graphics.fill(sbX, sbY, sbX + SCROLLBAR_W, sbY + sbH, 0xFF2A2A35);
            graphics.fill(sbX - 1, handleY - 1, sbX + SCROLLBAR_W + 1, handleY + handleH + 1, 0xCCFF4B4B);
            graphics.fill(sbX, handleY, sbX + SCROLLBAR_W, handleY + handleH, COLOR_RED);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawValueHint(GuiGraphics g, Entry e, int rightX, int y, int height) {
        if (e.control instanceof CycleButton<?>) return;
        double pct = Math.max(0, Math.min(1, e.pctHint));
        int w = 160;
        int leftX = rightX - w;
        g.fill(leftX, y, rightX, y + height, 0xFF23232E);
        int fillW = (int) (pct * w);
        int color = pct < 0.33 ? 0xFFE9B23D : (pct < 0.75 ? 0xFF4BD37A : 0xFFE93232);
        if (fillW > 0) g.fill(leftX, y, leftX + fillW, y + height, color);
    }

    private void drawTabBar(GuiGraphics graphics) {
        int y = 8;
        int h = TAB_BAR_H - 4;

        for (int i = 0; i < TAB_COUNT; i++) {
            int x = tabStartX + i * (tabW + 3);
            boolean active = (i == activeTab);
            if (active) {
                graphics.fill(x - 1, y - 1, x + tabW + 1, y + h + 1, 0xFFC62828);
                graphics.fill(x, y, x + tabW, y + h, COLOR_RED_DARK);
                graphics.fill(x + 3, y + 3, x + tabW - 3, y + h - 3, 0xFFB31A1A);
                graphics.drawCenteredString(this.font, TABS[i],
                        x + tabW / 2, y + (h - 9) / 2, COLOR_WHITE);
                graphics.fill(x + 4, y + h - 2, x + tabW - 4, y + h, 0xFFFF8888);
            } else {
                graphics.fill(x - 1, y - 1, x + tabW + 1, y + h + 1, 0xFF0E0E14);
                graphics.fill(x, y, x + tabW, y + h, COLOR_TAB_INACTIVE_BG);
                graphics.fill(x + 1, y + h - 2, x + tabW - 1, y + h - 1, 0xFF32323F);
                graphics.drawCenteredString(this.font, TABS[i],
                        x + tabW / 2, y + (h - 9) / 2, COLOR_TAB_INACTIVE_TEXT);
            }
        }
    }

    private void drawPanel(GuiGraphics graphics) {
        graphics.fill(panelLeft - 3, panelTop - 3, panelRight + 3, panelBottom + 3, 0xFF050509);
        graphics.fill(panelLeft - 2, panelTop - 2, panelRight + 2, panelBottom + 2, 0xFF000000);
        graphics.fill(panelLeft - 1, panelTop - 1, panelRight + 1, panelBottom + 1, COLOR_PANEL_BORDER);
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, COLOR_PANEL_BG);
        graphics.fill(panelLeft + 2, panelTop + 2, panelLeft + 4, panelBottom - 2, 0xFFFF4B4B);
        graphics.fill(panelRight - 4, panelTop + 2, panelRight - 2, panelBottom - 2, 0x44FF4B4B);
        graphics.fill(panelLeft + 4, panelTop + 2, panelRight - 4, panelTop + 3, 0x24FFFFFF);
        graphics.fill(panelLeft + 4, panelBottom - 3, panelRight - 4, panelBottom - 2, 0x14FFFFFF);
    }

    private void drawSectionHeader(GuiGraphics graphics, String text, int y) {
        int lineY = y + 16;
        graphics.fill(panelLeft + 18, lineY + 6, panelRight - 18, lineY + 7, 0x28FFFFFF);
        graphics.fill(panelLeft + 18, lineY + 5, panelLeft + 80, lineY + 7, 0xFFFFB23D);
        graphics.drawString(this.font, text, panelLeft + 22, y + 4, COLOR_ORANGE, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double xDelta, double yDelta) {
        if (maxScroll > 0) {
            int delta = (int) Math.round(-yDelta * 36);
            int prev = scrollY;
            scrollY = Math.max(0, Math.min(maxScroll, scrollY + delta));
            if (scrollY != prev) {
                int shift = scrollY - prev;
                for (Entry e : entries) {
                    if (e.control instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                        w.setY(w.getY() + shift);
                    }
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, xDelta, yDelta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && maxScroll > 0) {
            int sbX = panelRight - SCROLLBAR_W - 8;
            int sbY = panelTop + 6;
            int sbH = panelBottom - panelTop - 12;
            if (mouseX >= sbX - 8 && mouseX <= sbX + SCROLLBAR_W + 8
                    && mouseY >= sbY - 8 && mouseY <= sbY + sbH + 8) {
                int handleH = Math.max(30, (int) ((contentHeight / (float) (contentHeight + maxScroll)) * sbH));
                float pos = (float) (mouseY - sbY - handleH / 2.0) / (float) (sbH - handleH);
                int newScroll = (int) Math.round(Math.max(0.0, Math.min(1.0, pos)) * maxScroll);
                int shift = newScroll - scrollY;
                scrollY = newScroll;
                if (shift != 0) {
                    for (Entry e : entries) {
                        if (e.control instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                            w.setY(w.getY() + shift);
                        }
                    }
                }
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private static class PercentageSlider extends AbstractSliderButton {
        private final DoubleConsumer onCommit;
        private final java.util.function.DoubleFunction<Component> labelFn;
        private final java.util.function.DoubleSupplier getCurrent;

        PercentageSlider(int x, int y, int w, int h, double initialPct,
                         DoubleConsumer onCommit,
                         java.util.function.DoubleFunction<Component> labelFn,
                         java.util.function.DoubleSupplier getCurrent) {
            super(x, y, w, h, labelFn.apply(initialPct), initialPct);
            this.onCommit = onCommit;
            this.labelFn = labelFn;
            this.getCurrent = getCurrent;
        }

        @Override
        protected void updateMessage() {
            setMessage(labelFn.apply(value));
        }

        @Override
        protected void applyValue() {
            onCommit.accept(value);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int x1 = this.getX();
            int y1 = this.getY();
            int x2 = x1 + this.getWidth();
            int y2 = y1 + this.getHeight();
            g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF0A0A10);
            g.fill(x1, y1, x2, y2, 0xFF1D1D27);
            int trackY1 = y1 + this.getHeight() / 2 - 2;
            int trackY2 = y1 + this.getHeight() / 2 + 2;
            g.fill(x1 + 4, trackY1, x2 - 4, trackY2, 0xFF121218);
            int handleX = x1 + (int) (value * (this.getWidth() - 10));
            g.fill(handleX, y1 + 3, handleX + 10, y2 - 3, 0xFFE93232);
            g.fill(handleX + 1, y1 + 5, handleX + 9, y2 - 5, 0xFFFF6868);
            g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                    this.getMessage().getString(), x1 + this.getWidth() / 2,
                    y1 + (this.getHeight() - 9) / 2, 0xFFF3F3F5);
        }

        public void syncFromSource() {
            this.value = Math.max(0, Math.min(1, getCurrent.getAsDouble()));
            updateMessage();
        }
    }
}
