package com.lani.demoncore.command;

import com.lani.demoncore.DemonCore;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.optimization.PerformanceMonitor;
import com.lani.demoncore.optimization.ResourceManager;
import com.lani.demoncore.optimization.CacheSystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class DemonCoreCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("demoncore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                    .executes(DemonCoreCommand::showStatus)
                )
                .then(Commands.literal("stats")
                    .executes(DemonCoreCommand::showStats)
                )
                .then(Commands.literal("perf")
                    .executes(DemonCoreCommand::showPerformance)
                )
                .then(Commands.literal("vulcan")
                    .then(Commands.literal("enable")
                        .executes(DemonCoreCommand::enableVulcan)
                    )
                    .then(Commands.literal("disable")
                        .executes(DemonCoreCommand::disableVulcan)
                    )
                    .then(Commands.literal("status")
                        .executes(DemonCoreCommand::vulcanStatus)
                    )
                )
                .executes(DemonCoreCommand::showHelp)
        );
    }
    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("demoncoreclient")
                .then(Commands.literal("status")
                    .executes(DemonCoreCommand::showStatus)
                )
                .then(Commands.literal("stats")
                    .executes(DemonCoreCommand::showStats)
                )
                .then(Commands.literal("perf")
                    .executes(DemonCoreCommand::showPerformance)
                )
                .then(Commands.literal("vulcan")
                    .then(Commands.literal("status")
                        .executes(DemonCoreCommand::vulcanStatus)
                    )
                )
                .executes(DemonCoreCommand::showHelp)
        );
    }
    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        int maxChunks = DemonCoreConfig.MAX_CHUNKS.get();
        double threshold = DemonCoreConfig.SPEED_THRESHOLD.get();
        boolean debug = DemonCoreConfig.ENABLE_DEBUG.get();
        boolean optimization = DemonCoreConfig.ENABLE_OPTIMIZATION.get();
        boolean cache = DemonCoreConfig.ENABLE_CACHE.get();
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "demoncore.command.status",
            threshold,
            maxChunks,
            Component.translatable(optimization ? "demoncore.command.enabled" : "demoncore.command.disabled"),
            Component.translatable(cache ? "demoncore.command.enabled" : "demoncore.command.disabled"),
            DemonCoreConfig.TARGET_FPS.get(),
            DemonCoreConfig.RESOURCE_BALANCE.get(),
            Component.translatable(debug ? "demoncore.command.on" : "demoncore.command.off")
        ), false);
        return 1;
    }
    private static int showStats(CommandContext<CommandSourceStack> ctx) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                "demoncore.command.stats.disabled"
            ), false);
            return 1;
        }
        String loaderStats = DemonCore.getInstance().getChunkLoader().getStats();
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "demoncore.command.stats", loaderStats
        ), false);
        return 1;
    }
    private static int showPerformance(CommandContext<CommandSourceStack> ctx) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                "demoncore.command.perf.disabled"
            ), false);
            return 1;
        }
        String perfStats = PerformanceMonitor.getDetailedStats();
        String resourceStats = ResourceManager.getStats();
        String cacheStats = DemonCoreConfig.ENABLE_CACHE.get() ? 
            CacheSystem.getStats() : Component.translatable("demoncore.command.cache.disabled").getString();
        String advancedStats = "\n" + com.lani.demoncore.optimization.GCStutterGuard.getStats() +
                               "\n" + com.lani.demoncore.optimization.FrameSpikeProtector.getStats() +
                               "\n" + com.lani.demoncore.optimization.AdaptiveTickBudget.getStats() +
                               "\n" + com.lani.demoncore.optimization.RenderRegionCoalescer.getStats() +
                               "\n" + com.lani.demoncore.optimization.DimensionChangeOptimizer.getStats();
        String gpuOptStats = "\n\n-- GPU/RAM OPTIMIZATIONS --" +
                               "\n" + com.lani.demoncore.optimization.GeometryCache.getStats() +
                               "\n" + com.lani.demoncore.optimization.VisibilityLattice.getStats() +
                               "\n" + com.lani.demoncore.optimization.EntityLODSystem.getStats() +
                               "\n" + com.lani.demoncore.optimization.BatchRenderCoordinator.getStats() +
                               "\n\n-- UNIQUE DEMONCORE FEATURES --" +
                               "\n" + com.lani.demoncore.optimization.GpuRamBalancer.getStats() +
                               "\n" + com.lani.demoncore.optimization.PredictiveFrameScheduler.getStats() +
                               "\n" + com.lani.demoncore.optimization.SilentChunkTracker.getStats() +
                               "\n" + com.lani.demoncore.optimization.BottleneckDetector.getStats();
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "demoncore.command.perf", perfStats, resourceStats, cacheStats, advancedStats + gpuOptStats
        ), false);
        return 1;
    }
    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "demoncore.command.help"
        ), false);
        return 1;
    }
    private static int enableVulcan(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getServer() == null || !ctx.getSource().getServer().isSingleplayer()) {
            ctx.getSource().sendFailure(Component.translatable("demoncore.command.vulcan.singleplayer_only"));
            return 0;
        }

        if (DemonCoreConfig.isVulcanMode()) {
            ctx.getSource().sendFailure(Component.translatable("demoncore.command.vulcan.already_enabled"));
            return 0;
        }
        DemonCoreConfig.VULCAN_MODE.set(true);
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "demoncore.command.vulcan.enabled"
        ), true);
        return 1;
    }
    private static int disableVulcan(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getServer() == null || !ctx.getSource().getServer().isSingleplayer()) {
            ctx.getSource().sendFailure(Component.translatable("demoncore.command.vulcan.singleplayer_only"));
            return 0;
        }

        if (!DemonCoreConfig.isVulcanMode()) {
            ctx.getSource().sendFailure(Component.translatable("demoncore.command.vulcan.already_disabled"));
            return 0;
        }
        DemonCoreConfig.VULCAN_MODE.set(false);
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "demoncore.command.vulcan.disabled"
        ), false);
        return 1;
    }
    private static int vulcanStatus(CommandContext<CommandSourceStack> ctx) {
        boolean vulcanActive = DemonCoreConfig.isVulcanMode();
        if (vulcanActive) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                "demoncore.command.vulcan.status.active"
            ), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                "demoncore.command.vulcan.status.disabled"
            ), false);
        }
        return 1;
    }
}
