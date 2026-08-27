package com.lani.demoncore;

import com.lani.demoncore.chunk.ChunkPreLoader;
import com.lani.demoncore.command.DemonCoreCommand;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.event.VehicleEventHandler;
import com.lani.demoncore.optimization.DimensionChangeOptimizer;
import com.lani.demoncore.optimization.GCStutterGuard;
import com.lani.demoncore.optimization.PerformanceMonitor;
import com.lani.demoncore.optimization.TickThrottleSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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

        modContainer.registerConfig(ModConfig.Type.COMMON, DemonCoreConfig.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, DemonCoreConfig.CLIENT_SPEC);

        this.chunkLoader = new ChunkPreLoader();

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(new VehicleEventHandler(chunkLoader));
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(this::onEntityTick);
        NeoForge.EVENT_BUS.addListener(this::onEntityLeave);

        if (FMLEnvironment.dist.isClient()) {
            ClientBootstrap.register(modContainer, modEventBus);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        GCStutterGuard.init();
        LOGGER.info("DemonCore ready. Chunk pre-loading {}, tick throttling {}.",
                DemonCoreConfig.getBool(DemonCoreConfig.CHUNK_LOADING_ENABLED, true) ? "on" : "off",
                DemonCoreConfig.getBool(DemonCoreConfig.TICK_THROTTLE_ENABLED, true) ? "on" : "off");

        if (DemonCoreConfig.isVulcanMode()) {
            LOGGER.warn("VULCAN MODE is enabled - DemonCore safety limits are disabled.");
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        DemonCoreCommand.register(event.getDispatcher());
    }

    private void onServerTickPre(ServerTickEvent.Pre event) {
        PerformanceMonitor.onServerTickStart();
    }

    private void onServerTickPost(ServerTickEvent.Post event) {
        PerformanceMonitor.onServerTickEnd();
        chunkLoader.tick();
    }

    private void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        chunkLoader.trackEntity(event.getEntity());
    }

    private void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (TickThrottleSystem.shouldSkip(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide) {
            chunkLoader.cleanup(event.getEntity().getUUID());
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        chunkLoader.shutdown();
        PerformanceMonitor.reset();
    }

    public static DemonCore getInstance() {
        return instance;
    }

    public ChunkPreLoader getChunkLoader() {
        return chunkLoader;
    }

    
    private static final class ClientBootstrap {

        private static ResourceKey<Level> lastDimension;
        private static long lastGpuBalancerTickMs;

        static void register(ModContainer modContainer, IEventBus modEventBus) {
            modContainer.registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (minecraft, parent) -> new com.lani.demoncore.client.gui.DemonCoreConfigScreen(parent));

            NeoForge.EVENT_BUS.addListener(ClientBootstrap::onClientTick);
            NeoForge.EVENT_BUS.addListener(ClientBootstrap::onRegisterClientCommands);
            NeoForge.EVENT_BUS.addListener(ClientBootstrap::onRenderGui);
        }

        private static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return;
            }

            GCStutterGuard.sample();
            com.lani.demoncore.optimization.SilentChunkTracker.tick();

            
            long now = System.currentTimeMillis();
            if (now - lastGpuBalancerTickMs >= 500L) {
                lastGpuBalancerTickMs = now;
                com.lani.demoncore.optimization.GpuRamBalancer.evaluate();
                com.lani.demoncore.optimization.GeometryCache.trimStale();
            }

            ResourceKey<Level> dimension = mc.level.dimension();
            if (lastDimension != null && !lastDimension.equals(dimension)) {
                DimensionChangeOptimizer.onDimensionChange(
                        lastDimension.location().toString(), dimension.location().toString());
            }
            lastDimension = dimension;
        }

        private static void onRegisterClientCommands(
                net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
            DemonCoreCommand.registerClient(event.getDispatcher());
        }

        private static void onRenderGui(
                net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
            com.lani.demoncore.client.PerformanceOverlay.render(event.getGuiGraphics());
        }
    }
}
