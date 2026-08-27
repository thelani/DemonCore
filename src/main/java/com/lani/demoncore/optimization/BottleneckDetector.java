package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class BottleneckDetector {

    private BottleneckDetector() {
    }

    public enum Bottleneck {
        
        UNKNOWN("Measuring"),
        
        FRAME_CAP("Frame limit"),
        
        GPU("GPU bound"),
        
        CPU_RENDER("CPU bound (rendering)"),
        
        CPU_LOGIC("CPU bound (game logic)"),
        
        MEMORY("Memory / GC bound"),
        
        BALANCED("Balanced");

        private final String label;

        Bottleneck(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static volatile Bottleneck current = Bottleneck.UNKNOWN;
    private static volatile String advice = "";
    private static long lastEvalNs;

    
    public static void update() {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.BOTTLENECK_DETECTION, true)) {
            current = Bottleneck.UNKNOWN;
            return;
        }

        long now = System.nanoTime();
        if (now - lastEvalNs < 500_000_000L) {
            return;
        }
        lastEvalNs = now;

        double total = FrameProfiler.getFrameTimeMs();
        if (total < 0.1 || FrameProfiler.getTotalFrames() < 120) {
            current = Bottleneck.UNKNOWN;
            advice = "";
            return;
        }

        double cpu = FrameProfiler.getCpuRenderMs();
        double gpuWait = FrameProfiler.getGpuWaitMs();
        double other = Math.max(0.0, total - cpu - gpuWait);

        double budget = FrameProfiler.targetFrameTimeMs();
        boolean hittingTarget = total <= budget * 1.10;

        double cpuShare = cpu / total;
        double gpuShare = gpuWait / total;
        double otherShare = other / total;

        double gcShare = GCStutterGuard.getGcTimeShare();

        Bottleneck result;
        String tip;

        if (gcShare > 0.06) {
            result = Bottleneck.MEMORY;
            tip = "Garbage collection is eating " + Math.round(gcShare * 100)
                    + "% of wall clock time. Reduce allocated RAM to 4-6 GB and use -XX:+UseG1GC.";
        } else if (hittingTarget && gpuShare > 0.25) {
            result = Bottleneck.FRAME_CAP;
            tip = "You are at your framerate limit. Raise or disable the FPS cap to go faster.";
        } else if (gpuShare > 0.35) {
            result = Bottleneck.GPU;
            tip = "The GPU is the limit. Lower resolution, shader quality or render distance.";
        } else if (cpuShare > 0.55) {
            result = Bottleneck.CPU_RENDER;
            tip = "Draw call submission is the limit. Lower entity and block entity cull distances.";
        } else if (otherShare > 0.45) {
            result = Bottleneck.CPU_LOGIC;
            tip = "Time is lost outside rendering - the GPU is idling. Likely the integrated server, "
                    + "chunk meshing or GC. Lower simulation distance and chunksPerTick.";
        } else {
            result = Bottleneck.BALANCED;
            tip = "No single dominant cost.";
        }

        current = result;
        advice = tip;
    }

    public static Bottleneck get() {
        return current;
    }

    public static String getAdvice() {
        return advice;
    }

    
    public static double getNonRenderShare() {
        double total = FrameProfiler.getFrameTimeMs();
        if (total < 0.1) {
            return 0.0;
        }
        double other = total - FrameProfiler.getCpuRenderMs() - FrameProfiler.getGpuWaitMs();
        return Math.max(0.0, Math.min(1.0, other / total));
    }

    public static String getStats() {
        double total = Math.max(0.001, FrameProfiler.getFrameTimeMs());
        return String.format("%s | CPU %.0f%% / GPU wait %.0f%% / other %.0f%%",
                current.label(),
                (FrameProfiler.getCpuRenderMs() / total) * 100.0,
                (FrameProfiler.getGpuWaitMs() / total) * 100.0,
                getNonRenderShare() * 100.0);
    }
}
