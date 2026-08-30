package com.lani.demoncore.optimization;

import com.lani.demoncore.compat.chunk.ChunkModCompat;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.event.HardwarePressureEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;

public final class HardwareMonitor {

    private HardwareMonitor() {
    }

    public enum HardwareTier {
        LOW_END(0.35),
        MID_RANGE(0.65),
        HIGH_END(0.85),
        ENTHUSIAST(1.0);

        private final double qualityCap;

        HardwareTier(double qualityCap) {
            this.qualityCap = qualityCap;
        }

        public double qualityCap() {
            return qualityCap;
        }
    }

    public enum ComponentPressure {
        IDLE(0.0, 0.20),
        LOW(0.20, 0.45),
        MODERATE(0.45, 0.70),
        HIGH(0.70, 0.88),
        CRITICAL(0.88, 1.01);

        private final double min;
        private final double max;

        ComponentPressure(double min, double max) {
            this.min = min;
            this.max = max;
        }

        public boolean contains(double value) {
            return value >= min && value < max;
        }
    }

    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();

    private static final int SAMPLES = 32;
    private static final long[] tickCpuTimes = new long[SAMPLES];
    private static final long[] tickWallTimes = new long[SAMPLES];
    private static int sampleIndex;
    private static int sampleCount;
    private static long lastProcessCpuNs;
    private static long lastWallClockNs;
    private static long lastEvalMs;

    private static volatile double heapUsage;
    private static volatile double nonHeapUsage;
    private static volatile double totalRamUsage;
    private static volatile double processCpuLoad;
    private static volatile double systemCpuLoad;
    private static volatile double gpuUtilization;
    private static volatile long availableProcessors;
    private static volatile long totalMemoryMb;
    private static volatile HardwareTier hardwareTier = HardwareTier.MID_RANGE;
    private static volatile ComponentPressure cpuPressure = ComponentPressure.IDLE;
    private static volatile ComponentPressure ramPressure = ComponentPressure.IDLE;
    private static volatile ComponentPressure gpuPressure = ComponentPressure.IDLE;
    private static volatile long evaluations;

    private static ComponentPressure lastReportedCpu = ComponentPressure.IDLE;
    private static ComponentPressure lastReportedRam = ComponentPressure.IDLE;
    private static ComponentPressure lastReportedGpu = ComponentPressure.IDLE;

    private static volatile double emaCpu = 0.2;
    private static volatile double emaRam = 0.3;
    private static volatile double emaGpu = 0.2;

    public static void init() {
        try {
            availableProcessors = Runtime.getRuntime().availableProcessors();
            MemoryUsage heap = MEMORY_BEAN.getHeapMemoryUsage();
            MemoryUsage nonHeap = MEMORY_BEAN.getNonHeapMemoryUsage();
            long max = Math.max(1L, heap.getMax() == -1 ? heap.getUsed() : heap.getMax());
            totalMemoryMb = max / (1024L * 1024L);
            detectHardwareTier();
            lastProcessCpuNs = readProcessCpuNs();
            lastWallClockNs = System.nanoTime();
        } catch (Throwable ignored) {
            availableProcessors = 4;
            totalMemoryMb = 4096;
            hardwareTier = HardwareTier.MID_RANGE;
        }
    }

    private static void detectHardwareTier() {
        long memGb = totalMemoryMb / 1024L;
        long cores = availableProcessors;

        double memScore = Math.min(1.0, memGb / 16.0);
        double cpuScore = Math.min(1.0, cores / 16.0);
        double composite = (memScore * 0.55) + (cpuScore * 0.45);

        if (composite >= 0.88 && memGb >= 12 && cores >= 12) {
            hardwareTier = HardwareTier.ENTHUSIAST;
        } else if (composite >= 0.60 && memGb >= 6 && cores >= 6) {
            hardwareTier = HardwareTier.HIGH_END;
        } else if (composite >= 0.30 && memGb >= 3 && cores >= 3) {
            hardwareTier = HardwareTier.MID_RANGE;
        } else {
            hardwareTier = HardwareTier.LOW_END;
        }
    }

