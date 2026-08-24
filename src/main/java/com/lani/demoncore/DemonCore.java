package com.lani.demoncore;

import com.lani.demoncore.chunk.ChunkPreLoader;
import com.lani.demoncore.command.DemonCoreCommand;
import com.lani.demoncore.compat.SodiumCompat;
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
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
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

        modContainer.registerConfig(ModConfig.Type.COMMON, DemonCoreConfig.SPEC);

        this.chunkLoader = new ChunkPreLoader();

        modEventBus.addListener(this::commonSetup);
        
        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(this::clientSetup);
        }
        
        NeoForge.EVENT_BUS.register(new VehicleEventHandler(chunkLoader));
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        DemonCoreCommand.register(event.getDispatcher());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("DemonCore initialized");
        
        if (DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            PerformanceMonitor.setTargetFps(DemonCoreConfig.TARGET_FPS.get());
        }
        
        if (DemonCoreConfig.isVulcanMode()) {
            LOGGER.warn("VULCAN MODE active");
        }
    }
    
    private void clientSetup(final FMLClientSetupEvent event) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            return;
        }
        
        // Initialize client-only optimization systems
        RenderingOptimizer.init();
        MemoryAllocator.init();
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            chunkLoader.tick();
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        chunkLoader.shutdown();
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
