package com.lani.demoncore.safety;

import com.lani.demoncore.chunk.ChunkPreLoader;
import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EntityUnloadPrevention {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityUnloadPrevention.class);
    private static final Map<Integer, Long> protectedEntities = new ConcurrentHashMap<>();
    private static final long PROTECTION_DURATION = 5000; // 5 seconds

    public static void preventUnload(Entity entity) {
        if (!DemonCoreConfig.PREVENT_CHUNK_UNLOAD.get()) {
            return;
        }

        if (entity.level() instanceof ServerLevel serverLevel) {
            protectedEntities.put(entity.getId(), System.currentTimeMillis());

            ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
            ChunkPreLoader.forceLoadChunk(serverLevel, chunkPos);

            // Load surrounding chunks
            int radius = 2;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    ChunkPos nearbyChunk = new ChunkPos(chunkPos.x + x, chunkPos.z + z);
                    ChunkPreLoader.forceLoadChunk(serverLevel, nearbyChunk);
                }
            }

            LOGGER.trace("Entity {} protected from unload at chunk {}", entity.getId(), chunkPos);
        }
    }

    public static boolean isProtected(Entity entity) {
        Long protectionTime = protectedEntities.get(entity.getId());
        if (protectionTime == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - protectionTime;
        if (elapsed > PROTECTION_DURATION) {
            protectedEntities.remove(entity.getId());
            return false;
        }

        return true;
    }

    public static void removeProtection(Entity entity) {
        protectedEntities.remove(entity.getId());
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        protectedEntities.entrySet().removeIf(entry -> 
            (now - entry.getValue()) > PROTECTION_DURATION
        );
    }
}
