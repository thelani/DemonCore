package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class PredictiveFrameScheduler {

    private PredictiveFrameScheduler() {
    }

    private static final int WINDOW = 64;
    private static final double[] CPU_WINDOW = new double[WINDOW];
    private static final double[] GPU_WINDOW = new double[WINDOW];
    private static int cursor;
    private static int count;

    private static volatile long microSleepsIssued;
    private static volatile long totalSleptNs;
    private static volatile long framesPredicted;
    private static volatile long savedFromOverrun;
    private static volatile double lastPredictedMs;
    private static volatile double lastBudgetMs;
    private static long lastFrameStartNs;

    
    public static void onFrameStart() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.PREDICTIVE_SCHEDULER, true)) {
            lastFrameStartNs = System.nanoTime();
            return;
        }
        long now = System.nanoTime();
        lastFrameStartNs = now;

        framesPredicted++;

        
        double predCpu = percentile(CPU_WINDOW, count, 0.95);
        double predGpu = percentile(GPU_WINDOW, count, 0.90);
        double predicted = predCpu + predGpu;
        lastPredictedMs = predicted;

        double budget = FrameProfiler.targetFrameTimeMs();
        double headroom = DemonCoreConfig.getDouble(DemonCoreConfig.PREDICTIVE_HEADROOM, 0.10);
        double effectiveBudget = budget * (1.0 - headroom);
        lastBudgetMs = effectiveBudget;

        double overrun = predicted - effectiveBudget;
        if (overrun > 0.0) {
            
            
            
            long sleepNs = Math.min(2_000_000L, (long) (overrun * 500_000.0)); 
            if (sleepNs > 50_000L) {
                long before = System.nanoTime();
                try {
                    long ms = sleepNs / 1_000_000L;
                    int ns = (int) (sleepNs % 1_000_000L);
                    if (ms > 0L) {
                        Thread.sleep(ms, ns);
                    } else {
                        Thread.sleep(0L, ns);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                long after = System.nanoTime();
                microSleepsIssued++;
                totalSleptNs += Math.max(0L, after - before);
                savedFromOverrun++;
            }
        }
    }

    
    public static void onFrameEnd() {
        double cpu = FrameProfiler.getCpuRenderMs();
        double gpu = FrameProfiler.getGpuWaitMs();
        synchronized (CPU_WINDOW) {
            CPU_WINDOW[cursor] = cpu;
            GPU_WINDOW[cursor] = gpu;
            cursor = (cursor + 1) % WINDOW;
            if (count < WINDOW) count++;
        }
    }

    
    private static double percentile(double[] arr, int n, double pct) {
        if (n <= 0) return 0.0;
        
        double[] sorted = new double[n];
        synchronized (arr) {
            System.arraycopy(arr, 0, sorted, 0, n);
        }
        java.util.Arrays.sort(sorted);
        int rank = (int) Math.min(n - 1, Math.max(0, Math.round(pct * (n - 1))));
        return sorted[rank];
    }

    public static long getMicroSleeps() { return microSleepsIssued; }
    public static long getTotalSleptUs() { return totalSleptNs / 1000L; }
    public static long getSavedFromOverrun() { return savedFromOverrun; }
    public static double getLastPredictedMs() { return lastPredictedMs; }
    public static double getLastBudgetMs() { return lastBudgetMs; }

    public static String getStats() {
        boolean on = DemonCoreConfig.getBool(DemonCoreConfig.PREDICTIVE_SCHEDULER, true);
        if (!on) {
            return "PredictiveSched: OFF";
        }
        return String.format("PredictiveSched: pred %.2f ms / budget %.2f ms | %d sleeps (%d us total) | %d/%d frames preempted | 1%% low smoother",
                lastPredictedMs, lastBudgetMs,
                microSleepsIssued, getTotalSleptUs(), savedFromOverrun, framesPredicted);
    }
}
