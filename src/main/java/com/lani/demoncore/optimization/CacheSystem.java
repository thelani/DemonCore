package com.lani.demoncore.optimization;

import com.lani.demoncore.DemonCore;
import com.lani.demoncore.compat.chunk.ChunkModCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.event.CacheEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CacheSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore/Cache");

    private CacheSystem() {}

    private static volatile long hits;
    private static volatile long misses;
    private static volatile long evictions;
    private static volatile int currentSize;
    private static volatile int baseMaxSize;
    private static volatile double multiplier = 1.0;
    private static volatile double lastHitRate;
    private static volatile long lastEventFireMs;
    private static volatile long lastTrimMs;
    private static volatile long autoTrimCount;
    private static volatile long totalTrimmedEntries;
    private static volatile boolean initialized;
    private static volatile long lastDebugLogMs;
    private static volatile long chunkResizes;
    private static volatile long lastChunkResizeFireMs;

    private static volatile double gpuPressureBoost = 0.0;
    private static volatile long gpuDrivenCacheGrowths;
    private static volatile long smartPathHits;
    private static volatile boolean aggressiveMode;
    private static volatile double compatPrefetchMult = 1.0;

    private static final long EVENT_THROTTLE_MS = 2000L;
    private static final long DEBUG_LOG_THROTTLE_MS = 5000L;

    public static void init() {
        baseMaxSize = ChunkPosCache.baseCapacity();
        aggressiveMode = ChunkModCompat.useAggressiveChunkCaching();
        compatPrefetchMult = ChunkModCompat.getPrefetchMultiplier();
        if (!initialized) {
            initialized = true;
            multiplier = 1.0;
            if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                LOGGER.info("[CacheSystem] Initialized baseMaxSize={} ({} KB approx) multiplier=1.0 | "
                                + "aggressive={} compatPrefetch={}",
                        baseMaxSize, (baseMaxSize * 16L) / 1024L,
                        aggressiveMode, String.format("%.2f", compatPrefetchMult));
            }
            fireEvent(CacheEvent.builder()
                    .cacheType(CacheEvent.CacheType.AGGREGATE)
                    .action(CacheEvent.Action.INIT)
                    .currentSize(0)
                    .maxSize(baseMaxSize)
                    .detail("System initialized | aggressive=" + aggressiveMode)
                    .build());
        } else {
            aggressiveMode = ChunkModCompat.useAggressiveChunkCaching();
            compatPrefetchMult = ChunkModCompat.getPrefetchMultiplier();
        }
    }

    public static void registerSmartPathHit() {
        smartPathHits++;
    }

    public static long getSmartPathHits() {
        return smartPathHits;
    }

    public static boolean isAggressiveMode() {
        return aggressiveMode;
    }

    public static double getCompatPrefetchMultiplier() {
        return compatPrefetchMult;
    }

    public static double getGpuPressureBoost() {
        return gpuPressureBoost;
    }

    /* ================================================================
       CHUNK POS CACHE PROXY METODLARI  (ChunkPosCache yalnızca buradan kullanılır)
       =================================================================*/

    public static boolean chunkRecordRequest(int chunkX, int chunkZ) {
        long beforeHits = ChunkPosCache.getHits();
        long beforeMisses = ChunkPosCache.getMisses();
        long beforeEvict = ChunkPosCache.getEvictions();

        boolean isNew = ChunkPosCache.recordRequestedInternal(chunkX, chunkZ);

        if (ChunkPosCache.getHits() > beforeHits) recordHit();
        if (ChunkPosCache.getMisses() > beforeMisses) recordMiss();
        if (ChunkPosCache.getEvictions() > beforeEvict) {
            long delta = ChunkPosCache.getEvictions() - beforeEvict;
            evictions += delta;
            for (long k = 0; k < delta; k++) recordEvictionInternal();
        }
        currentSize = ChunkPosCache.sizeInternal();
        return isNew;
    }

    public static boolean chunkIsCached(int chunkX, int chunkZ) {
        boolean present = ChunkPosCache.isCachedInternal(chunkX, chunkZ);
        if (present) recordHit(); else recordMiss();
        currentSize = ChunkPosCache.sizeInternal();
        return present;
    }

    public static void chunkForget(int chunkX, int chunkZ) {
        ChunkPosCache.forgetInternal(chunkX, chunkZ);
        currentSize = ChunkPosCache.sizeInternal();
    }

    public static int chunkTrimToRatio(double ratio) {
        int removed = ChunkPosCache.trimToRatioInternal(ratio);
        evictions += removed;
        totalTrimmedEntries += removed;
        currentSize = ChunkPosCache.sizeInternal();
        if (removed > 0 && shouldFire()) {
            fireEvent(CacheEvent.builder()
                    .cacheType(CacheEvent.CacheType.CHUNK_POS)
                    .action(CacheEvent.Action.TRIM)
                    .currentSize(currentSize)
                    .maxSize(currentMaxSize())
                    .entriesAffected(removed)
                    .keepFraction(ratio)
                    .detail("ChunkTrim ratio=" + ratio + " removed=" + removed)
                    .build());
        }
        return removed;
    }

    public static void chunkClear() {
        long beforeTotal = ChunkPosCache.getHits() + ChunkPosCache.getMisses();
        ChunkPosCache.clearInternal();
        currentSize = 0;
        if (shouldFire()) {
            fireEvent(CacheEvent.builder()
                    .cacheType(CacheEvent.CacheType.CHUNK_POS)
                    .action(CacheEvent.Action.CLEAR)
                    .currentSize(0)
                    .maxSize(currentMaxSize())
                    .entriesAffected(beforeTotal)
                    .detail("ChunkPosCache cleared")
                    .build());
        }
    }

    public static int chunkSize() {
        int sz = ChunkPosCache.sizeInternal();
        currentSize = sz;
        return sz;
    }

    public static double chunkHitRate() {
        return ChunkPosCache.getHitRateInternal();
    }

    public static long chunkEvictions() {
        return ChunkPosCache.getEvictions();
    }

    public static long chunkKilobytes() {
        return ChunkPosCache.getApproxKilobytesInternal();
    }

    public static String chunkStats() {
        return ChunkPosCache.getStatsInternal();
    }

    public static int currentMaxSize() {
        return ChunkPosCache.effectiveCapacity();
    }

    public static double getCurrentMultiplier() {
        return multiplier;
    }

    public static void applyCacheMultiplier(double m) {
        double prev = multiplier;
        double minMult = GpuRamBalancer.MIN_MULTIPLIER;
        double maxMult = GpuRamBalancer.MAX_MULTIPLIER;
        double clamped = Math.max(minMult, Math.min(maxMult, m));

        double gpuUtil = GpuRamBalancer.getLastGpuUtil();
        double gpuTarget = GpuRamBalancer.getGpuSoftTarget();
        boolean gpuHigh = gpuUtil > gpuTarget;
        if (gpuHigh && aggressiveMode) {
            clamped = Math.min(maxMult, clamped + 0.04);
            gpuPressureBoost += 0.05 * (clamped - gpuPressureBoost);
            gpuDrivenCacheGrowths++;
        } else {
            gpuPressureBoost += 0.08 * (0.0 - gpuPressureBoost);
        }

        if (Math.abs(clamped - prev) < 0.015 && Math.abs(clamped - 1.0) > 0.02) return;

        multiplier = clamped;
        int removed = ChunkPosCache.applyMultiplierAndTrim(clamped);
        evictions += removed;
        totalTrimmedEntries += removed;
        chunkResizes++;
        currentSize = ChunkPosCache.sizeInternal();

        long now = System.currentTimeMillis();
        if (now - lastChunkResizeFireMs >= EVENT_THROTTLE_MS || removed > 0) {
            lastChunkResizeFireMs = now;
            fireEvent(CacheEvent.builder()
                    .cacheType(CacheEvent.CacheType.CHUNK_POS)
                    .action(CacheEvent.Action.RESIZE)
                    .currentSize(currentSize)
                    .maxSize(currentMaxSize())
                    .entriesAffected(removed)
                    .keepFraction(clamped)
                    .detail("Multiplier " + String.format("%.2f", prev) + " -> " + String.format("%.2f", clamped)
                            + " removed=" + removed + " resizes=" + chunkResizes
                            + " gpuBoost=" + (gpuHigh ? "ON" : "OFF"))
                    .build());
        }

        if ((DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false))
                && now - lastDebugLogMs >= DEBUG_LOG_THROTTLE_MS) {
            lastDebugLogMs = now;
            LOGGER.info("[CacheSystem] applyCacheMultiplier {} -> {} (chunks: {} removed, size={}/{}, resize #{}, gpuBoost={})",
                    String.format("%.2f", prev), String.format("%.2f", clamped),
                    removed, currentSize, currentMaxSize(), chunkResizes,
                    gpuHigh ? String.format("ON(%.0f%% GPU)", gpuUtil * 100) : "OFF");
        }
    }

    /* ================================================================
       HIT / MISS / EVICTION kayıtları (aggregate metrikler)
       =================================================================*/

    public static void recordHit() {
        hits++;
        if (shouldFire()) {
            fireEvent(CacheEvent.builder()
                    .cacheType(CacheEvent.CacheType.AGGREGATE)
                    .action(CacheEvent.Action.HIT)
                    .currentSize(currentSize)
                    .maxSize(currentMaxSize())
                    .hitRate(getHitRate())
                    .entriesAffected(1L)
                    .build());
        }
    }

    public static void recordMiss() {
        misses++;
        if (shouldFire()) {
            fireEvent(CacheEvent.builder()
                    .cacheType(CacheEvent.CacheType.AGGREGATE)
                    .action(CacheEvent.Action.MISS)
                    .currentSize(currentSize)
                    .maxSize(currentMaxSize())
                    .hitRate(getHitRate())
                    .entriesAffected(1L)
                    .build());
        }
    }

    public static void recordEviction() {
        evictions++;
        recordEvictionInternal();
    }

    private static void recordEvictionInternal() {
        if (shouldFire()) {
            fireEvent(CacheEvent.builder()
                    .cacheType(CacheEvent.CacheType.AGGREGATE)
                    .action(CacheEvent.Action.EVICTION)
                    .currentSize(currentSize)
                    .maxSize(currentMaxSize())
                    .hitRate(getHitRate())
                    .entriesAffected(1L)
                    .build());
        }
    }

    @Deprecated
    public static void setSize(int size) {
        currentSize = size;
    }

    public static int size() {
        return currentSize;
    }

    public static double getHitRate() {
        long total = hits + misses;
        if (total == 0L) {
            lastHitRate = 0.0;
            return 0.0;
        }
        lastHitRate = (double) hits / (double) total;
        return lastHitRate;
    }

    public static long getHits() { return hits; }
    public static long getMisses() { return misses; }
    public static long getEvictions() { return evictions; }
    public static long getAutoTrimCount() { return autoTrimCount; }
    public static long getTotalTrimmedEntries() { return totalTrimmedEntries; }
    public static double getLastHitRate() { return lastHitRate; }
    public static long getChunkResizes() { return chunkResizes; }

    public static void trimAll(double keepFraction) {
        keepFraction = Math.max(0.0, Math.min(1.0, keepFraction));
        int before = currentSize;
        if (keepFraction >= 1.0) return;
        int removed = chunkTrimToRatio(keepFraction);
        totalTrimmedEntries += removed;
        autoTrimCount++;
        lastTrimMs = System.currentTimeMillis();

        if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
            LOGGER.info("[CacheSystem] TrimAll keepFraction={} | before={} after={} removed={}",
                    String.format("%.2f", keepFraction), before, currentSize, removed);
        }

        fireEvent(CacheEvent.builder()
                .cacheType(CacheEvent.CacheType.CHUNK_POS)
                .action(CacheEvent.Action.TRIM)
                .currentSize(currentSize)
                .maxSize(currentMaxSize())
                .hitRate(getHitRate())
                .entriesAffected(removed)
                .keepFraction(keepFraction)
                .detail("Manual trim to fraction " + keepFraction)
                .build());
    }

    public static void autoTrimIfNeeded() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.AUTO_TRIM, true)) return;
        long now = System.currentTimeMillis();
        if (now - lastTrimMs < 15_000L) return;

        init();

        double heapUsage = GCStutterGuard.getHeapUsage();
        double gcTimeShare = GCStutterGuard.getGcTimeShare();
        double ramCap = GpuRamBalancer.getHardRamCap();
        double gpuCap = GpuRamBalancer.getHardGpuCap();
        double gpuSoft = GpuRamBalancer.getGpuSoftTarget();
        double lastGpu = GpuRamBalancer.getLastGpuUtil();
        double lastRam = GpuRamBalancer.getLastRamUtil();
        int maxS = currentMaxSize();
        double fillRatio = maxS > 0 ? (double) currentSize / (double) maxS : 0.0;

        boolean gpuElevated = lastGpu > gpuSoft && aggressiveMode;
        boolean ramHeadroomAvailable = lastRam < ramCap * 0.80;

        boolean shouldTrim = (heapUsage > ramCap * 0.88 && fillRatio > 0.5)
                || (gcTimeShare > 0.10 && fillRatio > 0.3)
                || (fillRatio > 0.98)
                || (heapUsage > ramCap);

        if (gpuElevated && ramHeadroomAvailable && fillRatio < 0.95) {
            shouldTrim = false;
        }

        if (!shouldTrim) return;

        lastTrimMs = now;
        double targetFraction;
        String reason;

        boolean ramHard = heapUsage > ramCap;
        boolean gpuHard = lastGpu > gpuCap;

        if (ramHard || fillRatio > 0.98) {
            targetFraction = gpuHard ? 0.45 : 0.35;
            reason = "Critical pressure: heap=" + Math.round(heapUsage * 100)
                    + "/cap=" + Math.round(ramCap * 100)
                    + "% fill=" + Math.round(fillRatio * 100) + "%"
                    + (gpuHard ? " GPU>HARD" : "");
        } else if (heapUsage > ramCap * 0.88) {
            targetFraction = gpuElevated ? 0.70 : 0.55;
            reason = "High pressure: heap=" + Math.round(heapUsage * 100)
                    + "% GPU=" + Math.round(lastGpu * 100) + "/" + Math.round(gpuCap * 100) + "%"
                    + (gpuElevated ? " (preserving for GPU offload)" : "");
        } else {
            targetFraction = gpuElevated ? 0.85 : 0.70;
            reason = "GC pressure: " + Math.round(gcTimeShare * 100) + "%"
                    + (gpuElevated ? " (preserving for GPU offload)" : "");
        }

        int removed = chunkTrimToRatio(targetFraction);
        totalTrimmedEntries += removed;
        autoTrimCount++;

        if ((DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false))
                && now - lastDebugLogMs >= DEBUG_LOG_THROTTLE_MS) {
            lastDebugLogMs = now;
            LOGGER.info("[CacheSystem] AutoTrim | {} | removed={} | targetFraction={} | newSize={}/{} | aggressive={}",
                    reason, removed, String.format("%.2f", targetFraction), currentSize, currentMaxSize(),
                    aggressiveMode);
        }

        fireEvent(CacheEvent.builder()
                .cacheType(CacheEvent.CacheType.CHUNK_POS)
                .action(CacheEvent.Action.AUTO_TRIM)
                .currentSize(currentSize)
                .maxSize(currentMaxSize())
                .hitRate(getHitRate())
                .entriesAffected(removed)
                .keepFraction(targetFraction)
                .detail(reason)
                .build());
    }

    public static void clearAll() {
        chunkClear();
        currentSize = 0;
        if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
            LOGGER.info("[CacheSystem] All caches cleared (hits={} misses={} evictions={})", hits, misses, evictions);
        }
        fireEvent(CacheEvent.builder()
                .cacheType(CacheEvent.CacheType.AGGREGATE)
                .action(CacheEvent.Action.CLEAR)
                .currentSize(0)
                .maxSize(currentMaxSize())
                .entriesAffected(hits + misses)
                .detail("Full cache clear")
                .build());
    }

    private static boolean shouldFire() {
        long now = System.currentTimeMillis();
        if (now - lastEventFireMs >= EVENT_THROTTLE_MS) {
            lastEventFireMs = now;
            return true;
        }
        return false;
    }

    private static void fireEvent(CacheEvent event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (Exception e) {
            if (DemonCoreConfig.isDebug()) {
                LOGGER.warn("[CacheSystem] Event post failed: {}", e.getMessage());
            }
        }
    }

    public static long getGpuDrivenCacheGrowths() {
        return gpuDrivenCacheGrowths;
    }

    public static String getStats() {
        init();
        int chunkCacheSize = chunkSize();
        currentSize = chunkCacheSize;
        return String.format("CacheSystem[ChunkPosCache]: %d/%d entries (mult %s) | hitRate %.1f%% | %dH/%dM/%dE | "
                        + "autoTrim=%d (total %d removed) | chunkResizes=%d | "
                        + "aggressive=%s gpuBoost=%.2f gpuGrowths=%d smartPathHits=%d compatPrefetch=%.2f",
                currentSize, currentMaxSize(),
                String.format("%.2f", multiplier),
                getHitRate() * 100.0,
                hits, misses, evictions,
                autoTrimCount, totalTrimmedEntries,
                chunkResizes,
                aggressiveMode ? "ON" : "OFF", gpuPressureBoost, gpuDrivenCacheGrowths,
                smartPathHits, compatPrefetchMult);
    }
}
