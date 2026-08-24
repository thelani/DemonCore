package com.lani.demoncore.event;

import com.lani.demoncore.chunk.ChunkPreLoader;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.safety.EntityUnloadPrevention;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class VehicleEventHandler {
    
    private final ChunkPreLoader chunkLoader;
    private int tickCounter = 0;
    
    public VehicleEventHandler(ChunkPreLoader chunkLoader) {
        this.chunkLoader = chunkLoader;
    }
    
    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();

        if (entity.level().isClientSide) {
            return;
        }

        boolean isVehicle = entity.isVehicle();
        boolean hasPlayerPassenger = false;
        
        if (isVehicle) {
            for (Entity passenger : entity.getPassengers()) {
                if (passenger instanceof net.minecraft.world.entity.player.Player) {
                    hasPlayerPassenger = true;
                    break;
                }
            }
        }

        if (!hasPlayerPassenger) {
            return;
        }

        EntityUnloadPrevention.preventUnload(entity);

        double speed = entity.getDeltaMovement().length() * 20.0;
        double threshold = DemonCoreConfig.SPEED_THRESHOLD.get();

        if (speed < threshold) {
            return;
        }

        chunkLoader.loadChunks(entity, speed);
        tickCounter++;
    }
}
