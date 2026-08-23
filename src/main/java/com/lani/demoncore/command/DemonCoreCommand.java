package com.lani.demoncore.command;

import com.lani.demoncore.DemonCore;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.optimization.PerformanceMonitor;
import com.lani.demoncore.optimization.ResourceManager;
import com.lani.demoncore.optimization.CacheSystem;
import com.lani.demoncore.safety.VoidProtection;
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
            "§6Speed Threshold: §f" + threshold + " m/s\n" +
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
        String voidStats = VoidProtection.getStats();
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§c☢ DemonCore Statistics:\n" +
            "§f" + loaderStats + "\n" +
            "§f" + voidStats
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
            "§7Edit config/demoncore-common.toml to change settings"
        ), false);
        
        return 1;
    }
}
