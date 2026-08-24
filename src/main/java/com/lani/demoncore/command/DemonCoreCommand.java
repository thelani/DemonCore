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
    
    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        int maxChunks = DemonCoreConfig.MAX_CHUNKS.get();
        double threshold = DemonCoreConfig.SPEED_THRESHOLD.get();
        boolean debug = DemonCoreConfig.ENABLE_DEBUG.get();
        boolean optimization = DemonCoreConfig.ENABLE_OPTIMIZATION.get();
        boolean cache = DemonCoreConfig.ENABLE_CACHE.get();
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§c☢ DemonCore Status:\n" +
            "§6Activation Threshold: §f" + threshold + " b/s\n" +
            "§6Max Chunks: §f" + maxChunks + "\n" +
            "§6Optimization: §f" + (optimization ? "§aENABLED" : "§cDISABLED") + "\n" +
            "§6Cache System: §f" + (cache ? "§aENABLED" : "§cDISABLED") + "\n" +
            "§6Target FPS: §f" + DemonCoreConfig.TARGET_FPS.get() + "\n" +
            "§6Resource Balance: §f" + DemonCoreConfig.RESOURCE_BALANCE.get() + "\n" +
            "§6Debug: §f" + (debug ? "ON" : "OFF")
        ), false);
        
        return 1;
    }
    
    private static int showStats(CommandContext<CommandSourceStack> ctx) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "§c☢ Statistics unavailable - optimization disabled\n" +
                "§7Enable optimization in config to see stats"
            ), false);
            return 1;
        }
        
        String loaderStats = DemonCore.getInstance().getChunkLoader().getStats();
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§c☢ DemonCore Statistics:\n" +
            "§f" + loaderStats
        ), false);
        
        return 1;
    }
    
    private static int showPerformance(CommandContext<CommandSourceStack> ctx) {
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "§c☢ Performance monitoring unavailable - optimization disabled"
            ), false);
            return 1;
        }
        
        String perfStats = PerformanceMonitor.getDetailedStats();
        String resourceStats = ResourceManager.getStats();
        String cacheStats = DemonCoreConfig.ENABLE_CACHE.get() ? 
            CacheSystem.getStats() : "Cache disabled";
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§c☢ Performance Monitoring:\n" +
            "§6System: §f" + perfStats + "\n" +
            "§6Resources: §f" + resourceStats + "\n" +
            "§6Cache: §f" + cacheStats
        ), false);
        
        return 1;
    }
    
    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§c☢ DemonCore Commands:\n" +
            "§6/demoncore status §7- Show configuration\n" +
            "§6/demoncore stats §7- Show chunk loading stats\n" +
            "§6/demoncore perf §7- Show performance monitoring\n" +
            "§6/demoncore vulcan <enable|disable|status> §7- VULCAN MODE control\n" +
            "§7Edit config/demoncore-common.toml to change settings"
        ), false);
        
        return 1;
    }
    
    private static int enableVulcan(CommandContext<CommandSourceStack> ctx) {
        if (DemonCoreConfig.isVulcanMode()) {
            ctx.getSource().sendFailure(Component.literal(
                "§c⚠️  VULCAN MODE is already enabled!"
            ));
            return 0;
        }
        
        DemonCoreConfig.VULCAN_MODE.set(true);
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§c☢ ========================================\n" +
            "§c⚠️  VULCAN MODE ENABLED ⚠️\n" +
            "§c☢ ========================================\n" +
            "§6All safety limits have been removed!\n" +
            "§6Active effects:\n" +
            "§f  - 2X chunk loading capacity\n" +
            "§f  - 3X chunks per tick\n" +
            "§f  - Performance warnings ignored\n" +
            "§f  - Infinite chunk ticket lifetime\n" +
            "§c⚠️  WARNING: May cause crashes or void falls!\n" +
            "§7Use §6/demoncore vulcan disable §7to turn off"
        ), true);
        
        return 1;
    }
    
    private static int disableVulcan(CommandContext<CommandSourceStack> ctx) {
        if (!DemonCoreConfig.isVulcanMode()) {
            ctx.getSource().sendFailure(Component.literal(
                "§cVULCAN MODE is already disabled"
            ));
            return 0;
        }
        
        DemonCoreConfig.VULCAN_MODE.set(false);
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§c☢ VULCAN MODE disabled\n" +
            "§aSafety limits restored\n" +
            "§7Normal operation resumed"
        ), false);
        
        return 1;
    }
    
    private static int vulcanStatus(CommandContext<CommandSourceStack> ctx) {
        boolean vulcanActive = DemonCoreConfig.isVulcanMode();
        
        if (vulcanActive) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "§c☢ VULCAN MODE: §4§lACTIVE\n" +
                "§c⚠️  All safety limits removed!\n" +
                "§6Active multipliers:\n" +
                "§f  - Chunk capacity: §e2X\n" +
                "§f  - Load speed: §e3X\n" +
                "§f  - Performance checks: §cDISABLED\n" +
                "§7Use §6/demoncore vulcan disable §7to restore limits"
            ), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "§c☢ VULCAN MODE: §aDISABLED\n" +
                "§aSafety limits active\n" +
                "§7Use §6/demoncore vulcan enable §7to remove limits\n" +
                "§7§oWarning: Only for experienced users!"
            ), false);
        }
        
        return 1;
    }
}
