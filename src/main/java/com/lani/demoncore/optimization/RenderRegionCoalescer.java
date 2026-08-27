package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class RenderRegionCoalescer {

    private RenderRegionCoalescer() {
    }

    private static volatile long regionsSubmitted;
    private static volatile long regionsCoalesced;
    private static volatile long drawCallsSaved;
    private static volatile double coalesceRate;
    private static volatile int activeRegions;
    private static volatile long lastSampleMs;

    public static void onRegionSubmitted() {
        regionsSubmitted++;
    }

    public static void onRegionsCoalesced(int count) {
        regionsCoalesced++;
        drawCallsSaved += Math.max(0, count - 1);
        activeRegions = Math.max(0, activeRegions - count + 1);
    }

    public static void onRegionStarted() {
        activeRegions++;
    }

    public static void sample() {
        long now = System.currentTimeMillis();
        if (now - lastSampleMs < 1000L) {
            return;
        }
        lastSampleMs = now;

        if (regionsSubmitted > 0L) {
            coalesceRate += 0.2 * (((double) regionsCoalesced / (double) regionsSubmitted) - coalesceRate);
        }
    }

    public static long getRegionsSubmitted() {
        return regionsSubmitted;
    }

    public static long getRegionsCoalesced() {
        return regionsCoalesced;
    }

    public static long getDrawCallsSaved() {
        return drawCallsSaved;
    }

    public static double getCoalesceRate() {
        sample();
        return coalesceRate;
    }

    public static int getActiveRegions() {
        return activeRegions;
    }

    public static String getStats() {
        sample();
        boolean renderOpt = DemonCoreConfig.getBool(DemonCoreConfig.RENDER_OPTIMIZATION, true);
        return String.format("RenderRegions: %s | %d submitted, %d coalesced (%.1f%%) | %d draw calls saved | %d active",
                renderOpt ? "ON" : "OFF", regionsSubmitted, regionsCoalesced,
                coalesceRate * 100.0, drawCallsSaved, activeRegions);
    }
}
