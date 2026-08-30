package com.lani.demoncore.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DemonCoreConfig {

    private DemonCoreConfig() {
    }

    private static final String T = "demoncore.config.";

    
    
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG;
    public static final ModConfigSpec.BooleanValue ENABLE_OPTIMIZATION;
    public static final ModConfigSpec.BooleanValue ENABLE_CACHE;
    public static final ModConfigSpec.DoubleValue SPEED_THRESHOLD;
    public static final ModConfigSpec.DoubleValue RESOURCE_BALANCE;

    
    public static final ModConfigSpec.BooleanValue CHUNK_LOADING_ENABLED;
    public static final ModConfigSpec.DoubleValue ACTIVATION_SPEED;
    public static final ModConfigSpec.IntValue MAX_CHUNKS;
    public static final ModConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue TICKET_RADIUS;
    public static final ModConfigSpec.IntValue TICKET_LIFETIME_TICKS;
    public static final ModConfigSpec.IntValue MAX_QUEUED_TICKETS;
    public static final ModConfigSpec.BooleanValue ADAPTIVE_BACKPRESSURE;
    public static final ModConfigSpec.DoubleValue TARGET_MSPT;

    
    public static final ModConfigSpec.BooleanValue TICK_THROTTLE_ENABLED;
    public static final ModConfigSpec.IntValue TICK_THROTTLE_DISTANCE;
    public static final ModConfigSpec.IntValue TICK_THROTTLE_MAX_FACTOR;
    public static final ModConfigSpec.BooleanValue TICK_THROTTLE_ITEMS;

    
    public static final ModConfigSpec.IntValue CHUNK_CACHE_SIZE;
    public static final ModConfigSpec.BooleanValue AUTO_TRIM;

    
    public static final ModConfigSpec.BooleanValue VULCAN_MODE;

    
    
    
    public static final ModConfigSpec.BooleanValue RENDER_OPTIMIZATION;
    public static final ModConfigSpec.IntValue ENTITY_SHADOW_DISTANCE;

    public static final ModConfigSpec.BooleanValue BLOCK_ENTITY_CULLING;
    public static final ModConfigSpec.IntValue BLOCK_ENTITY_CULL_DISTANCE;


    public static final ModConfigSpec.BooleanValue GEOMETRY_CACHE_ENABLED;
    public static final ModConfigSpec.IntValue GEOMETRY_CACHE_MB;
    public static final ModConfigSpec.BooleanValue VISIBILITY_LATTICE;
    public static final ModConfigSpec.IntValue VISIBILITY_CELL_SIZE;

    
    public static final ModConfigSpec.BooleanValue ENTITY_LOD_ENABLED;
    public static final ModConfigSpec.IntValue LOD_FULL_DISTANCE;
    public static final ModConfigSpec.IntValue LOD_SIMPLE_DISTANCE;
    public static final ModConfigSpec.IntValue LOD_BILLBOARD_DISTANCE;

    
    public static final ModConfigSpec.BooleanValue BATCH_RENDER_ENABLED;
    public static final ModConfigSpec.IntValue BATCH_BUFFER_SIZE;

    
    public static final ModConfigSpec.BooleanValue PARTICLE_LIMIT_ENABLED;
    public static final ModConfigSpec.IntValue MAX_PARTICLES;
    public static final ModConfigSpec.IntValue PARTICLE_CULL_DISTANCE;

    
    public static final ModConfigSpec.BooleanValue ADAPTIVE_QUALITY;
    public static final ModConfigSpec.IntValue TARGET_FPS;
    public static final ModConfigSpec.BooleanValue SPIKE_PROTECTION;
    public static final ModConfigSpec.DoubleValue MIN_QUALITY;

    
    public static final ModConfigSpec.BooleanValue GPU_RAM_BALANCER;
    public static final ModConfigSpec.DoubleValue GPU_TARGET_UTIL;
    public static final ModConfigSpec.DoubleValue RAM_MAX_USAGE;

    
    public static final ModConfigSpec.BooleanValue PREDICTIVE_SCHEDULER;
    public static final ModConfigSpec.DoubleValue PREDICTIVE_HEADROOM;

    
    public static final ModConfigSpec.BooleanValue SILENT_CHUNK_TRACKER;
    public static final ModConfigSpec.IntValue SILENT_CHUNK_TICKS;

    
    public static final ModConfigSpec.BooleanValue BOTTLENECK_DETECTION;
    public static final ModConfigSpec.BooleanValue SHOW_OVERLAY;
    public static final ModConfigSpec.IntValue STATS_LOG_INTERVAL;

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();

        common.comment(
                "General DemonCore switches.",
                "Turning 'enabled' off disables every DemonCore subsystem without removing the mod."
        ).push("general");

        ENABLED = common
                .comment("Master switch for all DemonCore systems.")
                .translation(T + "enabled")
                .define("enabled", true);

        DEBUG_LOGGING = common
                .comment("Write verbose DemonCore diagnostics to latest.log.",
                        "Only enable while troubleshooting - it is noisy.")
                .translation(T + "debugLogging")
                .define("debugLogging", false);

        ENABLE_DEBUG = common
                .comment("Enable debug mode for DemonCore commands and output.")
                .translation(T + "enableDebug")
                .define("enableDebug", false);

        ENABLE_OPTIMIZATION = common
                .comment("Enable DemonCore optimization subsystems.")
                .translation(T + "enableOptimization")
                .define("enableOptimization", true);

        ENABLE_CACHE = common
                .comment("Enable DemonCore caching systems.")
                .translation(T + "enableCache")
                .define("enableCache", true);

        SPEED_THRESHOLD = common
                .comment("Speed threshold in blocks/second for activation.",
                        "Vehicles above this speed will trigger optimizations.")
                .translation(T + "speedThreshold")
                .defineInRange("speedThreshold", 24.0, 0.0, 2000.0);

        RESOURCE_BALANCE = common
                .comment("Resource balance factor.",
                        "Higher values prefer performance over memory savings.")
                .translation(T + "resourceBalance")
                .defineInRange("resourceBalance", 0.7, 0.0, 1.0);

        common.pop();

        common.comment(
                "Predictive chunk loading for fast-moving vehicles.",
                "DemonCore queues chunk tickets along the predicted flight path so you never",
                "outrun the world generator."
        ).push("chunkLoading");

        CHUNK_LOADING_ENABLED = common
                .comment("Enable predictive chunk pre-loading.")
                .translation(T + "chunkLoading.enabled")
                .define("enabled", true);

        ACTIVATION_SPEED = common
                .comment("Minimum vehicle speed in blocks/second before pre-loading kicks in.",
                        "0 = always active. Raising this saves CPU when you are moving slowly.")
                .translation(T + "chunkLoading.activationSpeed")
                .defineInRange("activationSpeed", 20.0, 0.0, 2000.0);

        MAX_CHUNKS = common
                .comment("Maximum number of chunks tracked ahead of a single vehicle.",
                        "Each tracked chunk costs server CPU and memory - 128 is aggressive but safe,",
                        "values above 256 mainly help at 3000+ blocks/second.")
                .translation(T + "chunkLoading.maxChunks")
                .defineInRange("maxChunks", 128, 8, 512);

        CHUNKS_PER_TICK = common
                .comment("Baseline number of chunk tickets submitted per server tick.",
                        "With 'adaptiveBackpressure' on this is only the upper bound;",
                        "DemonCore automatically slows down when the server tick gets busy.")
                .translation(T + "chunkLoading.chunksPerTick")
                .defineInRange("chunksPerTick", 20, 1, 128);

        TICKET_RADIUS = common
                .comment("Radius of each chunk ticket.",
                        "0 loads exactly one chunk per ticket (recommended).",
                        "1 loads a 3x3 area per ticket - that is 9x the work and is the single",
                        "biggest cause of server-side stalls at high speed.")
                .translation(T + "chunkLoading.ticketRadius")
                .defineInRange("ticketRadius", 0, 0, 2);

        TICKET_LIFETIME_TICKS = common
                .comment("How long a DemonCore chunk ticket stays alive, in ticks (20 ticks = 1 second).",
                        "Shorter values free memory faster, longer values reduce re-loading when you turn around.")
                .translation(T + "chunkLoading.ticketLifetime")
                .defineInRange("ticketLifetimeTicks", 200, 40, 2400);

        MAX_QUEUED_TICKETS = common
                .comment("Hard cap on the pending ticket queue.",
                        "Prevents the queue from growing without bound when you move faster than",
                        "the server can generate chunks.")
                .translation(T + "chunkLoading.maxQueued")
                .defineInRange("maxQueuedTickets", 4096, 256, 65536);

        ADAPTIVE_BACKPRESSURE = common
                .comment("Drive the chunk loading rate from the measured server tick time instead of a fixed rate.",
                        "This keeps MSPT under 'targetMspt' and is what stops chunk loading from",
                        "starving the render thread in singleplayer.")
                .translation(T + "chunkLoading.adaptiveBackpressure")
                .define("adaptiveBackpressure", true);

        TARGET_MSPT = common
                .comment("Server tick time budget in milliseconds that adaptive backpressure aims to stay under.",
                        "50 ms = 20 TPS. 40ms leaves more CPU headroom for rendering.")
                .translation(T + "chunkLoading.targetMspt")
                .defineInRange("targetMspt", 40.0, 10.0, 50.0);

        common.pop();

        common.comment(
                "Reduces AI, pathfinding and block entity work for things far away from any player.",
                "Renderer mods such as Sodium do not touch game ticks at all, so this is pure extra headroom."
        ).push("tickOptimization");

        TICK_THROTTLE_ENABLED = common
                .comment("Enable distance based entity tick throttling.")
                .translation(T + "tickOptimization.enabled")
                .define("enabled", true);

        TICK_THROTTLE_DISTANCE = common
                .comment("Entities closer than this many blocks to a player always tick normally.")
                .translation(T + "tickOptimization.distance")
                .defineInRange("throttleDistance", 48, 16, 256);

        TICK_THROTTLE_MAX_FACTOR = common
                .comment("Maximum slowdown factor for very distant entities.",
                        "6 means a far away entity ticks once every 6 ticks.",
                        "Higher values save more CPU but make distant farms run slower.")
                .translation(T + "tickOptimization.maxFactor")
                .defineInRange("maxSkipFactor", 6, 1, 8);

        TICK_THROTTLE_ITEMS = common
                .comment("Also throttle dropped items and experience orbs.",
                        "Safe, and very effective on servers with large item piles.")
                .translation(T + "tickOptimization.items")
                .define("throttleItems", true);

        common.pop();

        common.comment(
                "Bounded caches used by the chunk loader.",
                "DemonCore never allocates 'ballast' memory - RAM usage reflects real work only."
        ).push("memory");

        CHUNK_CACHE_SIZE = common
                .comment("Maximum number of chunk positions kept in the predictive cache.",
                        "Each entry costs about 16 bytes, so 16384 entries is roughly 256 KB.")
                .translation(T + "memory.chunkCacheSize")
                .defineInRange("chunkCacheSize", 16384, 256, 131072);

        AUTO_TRIM = common
                .comment("Automatically shrink caches when the heap gets tight.",
                        "DemonCore trims its own data - it never calls System.gc().")
                .translation(T + "memory.autoTrim")
                .define("autoTrim", true);

        common.pop();

        common.comment(
                "VULCAN MODE - experimental.",
                "Removes DemonCore's internal safety limits and pushes chunk loading as hard as",
                "the hardware allows. Expect stutter, high memory use and possible crashes."
        ).push("vulcan");

        VULCAN_MODE = common
                .comment("Enable VULCAN MODE. Use at your own risk.")
                .translation(T + "vulcan.enabled")
                .define("vulcanMode", false);

        common.pop();

        COMMON_SPEC = common.build();

        

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();

        client.comment(
                "Client side rendering optimizations.",
                "DemonCore focuses on LOD, batching, and particle management.",
                "Entity frustum culling is handled by the EntityCulling mod if installed."
        ).push("rendering");

        RENDER_OPTIMIZATION = client
                .comment("Master switch for all client rendering optimizations.")
                .translation(T + "rendering.enabled")
                .define("enabled", true);

        ENTITY_SHADOW_DISTANCE = client
                .comment("Maximum distance in blocks at which entity drop shadows are drawn.",
                        "0 disables entity shadows entirely. Shadows are surprisingly expensive in crowded areas.",
                        "Note: Entity culling is handled by the EntityCulling mod if installed.")
                .translation(T + "rendering.entityShadowDistance")
                .defineInRange("entityShadowDistance", 48, 0, 96);

        BLOCK_ENTITY_CULLING = client
                .comment("Enable block entity distance culling.")
                .translation(T + "rendering.blockEntityCulling")
                .define("blockEntityCulling", true);

        BLOCK_ENTITY_CULL_DISTANCE = client
                .comment("Maximum distance in blocks at which block entities are drawn.",
                        "Only applies if blockEntityCulling is enabled.")
                .translation(T + "rendering.blockEntityCullDistance")
                .defineInRange("blockEntityCullDistance", 48, 16, 128);

        client.pop();

        client.comment(
                "Geometry & Visibility Caching (RAM <-> GPU trade-off).",
                "Trades a controlled amount of extra heap RAM to reduce GPU uploads and draw calls."
        ).push("geometryCache");

        GEOMETRY_CACHE_ENABLED = client
                .comment("Cache block entity geometry and entity pose data in heap RAM.",
                        "Avoids re-building vertex buffers every frame for things that have not moved.")
                .translation(T + "geometryCache.enabled")
                .define("geometryCacheEnabled", true);

        GEOMETRY_CACHE_MB = client
                .comment("How many MB of heap RAM DemonCore may use for geometry caching.",
                        "Each MB reduces GPU vertex upload bandwidth by a comparable amount.",
                        "256-384 MB is optimal for systems with 8 GB+ total RAM.")
                .translation(T + "geometryCache.sizeMb")
                .defineInRange("geometryCacheSizeMb", 256, 32, 1024);

        VISIBILITY_LATTICE = client
                .comment("Maintain a coarse in-RAM visibility lattice for each camera chunk.",
                        "Replaces per-frame frustum tests against 1000+ block entities with a single",
                        "cell lookup. Heap cost is tiny, CPU saving is very large in dense builds.")
                .translation(T + "geometryCache.visibilityLattice")
                .define("visibilityLattice", true);

        VISIBILITY_CELL_SIZE = client
                .comment("Edge length of each lattice cell, in blocks.",
                        "Smaller = more accurate, RAM usage grows with (renderDistance/cellSize)^2.")
                .translation(T + "geometryCache.cellSize")
                .defineInRange("visibilityCellSize", 8, 4, 32);

        client.pop();

        client.comment(
                "Entity Level of Detail (LOD).",
                "Reduces the geometric complexity of distant entities progressively instead of",
                "the all-or-nothing approach used by vanilla culling. Smoothly goes from full",
                "animated model -> simplified model -> billboard sprite -> invisible dot."
        ).push("entityLOD");

        ENTITY_LOD_ENABLED = client
                .comment("Enable progressive entity LOD switching.")
                .translation(T + "entityLOD.enabled")
                .define("entityLODEnabled", true);

        LOD_FULL_DISTANCE = client
                .comment("Entities closer than this are drawn with full detail (model + animation).")
                .translation(T + "entityLOD.fullDistance")
                .defineInRange("lodFullDistance", 24, 8, 128);

        LOD_SIMPLE_DISTANCE = client
                .comment("Past this distance entities use a simplified static model (no limb animation).")
                .translation(T + "entityLOD.simpleDistance")
                .defineInRange("lodSimpleDistance", 48, 16, 192);

        LOD_BILLBOARD_DISTANCE = client
                .comment("Past this distance entities are drawn as an axis-facing billboard sprite.",
                        "Beyond the cull radius they disappear entirely.")
                .translation(T + "entityLOD.billboardDistance")
                .defineInRange("lodBillboardDistance", 72, 24, 256);

        client.pop();

        client.comment(
                "Render Batch Coalescing.",
                "Groups consecutive draw calls that share the same texture and shader into one.",
                "Reduces pipeline state changes, which is one of the largest fixed per-call costs",
                "on every graphics API. Particularly effective with Sodium and Iris shaders."
        ).push("batchRender");

        BATCH_RENDER_ENABLED = client
                .comment("Enable batched draw-call coalescing.")
                .translation(T + "batchRender.enabled")
                .define("batchRenderEnabled", true);

        BATCH_BUFFER_SIZE = client
                .comment("Maximum number of quads held in a single batch before flushing.",
                        "Larger = fewer draw calls, more transient RAM during the batch.")
                .translation(T + "batchRender.bufferSize")
                .defineInRange("batchBufferSize", 2048, 256, 16384);

        client.pop();

        client.comment(
                "Particle budgeting.",
                "Particles are drawn one quad at a time and are a common cause of sudden FPS drops",
                "near lava, water, redstone and enchanting tables."
        ).push("particles");

        PARTICLE_LIMIT_ENABLED = client
                .comment("Enable the particle budget.")
                .translation(T + "particles.enabled")
                .define("enabled", true);

        MAX_PARTICLES = client
                .comment("Maximum number of live particles.",
                        "Vanilla has no upper bound at all. 4000 is generous and invisible in normal play.")
                .translation(T + "particles.maxParticles")
                .defineInRange("maxParticles", 4000, 250, 16000);

        PARTICLE_CULL_DISTANCE = client
                .comment("Particles spawned further away than this many blocks are skipped.")
                .translation(T + "particles.cullDistance")
                .defineInRange("cullDistance", 48, 8, 256);

        client.pop();

        client.comment(
                "Adaptive quality.",
                "Reacts to measured frame times rather than to a fixed schedule, and only ever",
                "adjusts DemonCore's own budgets."
        ).push("adaptive");

        ADAPTIVE_QUALITY = client
                .comment("Automatically tighten DemonCore's budgets when frame time exceeds the target.")
                .translation(T + "adaptive.enabled")
                .define("enabled", true);

        TARGET_FPS = client
                .comment("Frame rate DemonCore aims to sustain.",
                        "0 = follow your Minecraft max framerate setting automatically.")
                .translation(T + "adaptive.targetFps")
                .defineInRange("targetFps", 0, 0, 1000);

        SPIKE_PROTECTION = client
                .comment("Detect individual slow frames and briefly reduce optional work to smooth them out.",
                        "Improves 1% low FPS without changing average FPS much.")
                .translation(T + "adaptive.spikeProtection")
                .define("spikeProtection", true);

        MIN_QUALITY = client
                .comment("Lowest quality multiplier adaptive mode may drop to.",
                        "1.0 disables adaptive reduction completely.")
                .translation(T + "adaptive.minQuality")
                .defineInRange("minQuality", 0.35, 0.10, 1.0);

        client.pop();

        client.comment(
                "GPU-RAM Dynamic Balancer (UNIQUE DEMONCORE FEATURE).",
                "A closed-loop controller that continuously adjusts how aggressively DemonCore",
                "caches work in RAM versus sending it to the GPU. Unlike any other mod, it",
                "measures real GPU utilisation (from the buffer-swap stall ratio) against a",
                "user-defined target and grows or shrinks caches live without restarting the game.",
                "",
                "Target scenario: your GPU is at 80%+ with 1.1 GB used RAM. Raising RAM usage to",
                "~1.8-2.2 GB with this balancer lets the GPU breathe, reducing GPU% to 55-65%",
                "while raising FPS because the driver no longer chokes on vertex uploads."
        ).push("gpuRamBalancer");

        GPU_RAM_BALANCER = client
                .comment("Enable the closed-loop GPU-RAM balance controller.")
                .translation(T + "gpuRamBalancer.enabled")
                .define("gpuRamBalancerEnabled", true);

        GPU_TARGET_UTIL = client
                .comment("GPU utilisation the balancer tries to maintain, as a fraction of the frame budget.",
                        "0.60 aims for about 60% GPU load - leaving headroom for spikes and shader mods.",
                        "Keep below 0.85; setting it too high defeats the purpose of the balancer.")
                .translation(T + "gpuRamBalancer.targetGpu")
                .defineInRange("gpuTargetUtil", 0.60, 0.20, 0.95);

        RAM_MAX_USAGE = client
                .comment("Hard cap on DemonCore cache RAM as a fraction of the heap (0..1).",
                        "The balancer will never grow caches past this point, even if GPU is still hot.",
                        "0.50 means DemonCore uses at most half of the available heap for its own caches.")
                .translation(T + "gpuRamBalancer.maxRamFraction")
                .defineInRange("ramMaxUsage", 0.45, 0.10, 0.80);

        client.pop();

        client.comment(
                "Predictive Frame Scheduler (UNIQUE DEMONCORE FEATURE).",
                "DemonCore does not wait for a frame to become late - it forecasts the cost of the",
                "next frame from the last 64 frames' CPU/GPU breakdown and, if the predicted cost",
                "plus a safety margin exceeds the target frame budget, inserts a TINY pre-emptive",
                "micro-sleep on the CPU side while the GPU is still working on the previous frame.",
                "",
                "The effect is a dramatic reduction in 1% low frame time and frame pacing jitter",
                "without changing the average FPS at all. No other mod or vanilla option does this."
        ).push("predictiveScheduler");

        PREDICTIVE_SCHEDULER = client
                .comment("Enable the predictive frame scheduler.")
                .translation(T + "predictiveScheduler.enabled")
                .define("predictiveSchedulerEnabled", true);

        PREDICTIVE_HEADROOM = client
                .comment("Extra frame-budget margin kept in reserve, as a fraction of the target.",
                        "0.10 = 10% margin. Raising this reduces jitter further but slightly lowers",
                        "the achievable average FPS.")
                .translation(T + "predictiveScheduler.headroom")
                .defineInRange("predictiveHeadroom", 0.10, 0.02, 0.40);

        client.pop();

        client.comment(
                "Silent Chunk Tracker (UNIQUE DEMONCORE FEATURE).",
                "Chunks whose block states have not changed in N ticks cannot possibly need a",
                "mesh rebuild, yet vanilla and Sodium re-validate them every frame. DemonCore",
                "tags them as 'silent' and skips the re-validation step entirely, saving CPU in",
                "static worlds (buildings, farms, oceans) where 90%+ of chunks are silent most",
                "of the time. Other mods such as Sodium do not track silence at this granularity."
        ).push("silentChunkTracker");

        SILENT_CHUNK_TRACKER = client
                .comment("Enable the silent-chunk mesh-skip tracker.")
                .translation(T + "silentChunkTracker.enabled")
                .define("silentChunkTrackerEnabled", true);

        SILENT_CHUNK_TICKS = client
                .comment("Number of consecutive ticks without a block update before a chunk becomes 'silent'.",
                        "Higher = fewer false negatives, slower to react when a chunk starts changing again.")
                .translation(T + "silentChunkTracker.ticks")
                .defineInRange("silentChunkTicks", 20, 5, 200);

        client.pop();

        client.comment(
                "Diagnostics.",
                "The bottleneck detector measures how much of each frame is spent on the CPU",
                "versus waiting for the GPU, so you can see which one is actually limiting you."
        ).push("diagnostics");

        BOTTLENECK_DETECTION = client
                .comment("Measure CPU time and GPU wait time per frame to identify the real bottleneck.")
                .translation(T + "diagnostics.bottleneck")
                .define("bottleneckDetection", true);

        SHOW_OVERLAY = client
                .comment("Draw a compact live performance overlay in the top left corner.")
                .translation(T + "diagnostics.overlay")
                .define("showOverlay", false);

        STATS_LOG_INTERVAL = client
                .comment("Write a performance summary to latest.log every N seconds. 0 disables it.")
                .translation(T + "diagnostics.logInterval")
                .defineInRange("statsLogIntervalSeconds", 0, 0, 3600);

        client.pop();

        CLIENT_SPEC = client.build();
    }

    
    
    

    public static boolean isEnabled() {
        return get(ENABLED, true);
    }

    public static boolean isVulcanMode() {
        return get(VULCAN_MODE, false);
    }

    public static boolean isDebug() {
        return get(DEBUG_LOGGING, false);
    }

    public static boolean isRenderOptimizationEnabled() {
        return isEnabled() && get(RENDER_OPTIMIZATION, true);
    }

    private static boolean get(ModConfigSpec.BooleanValue value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return value.get();
        } catch (IllegalStateException e) {
            
            return fallback;
        }
    }

    public static int getInt(ModConfigSpec.IntValue value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static double getDouble(ModConfigSpec.DoubleValue value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static boolean getBool(ModConfigSpec.BooleanValue value, boolean fallback) {
        return get(value, fallback);
    }
}
