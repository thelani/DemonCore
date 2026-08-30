package com.lani.demoncore.chunk;

import com.lani.demoncore.DemonCore;
import com.lani.demoncore.compat.chunk.ChunkModCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.optimization.AdaptiveTickBudget;
import com.lani.demoncore.optimization.CacheSystem;
import com.lani.demoncore.optimization.GpuRamBalancer;
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

    private record TicketRequest(ServerLevel level, ChunkPos pos) {}

    private final Deque<TicketRequest> pending = new ArrayDeque<>();
    private final Map<UUID, Integer> trackedPerEntity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRequestTick = new ConcurrentHashMap<>();

    private long serverTick;
    private long totalSubmitted;
    private long totalDropped;
    private long totalDeduped;
    private long ticketSkipped;

    public int loadChunks(Entity entity, double speed) {
        if (!DemonCoreConfig.isEnabled()
                || !DemonCoreConfig.getBool(DemonCoreConfig.CHUNK_LOADING_ENABLED, true)) {
            return 0;
        }
        if (!(entity.level() instanceof ServerLevel level)) return 0;

        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-4) return 0;

        double threshold = DemonCoreConfig.getDouble(DemonCoreConfig.ACTIVATION_SPEED, 24.0);
        if (threshold > 0.0 && speed < threshold) return 0;

        UUID id = entity.getUUID();
        Long last = lastRequestTick.get(id);
        if (last != null && last == serverTick) return 0;
        lastRequestTick.put(id, serverTick);

        int maxChunks = resolveChunkBudget(speed);
        if (maxChunks <= 0) return 0;

        List<ChunkPos> predicted = SmartChunkCalculator.predictPath(
                entity.position(), motion, speed, maxChunks);

        if (predicted.isEmpty()) return 0;

        boolean useTickets = ChunkModCompat.shouldUseTickets();
        int cap = resolveMaxQueuedTickets();

        int queued = 0;
        synchronized (pending) {
            for (ChunkPos pos : predicted) {
                if (!CacheSystem.chunkRecordRequest(pos.x, pos.z)) {
                    totalDeduped++;
                    continue;
                }

                if (!useTickets) {
                    ticketSkipped++;
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

        if (queued > 0) trackedPerEntity.merge(id, queued, Integer::sum);
        return queued;
    }

    private int resolveChunkBudget(double speed) {
        int maxChunks = DemonCoreConfig.getInt(DemonCoreConfig.MAX_CHUNKS, 96);
        if (ChunkModCompat.shouldReduceQueue()) maxChunks = Math.min(maxChunks, 64);

        if (DemonCoreConfig.isVulcanMode()) {
            maxChunks *= 2;
        } else if (DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_BACKPRESSURE, true)) {
            double scale = Math.min(1.0, Math.min(
                    PerformanceMonitor.getServerLevel().budgetScale(),
                    AdaptiveTickBudget.getBudgetScale()
            ));
            double gpuScale = 1.0;
            double gpu = GpuRamBalancer.getLastGpuUtil();
            if (gpu > GpuRamBalancer.getHardGpuCap()) {
                gpuScale = Math.max(0.35, 1.0 - (gpu - GpuRamBalancer.getHardGpuCap()) * 3.0);
            }
            double ram = GpuRamBalancer.getLastRamUtil();
            double ramScale = 1.0;
            if (ram > GpuRamBalancer.getHardRamCap()) {
                ramScale = Math.max(0.35, 1.0 - (ram - GpuRamBalancer.getHardRamCap()) * 2.4);
            }
            scale = Math.min(scale, Math.min(gpuScale, ramScale));
            maxChunks = (int) Math.max(4, maxChunks * scale);
        }

        if (speed > 500.0) {
            double boost = 1.0 + Math.min(1.0, Math.log10(speed / 500.0));
            maxChunks = (int) (maxChunks * boost);
        }

        return maxChunks;
    }

    private int resolveMaxQueuedTickets() {
        int cap = DemonCoreConfig.getInt(DemonCoreConfig.MAX_QUEUED_TICKETS, 4096);
        if (ChunkModCompat.shouldReduceQueue()) cap = Math.min(cap, 1024);
        return cap;
    }

    public void tick() {
        serverTick++;
        ChunkModCompat.resolve();
        if (!DemonCoreConfig.isEnabled()) return;
        drainQueue();
        if ((serverTick % 200L) == 0L) {
            trackedPerEntity.clear();
            lastRequestTick.entrySet().removeIf(e -> serverTick - e.getValue() > 200L);
        }
    }

    private void drainQueue() {
        int budget = resolvePerTickBudget();
        if (budget <= 0) return;

        int radiusCfg = DemonCoreConfig.getInt(DemonCoreConfig.TICKET_RADIUS, 0);
        int radius = ChunkModCompat.getMaxTicketRadiusOverride(radiusCfg);
        TicketType<ChunkPos> type = ticketType();

        List<TicketRequest> batch;
        synchronized (pending) {
            if (pending.isEmpty()) return;
            int n = Math.min(budget, pending.size());
            batch = new ArrayList<>(n);
            for (int i = 0; i < n; i++) batch.add(pending.removeFirst());
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
        int cfg = DemonCoreConfig.getInt(DemonCoreConfig.CHUNKS_PER_TICK, 16);
        int cap = ChunkModCompat.getChunksPerTickCap(cfg);
        int budget = Math.min(cfg, cap);

        if (DemonCoreConfig.isVulcanMode()) return budget * 3;
        if (!DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_BACKPRESSURE, true)) return budget;

        double headroom = Math.min(PerformanceMonitor.getServerHeadroom(), AdaptiveTickBudget.getBudgetScale());
        double gpu = GpuRamBalancer.getLastGpuUtil();
        if (gpu > GpuRamBalancer.getHardGpuCap()) {
            headroom = Math.min(headroom, 0.70);
        }
        double ram = GpuRamBalancer.getLastRamUtil();
        if (ram > GpuRamBalancer.getHardRamCap()) {
            headroom = Math.min(headroom, 0.65);
        }
        int scaled = (int) Math.round(budget * headroom);
        return Math.max(1, scaled);
    }

    public int getQueueSize() {
        synchronized (pending) { return pending.size(); }
    }

    public int getLoadedCount(UUID id) { return trackedPerEntity.getOrDefault(id, 0); }
    public long getTicketSkipped() { return ticketSkipped; }

    public void cleanup(UUID id) {
        trackedPerEntity.remove(id);
        lastRequestTick.remove(id);
    }

    public void trackEntity(Entity entity) {
        if (entity == null) return;
        trackedPerEntity.putIfAbsent(entity.getUUID(), 0);
    }

    public void shutdown() {
        synchronized (pending) { pending.clear(); }
        trackedPerEntity.clear();
        lastRequestTick.clear();
        CacheSystem.chunkClear();
    }

    public String getStats() {
        return String.format(
                "Chunk loader: %d queued | %d submitted | %d deduped | %d dropped | %d skipped%n%s%n%s%n%s",
                getQueueSize(), totalSubmitted, totalDeduped, totalDropped, ticketSkipped,
                CacheSystem.chunkStats(),
                PerformanceMonitor.getServerStats(),
                ChunkModCompat.getStats());
    }
}
