package com.lani.demoncore.chunk;

import com.lani.demoncore.DemonCore;
import com.lani.demoncore.compat.SodiumCompat;
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
            int lifetime = DemonCoreConfig.isVulcanMode() ? Integer.MAX_VALUE : 
                          (preventUnload ? Integer.MAX_VALUE : 300);
            TICKET = TicketType.create("demoncore", Comparator.comparingLong(ChunkPos::toLong), lifetime);
        }
        return TICKET;
    }
    
    private static TicketType<ChunkPos> getPersistentTicket() {
        if (PERSISTENT_TICKET == null) {
            boolean preventUnload = DemonCoreConfig.PREVENT_CHUNK_UNLOAD != null && DemonCoreConfig.PREVENT_CHUNK_UNLOAD.get();
            int lifetime = DemonCoreConfig.isVulcanMode() ? Integer.MAX_VALUE : 
                          (preventUnload ? Integer.MAX_VALUE : 600);
            PERSISTENT_TICKET = TicketType.create("demoncore_persistent", Comparator.comparingLong(ChunkPos::toLong), lifetime);
        }
        return PERSISTENT_TICKET;
    }

    private final Map<UUID, Set<ChunkPos>> loadedChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ChunkPos>> persistentChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLoadTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivityTime = new ConcurrentHashMap<>(); // Track last activity for cleanup
    private final Map<UUID, ServerLevel> entityLevels = new ConcurrentHashMap<>(); // Track entity levels for cleanup

    private final ChunkVisibilityManager visibilityManager = new ChunkVisibilityManager();

    private int totalChunksLoaded = 0;
    private int totalLoadCalls = 0;
    private int totalChunksUnloaded = 0;
    
    private static final long CHUNK_TIMEOUT = 30000; // 30 seconds without activity

    public void loadChunks(Entity entity, double speed) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        totalLoadCalls++;
        UUID id = entity.getUUID();
        Vec3 motion = entity.getDeltaMovement();

        if (motion.lengthSqr() < 0.001) {
            return;
        }

        double threshold = DemonCoreConfig.SPEED_THRESHOLD.get();
        if (threshold > 0.0 && speed < threshold) {
            return;
        }

        if (!DemonCoreConfig.ENABLE_OPTIMIZATION.get()) {
            loadChunksSimple(level, entity, speed, motion);
            return;
        }

        if (PerformanceMonitor.isCritical()) {
            // VULCAN MODE: Ignore performance warnings
            if (DemonCoreConfig.isVulcanMode()) {
                // Pushing limits in VULCAN MODE
            }
        }

        long now = System.currentTimeMillis();
        Long lastLoad = lastLoadTime.get(id);
        int delay = PerformanceMonitor.recommendTickDelay();

        if (lastLoad != null && now - lastLoad < delay) {
            return;
        }

        lastLoadTime.put(id, now);

        if (DemonCoreConfig.ENABLE_CACHE.get()) {
            Double cachedSpeed = CacheSystem.getSpeed(id);
            if (cachedSpeed != null && Math.abs(cachedSpeed - speed) < 10.0) {
                Set<ChunkPos> cachedChunks = loadedChunks.get(id);
                if (cachedChunks != null && !cachedChunks.isEmpty()) {
                    return;
                }
            }
            CacheSystem.putSpeed(id, speed);
        }

        int maxChunks = DemonCoreConfig.MAX_CHUNKS.get();

        if (SodiumCompat.isOptimizationEnabled()) {
            maxChunks = SodiumCompat.getOptimalChunkDistance(maxChunks);
        }
        
        // VULCAN MODE: Double all chunk limits
        if (DemonCoreConfig.isVulcanMode()) {
            maxChunks *= 2;
        }

        if (speed >= 2000.0) {
            maxChunks = (int) (maxChunks * 1.5);
        }

        if (speed >= 5000.0) {
            maxChunks = (int) (maxChunks * 2.0);
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
            }
        }

        loadedChunks.put(id, chunks);
        totalChunksLoaded += chunks.size();
        
        lastActivityTime.put(id, System.currentTimeMillis());
        entityLevels.put(id, level);

        if (DemonCoreConfig.ENABLE_CACHE.get()) {
            BlockPos currentChunk = new BlockPos(
                (int) Math.floor(entity.position().x / 16.0),
                0,
                (int) Math.floor(entity.position().z / 16.0)
            );
            CacheSystem.putPosition(id, currentChunk);
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
        
        // VULCAN MODE: 3X chunks per tick, ignore all performance checks
        if (DemonCoreConfig.isVulcanMode()) {
            perTickLimit *= 3;
        } else if (PerformanceMonitor.isCritical()) {
            perTickLimit = Math.max(3, perTickLimit / 3);
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
                // Ignore load failures
            }
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
        
        // Periodic cleanup of stale chunks
        cleanupStaleChunks();
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
    }

    public int getLoadedCount(UUID id) {
        Set<ChunkPos> chunks = loadedChunks.get(id);
        return chunks != null ? chunks.size() : 0;
    }

    public void cleanup(UUID id) {
        ServerLevel level = entityLevels.remove(id);
        Set<ChunkPos> chunks = loadedChunks.remove(id);
        Set<ChunkPos> persistent = persistentChunks.remove(id);
        lastLoadTime.remove(id);
        lastActivityTime.remove(id);

        // Remove tickets for this entity
        if (level != null) {
            removeTicketsForEntity(level, chunks, persistent);
        }

        if (DemonCoreConfig.ENABLE_CACHE.get()) {
            CacheSystem.clearEntity(id);
        }

        visibilityManager.unregisterEntity(id);
    }
    
    private void removeTicketsForEntity(ServerLevel level, Set<ChunkPos> chunks, Set<ChunkPos> persistent) {
        if (level == null) return;
        
        ServerChunkCache cache = level.getChunkSource();
        int removed = 0;
        
        try {
            // Remove regular tickets
            if (chunks != null) {
                for (ChunkPos pos : chunks) {
                    try {
                        cache.removeRegionTicket(getTicket(), pos, 1, pos);
                        removed++;
                    } catch (Exception e) {
                        // Ignore individual failures
                    }
                }
            }
            
            // Remove persistent tickets
            if (persistent != null) {
                for (ChunkPos pos : persistent) {
                    try {
                        cache.removeRegionTicket(getPersistentTicket(), pos, 1, pos);
                        removed++;
                    } catch (Exception e) {
                        // Ignore individual failures
                    }
                }
            }
            
            totalChunksUnloaded += removed;
        } catch (Exception e) {
            // Ignore cleanup failures
        }
    }
    
    private void cleanupStaleChunks() {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();
        
        for (Map.Entry<UUID, Long> entry : lastActivityTime.entrySet()) {
            if (now - entry.getValue() > CHUNK_TIMEOUT) {
                toRemove.add(entry.getKey());
            }
        }
        
        if (!toRemove.isEmpty()) {
            for (UUID id : toRemove) {
                cleanup(id);
            }
        }
    }

    public void shutdown() {
        // Clean up all remaining tickets
        for (Map.Entry<UUID, ServerLevel> entry : entityLevels.entrySet()) {
            UUID id = entry.getKey();
            ServerLevel level = entry.getValue();
            Set<ChunkPos> chunks = loadedChunks.get(id);
            Set<ChunkPos> persistent = persistentChunks.get(id);
            
            removeTicketsForEntity(level, chunks, persistent);
        }

        loadedChunks.clear();
        persistentChunks.clear();
        lastLoadTime.clear();
        lastActivityTime.clear();
        entityLevels.clear();

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

        return String.format("%s | %s | %s | %s | Sodium: %s | Entities: %d | Loaded: %d/%d | Unloaded: %d | Persistent: %d (unload protected)",
            PerformanceMonitor.getDetailedStats(),
            ResourceManager.getStats(),
            CacheSystem.getStats(),
            visibilityManager.getStats(),
            SodiumCompat.getStats(),
            loadedChunks.size(),
            totalChunksLoaded,
            totalLoadCalls,
            totalChunksUnloaded,
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
