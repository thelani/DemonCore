package com.lani.demoncore.chunk;

import com.lani.demoncore.DemonCore;
import com.lani.demoncore.compat.SodiumCompat;
import com.lani.demoncore.compat.SableCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.optimization.*;
import com.lani.demoncore.visibility.ChunkVisibilityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkPreLoader {

    private static TicketType<ChunkPos> TICKET = null;
    private static TicketType<ChunkPos> PERSISTENT_TICKET = null;
    
    private static TicketType<ChunkPos> getTicket() {
        if (TICKET == null) {
            boolean preventUnload = DemonCoreConfig.PREVENT_CHUNK_UNLOAD != null && DemonCoreConfig.PREVENT_CHUNK_UNLOAD.get();
            TICKET = TicketType.create("demoncore", Comparator.comparingLong(ChunkPos::toLong), 
                preventUnload ? Integer.MAX_VALUE : 300);
        }
        return TICKET;
    }
    
    private static TicketType<ChunkPos> getPersistentTicket() {
        if (PERSISTENT_TICKET == null) {
            boolean preventUnload = DemonCoreConfig.PREVENT_CHUNK_UNLOAD != null && DemonCoreConfig.PREVENT_CHUNK_UNLOAD.get();
            PERSISTENT_TICKET = TicketType.create("demoncore_persistent", Comparator.comparingLong(ChunkPos::toLong), 
                preventUnload ? Integer.MAX_VALUE : 600);
        }
        return PERSISTENT_TICKET;
    }

    private final Map<UUID, Set<ChunkPos>> loadedChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ChunkPos>> persistentChunks = new ConcurrentHashMap<>(); // Arkadaki chunk'lar (unload edilmesin)
    private final Map<UUID, Long> lastLoadTime = new ConcurrentHashMap<>();

    private final ChunkVisibilityManager visibilityManager = new ChunkVisibilityManager();

    private int totalChunksLoaded = 0;
    private int totalLoadCalls = 0;

    public void loadChunks(Entity entity, double speed) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (SableCompat.isEntityInSubLevel(entity)) {
            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.01) {
                DemonCore.LOGGER.info("Entity {} is in SubLevel at {}, speed {} m/s - AGGRESSIVE LOADING", 
                    entity.getName().getString(), 
                    entity.position(),
                    String.format("%.0f", speed));
            }

            if (DemonCoreConfig.PREVENT_CHUNK_UNLOAD.get()) {

            }
        }

        totalLoadCalls++;
        UUID id = entity.getUUID();
        Vec3 motion = entity.getDeltaMovement();

        if (motion.lengthSqr() < 0.001) {
            return;
        }

        double threshold = DemonCoreConfig.SPEED_THRESHOLD.get();
        if (threshold > 0.0 && speed < threshold) {
            return; // Sadece threshold 0'dan büyükse kontrol et
        }

        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            loadChunksSimple(level, entity, speed, motion);
            return;
        }

        if (PerformanceMonitor.isCritical()) {
            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.1) {
                DemonCore.LOGGER.warn("Performance critical but loading chunks in MINIMAL mode to prevent void fall");
            }

        }

        long now = System.currentTimeMillis();
        Long lastLoad = lastLoadTime.get(id);
        int delay = PerformanceMonitor.recommendTickDelay();

        if (lastLoad != null && now - lastLoad < delay) {
            return; // Henüz erken
        }

        lastLoadTime.put(id, now);

        if (DemonCoreConfig.ENABLE_CACHE.get()) {
            Double cachedSpeed = CacheSystem.getSpeed(id);
            if (cachedSpeed != null && Math.abs(cachedSpeed - speed) < 10.0) {

                Set<ChunkPos> cachedChunks = loadedChunks.get(id);
                if (cachedChunks != null && !cachedChunks.isEmpty()) {
                    return; // Mevcut chunk'lar yeterli
                }
            }
            CacheSystem.putSpeed(id, speed);
        }

        int maxChunks = DemonCoreConfig.MAX_CHUNKS.get();

        if (SodiumCompat.isOptimizationEnabled()) {
            maxChunks = SodiumCompat.getOptimalChunkDistance(maxChunks);
        }

        if (speed >= 2000.0) {
            maxChunks = (int) (maxChunks * 1.5);
            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.02) {
                DemonCore.LOGGER.info("HIGH SPEED BONUS: {} m/s -> {} max chunks (1.5X)", 
                    String.format("%.0f", speed), maxChunks);
            }
        }

        if (speed >= 5000.0) {
            maxChunks = (int) (maxChunks * 2.0);
            if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.02) {
                DemonCore.LOGGER.warn("EXTREME SPEED BONUS: {} m/s -> {} max chunks (2X)", 
                    String.format("%.0f", speed), maxChunks);
            }
        }

        Set<BlockPos> chunkPositions = SmartChunkCalculator.calculateChunks(
            entity.position(),
            motion,
            speed,
            maxChunks
        );

        Set<ChunkPos> chunks = new HashSet<>();
        for (BlockPos blockPos : chunkPositions) {
            chunks.add(new ChunkPos(blockPos.getX(), blockPos.getZ()));
        }

        visibilityManager.registerEntityChunks(id, chunks);

        if (chunks.size() > 10) {
            ResourceManager.submitTask(() -> {
                addTicketsOptimized(level, chunks);
            }, "ChunkLoad-" + id);
        } else {

            addTicketsOptimized(level, chunks);
        }

        if (SodiumCompat.isOptimizationEnabled()) {
            SodiumCompat.optimizeChunkRenderBatch(chunks);
        }

        Set<ChunkPos> oldChunks = loadedChunks.get(id);
        if (oldChunks != null && !oldChunks.isEmpty()) {

            Set<ChunkPos> toKeep = new HashSet<>(oldChunks);
            toKeep.removeAll(chunks); // Yeni chunk'larda olmayanlar
            
            if (!toKeep.isEmpty()) {

                TicketType<ChunkPos> persistentTicket = getPersistentTicket();
                int keepCount = 0;
                for (ChunkPos pos : toKeep) {
                    try {

                        level.getChunkSource().addRegionTicket(persistentTicket, pos, 1, pos);
                        keepCount++;
                    } catch (Exception ignored) {}
                }

                Set<ChunkPos> existing = persistentChunks.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
                existing.addAll(toKeep);

                int maxPersistent = DemonCoreConfig.PREVENT_CHUNK_UNLOAD.get() ? 2000 : 512;
                if (existing.size() > maxPersistent) {

                    List<ChunkPos> toRemove = new ArrayList<>(existing);
                    int removeCount = existing.size() - maxPersistent;
                    for (int i = 0; i < removeCount && i < toRemove.size(); i++) {
                        existing.remove(toRemove.get(i));
                    }
                }
                
                if (DemonCoreConfig.ENABLE_DEBUG.get() && keepCount > 0 && Math.random() < 0.05) {
                    DemonCore.LOGGER.info("Persistent: {} chunks protected from unload (total: {})", 
                        keepCount, existing.size());
                }
            }
        }

        loadedChunks.put(id, chunks);
        totalChunksLoaded += chunks.size();

        if (DemonCoreConfig.ENABLE_CACHE.get()) {
            BlockPos currentChunk = new BlockPos(
                (int) Math.floor(entity.position().x / 16.0),
                0,
                (int) Math.floor(entity.position().z / 16.0)
            );
            CacheSystem.putPosition(id, currentChunk);
        }

        if (DemonCoreConfig.ENABLE_DEBUG.get() && Math.random() < 0.05) {
            DemonCore.LOGGER.info("Loaded {} chunks for speed {} m/s | Strategy: {} | FPS: {} | Sodium: {}",
                chunks.size(),
                String.format("%.0f", speed),
                ResourceManager.getCurrentStrategy().description,
                PerformanceMonitor.getCurrentFps(),
                SodiumCompat.isOptimizationEnabled() ? "ACTIVE" : "OFF");
        }
    }

    private void loadChunksSimple(ServerLevel level, Entity entity, double speed, Vec3 motion) {
        UUID id = entity.getUUID();

        int chunksAhead = calculateChunksSimple(speed);
        Set<ChunkPos> chunks = calculatePositionsSimple(entity.position(), motion.normalize(), chunksAhead);

        addTickets(level, chunks);
        loadedChunks.put(id, chunks);
    }

    private int calculateChunksSimple(double speed) {
        int maxChunks = DemonCoreConfig.MAX_CHUNKS.get();

        if (speed < 500) return Math.min(4, maxChunks);
        if (speed < 1000) return Math.min(6, maxChunks);
        if (speed < 2000) return Math.min(8, maxChunks);
        if (speed < 4000) return Math.min(10, maxChunks);
        return Math.min(12, maxChunks);
    }

    private Set<ChunkPos> calculatePositionsSimple(Vec3 pos, Vec3 dir, int ahead) {
        Set<ChunkPos> chunks = new HashSet<>();

        for (int i = 0; i <= ahead; i++) {
            double x = pos.x + (dir.x * i * 16.0);
            double z = pos.z + (dir.z * i * 16.0);

            int chunkX = (int) Math.floor(x / 16.0);
            int chunkZ = (int) Math.floor(z / 16.0);

            chunks.add(new ChunkPos(chunkX, chunkZ));

            if (i < 3) {
                chunks.add(new ChunkPos(chunkX + 1, chunkZ));
                chunks.add(new ChunkPos(chunkX - 1, chunkZ));
                chunks.add(new ChunkPos(chunkX, chunkZ + 1));
                chunks.add(new ChunkPos(chunkX, chunkZ - 1));
            }
        }

        return chunks;
    }

    private void addTickets(ServerLevel level, Set<ChunkPos> chunks) {
        ServerChunkCache cache = level.getChunkSource();
        TicketType<ChunkPos> ticket = getTicket();

        int count = 0;
        for (ChunkPos pos : chunks) {
            if (count >= 3) break;

            try {

                cache.addRegionTicket(ticket, pos, 2, pos);
                count++;
            } catch (Exception e) {

            }
        }
    }

    private void addTicketsOptimized(ServerLevel level, Set<ChunkPos> chunks) {
        ServerChunkCache cache = level.getChunkSource();

        int perTickLimit = DemonCoreConfig.CHUNKS_PER_TICK.get();

        if (PerformanceMonitor.isCritical()) {

            perTickLimit = Math.max(3, perTickLimit / 3);
            DemonCore.LOGGER.warn("CRITICAL performance but still loading {} chunks to prevent void fall", perTickLimit);
        } else if (PerformanceMonitor.isLowFps()) {
            perTickLimit = Math.max(5, perTickLimit / 2);
        } else if (PerformanceMonitor.isCpuPressure()) {
            perTickLimit = Math.max(8, perTickLimit - 5);
        } else {

            double ramUsage = PerformanceMonitor.getMemoryUsagePercent();
            if (ramUsage < 0.5) {

                perTickLimit = Math.min(60, perTickLimit * 3);
            } else if (ramUsage < 0.7) {

                perTickLimit = Math.min(50, perTickLimit * 2);
            }
        }

        int count = 0;
        int skipped = 0;
        for (ChunkPos pos : chunks) {
            if (count >= perTickLimit && skipped >= 10) {

                break;
            }

            try {

                boolean alreadyLoaded = false;
                if (DemonCoreConfig.ENABLE_CACHE.get()) {
                    BlockPos blockPos = new BlockPos(pos.x, 0, pos.z);
                    if (CacheSystem.isChunkLoaded(blockPos)) {
                        skipped++;
                        continue;
                    }
                    CacheSystem.putChunkState(blockPos, CacheSystem.ChunkState.LOADING);
                }

                cache.addRegionTicket(getTicket(), pos, 1, pos);

                cache.addRegionTicket(getPersistentTicket(), pos, 1, pos);
                
                count++;

                if (DemonCoreConfig.ENABLE_CACHE.get()) {
                    BlockPos blockPos = new BlockPos(pos.x, 0, pos.z);
                    CacheSystem.putChunkState(blockPos, CacheSystem.ChunkState.LOADED);
                }

                visibilityManager.markVisible(pos);

            } catch (Exception e) {
                if (DemonCoreConfig.ENABLE_DEBUG.get()) {
                    DemonCore.LOGGER.debug("Failed to load chunk {}: {}", pos, e.getMessage());
                }
            }
        }
        
        if (DemonCoreConfig.ENABLE_DEBUG.get() && count > 0) {
            DemonCore.LOGGER.info("Loaded {} chunks this tick (limit: {}, skipped: {})", count, perTickLimit, skipped);
        }
    }

    public void tick() {
        if (DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            PerformanceMonitor.tick();
            ResourceManager.tick();

            if (DemonCoreConfig.ENABLE_CACHE.get()) {
                CacheSystem.tick();
            }

            if (DemonCoreConfig.AUTO_GC.get() && PerformanceMonitor.isMemoryPressure()) {
                PerformanceMonitor.forceCleanup();
            }

            if (PerformanceMonitor.isCritical()) {
                emergencyCleanup();
            }
        }

    }

    public void tickClientSide(net.minecraft.client.multiplayer.ClientLevel level) {
        if (DemonCoreConfig.ENABLE_VISIBILITY_TIMEOUT.get()) {
            visibilityManager.tick(level);
        }
    }

    private void emergencyCleanup() {

        ResourceManager.emergencyCleanup();

        if (DemonCoreConfig.ENABLE_CACHE.get()) {
            CacheSystem.emergencyCleanup();
        }

        if (DemonCoreConfig.ENABLE_DEBUG.get()) {
            DemonCore.LOGGER.debug("Emergency cleanup - {} chunks protected, {} persistent",
                loadedChunks.size(), 
                persistentChunks.values().stream().mapToInt(Set::size).sum());
        }
    }

    public int getLoadedCount(UUID id) {
        Set<ChunkPos> chunks = loadedChunks.get(id);
        return chunks != null ? chunks.size() : 0;
    }

    public void cleanup(UUID id) {
        loadedChunks.remove(id);
        persistentChunks.remove(id); // Persistent chunk'ları da temizle
        lastLoadTime.remove(id);

        if (DemonCoreConfig.ENABLE_CACHE.get()) {
            CacheSystem.clearEntity(id);
        }

        visibilityManager.unregisterEntity(id);
    }

    public void shutdown() {
        DemonCore.LOGGER.info("ChunkPreLoader shutdown - Stats: {} chunks loaded in {} calls",
            totalChunksLoaded, totalLoadCalls);

        loadedChunks.clear();
        persistentChunks.clear(); // Persistent chunk'ları da temizle
        lastLoadTime.clear();

        if (DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            ResourceManager.shutdown();
        }

        if (DemonCoreConfig.ENABLE_CACHE.get()) {
            CacheSystem.clearAll();
        }

        visibilityManager.clear();
    }

    public String getStats() {
        int totalPersistent = persistentChunks.values().stream()
            .mapToInt(Set::size)
            .sum();
        
        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            return String.format("Simple mode | Entities: %d | Total loaded: %d | Persistent: %d",
                loadedChunks.size(), totalChunksLoaded, totalPersistent);
        }

        return String.format("%s | %s | %s | %s | Sodium: %s | Entities: %d | Loaded: %d/%d | Persistent: %d (unload protected)",
            PerformanceMonitor.getDetailedStats(),
            ResourceManager.getStats(),
            CacheSystem.getStats(),
            visibilityManager.getStats(),
            SodiumCompat.getStats(),
            loadedChunks.size(),
            totalChunksLoaded,
            totalLoadCalls,
            totalPersistent);
    }

    public ChunkVisibilityManager getVisibilityManager() {
        return visibilityManager;
    }

    /**
     * Force load a specific chunk immediately
     * Used by safety systems to prevent entity unload
     */
    public static void forceLoadChunk(ServerLevel level, ChunkPos chunkPos) {
        try {
            ServerChunkCache cache = level.getChunkSource();
            TicketType<ChunkPos> ticket = TicketType.create("demoncore_force", 
                Comparator.comparingLong(ChunkPos::toLong), 600);
            
            cache.addRegionTicket(ticket, chunkPos, 0, chunkPos);
            
            if (DemonCoreConfig.ENABLE_DEBUG.get()) {
                DemonCore.LOGGER.trace("Force loaded chunk {}", chunkPos);
            }
        } catch (Exception e) {
            DemonCore.LOGGER.warn("Failed to force load chunk {}: {}", chunkPos, e.getMessage());
        }
    }
}
