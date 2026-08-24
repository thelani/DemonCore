package com.lani.demoncore.visibility;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkVisibilityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkVisibilityManager.class);

    private final Map<ChunkPos, Long> lastVisibleTime = new ConcurrentHashMap<>();
    private final Set<ChunkPos> invisibleChunks = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Set<ChunkPos>> entityChunks = new ConcurrentHashMap<>();

    private int totalChecks = 0;
    private int totalUnloaded = 0;
    private long lastCheckTime = 0;

    public void markVisible(ChunkPos pos) {
        long now = System.currentTimeMillis();
        lastVisibleTime.put(pos, now);
        invisibleChunks.remove(pos);
    }

    public void markInvisible(ChunkPos pos) {
        invisibleChunks.add(pos);
    }

    public void registerEntityChunks(UUID entityId, Set<ChunkPos> chunks) {
        entityChunks.put(entityId, chunks);

        long now = System.currentTimeMillis();
        for (ChunkPos pos : chunks) {
            lastVisibleTime.put(pos, now);
        }
    }

    public void unregisterEntity(UUID entityId) {
        Set<ChunkPos> chunks = entityChunks.remove(entityId);
        if (chunks != null) {

            for (ChunkPos pos : chunks) {
                lastVisibleTime.remove(pos);
                invisibleChunks.remove(pos);
            }
        }
    }

    public void tick(ClientLevel level) {
        if (!DemonCoreConfig.ENABLE_VISIBILITY_TIMEOUT.get()) {
            return; // Kapalıysa kontrol etme
        }

        long now = System.currentTimeMillis();
        long checkInterval = DemonCoreConfig.VISIBILITY_CHECK_INTERVAL.get() * 50L; // tick to ms

        if (now - lastCheckTime < checkInterval) {
            return;
        }

        lastCheckTime = now;
        totalChecks++;

        long timeoutMs = DemonCoreConfig.VISIBILITY_TIMEOUT_SECONDS.get() * 1000L;

        Set<ChunkPos> toUnload = ConcurrentHashMap.newKeySet();

        for (Map.Entry<ChunkPos, Long> entry : lastVisibleTime.entrySet()) {
            ChunkPos pos = entry.getKey();
            long lastSeen = entry.getValue();

            if (now - lastSeen > timeoutMs) {

                if (!isChunkVisible(level, pos)) {
                    toUnload.add(pos);
                } else {

                    markVisible(pos);
                }
            }
        }

        if (!toUnload.isEmpty()) {
            unloadChunks(level, toUnload);
        }

        if (DemonCoreConfig.ENABLE_DEBUG.get() && totalChecks % 20 == 0) {
            LOGGER.debug("Visibility check #{}: {} chunks tracked, {} invisible, {} unloaded total",
                totalChecks, lastVisibleTime.size(), invisibleChunks.size(), totalUnloaded);
        }
    }

    private boolean isChunkVisible(ClientLevel level, ChunkPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        double chunkCenterX = pos.getMinBlockX() + 8;
        double chunkCenterZ = pos.getMinBlockZ() + 8;

        int renderDistance = mc.options.renderDistance().get() * 16;
        double distSq = Math.pow(chunkCenterX - playerX, 2) + Math.pow(chunkCenterZ - playerZ, 2);

        if (distSq > renderDistance * renderDistance) {
            return false; // Render distance dışında
        }

        double dx = chunkCenterX - playerX;
        double dz = chunkCenterZ - playerZ;
        double yaw = Math.toRadians(mc.player.getYRot());

        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);

        double dot = dx * lookX + dz * lookZ;

        return dot > -50.0; // Geniş açı (arkada bile bir süre tut)
    }

    private void unloadChunks(ClientLevel level, Set<ChunkPos> chunks) {
        for (ChunkPos pos : chunks) {
            try {

                level.getChunk(pos.x, pos.z).setUnsaved(false);

                lastVisibleTime.remove(pos);
                invisibleChunks.remove(pos);

                totalUnloaded++;

                if (DemonCoreConfig.ENABLE_DEBUG.get()) {
                    LOGGER.debug("Unloaded invisible chunk: {} (timeout)", pos);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to unload chunk {}: {}", pos, e.getMessage());
            }
        }
    }

    public boolean isVisible(ChunkPos pos) {
        return !invisibleChunks.contains(pos);
    }

    public boolean isTimedOut(ChunkPos pos) {
        Long lastSeen = lastVisibleTime.get(pos);
        if (lastSeen == null) return false;

        long now = System.currentTimeMillis();
        long timeoutMs = DemonCoreConfig.VISIBILITY_TIMEOUT_SECONDS.get() * 1000L;

        return (now - lastSeen) > timeoutMs;
    }

    public void clear() {
        lastVisibleTime.clear();
        invisibleChunks.clear();
        entityChunks.clear();
        totalChecks = 0;
        totalUnloaded = 0;
    }

    public String getStats() {
        return String.format(
            "Visibility: %d tracked, %d invisible, %d unloaded (checks: %d)",
            lastVisibleTime.size(),
            invisibleChunks.size(),
            totalUnloaded,
            totalChecks
        );
    }
}
