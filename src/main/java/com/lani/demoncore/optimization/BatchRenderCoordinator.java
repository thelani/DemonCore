package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public final class BatchRenderCoordinator {

    private BatchRenderCoordinator() {
    }

    private static final IntList BATCH_SIZES_THIS_FRAME = new IntArrayList(256);

    private static volatile int drawCallsRaw;
    private static volatile int drawCallsBatched;
    private static volatile int quadsMerged;
    private static volatile int batchesFlushed;
    private static volatile long totalQuadsMerged;
    private static volatile long totalRawCalls;
    private static volatile long totalBatchedCalls;

    private static int pendingBatchSize;
    private static int pendingBatchShaderId = -1;

    
    public static void beginFrame() {
        BATCH_SIZES_THIS_FRAME.clear();
        drawCallsRaw = 0;
        drawCallsBatched = 0;
        quadsMerged = 0;
        batchesFlushed = 0;
        pendingBatchSize = 0;
        pendingBatchShaderId = -1;
    }

    private static int batchBufferSize() {
        return DemonCoreConfig.getInt(DemonCoreConfig.BATCH_BUFFER_SIZE, 2048);
    }

    
    public static boolean offer(int shaderId, int quads) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.BATCH_RENDER_ENABLED, true)) {
            drawCallsRaw++;
            totalRawCalls++;
            return false;
        }
        drawCallsRaw++;
        totalRawCalls++;

        if (pendingBatchShaderId == -1) {
            pendingBatchShaderId = shaderId;
            pendingBatchSize = quads;
            return true;
        }
        if (pendingBatchShaderId == shaderId && pendingBatchSize + quads <= batchBufferSize()) {
            pendingBatchSize += quads;
            quadsMerged += quads;
            totalQuadsMerged += quads;
            return true;
        }

        flushInternal();
        pendingBatchShaderId = shaderId;
        pendingBatchSize = quads;
        return true;
    }

    
    public static void flush() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.BATCH_RENDER_ENABLED, true)) {
            return;
        }
        flushInternal();
    }

    private static void flushInternal() {
        if (pendingBatchSize > 0) {
            drawCallsBatched++;
            totalBatchedCalls++;
            batchesFlushed++;
            BATCH_SIZES_THIS_FRAME.add(pendingBatchSize);
            pendingBatchSize = 0;
            pendingBatchShaderId = -1;
        }
    }

    
    public static void endFrame() {
        flushInternal();
    }

    
    
    

    public static double getMergeRate() {
        long total = totalRawCalls;
        if (total == 0) return 0.0;
        return (double) (totalRawCalls - totalBatchedCalls) / (double) total;
    }

    public static long getRawCalls() { return totalRawCalls; }
    public static long getBatchedCalls() { return totalBatchedCalls; }
    public static long getQuadsMerged() { return totalQuadsMerged; }

    public static String getStats() {
        int avg = 0;
        if (!BATCH_SIZES_THIS_FRAME.isEmpty()) {
            int sum = 0;
            for (int i = 0; i < BATCH_SIZES_THIS_FRAME.size(); i++) {
                sum += BATCH_SIZES_THIS_FRAME.getInt(i);
            }
            avg = sum / BATCH_SIZES_THIS_FRAME.size();
        }
        return String.format("BatchRender: %d raw -> %d batched (%.1f%% merge) | %d quads merged | avg %d quads/batch | cap %d",
                drawCallsRaw, drawCallsBatched,
                drawCallsRaw == 0 ? 0.0 : 100.0 * (drawCallsRaw - drawCallsBatched) / (double) drawCallsRaw,
                quadsMerged, avg, batchBufferSize());
    }
}
