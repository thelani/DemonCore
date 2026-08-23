package com.lani.demoncore.event;

import com.lani.demoncore.chunk.ChunkPreLoader;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.detection.SpeedTracker;
import com.lani.demoncore.detection.VehicleDetector;
import com.lani.demoncore.safety.EntityUnloadPrevention;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class VehicleEventHandler {
    
    private final ChunkPreLoader chunkLoader;
    private final SpeedTracker speedTracker;
    private int tickCounter = 0;
    
    public VehicleEventHandler(ChunkPreLoader chunkLoader) {
        this.chunkLoader = chunkLoader;
        this.speedTracker = new SpeedTracker();
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

        double speed = speedTracker.updateSpeed(entity);
        double threshold = DemonCoreConfig.SPEED_THRESHOLD.get();

        if (speed < threshold) {
            return;
        }

        chunkLoader.loadChunks(entity, speed);

        if (DemonCoreConfig.ENABLE_DEBUG.get() && tickCounter % 100 == 0) {
            com.lani.demoncore.DemonCore.LOGGER.info(
                "Loading chunks for vehicle at {} m/s - Entity: {} - Passengers: {}",
                String.format("%.0f", speed),
                entity.getClass().getSimpleName(),
                entity.getPassengers().size()
            );
        }
        
        tickCounter++;
    }
    
    public SpeedTracker getSpeedTracker() {
        return speedTracker;
    }
}
