package com.lani.demoncore.optimization;

import com.lani.demoncore.compat.chunk.ChunkModCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.event.CacheEvent;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GeometryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore/GeoCache");

    private GeometryCache() {
    }

    public static final class CacheEntry {
        public final long posHash;
        public final long lastFrameSeen;
        public final int renderStateVersion;
        public final float yaw;
        public final float partialYaw;

        CacheEntry(long posHash, long frame, int version, float yaw, float partialYaw) {
            this.posHash = posHash;
            this.lastFrameSeen = frame;
            this.renderStateVersion = version;
            this.yaw = yaw;
            this.partialYaw = partialYaw;
        }
    }

    private static final Long2ObjectLinkedOpenHashMap<CacheEntry> BE_CACHE = new Long2ObjectLinkedOpenHashMap<>(2048);
    private static final Long2ObjectLinkedOpenHashMap<CacheEntry> ENTITY_CACHE = new Long2ObjectLinkedOpenHashMap<>(4096);

    private static volatile long frameCounter;
    private static volatile long hits;
    private static volatile long misses;
    private static volatile long evictions;
    private static volatile int retainedBytesEstimate;
    private static volatile double currentMultiplier = 1.0;
    private static volatile int baseMaxEntries;
    private static volatile int effectiveMaxEntries;
    private static volatile long lastEventFireMs;
    private static volatile long lastTrimMs;
    private static volatile long lastMultiplierApplyMs;
    private static volatile long multiplierApplyCount;

    private static final long EVENT_THROTTLE_MS = 2500L;
    private static final long DEBUG_LOG_THROTTLE_MS = 5000L;
    private static long lastDebugLogMs;

    public static void beginFrame() {
        frameCounter++;
    }

    private static int capacityBytes() {
        return DemonCoreConfig.getInt(DemonCoreConfig.GEOMETRY_CACHE_MB, 192) * 1024 * 1024;
    }

    private static final int ENTRY_APPROX_BYTES = 80;

    private static int baseMaxEntries() {
        int raw = Math.max(512, capacityBytes() / ENTRY_APPROX_BYTES);
        if (ChunkModCompat.useAggressiveChunkCaching()) {
            raw = (int) Math.round(raw * 1.5);
        }
        boolean cacheAggressive = CacheSystem.isAggressiveMode();
        if (cacheAggressive && !ChunkModCompat.useAggressiveChunkCaching()) {
            raw = (int) Math.round(raw * 1.12);
        }
        return raw;
    }

    private static int maxEntries() {
        if (baseMaxEntries == 0) {
            baseMaxEntries = baseMaxEntries();
        }
        double mult = GpuRamBalancer.getCacheMultiplier();
        if (mult <= 0.0) mult = 1.0;
        effectiveMaxEntries = Math.max(256, Math.min(50_000, (int) Math.round(baseMaxEntries * mult)));
        return effectiveMaxEntries;
    }

    private static long beKey(BlockEntity be) {
        BlockPos p = be.getBlockPos();
        return (long) p.getX() & 0x3FFFFFFL | ((long) p.getZ() & 0x3FFFFFFL) << 26 | ((long) p.getY() & 0xFFFFL) << 52;
    }

    public static boolean isBlockEntityFresh(BlockEntity be, float partialTick) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.GEOMETRY_CACHE_ENABLED, true)) {
            misses++;
            return false;
        }
        long key = beKey(be);
        CacheEntry existing;
        synchronized (BE_CACHE) {
            existing = BE_CACHE.get(key);
        }
        if (existing == null) {
            misses++;
            if (shouldFireEvent()) {
                fireCacheEvent(CacheEvent.CacheType.GEOMETRY_BLOCK_ENTITY, CacheEvent.Action.MISS, 1);
            }
            return false;
        }
        float dy = Math.abs(existing.partialYaw - partialTick);
        if (dy < 0.001f && Math.abs(existing.lastFrameSeen - frameCounter) < 60L) {
            hits++;
            synchronized (BE_CACHE) {
                BE_CACHE.getAndMoveToLast(key);
            }
            if (shouldFireEvent()) {
                fireCacheEvent(CacheEvent.CacheType.GEOMETRY_BLOCK_ENTITY, CacheEvent.Action.HIT, 1);
            }
            return true;
        }
        misses++;
        return false;
    }

    public static void putBlockEntity(BlockEntity be, float yaw, float partialYaw) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.GEOMETRY_CACHE_ENABLED, true)) {
            return;
        }
        long key = beKey(be);
        synchronized (BE_CACHE) {
            BE_CACHE.put(key, new CacheEntry(key, frameCounter, be.hashCode(), yaw, partialYaw));
            int maxE = maxEntries();
            while (BE_CACHE.size() > maxE) {
                BE_CACHE.removeFirst();
                evictions++;
                if (shouldFireEvent()) {
                    fireCacheEvent(CacheEvent.CacheType.GEOMETRY_BLOCK_ENTITY, CacheEvent.Action.EVICTION, 1);
                }
            }
        }
    }

    private static long entityKey(Entity e) {
        return e.getId() & 0xFFFFFFFFL;
    }

    public static boolean isEntityPoseFresh(Entity e, float yaw, float partialYaw) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.GEOMETRY_CACHE_ENABLED, true)) {
            misses++;
            return false;
        }
        long key = entityKey(e);
        CacheEntry existing;
        synchronized (ENTITY_CACHE) {
            existing = ENTITY_CACHE.get(key);
        }
        if (existing == null) {
            misses++;
            if (shouldFireEvent()) {
                fireCacheEvent(CacheEvent.CacheType.GEOMETRY_ENTITY, CacheEvent.Action.MISS, 1);
            }
            return false;
        }
        float dy = Math.abs(existing.yaw - yaw) + Math.abs(existing.partialYaw - partialYaw);
        if (dy < 0.02f && Math.abs(existing.lastFrameSeen - frameCounter) < 2L) {
            hits++;
            synchronized (ENTITY_CACHE) {
                ENTITY_CACHE.getAndMoveToLast(key);
            }
            if (shouldFireEvent()) {
                fireCacheEvent(CacheEvent.CacheType.GEOMETRY_ENTITY, CacheEvent.Action.HIT, 1);
            }
            return true;
        }
        misses++;
        return false;
    }

    public static void putEntity(Entity e, float yaw, float partialYaw) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.GEOMETRY_CACHE_ENABLED, true)) {
            return;
        }
        long key = entityKey(e);
        synchronized (ENTITY_CACHE) {
            ENTITY_CACHE.put(key, new CacheEntry(key, frameCounter, 0, yaw, partialYaw));
            int maxE = maxEntries();
            while (ENTITY_CACHE.size() > maxE) {
                ENTITY_CACHE.removeFirst();
                evictions++;
                if (shouldFireEvent()) {
                    fireCacheEvent(CacheEvent.CacheType.GEOMETRY_ENTITY, CacheEvent.Action.EVICTION, 1);
                }
            }
        }
    }

    public static void trimStale() {
        long now = System.currentTimeMillis();
        long cutoff = frameCounter - 300L;
        int beRemoved = 0;
        int entRemoved = 0;
        synchronized (BE_CACHE) {
            var it = BE_CACHE.values().iterator();
            while (it.hasNext()) {
                if (it.next().lastFrameSeen < cutoff) {
                    it.remove();
                    beRemoved++;
                    evictions++;
                }
            }
        }
        synchronized (ENTITY_CACHE) {
            var it = ENTITY_CACHE.values().iterator();
            while (it.hasNext()) {
                if (it.next().lastFrameSeen < cutoff) {
                    it.remove();
                    entRemoved++;
                    evictions++;
                }
            }
        }
        lastTrimMs = now;
        int totalRemoved = beRemoved + entRemoved;
        if (totalRemoved > 0) {
            if (shouldFireEvent()) {
                fireCacheEvent(CacheEvent.CacheType.AGGREGATE, CacheEvent.Action.TRIM, totalRemoved);
            }
            if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                if (now - lastDebugLogMs > DEBUG_LOG_THROTTLE_MS) {
                    lastDebugLogMs = now;
                    LOGGER.info("[GeoCache] trimStale: BE={} Entity={} removed | sizes BE={}/{} Entity={}/{} | hitRate={}%",
                            beRemoved, entRemoved,
                            sizeBlockEntity(), effectiveMaxEntries,
                            sizeEntity(), effectiveMaxEntries,
                            String.format("%.1f", getHitRate() * 100.0));
                }
            }
        }
    }

    public static void applyCacheMultiplier(double multiplier) {
        double prev = currentMultiplier;
        currentMultiplier = Math.max(GpuRamBalancer.MIN_MULTIPLIER,
                Math.min(GpuRamBalancer.MAX_MULTIPLIER, multiplier));
        multiplierApplyCount++;
        lastMultiplierApplyMs = System.currentTimeMillis();

        double effectiveMult = currentMultiplier;
        if (baseMaxEntries == 0) {
            baseMaxEntries = baseMaxEntries();
        }
        int newMax = Math.max(256, (int) Math.round(baseMaxEntries * effectiveMult));
        effectiveMaxEntries = newMax;

        int beTrimmed = 0;
        int entTrimmed = 0;
        synchronized (BE_CACHE) {
            while (BE_CACHE.size() > newMax) {
                BE_CACHE.removeFirst();
                beTrimmed++;
                evictions++;
            }
        }
        synchronized (ENTITY_CACHE) {
            while (ENTITY_CACHE.size() > newMax) {
                ENTITY_CACHE.removeFirst();
                entTrimmed++;
                evictions++;
            }
        }

        int totalTrimmed = beTrimmed + entTrimmed;
        if (Math.abs(currentMultiplier - prev) >= 0.05) {
            if (shouldFireEvent()) {
                fireCacheEvent(CacheEvent.CacheType.AGGREGATE, CacheEvent.Action.RESIZE, totalTrimmed);
            }
            if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                long now = System.currentTimeMillis();
                if (now - lastDebugLogMs > DEBUG_LOG_THROTTLE_MS) {
                    lastDebugLogMs = now;
                    LOGGER.info("[GeoCache] applyMultiplier: {} -> {} | newMaxEntries={} | trimmed BE={} Ent={} | sizes BE={} Ent={}",
                            String.format("%.2f", prev), String.format("%.2f", currentMultiplier),
                            newMax, beTrimmed, entTrimmed,
                            sizeBlockEntity(), sizeEntity());
                }
            }
        }
    }

    public static int sizeBlockEntity() {
        synchronized (BE_CACHE) {
            return BE_CACHE.size();
        }
    }

    public static int sizeEntity() {
        synchronized (ENTITY_CACHE) {
            return ENTITY_CACHE.size();
        }
    }

    public static double getHitRate() {
        long total = hits + misses;
        return total == 0L ? 0.0 : (double) hits / (double) total;
    }

    public static int getRetainedMbEstimate() {
        int entries = sizeBlockEntity() + sizeEntity();
        retainedBytesEstimate = entries * ENTRY_APPROX_BYTES;
        return retainedBytesEstimate / (1024 * 1024);
    }

    public static long getHits() {
        return hits;
    }

    public static long getMisses() {
        return misses;
    }

    public static long getEvictions() {
        return evictions;
    }

    public static double getCurrentMultiplier() { return currentMultiplier; }
    public static int getEffectiveMaxEntries() { return effectiveMaxEntries; }
    public static int getBaseMaxEntries() { return baseMaxEntries; }
    public static long getMultiplierApplyCount() { return multiplierApplyCount; }

    private static boolean shouldFireEvent() {
        long now = System.currentTimeMillis();
        if (now - lastEventFireMs >= EVENT_THROTTLE_MS) {
            lastEventFireMs = now;
            return true;
        }
        return false;
    }

    private static void fireCacheEvent(CacheEvent.CacheType type, CacheEvent.Action action, long affected) {
        try {
            NeoForge.EVENT_BUS.post(CacheEvent.builder()
                    .cacheType(type)
                    .action(action)
                    .currentSize(sizeBlockEntity() + sizeEntity())
                    .maxSize(effectiveMaxEntries * 2)
                    .hitRate(getHitRate())
                    .entriesAffected(affected)
                    .detail(type.name() + " " + action.name())
                    .build());
        } catch (Exception e) {
            if (DemonCoreConfig.isDebug()) {
                LOGGER.warn("[GeoCache] Event post failed: {}", e.getMessage());
            }
        }
    }

    public static String getStats() {
        int base = baseMaxEntries();
        int eff = effectiveMaxEntries;
        return String.format("GeoCache: %d BE + %d Ent (~%d MB cap base / %d eff, %d MB used) | mult %.2f | hit %.1f%% | %d hits, %d misses, %d evict | multApply=%d",
                sizeBlockEntity(), sizeEntity(),
                DemonCoreConfig.getInt(DemonCoreConfig.GEOMETRY_CACHE_MB, 192),
                eff == 0 ? base : eff,
                getRetainedMbEstimate(),
                currentMultiplier,
                getHitRate() * 100.0, hits, misses, evictions, multiplierApplyCount);
    }
}
