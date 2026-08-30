package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class PerformanceMonitor {

    private PerformanceMonitor() {
    }

    public enum Level {
        EXCELLENT(1.00),
        GOOD(0.85),
        FAIR(0.65),
        POOR(0.45),
        CRITICAL(0.25);

        private final double budgetScale;

        Level(double budgetScale) {
            this.budgetScale = budgetScale;
        }

        public double budgetScale() {
            return budgetScale;
        }

        public boolean isAtLeast(Level other) {
            return this.ordinal() <= other.ordinal();
        }

        public boolean isWorseThan(Level other) {
            return this.ordinal() > other.ordinal();
        }

        public static Level fromIndex(int index) {
            Level[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, index))];
        }
    }

    public enum AggregateDomain {
        SERVER,
        CLIENT,
        HARDWARE,
        OVERALL
    }

    private static final int MSPT_SAMPLES = 100;
    private static final long[] msptSamples = new long[MSPT_SAMPLES];
    private static int msptIndex;
    private static int msptCount;

    private static long tickStartNs;
    private static volatile double averageMspt;
    private static volatile double peakMspt;
    private static volatile long serverTicks;

    private static volatile Level lastServerLevel = Level.FAIR;
    private static volatile Level lastClientLevel = Level.FAIR;
    private static volatile Level lastHardwareLevel = Level.FAIR;
    private static volatile Level lastOverallLevel = Level.FAIR;
    private static volatile long lastLevelChangeMs;
    private static volatile int consecutiveLevelSamples;
    private static Level pendingLevel = null;
    private static Level pendingLevelTarget = null;
    private static int pendingLevelCount;

    public static void onServerTickStart() {
        tickStartNs = System.nanoTime();
    }

    public static void onServerTickEnd() {
        if (tickStartNs == 0L) {
            return;
        }
        long elapsed = System.nanoTime() - tickStartNs;
        if (elapsed <= 0L || elapsed > 5_000_000_000L) {
            return;
        }

        serverTicks++;
        msptSamples[msptIndex] = elapsed;
        msptIndex = (msptIndex + 1) % MSPT_SAMPLES;
        if (msptCount < MSPT_SAMPLES) {
            msptCount++;
        }

        long sum = 0L;
        long peak = 0L;
        for (int i = 0; i < msptCount; i++) {
            sum += msptSamples[i];
            if (msptSamples[i] > peak) {
                peak = msptSamples[i];
            }
        }
        averageMspt = (sum / (double) msptCount) / 1_000_000.0;
        peakMspt = peak / 1_000_000.0;

        GCStutterGuard.sample();
        HardwareMonitor.evaluate();
        updateAggregateLevels();
    }

    public static double getAverageMspt() {
        return averageMspt;
    }

    public static double getPeakMspt() {
        return peakMspt;
    }

    public static double getTps() {
        double mspt = averageMspt;
        return mspt <= 50.0 ? 20.0 : Math.max(1.0, 1000.0 / mspt);
    }

    public static long getServerTicks() {
        return serverTicks;
    }

    public static Level getServerLevel() {
        double target = DemonCoreConfig.getDouble(DemonCoreConfig.TARGET_MSPT, 38.0);
        double mspt = averageMspt;

        if (mspt <= target * 0.5) {
            return Level.EXCELLENT;
        }
        if (mspt <= target * 0.75) {
            return Level.GOOD;
        }
        if (mspt <= target) {
            return Level.FAIR;
        }
        if (mspt <= target * 1.4) {
            return Level.POOR;
        }
        return Level.CRITICAL;
    }

    public static double getServerHeadroom() {
        double target = DemonCoreConfig.getDouble(DemonCoreConfig.TARGET_MSPT, 38.0);
        if (target <= 0.0) {
            return 1.0;
        }
        double headroom = (target - averageMspt) / target;
        return Math.max(0.0, Math.min(1.0, headroom));
    }

    public static Level getClientLevel() {
        double budget = FrameProfiler.targetFrameTimeMs();
        double frame = FrameProfiler.getFrameTimeMs();

        if (frame <= 0.001) {
            return Level.EXCELLENT;
        }
        double ratio = frame / budget;

        if (ratio <= 0.7) {
            return Level.EXCELLENT;
        }
        if (ratio <= 1.0) {
            return Level.GOOD;
        }
        if (ratio <= 1.35) {
            return Level.FAIR;
        }
        if (ratio <= 2.0) {
            return Level.POOR;
        }
        return Level.CRITICAL;
    }

    public static Level getHardwareLevel() {
        return HardwareMonitor.recommendedOverallLevel();
    }

    private static void updateAggregateLevels() {
        Level s = getServerLevel();
        Level c = FrameProfiler.getTotalFrames() < 60 ? s : getClientLevel();
        Level h = HardwareMonitor.getEvaluations() < 4 ? s : getHardwareLevel();
        Level overall = computeOverall(s, c, h);

        lastServerLevel = s;
        lastClientLevel = c;
        lastHardwareLevel = h;

        if (pendingLevelTarget == null || !pendingLevelTarget.equals(overall)) {
            pendingLevelTarget = overall;
            pendingLevelCount = 1;
        } else {
            pendingLevelCount++;
        }

        int required = overall.isWorseThan(lastOverallLevel) ? 2 : 6;
        if (pendingLevelCount >= required && !overall.equals(lastOverallLevel)) {
            Level previous = lastOverallLevel;
            lastOverallLevel = overall;
            lastLevelChangeMs = System.currentTimeMillis();
            pendingLevelCount = 0;
            consecutiveLevelSamples = 0;
            notifyLevelChange(previous, overall);
        } else {
            consecutiveLevelSamples++;
        }
    }

    private static Level computeOverall(Level server, Level client, Level hardware) {
        HardwareMonitor.HardwareTier tier = HardwareMonitor.getHardwareTier();
        double sW = 0.30;
        double cW = 0.40;
        double hW = 0.30;

        if (tier == HardwareMonitor.HardwareTier.LOW_END) {
            sW = 0.45;
            cW = 0.30;
            hW = 0.25;
        } else if (tier == HardwareMonitor.HardwareTier.ENTHUSIAST) {
            sW = 0.20;
            cW = 0.50;
            hW = 0.30;
        }

        double sVal = Level.values().length - 1 - server.ordinal();
        double cVal = Level.values().length - 1 - client.ordinal();
        double hVal = Level.values().length - 1 - hardware.ordinal();

        double weighted = sW * sVal + cW * cVal + hW * hVal;
        int max = Level.values().length - 1;
        int idx = max - (int) Math.round(weighted);
        idx = Math.max(0, Math.min(max, idx));
        return Level.fromIndex(idx);
    }

    private static void notifyLevelChange(Level previous, Level current) {
        try {
            Class<?> eventCls = Class.forName("com.lani.demoncore.event.OptimizationLevelChangeEvent");
            Object event = eventCls
                    .getConstructor(Level.class, Level.class, AggregateDomain.class)
                    .newInstance(previous, current, AggregateDomain.OVERALL);
            Object bus = Class.forName("net.neoforged.neoforge.common.NeoForge")
                    .getField("EVENT_BUS")
                    .get(null);
            bus.getClass().getMethod("post", Object.class).invoke(bus, event);
        } catch (Throwable ignored) {
        }
    }

    public static Level getLastServerLevel() { return lastServerLevel; }
    public static Level getLastClientLevel() { return lastClientLevel; }
    public static Level getLastHardwareLevel() { return lastHardwareLevel; }
    public static Level getLastOverallLevel() { return lastOverallLevel; }
    public static long getTimeSinceLastLevelChangeMs() {
        return lastLevelChangeMs == 0 ? -1 : System.currentTimeMillis() - lastLevelChangeMs;
    }
    public static int getConsecutiveLevelSamples() { return consecutiveLevelSamples; }

    public static double getBudgetScale(AggregateDomain domain) {
        return switch (domain) {
            case SERVER -> lastServerLevel.budgetScale();
            case CLIENT -> lastClientLevel.budgetScale();
            case HARDWARE -> lastHardwareLevel.budgetScale();
            case OVERALL -> lastOverallLevel.budgetScale();
        };
    }

    public static void reset() {
        msptIndex = 0;
        msptCount = 0;
        serverTicks = 0L;
        averageMspt = 0.0;
        peakMspt = 0.0;
        lastServerLevel = Level.FAIR;
        lastClientLevel = Level.FAIR;
        lastHardwareLevel = Level.FAIR;
        lastOverallLevel = Level.FAIR;
        lastLevelChangeMs = 0L;
        consecutiveLevelSamples = 0;
        pendingLevel = null;
        pendingLevelTarget = null;
        pendingLevelCount = 0;
    }

    public static String getServerStats() {
        return String.format("Server: %.2f ms/tick avg, %.2f ms peak (%.1f TPS) | %s | headroom %.0f%%",
                averageMspt, peakMspt, getTps(), getServerLevel(), getServerHeadroom() * 100.0);
    }

    public static String getClientStats() {
        return FrameProfiler.getStats();
    }

    public static String getDetailedStats() {
        return getServerStats() + "\n" + getClientStats()
                + "\nOverall: " + lastOverallLevel.name()
                + " (S:" + lastServerLevel.name() + " C:" + lastClientLevel.name() + " H:" + lastHardwareLevel.name() + ")";
    }
}
