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
    private static final int TAB_BAR_H = 24;
    private static final int SCROLLBAR_W = 6;
    private static final int ENTRY_H = 22;
    private static final int LEFT_MARGIN = 20;
    private static final int RIGHT_MARGIN = 40;
    private static final int CONTROL_W = 160;

    private final List<Entry> entries = new ArrayList<>();
    private int contentStartY;
    private int contentHeight;

    public DemonCoreConfigScreen(Screen parent) {
        super(Component.literal("DemonCore Configuration"));
        this.parent = parent;
    }

    public static Screen createScreen(Screen parent) {
        return new DemonCoreConfigScreen(parent);
    }

    private record Entry(Component label, int y, GuiEventListener control, String tooltip) {
    }

    
    
    

    private int addBool(int x, int y, String nameKey, String tip,
                        ModConfigSpec.BooleanValue value, boolean def) {
        Component label = Component.translatable(nameKey);
        boolean current = safeGet(value, def);
        CycleButton<Boolean> btn = CycleButton.onOffBuilder(current)
                .create(x, y, CONTROL_W, 20, label, (b, v) -> {
                    value.set(v);
                    value.save();
                });
        if (tip != null && !tip.isEmpty()) {
            btn.setTooltip(Tooltip.create(Component.literal(tip)));
        }
        addRenderableWidget(btn);
        entries.add(new Entry(label, y, btn, tip));
        return y + ENTRY_H;
    }

    private int addInt(int x, int y, String nameKey, String tip,
                       ModConfigSpec.IntValue value, int def, int min, int max) {
        Component label = Component.translatable(nameKey);
        int current = safeGet(value, def);
        int sliderX = x + 2;
        int sliderW = CONTROL_W - 4;
        PercentageSlider slider = new PercentageSlider(
                sliderX, y, sliderW, 20,
                (double) (current - min) / (double) (max - min),
                newVal -> {
                    int intVal = min + (int) Math.round(newVal * (max - min));
                    intVal = Math.max(min, Math.min(max, intVal));
                    value.set(intVal);
                    value.save();
                },
                pct -> {
                    int v = min + (int) Math.round(pct * (max - min));
                    return Component.literal(label.getString() + ": " + v);
                },
                () -> (double) (safeGet(value, def) - min) / (double) (max - min)
        );
        if (tip != null && !tip.isEmpty()) {
            slider.setTooltip(Tooltip.create(Component.literal(tip + " [" + min + " - " + max + "]")));
        }
        addRenderableWidget(slider);
        entries.add(new Entry(label, y, slider, tip));
        return y + ENTRY_H;
    }

    private int addDouble(int x, int y, String nameKey, String tip,
                          ModConfigSpec.DoubleValue value, double def,
                          double min, double max, String format) {
        Component label = Component.translatable(nameKey);
        double current = safeGet(value, def);
        int sliderX = x + 2;
        int sliderW = CONTROL_W - 4;
        PercentageSlider slider = new PercentageSlider(
                sliderX, y, sliderW, 20,
                (current - min) / (max - min),
                newVal -> {
                    double v = min + newVal * (max - min);
                    value.set(v);
                    value.save();
                },
                pct -> {
                    double v = min + pct * (max - min);
                    return Component.literal(label.getString() + ": " + String.format(format, v));
                },
                () -> (safeGet(value, def) - min) / (max - min)
        );
        if (tip != null && !tip.isEmpty()) {
            slider.setTooltip(Tooltip.create(Component.literal(
                    tip + " [" + String.format(format, min) + " - " + String.format(format, max) + "]")));
        }
        addRenderableWidget(slider);
        entries.add(new Entry(label, y, slider, tip));
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
        contentStartY = TAB_BAR_H + 30;
        contentHeight = this.height - contentStartY - 50;
        rebuildControls();

        
        addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20)
                .build()
        );

        
        addRenderableWidget(Button.builder(
                Component.literal("Reset All"),
                button -> resetAllToDefaults())
                .bounds(20, this.height - 30, 120, 20)
                .build()
        );

        
        int tabW = (this.width - 40) / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            final int tab = i;
            String label = TABS[i];
            boolean active = (tab == activeTab);
            MutableComponent text = Component.literal(label);
            if (active) text = text.withStyle(s -> s.withColor(0xFF5555));
            Button tabBtn = Button.builder(text, b -> {
                        activeTab = tab;
                        scrollY = 0;
                        clearDynamicControls();
                        rebuildControls();
                    })
                    .bounds(20 + i * tabW, 5, tabW - 2, TAB_BAR_H - 4)
                    .build();
            addRenderableWidget(tabBtn);
        }
    }

    private void clearDynamicControls() {
        
        List<GuiEventListener> toRemove = new ArrayList<>();
        for (Entry e : entries) {
            if (children().contains(e.control)) toRemove.add(e.control);
        }
        for (GuiEventListener l : toRemove) {
            removeWidget(l);
        }
        entries.clear();
    }

    private void rebuildControls() {
        
        List<GuiEventListener> toRemove = new ArrayList<>();
        for (Entry e : entries) {
            if (children().contains(e.control)) toRemove.add(e.control);
        }
        for (GuiEventListener l : toRemove) removeWidget(l);
        entries.clear();

        int xRight = this.width - RIGHT_MARGIN - CONTROL_W;
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
        y = sectionTitle(y, "General / Master Switches");
        y = addBool(x, y, T + "enabled", "Master switch for all DemonCore systems.",
                DemonCoreConfig.ENABLED, true);
        y = addBool(x, y, T + "debugLogging",
                "Write verbose DemonCore diagnostics to latest.log.",
                DemonCoreConfig.DEBUG_LOGGING, false);
        y = addBool(x, y, T + "enableDebug",
                "Enable debug mode for DemonCore commands and output.",
                DemonCoreConfig.ENABLE_DEBUG, false);
        y = addBool(x, y, T + "enableOptimization",
                "Enable DemonCore optimization subsystems.",
                DemonCoreConfig.ENABLE_OPTIMIZATION, true);
        y = addBool(x, y, T + "enableCache",
                "Enable DemonCore caching systems.",
                DemonCoreConfig.ENABLE_CACHE, true);
        y = addDouble(x, y, T + "speedThreshold",
                "Speed threshold in blocks/second for activation.",
                DemonCoreConfig.SPEED_THRESHOLD, 24.0, 0.0, 2000.0, "%.0f");
        y = addDouble(x, y, T + "resourceBalance",
                "Resource balance factor (1.0 = max performance).",
                DemonCoreConfig.RESOURCE_BALANCE, 0.7, 0.0, 1.0, "%.2f");
        y = addBool(x, y, T + "vulcan.enabled",
                "VULCAN MODE - disables internal safety limits. Use at your own risk.",
                DemonCoreConfig.VULCAN_MODE, false);
        return y;
    }

    private int buildChunk(int x, int y, String T) {
        y = sectionTitle(y, "Predictive Chunk Loading");
        y = addBool(x, y, T + "chunkLoading.enabled",
                "Enable predictive chunk pre-loading for fast-moving vehicles.",
                DemonCoreConfig.CHUNK_LOADING_ENABLED, true);
        y = addDouble(x, y, T + "chunkLoading.activationSpeed",
                "Minimum vehicle speed (blocks/s) before pre-loading kicks in.",
                DemonCoreConfig.ACTIVATION_SPEED, 24.0, 0.0, 2000.0, "%.0f");
        y = addInt(x, y, T + "chunkLoading.maxChunks",
                "Max chunks tracked ahead of a single vehicle.",
                DemonCoreConfig.MAX_CHUNKS, 96, 8, 512);
        y = addInt(x, y, T + "chunkLoading.chunksPerTick",
                "Baseline tickets per server tick.",
                DemonCoreConfig.CHUNKS_PER_TICK, 16, 1, 128);
        y = addInt(x, y, T + "chunkLoading.ticketRadius",
                "0 = single chunk per ticket (recommended). 1 = 3x3 area (9x work).",
                DemonCoreConfig.TICKET_RADIUS, 0, 0, 2);
        y = addInt(x, y, T + "chunkLoading.ticketLifetime",
                "How long a chunk ticket stays alive (ticks).",
                DemonCoreConfig.TICKET_LIFETIME_TICKS, 200, 40, 2400);
        y = addInt(x, y, T + "chunkLoading.maxQueued",
                "Hard cap on the pending ticket queue.",
                DemonCoreConfig.MAX_QUEUED_TICKETS, 4096, 256, 65536);
        y = addBool(x, y, T + "chunkLoading.adaptiveBackpressure",
                "Drive the chunk loading rate from measured server tick time.",
                DemonCoreConfig.ADAPTIVE_BACKPRESSURE, true);
        y = addDouble(x, y, T + "chunkLoading.targetMspt",
                "Server tick budget (ms) adaptive backpressure aims to stay under.",
                DemonCoreConfig.TARGET_MSPT, 38.0, 10.0, 50.0, "%.1f");
        y = sectionTitle(y, "Memory");
        y = addInt(x, y, T + "memory.chunkCacheSize",
                "Max chunk positions kept in predictive cache.",
                DemonCoreConfig.CHUNK_CACHE_SIZE, 8192, 256, 131072);
        y = addBool(x, y, T + "memory.autoTrim",
                "Automatically shrink caches when heap is tight.",
                DemonCoreConfig.AUTO_TRIM, true);
        return y;
    }

    private int buildTick(int x, int y, String T) {
        y = sectionTitle(y, "Entity Tick Throttling (server)");
        y = addBool(x, y, T + "tickOptimization.enabled",
                "Enable distance-based entity tick throttling.",
                DemonCoreConfig.TICK_THROTTLE_ENABLED, true);
        y = addInt(x, y, T + "tickOptimization.distance",
                "Entities closer than this always tick normally.",
                DemonCoreConfig.TICK_THROTTLE_DISTANCE, 64, 16, 256);
        y = addInt(x, y, T + "tickOptimization.maxSkipFactor",
                "Max slowdown factor (4 = tick every 4th tick for distant entities).",
                DemonCoreConfig.TICK_THROTTLE_MAX_FACTOR, 4, 1, 8);
        y = addBool(x, y, T + "tickOptimization.items",
                "Also throttle dropped items and XP orbs (very effective).",
                DemonCoreConfig.TICK_THROTTLE_ITEMS, true);
        return y;
    }

    private int buildRender(int x, int y, String T) {
        y = sectionTitle(y, "Client Rendering Optimizations");
        y = addBool(x, y, T + "rendering.enabled",
                "Master switch for client rendering optimizations.",
                DemonCoreConfig.RENDER_OPTIMIZATION, true);
        y = addBool(x, y, T + "rendering.entityCulling",
                "Skip rendering entities outside the camera frustum / beyond cull distance.",
                DemonCoreConfig.ENTITY_CULLING, true);
        y = addInt(x, y, T + "rendering.entityCullDistance",
                "Entities further away than this are not rendered.",
                DemonCoreConfig.ENTITY_CULL_DISTANCE, 96, 16, 256);
        y = addInt(x, y, T + "rendering.entityShadowDistance",
                "Max distance (blocks) for entity drop shadows (0 = disable).",
                DemonCoreConfig.ENTITY_SHADOW_DISTANCE, 24, 0, 64);
        y = addBool(x, y, T + "rendering.blockEntityCulling",
                "Skip rendering distant block entities (chests, signs, etc.).",
                DemonCoreConfig.BLOCK_ENTITY_CULLING, true);
        y = addInt(x, y, T + "rendering.blockEntityCullDistance",
                "Block entities further away than this are not rendered.",
                DemonCoreConfig.BLOCK_ENTITY_CULL_DISTANCE, 48, 8, 256);
        y = sectionTitle(y, "Particles");
        y = addBool(x, y, T + "particles.enabled",
                "Enable the particle budget.",
                DemonCoreConfig.PARTICLE_LIMIT_ENABLED, true);
        y = addInt(x, y, T + "particles.maxParticles",
                "Max live particles (vanilla has NO upper bound!).",
                DemonCoreConfig.MAX_PARTICLES, 4000, 250, 16000);
        y = addInt(x, y, T + "particles.cullDistance",
                "Particles spawned further away than this are skipped.",
                DemonCoreConfig.PARTICLE_CULL_DISTANCE, 48, 8, 256);
        y = sectionTitle(y, "Adaptive Quality");
        y = addBool(x, y, T + "adaptive.enabled",
                "Automatically tighten budgets when frame time exceeds target.",
                DemonCoreConfig.ADAPTIVE_QUALITY, true);
        y = addInt(x, y, T + "adaptive.targetFps",
                "Frame rate DemonCore aims to sustain (0 = follow video settings).",
                DemonCoreConfig.TARGET_FPS, 0, 0, 1000);
        y = addBool(x, y, T + "adaptive.spikeProtection",
                "Briefly reduce optional work to smooth out slow frames.",
                DemonCoreConfig.SPIKE_PROTECTION, true);
        y = addDouble(x, y, T + "adaptive.minQuality",
                "Lowest quality multiplier adaptive mode may drop to (1.0 = no reduction).",
                DemonCoreConfig.MIN_QUALITY, 0.35, 0.10, 1.0, "%.2f");
        y = sectionTitle(y, "Render Batch");
        y = addBool(x, y, T + "batchRender.enabled",
                "Enable batched draw-call coalescing (groups similar calls together).",
                DemonCoreConfig.BATCH_RENDER_ENABLED, true);
        y = addInt(x, y, T + "batchRender.bufferSize",
                "Max quads held in a single batch before flushing.",
                DemonCoreConfig.BATCH_BUFFER_SIZE, 2048, 256, 16384);
        return y;
    }

    private int buildCache(int x, int y, String T) {
        y = sectionTitle(y, "Geometry & Visibility Caching");
        y = addBool(x, y, T + "geometryCache.enabled",
                "Cache block entity geometry and entity pose data in heap RAM.",
                DemonCoreConfig.GEOMETRY_CACHE_ENABLED, true);
        y = addInt(x, y, T + "geometryCache.sizeMb",
                "MB of heap RAM DemonCore may use for geometry caching.",
                DemonCoreConfig.GEOMETRY_CACHE_MB, 192, 32, 1024);
        y = addBool(x, y, T + "geometryCache.visibilityLattice",
                "Coarse in-RAM visibility lattice (replaces per-object frustum tests).",
                DemonCoreConfig.VISIBILITY_LATTICE, true);
        y = addInt(x, y, T + "geometryCache.cellSize",
                "Edge length of each lattice cell in blocks.",
                DemonCoreConfig.VISIBILITY_CELL_SIZE, 8, 4, 32);
        return y;
    }

    private int buildLOD(int x, int y, String T) {
        y = sectionTitle(y, "Entity Level of Detail");
        y = addBool(x, y, T + "entityLOD.enabled",
                "Enable progressive entity LOD switching (fade out smoothly).",
                DemonCoreConfig.ENTITY_LOD_ENABLED, true);
        y = addInt(x, y, T + "entityLOD.fullDistance",
                "Entities closer than this = full detail (model + animation).",
                DemonCoreConfig.LOD_FULL_DISTANCE, 24, 8, 128);
        y = addInt(x, y, T + "entityLOD.simpleDistance",
                "Past this distance = static simplified model (no limb animation).",
                DemonCoreConfig.LOD_SIMPLE_DISTANCE, 48, 16, 192);
        y = addInt(x, y, T + "entityLOD.billboardDistance",
                "Past this distance = axis-facing billboard sprite.",
                DemonCoreConfig.LOD_BILLBOARD_DISTANCE, 72, 24, 256);
        return y;
    }

    private int buildGPUBalancer(int x, int y, String T) {
        y = sectionTitle(y, "GPU-RAM Dynamic Balancer (UNIQUE)");
        y = addBool(x, y, T + "gpuRamBalancer.enabled",
                "Closed-loop controller: grows caches in RAM when GPU is hot, shrinks when not.",
                DemonCoreConfig.GPU_RAM_BALANCER, true);
        y = addDouble(x, y, T + "gpuRamBalancer.targetGpu",
                "Target GPU utilisation to maintain (0.85 = headroom for spikes/shaders).",
                DemonCoreConfig.GPU_TARGET_UTIL, 0.60, 0.20, 0.95, "%.2f");
        y = addDouble(x, y, T + "gpuRamBalancer.maxRamFraction",
                "Hard cap: max fraction of heap DemonCore may consume for caches.",
                DemonCoreConfig.RAM_MAX_USAGE, 0.45, 0.10, 0.80, "%.2f");
        return y;
    }

    private int buildUniqueFeatures(int x, int y, String T) {
        y = sectionTitle(y, "Predictive Frame Scheduler (UNIQUE)");
        y = addBool(x, y, T + "predictiveScheduler.enabled",
                "Micro-sleep before slow frames to eliminate jitter (1% low FPS boost).",
                DemonCoreConfig.PREDICTIVE_SCHEDULER, true);
        y = addDouble(x, y, T + "predictiveScheduler.headroom",
                "Extra frame-budget margin kept in reserve.",
                DemonCoreConfig.PREDICTIVE_HEADROOM, 0.10, 0.02, 0.40, "%.2f");
        y = sectionTitle(y, "Silent Chunk Tracker (UNIQUE)");
        y = addBool(x, y, T + "silentChunkTracker.enabled",
                "Skip mesh re-validation for chunks that haven't changed in N ticks.",
                DemonCoreConfig.SILENT_CHUNK_TRACKER, true);
        y = addInt(x, y, T + "silentChunkTracker.ticks",
                "Consecutive no-change ticks before marking a chunk 'silent'.",
                DemonCoreConfig.SILENT_CHUNK_TICKS, 20, 5, 200);
        y = sectionTitle(y, "Diagnostics");
        y = addBool(x, y, T + "diagnostics.bottleneck",
                "Measure CPU vs GPU wait per frame to find the real bottleneck.",
                DemonCoreConfig.BOTTLENECK_DETECTION, true);
        y = addBool(x, y, T + "diagnostics.overlay",
                "Draw the compact live performance overlay top-left.",
                DemonCoreConfig.SHOW_OVERLAY, false);
        y = addInt(x, y, T + "diagnostics.logInterval",
                "Write performance summary to latest.log every N seconds (0 = disable).",
                DemonCoreConfig.STATS_LOG_INTERVAL, 0, 0, 3600);
        return y;
    }

    

    private int sectionTitle(int y, String text) {
        
        entries.add(new Entry(Component.literal("--- " + text + " ---"), y, null, null));
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
        trySet(DemonCoreConfig.ENTITY_CULLING, true);
        trySet(DemonCoreConfig.ENTITY_CULL_DISTANCE, 96);
        trySet(DemonCoreConfig.ENTITY_SHADOW_DISTANCE, 24);
        trySet(DemonCoreConfig.BLOCK_ENTITY_CULLING, true);
        trySet(DemonCoreConfig.BLOCK_ENTITY_CULL_DISTANCE, 48);
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
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        
        String title = "DemonCore Configuration";
        graphics.drawCenteredString(this.font, title, this.width / 2, TAB_BAR_H + 8, 0xFF5555);

        
        int labelX = LEFT_MARGIN;
        for (Entry e : entries) {
            if (e.control == null) {
                
                graphics.drawString(this.font, e.label.getString(),
                        labelX, e.y + 6, 0xFFAA00, false);
            } else if (e.control instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                
                graphics.drawString(this.font, e.label.getString(),
                        labelX, w.getY() + 6, 0xFFFFFF, false);
            }
        }

        
        if (maxScroll > 0) {
            int sbX = this.width - RIGHT_MARGIN - SCROLLBAR_W - 2;
            int sbY = contentStartY;
            int sbH = contentHeight;
            int handleH = Math.max(20, (int) ((contentHeight / (float) (contentHeight + maxScroll)) * sbH));
            int handleY = sbY + (int) ((scrollY / (float) maxScroll) * (sbH - handleH));
            graphics.fill(sbX, sbY, sbX + SCROLLBAR_W, sbY + sbH, 0x33FFFFFF);
            graphics.fill(sbX, handleY, sbX + SCROLLBAR_W, handleY + handleH, 0xFF5555);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double xDelta, double yDelta) {
        if (maxScroll > 0) {
            int delta = (int) Math.round(-yDelta * 24);
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

        public void syncFromSource() {
            this.value = Math.max(0, Math.min(1, getCurrent.getAsDouble()));
            updateMessage();
        }
    }
}
