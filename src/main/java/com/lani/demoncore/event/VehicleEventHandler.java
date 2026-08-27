package com.lani.demoncore.event;

import com.lani.demoncore.chunk.ChunkPreLoader;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.optimization.SmartChunkCalculator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class VehicleEventHandler {

    private final ChunkPreLoader chunkLoader;

    public VehicleEventHandler(ChunkPreLoader chunkLoader) {
        this.chunkLoader = chunkLoader;
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!DemonCoreConfig.isEnabled()
                || !DemonCoreConfig.getBool(DemonCoreConfig.CHUNK_LOADING_ENABLED, true)) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }

        Entity source = player.getVehicle() != null ? player.getVehicle() : player;
        double speed = SmartChunkCalculator.toBlocksPerSecond(source.getDeltaMovement());
        if (speed <= 0.0) {
            return;
        }

        chunkLoader.loadChunks(source, speed);
    }
}
