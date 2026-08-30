package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.event.PredictiveFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PredictiveFrameScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore/PredSched");

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
    private static volatile long budgetExceededCount;
    private static volatile long severeOverrunCount;
    private static volatile double lastPredictedMs;
    private static volatile double lastBudgetMs;
    private static volatile double lastCpuMs;
    private static volatile double lastGpuMs;
    private static volatile double avgHeadroom;
    private static volatile double minHeadroom;
    private static volatile double maxHeadroom;
    private static long lastFrameStartNs;
    private static long lastEventFireMs;
    private static volatile boolean initialized;

    private static final long EVENT_THROTTLE_MS = 1500L;
    private static final long DEBUG_LOG_THROTTLE_MS = 3000L;
    private static long lastDebugLogMs;

    private static double emaPredictedMs = 16.0;

    public static void onFrameStart() {
        boolean enabled = DemonCoreConfig.getBool(DemonCoreConfig.PREDICTIVE_SCHEDULER, true);
        long now = System.nanoTime();
        lastFrameStartNs = now;

        if (!enabled) {
            if (shouldFireEvent()) {
                fireEvent(PredictiveFrameEvent.builder()
                        .phase(PredictiveFrameEvent.Phase.DISABLED)
                        .schedulerEnabled(false)
                        .build());
            }
            return;
        }

        if (!initialized) {
            initialized = true;
            if (DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false)) {
                LOGGER.info("[PredSched] PredictiveFrameScheduler initialized | window={} samples", WINDOW);
            }
        }

        framesPredicted++;

        double predCpu = percentile(CPU_WINDOW, count, 0.95);
        double predGpu = percentile(GPU_WINDOW, count, 0.90);
        double predicted = predCpu + predGpu;

        emaPredictedMs += 0.25 * (predicted - emaPredictedMs);
        lastPredictedMs = emaPredictedMs;
        lastCpuMs = predCpu;
        lastGpuMs = predGpu;

        double budget = FrameProfiler.targetFrameTimeMs();
        double headroom = DemonCoreConfig.getDouble(DemonCoreConfig.PREDICTIVE_HEADROOM, 0.10);
        double effectiveBudget = budget * (1.0 - headroom);
        lastBudgetMs = effectiveBudget;

        updateHeadroomStats(predicted, effectiveBudget);

        double overrun = predicted - effectiveBudget;
        BottleneckDetector.Bottleneck activeBn = BottleneckDetector.get();
        double bnSleepBoost = 1.0;
        if (activeBn == BottleneckDetector.Bottleneck.GPU) {
            bnSleepBoost = 1.35;
        } else if (activeBn == BottleneckDetector.Bottleneck.CPU_RENDER) {
            bnSleepBoost = 1.18;
        }
        if (overrun > 0.0) {
            budgetExceededCount++;
            if (overrun > budget * 0.15) {
                severeOverrunCount++;
            }

            long sleepNs = Math.min(3_000_000L, (long) (overrun * 500_000.0 * bnSleepBoost));
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
                long actualSlept = Math.max(0L, after - before);
                microSleepsIssued++;
                totalSleptNs += actualSlept;
                savedFromOverrun++;

                if (shouldFireEvent()) {
                    fireEvent(PredictiveFrameEvent.builder()
                            .phase(PredictiveFrameEvent.Phase.PREEMPTIVE_SLEEP_ISSUED)
                            .predictedMs(predicted)
                            .budgetMs(effectiveBudget)
                            .cpuMs(predCpu)
                            .gpuMs(predGpu)
                            .headroomFraction(headroom)
                            .sleepNs(actualSlept)
                            .framesPredicted(framesPredicted)
                            .savedFromOverrun(savedFromOverrun)
                            .microSleepsIssued(microSleepsIssued)
                            .build());
                }

                debugLogSleep(predicted, effectiveBudget, actualSlept, predCpu, predGpu);
            } else {
                if (shouldFireEvent()) {
                    fireEvent(PredictiveFrameEvent.builder()
                            .phase(PredictiveFrameEvent.Phase.OVERRUN_PREVENTED)
                            .predictedMs(predicted)
                            .budgetMs(effectiveBudget)
                            .cpuMs(predCpu)
                            .gpuMs(predGpu)
                            .headroomFraction(headroom)
                            .framesPredicted(framesPredicted)
                            .savedFromOverrun(savedFromOverrun)
                            .build());
                }
            }

            if (shouldFireEvent() && predicted > effectiveBudget * 1.15) {
                fireEvent(PredictiveFrameEvent.builder()
                        .phase(PredictiveFrameEvent.Phase.BUDGET_EXCEEDED)
                        .predictedMs(predicted)
                        .budgetMs(effectiveBudget)
                        .cpuMs(predCpu)
                        .gpuMs(predGpu)
                        .headroomFraction(headroom)
                        .framesPredicted(framesPredicted)
                        .build());
            }
        } else {
            if (shouldFireEvent()) {
                fireEvent(PredictiveFrameEvent.builder()
                        .phase(PredictiveFrameEvent.Phase.FRAME_START)
                        .predictedMs(predicted)
                        .budgetMs(effectiveBudget)
                        .cpuMs(predCpu)
                        .gpuMs(predGpu)
                        .headroomFraction(headroom)
                        .framesPredicted(framesPredicted)
                        .savedFromOverrun(savedFromOverrun)
                        .microSleepsIssued(microSleepsIssued)
                        .build());
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

        if (shouldFireEvent()) {
            fireEvent(PredictiveFrameEvent.builder()
                    .phase(PredictiveFrameEvent.Phase.PREDICTION_UPDATED)
                    .predictedMs(lastPredictedMs)
                    .budgetMs(lastBudgetMs)
                    .cpuMs(cpu)
                    .gpuMs(gpu)
                    .framesPredicted(framesPredicted)
                    .build());
        }
    }

    private static void updateHeadroomStats(double predicted, double budget) {
        double head = Math.max(0.0, budget - predicted);
        if (framesPredicted == 1L) {
            avgHeadroom = head;
            minHeadroom = head;
            maxHeadroom = head;
        } else {
            avgHeadroom += 0.05 * (head - avgHeadroom);
            minHeadroom = Math.min(minHeadroom, head);
            maxHeadroom = Math.max(maxHeadroom, head);
        }
    }

    private static void debugLogSleep(double predicted, double budget, long sleepNs, double cpu, double gpu) {
        if (!(DemonCoreConfig.isDebug() || DemonCoreConfig.getBool(DemonCoreConfig.DEBUG_LOGGING, false))) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDebugLogMs < DEBUG_LOG_THROTTLE_MS) {
            return;
        }
        lastDebugLogMs = now;

        LOGGER.info("[PredSched] Preemptive sleep | predict={}ms / budget={}ms | overrun={}ms | slept={}us | CPU={}ms GPU={}ms | totalSaved={}/{} frames",
                String.format("%.2f", predicted),
                String.format("%.2f", budget),
                String.format("%.2f", predicted - budget),
                sleepNs / 1000L,
                String.format("%.2f", cpu),
                String.format("%.2f", gpu),
                savedFromOverrun, framesPredicted);
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
    public static long getBudgetExceededCount() { return budgetExceededCount; }
    public static long getSevereOverrunCount() { return severeOverrunCount; }
    public static double getLastPredictedMs() { return lastPredictedMs; }
    public static double getLastBudgetMs() { return lastBudgetMs; }
    public static double getAvgHeadroomMs() { return avgHeadroom; }
    public static double getMinHeadroomMs() { return minHeadroom; }
    public static double getMaxHeadroomMs() { return maxHeadroom; }

    private static boolean shouldFireEvent() {
        long now = System.currentTimeMillis();
        if (now - lastEventFireMs >= EVENT_THROTTLE_MS) {
            lastEventFireMs = now;
            return true;
        }
        return false;
    }

    private static void fireEvent(PredictiveFrameEvent event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (Exception e) {
            if (DemonCoreConfig.isDebug()) {
                LOGGER.warn("[PredSched] Event post failed: {}", e.getMessage());
            }
        }
    }

    public static String getStats() {
        boolean on = DemonCoreConfig.getBool(DemonCoreConfig.PREDICTIVE_SCHEDULER, true);
        if (!on) {
            return "PredictiveSched: OFF";
        }
        long severe = severeOverrunCount;
        return String.format("PredictiveSched: pred %.2f ms / budget %.2f ms | %d sleeps (%d us total) | %d/%d preempted | %d severe overruns | headroom avg/min/max %.2f/%.2f/%.2f ms",
                lastPredictedMs, lastBudgetMs,
                microSleepsIssued, getTotalSleptUs(), savedFromOverrun, framesPredicted, severe,
                avgHeadroom, minHeadroom, maxHeadroom);
    }
}
