package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class GeometryCache {

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

    
    public static void beginFrame() {
        frameCounter++;
    }

    private static int capacityBytes() {
        return DemonCoreConfig.getInt(DemonCoreConfig.GEOMETRY_CACHE_MB, 192) * 1024 * 1024;
    }

    private static final int ENTRY_APPROX_BYTES = 80;

    private static int maxEntries() {
        return Math.max(512, capacityBytes() / ENTRY_APPROX_BYTES);
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
            return false;
        }
        float dy = Math.abs(existing.partialYaw - partialTick);
        if (dy < 0.001f && Math.abs(existing.lastFrameSeen - frameCounter) < 60L) {
            hits++;
            synchronized (BE_CACHE) {
                BE_CACHE.getAndMoveToLast(key);
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
            while (BE_CACHE.size() > maxEntries()) {
                BE_CACHE.removeFirst();
                evictions++;
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
            return false;
        }
        float dy = Math.abs(existing.yaw - yaw) + Math.abs(existing.partialYaw - partialYaw);
        if (dy < 0.02f && Math.abs(existing.lastFrameSeen - frameCounter) < 2L) {
            hits++;
            synchronized (ENTITY_CACHE) {
                ENTITY_CACHE.getAndMoveToLast(key);
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
            while (ENTITY_CACHE.size() > maxEntries()) {
                ENTITY_CACHE.removeFirst();
                evictions++;
            }
        }
    }

    
    
    

    public static void trimStale() {
        long cutoff = frameCounter - 300L;
        synchronized (BE_CACHE) {
            var it = BE_CACHE.values().iterator();
            while (it.hasNext()) {
                if (it.next().lastFrameSeen < cutoff) {
                    it.remove();
                    evictions++;
                }
            }
        }
        synchronized (ENTITY_CACHE) {
            var it = ENTITY_CACHE.values().iterator();
            while (it.hasNext()) {
                if (it.next().lastFrameSeen < cutoff) {
                    it.remove();
                    evictions++;
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

    
    public static void applyCacheMultiplier(double multiplier) {
        
        
    }

    public static String getStats() {
        return String.format("GeoCache: %d BE + %d Ent (~%d MB cap, %d MB used) | hit %.1f%% | %d hits, %d misses, %d evict",
                sizeBlockEntity(), sizeEntity(),
                DemonCoreConfig.getInt(DemonCoreConfig.GEOMETRY_CACHE_MB, 192),
                getRetainedMbEstimate(),
                getHitRate() * 100.0, hits, misses, evictions);
    }
}