    public static void evaluate() {
        long now = System.currentTimeMillis();
        if (now - lastEvalMs < 250L) {
            return;
        }
        lastEvalMs = now;
        evaluations++;

        updateMemoryStats();
        updateCpuStats();
        updateGpuStats();

        ComponentPressure oldCpu = cpuPressure;
        ComponentPressure oldRam = ramPressure;
        ComponentPressure oldGpu = gpuPressure;
        cpuPressure = classify(emaCpu);
        ramPressure = classify(emaRam);
        gpuPressure = classify(emaGpu);

        boolean changed = oldCpu != cpuPressure || oldRam != ramPressure || oldGpu != gpuPressure;
        boolean isCritNow = cpuPressure == ComponentPressure.CRITICAL
                || ramPressure == ComponentPressure.CRITICAL
                || gpuPressure == ComponentPressure.CRITICAL;
        boolean allIdle = cpuPressure == ComponentPressure.IDLE
                && ramPressure == ComponentPressure.IDLE
                && gpuPressure == ComponentPressure.IDLE;

        HardwarePressureEvent.Trigger trigger = null;
        if (oldCpu != cpuPressure) trigger = HardwarePressureEvent.Trigger.CPU_PRESSURE_CHANGED;
        else if (oldRam != ramPressure) trigger = HardwarePressureEvent.Trigger.RAM_PRESSURE_CHANGED;
        else if (oldGpu != gpuPressure) trigger = HardwarePressureEvent.Trigger.GPU_PRESSURE_CHANGED;
        else if (isCritNow) trigger = HardwarePressureEvent.Trigger.ANY_CRITICAL;
        else if (allIdle && (lastReportedCpu != ComponentPressure.IDLE
                || lastReportedRam != ComponentPressure.IDLE
                || lastReportedGpu != ComponentPressure.IDLE)) {
            trigger = HardwarePressureEvent.Trigger.ALL_IDLE;
        }

        if (trigger != null || (evaluations % 40 == 0)) {
            boolean reportable = changed
                    || (lastReportedCpu != cpuPressure)
                    || (lastReportedRam != ramPressure)
                    || (lastReportedGpu != gpuPressure)
                    || (evaluations % 120 == 0);
            if (reportable) {
                lastReportedCpu = cpuPressure;
                lastReportedRam = ramPressure;
                lastReportedGpu = gpuPressure;
                postPressureEvent(trigger == null ? HardwarePressureEvent.Trigger.ANY_CRITICAL : trigger);
            }
        }
    }

    private static void postPressureEvent(HardwarePressureEvent.Trigger trigger) {
        try {
            HardwarePressureEvent event = new HardwarePressureEvent(
                    cpuPressure, ramPressure, gpuPressure,
                    hardwareTier, trigger, getSystemScore());
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        } catch (Throwable ignored) {
        }
    }

    private static void updateMemoryStats() {
        try {
            MemoryUsage heap = MEMORY_BEAN.getHeapMemoryUsage();
            MemoryUsage nonHeap = MEMORY_BEAN.getNonHeapMemoryUsage();

            long hUsed = heap.getUsed();
            long hMax = heap.getMax() == -1 ? Runtime.getRuntime().maxMemory() : heap.getMax();
            long nhUsed = nonHeap.getUsed();
            long nhMax = Math.max(1L, nonHeap.getMax() == -1 ? nhUsed * 2 : nonHeap.getMax());

            heapUsage = hMax > 0 ? (double) hUsed / (double) hMax : 0.0;
            nonHeapUsage = nhMax > 0 ? (double) nhUsed / (double) nhMax : 0.0;

            double maxRamFraction = DemonCoreConfig.getDouble(DemonCoreConfig.RAM_MAX_USAGE, 0.45);
            totalRamUsage = Math.max(heapUsage, nonHeapUsage) / Math.max(0.1, maxRamFraction);
            totalRamUsage = Math.min(1.0, totalRamUsage);

            emaRam += 0.18 * (totalRamUsage - emaRam);
        } catch (Throwable ignored) {
        }
    }

    private static void updateCpuStats() {
        try {
            long cpuNs = readProcessCpuNs();
            long wallNs = System.nanoTime();

            long deltaCpu = cpuNs - lastProcessCpuNs;
            long deltaWall = wallNs - lastWallClockNs;

            if (deltaWall > 1_000_000L) {
                tickCpuTimes[sampleIndex] = deltaCpu;
                tickWallTimes[sampleIndex] = deltaWall;
                sampleIndex = (sampleIndex + 1) % SAMPLES;
                if (sampleCount < SAMPLES) {
                    sampleCount++;
                }

                long totalCpu = 0L;
                long totalWall = 0L;
                for (int i = 0; i < sampleCount; i++) {
                    totalCpu += tickCpuTimes[i];
                    totalWall += tickWallTimes[i];
                }
                double rawLoad = totalWall > 0L
                        ? Math.min(1.0, (double) totalCpu / (double) (totalWall * availableProcessors))
                        : 0.0;
                double msptFactor = Math.min(1.0, PerformanceMonitor.getAverageMspt() / 50.0);
                processCpuLoad = Math.max(rawLoad, msptFactor);
                systemCpuLoad = readSystemCpuLoad();

                emaCpu += 0.15 * (processCpuLoad - emaCpu);
            }
            lastProcessCpuNs = cpuNs;
            lastWallClockNs = wallNs;
        } catch (Throwable ignored) {
            processCpuLoad = Math.min(1.0, PerformanceMonitor.getAverageMspt() / 50.0);
            emaCpu += 0.15 * (processCpuLoad - emaCpu);
        }
    }

