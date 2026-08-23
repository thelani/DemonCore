package com.lani.demoncore;

import com.lani.demoncore.chunk.ChunkPreLoader;
import com.lani.demoncore.command.DemonCoreCommand;
import com.lani.demoncore.compat.SodiumCompat;
import com.lani.demoncore.compat.SableCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.event.VehicleEventHandler;
import com.lani.demoncore.optimization.PerformanceMonitor;
import com.lani.demoncore.optimization.ResourceManager;
import com.lani.demoncore.optimization.RenderingOptimizer;
import com.lani.demoncore.optimization.MemoryAllocator;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DemonCore.MOD_ID)
public class DemonCore {
    public static final String MOD_ID = "demoncore";
    public static final Logger LOGGER = LoggerFactory.getLogger("DemonCore");

    private static DemonCore instance;
    private final ChunkPreLoader chunkLoader;

    public DemonCore(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;

        LOGGER.info("☢ ========================================");
        LOGGER.info("☢  DemonCore - MAXIMUM Performance");
        LOGGER.info("☢  Always Active + Visibility + Sodium");
        LOGGER.info("☢ ========================================");

        modContainer.registerConfig(ModConfig.Type.COMMON, DemonCoreConfig.SPEC);

        this.chunkLoader = new ChunkPreLoader();

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(new VehicleEventHandler(chunkLoader));
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);

        LOGGER.info("☢ DemonCore initialized");
    }

    private void registerCommands(RegisterCommandsEvent event) {
        DemonCoreCommand.register(event.getDispatcher());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("☢ DemonCore Setup Complete");
        LOGGER.info("☢ Configuration:");
        LOGGER.info("  - Speed Threshold: {} m/s {}",
            DemonCoreConfig.SPEED_THRESHOLD.get(),
            DemonCoreConfig.SPEED_THRESHOLD.get() == 0.0 ? "(ALWAYS ACTIVE)" : "");
        LOGGER.info("  - Max Chunks: {} (MAXIMUM)", DemonCoreConfig.MAX_CHUNKS.get());
        LOGGER.info("  - Chunks Per Tick: {} (2x FASTER)", DemonCoreConfig.CHUNKS_PER_TICK.get());
        LOGGER.info("  - Optimization: {}", DemonCoreConfig.ENABLE_OPTIMIZATION.get() ? "ENABLED" : "DISABLED");
        LOGGER.info("  - Cache System: {}", DemonCoreConfig.ENABLE_CACHE.get() ? "ENABLED" : "DISABLED");
        LOGGER.info("  - Target FPS: {}", DemonCoreConfig.TARGET_FPS.get());
        LOGGER.info("  - Resource Balance: {}", DemonCoreConfig.RESOURCE_BALANCE.get());
        LOGGER.info("  - Aggressive Mode: {}", DemonCoreConfig.AGGRESSIVE_MODE.get() ? "YES" : "NO");

        LOGGER.info("☢ Chunk Visibility System:");
        LOGGER.info("  - Timeout: {} {}",
            DemonCoreConfig.ENABLE_VISIBILITY_TIMEOUT.get() ? "ENABLED" : "DISABLED",
            DemonCoreConfig.ENABLE_VISIBILITY_TIMEOUT.get() ?
                "(" + DemonCoreConfig.VISIBILITY_TIMEOUT_SECONDS.get() + "s)" : "");
        LOGGER.info("  - Check Interval: {} ticks", DemonCoreConfig.VISIBILITY_CHECK_INTERVAL.get());

        LOGGER.info("☢ Sodium Optimization:");
        if (DemonCoreConfig.SODIUM_OPTIMIZATION.get()) {
            boolean sodiumLoaded = SodiumCompat.isSodiumLoaded();
            LOGGER.info("  - Config: ENABLED");
            LOGGER.info("  - Status: {}", sodiumLoaded ? "ACTIVE ✓" : "Sodium not found (vanilla mode)");
            if (sodiumLoaded) {
                LOGGER.info("  - Integration: {}", SodiumCompat.isOptimizationEnabled() ? "SUCCESS" : "FAILED");
            }
        } else {
            LOGGER.info("  - Config: DISABLED");
        }

        LOGGER.info("☢ Sable Velocity Tracking:");
        SableCompat.init();
        if (SableCompat.isSableLoaded()) {
            LOGGER.info("  - Status: DETECTED ✓");
            LOGGER.info("  - Velocity tracking: ENABLED");
        } else {
            LOGGER.info("  - Status: NOT LOADED (vanilla velocity only)");
        }

        if (DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            LOGGER.info("☢ Advanced optimization systems active");
            LOGGER.info("  - CPU Cores: {}", ResourceManager.getCoreCount());
            LOGGER.info("  - Void Protection: ENABLED ✓");
            PerformanceMonitor.setTargetFps(DemonCoreConfig.TARGET_FPS.get());

            RenderingOptimizer.init();
            MemoryAllocator.init();
        }

        LOGGER.info("☢ ========================================");
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            chunkLoader.tick();
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("☢ DemonCore shutting down...");
        chunkLoader.shutdown();
        LOGGER.info("☢ DemonCore shutdown complete");
    }
    
    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (DemonCoreConfig.ENABLE_VISIBILITY_TIMEOUT.get() && event.getEntity().level().isClientSide) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                chunkLoader.tickClientSide(mc.level);
            }
        }
    }

    public static DemonCore getInstance() {
        return instance;
    }

    public ChunkPreLoader getChunkLoader() {
        return chunkLoader;
    }
}
