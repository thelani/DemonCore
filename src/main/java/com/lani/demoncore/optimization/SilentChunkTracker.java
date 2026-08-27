package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.world.level.ChunkPos;

public final class SilentChunkTracker {

    private SilentChunkTracker() {
    }

    private static final Long2IntLinkedOpenHashMap QUIET_COUNTS = new Long2IntLinkedOpenHashMap(512);
    private static final Long2IntLinkedOpenHashMap SILENT_SET = new Long2IntLinkedOpenHashMap(512);

    private static volatile long totalLookups;
    private static volatile long silentHits;
    private static volatile long promotedToSilent;
    private static volatile long markedDirty;
    private static int silentThresholdTicks;

    private static int threshold() {
        int t = DemonCoreConfig.getInt(DemonCoreConfig.SILENT_CHUNK_TICKS, 20);
        silentThresholdTicks = t;
        return t;
    }

    
    public static void tick() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.SILENT_CHUNK_TRACKER, true)) {
            return;
        }
        int thresh = threshold();
        synchronized (QUIET_COUNTS) {
            var it = QUIET_COUNTS.long2IntEntrySet().fastIterator();
            while (it.hasNext()) {
                Long2IntMap.Entry e = it.next();
                int v = e.getIntValue();
                if (v < thresh * 2) { 
                    e.setValue(v + 1);
                }
                if (v >= thresh) {
                    long key = e.getLongKey();
                    if (!SILENT_SET.containsKey(key)) {
                        SILENT_SET.put(key, v);
                        promotedToSilent++;
                    }
                }
            }
        }
    }

    
    public static void onChunkDirty(int chunkX, int chunkZ) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.SILENT_CHUNK_TRACKER, true)) {
            return;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (QUIET_COUNTS) {
            QUIET_COUNTS.put(key, 0);
            SILENT_SET.remove(key);
            markedDirty++;
        }
    }

    
    public static void registerChunk(int chunkX, int chunkZ) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.SILENT_CHUNK_TRACKER, true)) {
            return;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (QUIET_COUNTS) {
            if (!QUIET_COUNTS.containsKey(key)) {
                QUIET_COUNTS.put(key, 0);
            }
        }
    }

    
    public static boolean skipMeshValidation(int chunkX, int chunkZ,
                                              int playerChunkX, int playerChunkZ) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.SILENT_CHUNK_TRACKER, true)) {
            return false;
        }
        totalLookups++;
        
        if (Math.abs(chunkX - playerChunkX) <= 1 && Math.abs(chunkZ - playerChunkZ) <= 1) {
            return false;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        boolean silent;
        synchronized (QUIET_COUNTS) {
            silent = SILENT_SET.containsKey(key);
        }
        if (silent) silentHits++;
        return silent;
    }

    public static int silentCount() {
        synchronized (QUIET_COUNTS) {
            return SILENT_SET.size();
        }
    }

    public static int trackedCount() {
        synchronized (QUIET_COUNTS) {
            return QUIET_COUNTS.size();
        }
    }

    public static double silentHitRate() {
        return totalLookups == 0L ? 0.0 : (double) silentHits / (double) totalLookups;
    }

    public static String getStats() {
        boolean on = DemonCoreConfig.getBool(DemonCoreConfig.SILENT_CHUNK_TRACKER, true);
        if (!on) return "SilentChunks: OFF";
        return String.format("SilentChunks: %d/%d tracked silent (%.0f%% hit) | %d promoted | %d dirty events | threshold %d ticks",
                silentCount(), trackedCount(),
                silentHitRate() * 100.0, promotedToSilent, markedDirty, silentThresholdTicks);
    }
}