    private static long readProcessCpuNs() {
        try {
            if (OS_BEAN instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getProcessCpuTime();
            }
        } catch (Throwable ignored) {
        }
        return (long) ((double) System.nanoTime() * Math.min(1.0, PerformanceMonitor.getAverageMspt() / 50.0));
    }

    private static double readSystemCpuLoad() {
        try {
            if (OS_BEAN instanceof com.sun.management.OperatingSystemMXBean sun) {
                double load = sun.getCpuLoad();
                if (load >= 0.0 && load <= 1.0) {
                    return load;
                }
            }
        } catch (Throwable ignored) {
        }
        return -1.0;
    }

    private static void updateGpuStats() {
        try {
            double budget = FrameProfiler.targetFrameTimeMs();
            double gpuWait = FrameProfiler.getGpuWaitMs();
            double direct = budget > 0.001 ? Math.min(1.0, gpuWait / budget) : 0.0;
            double profilerUtil = GpuRamBalancer.getLastGpuUtil();
            gpuUtilization = Math.max(direct, profilerUtil);

            BottleneckDetector.update();
            BottleneckDetector.Bottleneck bn = BottleneckDetector.get();
            if (bn == BottleneckDetector.Bottleneck.GPU) {
                gpuUtilization = Math.max(gpuUtilization, 0.75);
            }

            int compatLevel = ChunkModCompat.getCompatibilityLevel();
            if (compatLevel >= 2) {
                gpuUtilization *= 0.90;
                double capReduction = 0.05 * (compatLevel - 1);
                gpuUtilization = Math.max(0.0, gpuUtilization - capReduction);
            }

            emaGpu += 0.18 * (gpuUtilization - emaGpu);
        } catch (Throwable ignored) {
            gpuUtilization = 0.0;
        }
    }

    private static ComponentPressure classify(double value) {
        for (ComponentPressure p : ComponentPressure.values()) {
            if (p.contains(value)) {
                return p;
            }
        }
        return ComponentPressure.CRITICAL;
    }

    public static double getSystemScore() {
        double cpuW = 0.35;
        double ramW = 0.30;
        double gpuW = 0.35;
        double score = 1.0 - (cpuW * emaCpu + ramW * emaRam + gpuW * emaGpu);
        return Math.max(0.0, Math.min(1.0, score));
    }

    public static PerformanceMonitor.Level recommendedOverallLevel() {
        double score = getSystemScore();
        double tierCap = hardwareTier.qualityCap;
        double effective = score * (0.6 + 0.4 * tierCap);

        if (effective >= 0.82) return PerformanceMonitor.Level.EXCELLENT;
        if (effective >= 0.66) return PerformanceMonitor.Level.GOOD;
        if (effective >= 0.50) return PerformanceMonitor.Level.FAIR;
        if (effective >= 0.32) return PerformanceMonitor.Level.POOR;
        return PerformanceMonitor.Level.CRITICAL;
    }

    public static HardwareTier getHardwareTier() { return hardwareTier; }
    public static ComponentPressure getCpuPressure() { return cpuPressure; }
    public static ComponentPressure getRamPressure() { return ramPressure; }
    public static ComponentPressure getGpuPressure() { return gpuPressure; }

    public static double getHeapUsage() { return heapUsage; }
    public static double getNonHeapUsage() { return nonHeapUsage; }
    public static double getTotalRamUsage() { return emaRam; }
    public static double getProcessCpuLoad() { return emaCpu; }
    public static double getSystemCpuLoad() { return systemCpuLoad; }
    public static double getGpuUtilization() { return emaGpu; }
    public static long getAvailableProcessors() { return availableProcessors; }
    public static long getTotalMemoryMb() { return totalMemoryMb; }
    public static long getEvaluations() { return evaluations; }

    public static String getStats() {
        return String.format(
                "HW: %s | CPU %.0f%% (%s) | RAM %.0f%% (%s) | GPU %.0f%% (%s) | score %.0f%% | rec %s",
                hardwareTier.name(),
                emaCpu * 100.0, cpuPressure.name(),
                emaRam * 100.0, ramPressure.name(),
                emaGpu * 100.0, gpuPressure.name(),
                getSystemScore() * 100.0,
                recommendedOverallLevel().name());
    }
}
