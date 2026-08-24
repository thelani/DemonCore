package com.lani.demoncore.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class DemonCoreConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue SPEED_THRESHOLD;
    public static final ModConfigSpec.IntValue MAX_CHUNKS;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG;

    public static final ModConfigSpec.BooleanValue ENABLE_OPTIMIZATION;
    public static final ModConfigSpec.IntValue TARGET_FPS;
    public static final ModConfigSpec.BooleanValue AGGRESSIVE_MODE;
    public static final ModConfigSpec.ConfigValue<String> RESOURCE_BALANCE;

    public static final ModConfigSpec.BooleanValue ENABLE_CACHE;
    public static final ModConfigSpec.IntValue CACHE_SIZE;

    public static final ModConfigSpec.BooleanValue AUTO_GC;
    public static final ModConfigSpec.DoubleValue RAM_THRESHOLD;
    public static final ModConfigSpec.DoubleValue CPU_THRESHOLD;

    public static final ModConfigSpec.BooleanValue ENABLE_VISIBILITY_TIMEOUT;
    public static final ModConfigSpec.IntValue VISIBILITY_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue VISIBILITY_CHECK_INTERVAL;

    public static final ModConfigSpec.BooleanValue PREVENT_CHUNK_UNLOAD;

    public static final ModConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ModConfigSpec.BooleanValue SODIUM_OPTIMIZATION;

    public static final ModConfigSpec.BooleanValue AGGRESSIVE_GPU_OPTIMIZATION;
    public static final ModConfigSpec.BooleanValue DYNAMIC_RENDER_DISTANCE;
    public static final ModConfigSpec.IntValue MIN_RENDER_DISTANCE;

    public static final ModConfigSpec.BooleanValue AGGRESSIVE_RAM_ALLOCATION;
    public static final ModConfigSpec.DoubleValue TARGET_RAM_USAGE;

    public static final ModConfigSpec.BooleanValue VULCAN_MODE;

    /**
     * Check if VULCAN MODE is active
     * VULCAN MODE removes all safety limits for maximum performance
     */
    public static boolean isVulcanMode() {
        return VULCAN_MODE != null && VULCAN_MODE.get();
    }

    static {

        BUILDER.comment("=================================")
               .comment("  DemonCore - Performance Optimization")
               .comment("=================================")
               .push("basic");

        SPEED_THRESHOLD = BUILDER
            .comment("Minimum movement threshold (blocks/s) to activate chunk loading")
            .comment("0.0 = ALWAYS ACTIVE (no threshold)")
            .defineInRange("activationThreshold", 0.0, 0.0, 1000.0);

        MAX_CHUNKS = BUILDER
            .comment("Maximum chunks to preload")
            .comment("Higher values = more aggressive loading")
            .defineInRange("maxChunks", 256, 4, 512);

        ENABLE_DEBUG = BUILDER
            .comment("Enable debug logging to latest.log")
            .define("enableDebug", false);

        BUILDER.pop();

        BUILDER.comment("")
               .comment("Performance Optimization")
               .push("optimization");

        ENABLE_OPTIMIZATION = BUILDER
            .comment("Enable smart optimization system")
            .define("enableOptimization", true);

        TARGET_FPS = BUILDER
            .comment("Target FPS for optimization")
            .defineInRange("targetFPS", 58, 30, 240);

        AGGRESSIVE_MODE = BUILDER
            .comment("Aggressive optimization mode - ACTIVE")
            .define("aggressiveMode", true);

        RESOURCE_BALANCE = BUILDER
            .comment("Resource balance strategy")
            .comment("Options: AUTO, RAM_SAVING, CPU_SAVING, BALANCED, MINIMAL, AGGRESSIVE")
            .define("resourceBalance", "AGGRESSIVE");

        BUILDER.pop();

        BUILDER.comment("")
               .comment("Cache System")
               .push("cache");

        ENABLE_CACHE = BUILDER
            .comment("Enable intelligent caching system")
            .define("enableCache", true);

        CACHE_SIZE = BUILDER
            .comment("Cache size for optimal performance")
            .defineInRange("cacheSize", 600, 10, 2000);

        BUILDER.pop();

        BUILDER.comment("")
               .comment("Performance Thresholds")
               .push("performance");

        AUTO_GC = BUILDER
            .comment("Automatic garbage collection")
            .define("autoGC", true);

        RAM_THRESHOLD = BUILDER
            .comment("RAM usage threshold (0.0-1.0)")
            .defineInRange("ramThreshold", 0.85, 0.5, 0.95);

        CPU_THRESHOLD = BUILDER
            .comment("CPU usage threshold (0.0-1.0)")
            .defineInRange("cpuThreshold", 0.85, 0.5, 0.95);

        BUILDER.pop();

        BUILDER.comment("")
               .comment("Chunk Visibility System")
               .comment("Unload invisible chunks after timeout")
               .push("visibility");

        ENABLE_VISIBILITY_TIMEOUT = BUILDER
            .comment("Enable chunk visibility timeout")
            .comment("Unload chunks that are not visible for X seconds")
            .define("enableTimeout", true);

        VISIBILITY_TIMEOUT_SECONDS = BUILDER
            .comment("Timeout in seconds")
            .defineInRange("timeoutSeconds", 40, 5, 120);

        VISIBILITY_CHECK_INTERVAL = BUILDER
            .comment("Check interval in ticks (100 ticks = 5 seconds)")
            .defineInRange("checkInterval", 100, 20, 200);

        PREVENT_CHUNK_UNLOAD = BUILDER
            .comment("Prevent chunk unloading for moving entities")
            .comment("Keeps chunks loaded to prevent issues")
            .define("preventChunkUnload", true);

        BUILDER.pop();

        BUILDER.comment("")
               .comment("Chunk Loading Speed")
               .push("loading");

        CHUNKS_PER_TICK = BUILDER
            .comment("Chunks per tick loading rate")
            .comment("Default: 20, Maximum: 80")
            .defineInRange("chunksPerTick", 20, 3, 80);

        SODIUM_OPTIMIZATION = BUILDER
            .comment("Sodium optimization mode")
            .comment("Uses Sodium's render pipeline for better performance")
            .define("sodiumOptimization", true);
        
        BUILDER.pop();

        BUILDER.comment("")
               .comment("Sodium Maximum Chunk Distance")
               .push("sodium");

        BUILDER.comment("Sodium chunk render distance increased from 32 to 128")
               .comment("This is applied automatically via mixins")
               .comment("No manual configuration needed!");

        BUILDER.pop();

        BUILDER.comment("")
               .comment("GPU & Rendering Optimization")
               .comment("FIX: GPU bottleneck at high speeds")
               .push("gpu");

        AGGRESSIVE_GPU_OPTIMIZATION = BUILDER
            .comment("Aggressive GPU optimization")
            .comment("Reduces GPU load during heavy chunk generation")
            .define("aggressiveGPU", true);

        DYNAMIC_RENDER_DISTANCE = BUILDER
            .comment("Dynamic render distance based on load")
            .comment("Automatically reduces render distance under heavy load")
            .define("dynamicRenderDistance", true);

        MIN_RENDER_DISTANCE = BUILDER
            .comment("Minimum render distance under load")
            .comment("Lower = better performance, less visibility")
            .defineInRange("minRenderDistance", 6, 2, 16);

        BUILDER.pop();

        BUILDER.comment("")
               .comment("RAM Allocation & Management")
               .comment("FIX: Only 3GB used out of 8GB available")
               .push("ram");

        AGGRESSIVE_RAM_ALLOCATION = BUILDER
            .comment("Aggressive RAM allocation")
            .comment("Uses MORE RAM for chunk caching")
            .comment("FIXES: Low RAM usage (3GB/8GB)")
            .define("aggressiveRAM", true);

        TARGET_RAM_USAGE = BUILDER
            .comment("Target RAM usage (0.0-1.0)")
            .comment("0.6 = Use 60% of available RAM (4.8GB/8GB)")
            .comment("Higher = More cache, better performance")
            .defineInRange("targetRAMUsage", 0.6, 0.3, 0.85);

        BUILDER.pop();

        BUILDER.comment("")
               .comment("VULCAN MODE - EXPERIMENTAL")
               .comment("⚠️  WARNING: Removes all safety limits!")
               .comment("Only enable if you know what you're doing")
               .push("vulcan");

        VULCAN_MODE = BUILDER
            .comment("VULCAN MODE - Maximum performance, no limits")
            .comment("⚠️  EXPERIMENTAL: May cause instability")
            .comment("This mode:")
            .comment("  - Disables chunk unload prevention limits")
            .comment("  - Maximizes chunk loading rate")
            .comment("  - Ignores performance thresholds")
            .comment("  - Pushes hardware to maximum capacity")
            .define("vulcanMode", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
