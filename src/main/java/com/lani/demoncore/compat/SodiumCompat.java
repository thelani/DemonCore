package com.lani.demoncore.compat;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@EventBusSubscriber(modid = "demoncore", bus = EventBusSubscriber.Bus.MOD)
public class SodiumCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(SodiumCompat.class);

    private static boolean sodiumLoaded = false;
    private static boolean sodiumChecked = false;
    private static Method chunkRenderMethod = null;

    static {
        if (ModList.get().isLoaded("sodium")) {
            try {
                createSodiumConfigIfMissing();
                LOGGER.info("☢️ DemonCore: Sodium config checked during initialization");
            } catch (Exception e) {
                LOGGER.error("☢️ DemonCore: Failed to create Sodium config during initialization!", e);
            }
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("sodium")) {
            try {
                createSodiumConfigIfMissing();
            } catch (Exception e) {
                LOGGER.error("☢️ Failed to create Sodium config!", e);
            }
        }
    }

    private static void createSodiumConfigIfMissing() {
        try {
            Path configDir = Paths.get("config");
            Path sodiumConfig = configDir.resolve("sodium-options.json");

            if (!Files.exists(sodiumConfig)) {
                LOGGER.info("☢️ Creating default Sodium config...");

                Files.createDirectories(configDir);

                String defaultConfig = """
                {
                  "quality": {
                    "weather_quality": "DEFAULT",
                    "leaves_quality": "DEFAULT",
                    "enable_vignette": true,
                    "clouds_quality": "DEFAULT"
                  },
                  "advanced": {
                    "arena_memory_allocator": "ASYNC",
                    "allow_direct_memory_access": true,
                    "enable_memory_tracing": false,
                    "use_advanced_staging_buffers": true,
                    "cpu_render_ahead_limit": 3
                  },
                  "performance": {
                    "chunk_builder_threads": 0,
                    "always_defer_chunk_updates": false,
                    "animate_only_visible_textures": true,
                    "use_entity_culling": true,
                    "use_particle_culling": true,
                    "use_fog_occlusion": true,
                    "use_block_face_culling": true
                  },
                  "notifications": {
                    "hide_donation_button": false
                  }
                }
                """;

                Files.writeString(sodiumConfig, defaultConfig);
                LOGGER.info("☢️ Sodium config created successfully!");
            }
        } catch (Exception e) {
            LOGGER.error("☢️ Failed to create Sodium config!", e);
        }
    }

    public static boolean isSodiumLoaded() {
        if (!sodiumChecked) {
            try {

                Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod");
                sodiumLoaded = true;
                LOGGER.info("☢ Sodium detected! Enabling Sodium optimizations");
                initializeSodiumIntegration();
            } catch (ClassNotFoundException e) {
                sodiumLoaded = false;
                LOGGER.info("☢ Sodium not found, using vanilla chunk loading");
            }
            sodiumChecked = true;
        }
        return sodiumLoaded;
    }

    private static void initializeSodiumIntegration() {
        if (!DemonCoreConfig.SODIUM_OPTIMIZATION.get()) {
            LOGGER.info("☢ Sodium optimization disabled in config");
            return;
        }

        try {

            Class<?> renderManagerClass = Class.forName(
                "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager"
            );

            chunkRenderMethod = renderManagerClass.getMethod("scheduleRebuild", int.class, int.class, boolean.class);

            LOGGER.info("☢ Sodium integration initialized successfully");
        } catch (Exception e) {
            LOGGER.info("☢ Sodium API changed - advanced integration unavailable (this is OK, basic optimization still works)");
            LOGGER.debug("☢ Technical details: {}", e.getMessage());
            chunkRenderMethod = null;
        }
    }

    public static boolean scheduleChunkRender(ChunkPos pos) {
        if (!isSodiumLoaded() || chunkRenderMethod == null) {
            return false;
        }

        try {

            chunkRenderMethod.invoke(null, pos.x, pos.z, true);
            return true;
        } catch (Exception e) {

            return false;
        }
    }

    public static boolean isOptimizationEnabled() {
        return isSodiumLoaded() &&
               DemonCoreConfig.SODIUM_OPTIMIZATION.get() &&
               chunkRenderMethod != null;
    }

    public static void optimizeChunkRender(ChunkPos pos) {
        if (!isOptimizationEnabled()) {
            return;
        }

        try {

            scheduleChunkRender(pos);
        } catch (Exception e) {

        }
    }

    public static void optimizeChunkRenderBatch(Iterable<ChunkPos> chunks) {
        if (!isOptimizationEnabled()) {
            return;
        }

        for (ChunkPos pos : chunks) {
            optimizeChunkRender(pos);
        }
    }

    public static int getOptimalChunkDistance(int baseDistance) {
        if (!isSodiumLoaded()) {
            return baseDistance;
        }

        return (int) (baseDistance * 1.5);
    }

    public static int getOptimalCacheSize(int baseSize) {
        if (!isSodiumLoaded()) {
            return baseSize;
        }

        return (int) (baseSize * 1.3);
    }

    public static String getStats() {
        if (!sodiumChecked) {
            return "Not checked";
        }

        if (!sodiumLoaded) {
            return "Sodium not loaded (vanilla mode)";
        }

        if (!DemonCoreConfig.SODIUM_OPTIMIZATION.get()) {
            return "Sodium loaded but optimization disabled";
        }

        if (chunkRenderMethod == null) {
            return "Sodium loaded but integration failed";
        }

        return "Sodium optimization ACTIVE";
    }
}
