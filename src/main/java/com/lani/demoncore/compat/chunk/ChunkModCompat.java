package com.lani.demoncore.compat.chunk;

import com.lani.demoncore.compat.ModCompat;
import com.lani.demoncore.optimization.SmartChunkCalculator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class ChunkModCompat {

    private ChunkModCompat() {}

    private static final Set<String> KNOWN_CHUNK_MODS = Set.of(
            "c2me",                // Concurrent Chunk Management Engine
            "chunkpregen",         // Chunk Pregenerator
            "starlight",           // Starlight lighting engine
            "phosphophyllite",     // Phosphophyllite (rare chunk mgmt)
            "lithium",             // Lithium general
            "radium",              // Radium (Lithium fork)
            "canary",              // Canary
            "nvidium",             // Nvidium GPU chunk
            "chunksending",        // Chunk Sending optimizations
            "immediatelyfast",     // ImmediatelyFast (chunk upload)
            "chromium",            // Chromium chunk tweaks
            "verymanyplayers",     // VMP
            "modernfix",           // ModernFix chunk optimizations
            "servercore",          // ServerCore
            "kotlinforforge",      // safe ignore
            "spark",               // Spark profiler - safe ignore
            "embeddium",           // Embeddium render
            "rubidium",            // Rubidium render
            "magnesium",           // Magnesium render
            "ferritecore",         // FerriteCore memory
            "performant",          // Performant
            "entityculling",       // Entity Culling
            "sodium",              // Sodium (Fabric on Forge via compat)
            "iris",                // Iris shaders
            "oculus"               // Oculus shaders
    );

    private static boolean resolved;
    private static final Set<String> loaded = new ObjectOpenHashSet<>();

    private static volatile int maxTicketRadius = 0;
    private static volatile int chunksPerTickCap = -1;
    private static volatile boolean disableTickets = false;
    private static volatile boolean reduceQueue = false;
    private static volatile double prefetchMultiplier = 1.0;
    private static volatile boolean useSmartPredictivePath;
    private static volatile int compatibilityLevel;
    private static volatile boolean aggressiveChunkCaching;

    public static void resolve() {
        if (resolved) return;
        ModList list = ModList.get();
        if (list == null) return;
        resolved = true;
        for (String id : KNOWN_CHUNK_MODS) {
            if (list.isLoaded(id)) loaded.add(id);
        }
        computeOverrides();
    }

    private static void computeOverrides() {
        boolean c2me = loaded.contains("c2me");
        boolean star = loaded.contains("starlight");
        boolean vmp = loaded.contains("verymanyplayers");
        boolean srvc = loaded.contains("servercore");
        boolean mf = loaded.contains("modernfix") || ModCompat.hasMemoryMod();
        boolean nvidium = loaded.contains("nvidium");
        boolean immediatelyfast = loaded.contains("immediatelyfast");
        boolean perfMod = loaded.contains("performant");
        boolean lithiumFork = loaded.contains("lithium") || loaded.contains("radium") || loaded.contains("canary");
        boolean renderMod = loaded.contains("embeddium") || loaded.contains("rubidium") || loaded.contains("magnesium") || loaded.contains("sodium");

        if (c2me || vmp || srvc) {
            maxTicketRadius = 0;
            chunksPerTickCap = 6;
            reduceQueue = true;
            disableTickets = false;
            prefetchMultiplier = 0.50;
            compatibilityLevel = 2;
        } else {
            maxTicketRadius = -1;
            chunksPerTickCap = -1;
            reduceQueue = false;
            disableTickets = false;
            prefetchMultiplier = 1.0;
            compatibilityLevel = 0;
        }

        if (star) {
            reduceQueue = true;
            compatibilityLevel = Math.max(compatibilityLevel, 1);
        }

        if (mf) {
            disableTickets = false;
            prefetchMultiplier = Math.min(prefetchMultiplier, 0.85);
        }

        if (nvidium) {
            // Nvidium completely handles chunk rendering and memory management on GPU. 
            // DemonCore MUST step back to prevent flickering and memory leaks.
            reduceQueue = true;
            useSmartPredictivePath = false; // Disable DemonCore prefetching
            prefetchMultiplier = 0.1; // Minimal prefetching
            aggressiveChunkCaching = false; // Disable RAM hoarding, Nvidium uses VRAM
            compatibilityLevel = Math.max(compatibilityLevel, 3);
        } else if (renderMod || immediatelyfast) {
            // Sodium/Embeddium handles their own chunk meshing, but we can still cache.
            reduceQueue = true;
            useSmartPredictivePath = false; // Disable custom prefetch to avoid Sodium chunk loading conflicts
            prefetchMultiplier = Math.min(prefetchMultiplier, 0.5);
            aggressiveChunkCaching = false; // Let Sodium manage memory
            compatibilityLevel = Math.max(compatibilityLevel, 2);
        } else {
            // Vanilla render, DemonCore can take full control
            useSmartPredictivePath = true;
            aggressiveChunkCaching = true;
        }

        if (perfMod || lithiumFork) {
            chunksPerTickCap = Math.max(chunksPerTickCap, 8);
            prefetchMultiplier = Math.min(prefetchMultiplier, 0.8);
            useSmartPredictivePath = false; // Lithium/Canary optimizes server ticks and chunk tracking
        }

        if (loaded.isEmpty()) {
            useSmartPredictivePath = true;
            aggressiveChunkCaching = true;
            prefetchMultiplier = 1.0;
        }
    }

    public static boolean hasChunkOptimizationMod() {
        resolve();
        return !loaded.isEmpty();
    }

    public static boolean shouldUseTickets() {
        resolve();
        return !disableTickets;
    }

    public static int getMaxTicketRadiusOverride(int fallback) {
        resolve();
        return maxTicketRadius >= 0 ? maxTicketRadius : fallback;
    }

    public static int getChunksPerTickCap(int fallback) {
        resolve();
        return chunksPerTickCap >= 0 ? chunksPerTickCap : fallback;
    }

    public static boolean shouldReduceQueue() {
        resolve();
        return reduceQueue;
    }

    public static Set<String> getLoadedChunkMods() {
        resolve();
        return Collections.unmodifiableSet(loaded);
    }

    public static double getPrefetchMultiplier() {
        resolve();
        return prefetchMultiplier;
    }

    public static boolean shouldUseSmartPath() {
        resolve();
        return useSmartPredictivePath;
    }

    public static int getCompatibilityLevel() {
        resolve();
        return compatibilityLevel;
    }

    public static boolean useAggressiveChunkCaching() {
        resolve();
        return aggressiveChunkCaching;
    }

    public static List<ChunkPos> computeOptimizedPrefetch(Vec3 position, Vec3 motion, double speed, int baseBudget) {
        resolve();
        int budget = (int) Math.max(1, baseBudget * prefetchMultiplier);
        if (useSmartPredictivePath) {
            return SmartChunkCalculator.predictPath(position, motion, speed, budget);
        }
        return computeFallbackPrefetch(position, budget);
    }

    private static List<ChunkPos> computeFallbackPrefetch(Vec3 position, int budget) {
        List<ChunkPos> result = new ArrayList<>(Math.min(budget, 64));
        int px = (int) Math.floor(position.x / 16.0);
        int pz = (int) Math.floor(position.z / 16.0);
        int radius = Math.max(1, (int) Math.ceil(Math.sqrt(budget / Math.PI)));
        for (int dz = -radius; dz <= radius && result.size() < budget; dz++) {
            for (int dx = -radius; dx <= radius && result.size() < budget; dx++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    result.add(new ChunkPos(px + dx, pz + dz));
                }
            }
        }
        return result;
    }

    public static String getStats() {
        resolve();
        if (loaded.isEmpty()) {
            return "Chunk compat: none (DemonCore native optimizations: smartPath="
                    + (useSmartPredictivePath ? "ON" : "OFF")
                    + " cache=" + (aggressiveChunkCaching ? "AGGRESSIVE" : "NORMAL")
                    + " prefetch=" + String.format("%.2f", prefetchMultiplier) + "x)";
        }
        return "Chunk compat: " + String.join(", ", loaded)
                + " | tickets=" + (disableTickets ? "DISABLED" : "ON")
                + " | cap=" + chunksPerTickCap
                + " | rad=" + maxTicketRadius
                + " | compatLvl=" + compatibilityLevel
                + " | prefetch=" + String.format("%.2f", prefetchMultiplier) + "x"
                + " | smartPath=" + (useSmartPredictivePath ? "ON" : "OFF")
                + " | cache=" + (aggressiveChunkCaching ? "AGGRESSIVE" : "NORMAL");
    }
}
