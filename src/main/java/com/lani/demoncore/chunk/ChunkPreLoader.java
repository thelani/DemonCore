package com.lani.demoncore.chunk;

import com.lani.demoncore.DemonCore;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.optimization.ChunkPosCache;
import com.lani.demoncore.optimization.PerformanceMonitor;
import com.lani.demoncore.optimization.SmartChunkCalculator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkPreLoader {

    private static TicketType<ChunkPos> ticketType;
    private static int ticketTypeLifetime = -1;

    private static synchronized TicketType<ChunkPos> ticketType() {
        int lifetime = DemonCoreConfig.getInt(DemonCoreConfig.TICKET_LIFETIME_TICKS, 200);
        if (ticketType == null || ticketTypeLifetime != lifetime) {
            ticketType = TicketType.create("demoncore", Comparator.comparingLong(ChunkPos::toLong), lifetime);
            ticketTypeLifetime = lifetime;
        }
        return ticketType;
    }

    private record TicketRequest(ServerLevel level, ChunkPos pos) {
    }

    private final Deque<TicketRequest> pending = new ArrayDeque<>();
    private final Map<UUID, Integer> trackedPerEntity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRequestTick = new ConcurrentHashMap<>();

    private long serverTick;
    private long totalSubmitted;
    private long totalDropped;
    private long totalDeduped;

    
    
    

    public void loadChunks(Entity entity, double speed) {
        if (!DemonCoreConfig.isEnabled()
                || !DemonCoreConfig.getBool(DemonCoreConfig.CHUNK_LOADING_ENABLED, true)) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-4) {
            return;
        }

        double threshold = DemonCoreConfig.getDouble(DemonCoreConfig.ACTIVATION_SPEED, 24.0);
        if (threshold > 0.0 && speed < threshold) {
            return;
        }

        UUID id = entity.getUUID();

        
        
        Long last = lastRequestTick.get(id);
        if (last != null && last == serverTick) {
            return;
        }
        lastRequestTick.put(id, serverTick);

        int maxChunks = resolveChunkBudget(speed);
        if (maxChunks <= 0) {
            return;
        }

        List<ChunkPos> predicted = SmartChunkCalculator.predictPath(
                entity.position(), motion, speed, maxChunks);

        if (predicted.isEmpty()) {
            return;
        }

        int queued = 0;
        int cap = DemonCoreConfig.getInt(DemonCoreConfig.MAX_QUEUED_TICKETS, 4096);

        synchronized (pending) {
            for (ChunkPos pos : predicted) {
                
                if (!ChunkPosCache.markRequested(pos.x, pos.z)) {
                    totalDeduped++;
                    continue;
                }

                pending.addLast(new TicketRequest(level, pos));
                queued++;

                while (pending.size() > cap) {
                    
                    
                    pending.removeFirst();
                    totalDropped++;
                }
            }
        }

        if (queued > 0) {
            trackedPerEntity.merge(id, queued, Integer::sum);
        }
    }

    private int resolveChunkBudget(double speed) {
        int maxChunks = DemonCoreConfig.getInt(DemonCoreConfig.MAX_CHUNKS, 96);

        if (DemonCoreConfig.isVulcanMode()) {
            maxChunks *= 2;
        } else if (DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_BACKPRESSURE, true)) {
            
            double scale = PerformanceMonitor.getServerLevel().budgetScale();
            maxChunks = (int) Math.max(4, maxChunks * scale);
        }

        
        
        if (speed > 500.0) {
            double boost = 1.0 + Math.min(1.0, Math.log10(speed / 500.0));
            maxChunks = (int) (maxChunks * boost);
        }

        return maxChunks;
    }

    
    
    

    public void tick() {
        serverTick++;

        if (!DemonCoreConfig.isEnabled()) {
            return;
        }

        drainQueue();

        if ((serverTick % 200L) == 0L) {
            trackedPerEntity.clear();
            lastRequestTick.entrySet().removeIf(e -> serverTick - e.getValue() > 200L);
        }
    }

    private void drainQueue() {
        int budget = resolvePerTickBudget();
        if (budget <= 0) {
            return;
        }

        int radius = DemonCoreConfig.getInt(DemonCoreConfig.TICKET_RADIUS, 0);
        TicketType<ChunkPos> type = ticketType();

        List<TicketRequest> batch;
        synchronized (pending) {
            if (pending.isEmpty()) {
                return;
            }
            int n = Math.min(budget, pending.size());
            batch = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                batch.add(pending.removeFirst());
            }
        }

        for (TicketRequest req : batch) {
            try {
                req.level().getChunkSource().addRegionTicket(type, req.pos(), radius, req.pos());
                totalSubmitted++;
            } catch (Exception e) {
                if (DemonCoreConfig.isDebug()) {
                    DemonCore.LOGGER.warn("Failed to add chunk ticket at {}: {}", req.pos(), e.toString());
                }
            }
        }
    }

    private int resolvePerTickBudget() {
        int budget = DemonCoreConfig.getInt(DemonCoreConfig.CHUNKS_PER_TICK, 16);

        if (DemonCoreConfig.isVulcanMode()) {
            return budget * 3;
        }

        if (!DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_BACKPRESSURE, true)) {
            return budget;
        }

        
        
        double headroom = PerformanceMonitor.getServerHeadroom();
        int scaled = (int) Math.round(budget * headroom);

        
        return Math.max(1, scaled);
    }

    

    public int getQueueSize() {
        synchronized (pending) {
            return pending.size();
        }
    }

    public int getLoadedCount(UUID id) {
        return trackedPerEntity.getOrDefault(id, 0);
    }

    public void cleanup(UUID id) {
        trackedPerEntity.remove(id);
        lastRequestTick.remove(id);
    }

    public void trackEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        UUID id = entity.getUUID();
        trackedPerEntity.putIfAbsent(id, 0);
    }

    public void shutdown() {
        synchronized (pending) {
            pending.clear();
        }
        trackedPerEntity.clear();
        lastRequestTick.clear();
        ChunkPosCache.clear();
    }

    public String getStats() {
        return String.format(
                "Chunk loader: %d queued | %d submitted | %d deduped | %d dropped%n%s%n%s",
                getQueueSize(), totalSubmitted, totalDeduped, totalDropped,
                ChunkPosCache.getStats(),
                PerformanceMonitor.getServerStats());
    }
}
