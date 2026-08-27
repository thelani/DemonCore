package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.client.Minecraft;

public final class ParticleBudget {

    private ParticleBudget() {
    }

    
    private static final int LIFETIME_TICKS = 40;

    private static final int[] spawnsPerTick = new int[LIFETIME_TICKS];
    private static int tickCursor;
    private static int spawnsThisTick;

    private static volatile int liveParticles;
    private static long rejected;
    private static long spawned;

    
    public static void onEngineTick() {
        spawnsPerTick[tickCursor] = spawnsThisTick;
        tickCursor = (tickCursor + 1) % LIFETIME_TICKS;
        spawnsThisTick = 0;

        int sum = 0;
        for (int n : spawnsPerTick) {
            sum += n;
        }
        liveParticles = sum;
    }

    public static int getLiveCount() {
        return liveParticles;
    }

    private static int effectiveLimit() {
        int limit = DemonCoreConfig.getInt(DemonCoreConfig.MAX_PARTICLES, 4000);
        if (DemonCoreConfig.getBool(DemonCoreConfig.ADAPTIVE_QUALITY, true)) {
            double quality = FrameProfiler.getQuality();
            limit = (int) Math.max(250, limit * (0.4 + 0.6 * quality));
        }
        return limit;
    }

    
    public static boolean allow(double x, double y, double z) {
        if (!DemonCoreConfig.isRenderOptimizationEnabled()
                || !DemonCoreConfig.getBool(DemonCoreConfig.PARTICLE_LIMIT_ENABLED, true)) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return true;
        }

        int cullDistance = DemonCoreConfig.getInt(DemonCoreConfig.PARTICLE_CULL_DISTANCE, 48);
        double distSq = mc.player.distanceToSqr(x, y, z);

        if (distSq > (double) cullDistance * cullDistance) {
            rejected++;
            return false;
        }

        int limit = effectiveLimit();
        int live = liveParticles;

        if (live < limit * 0.75) {
            spawnsThisTick++;
            spawned++;
            return true;
        }
        if (live >= limit) {
            rejected++;
            return false;
        }

        
        double fill = (live - limit * 0.75) / (limit * 0.25);
        double allowedDistance = cullDistance * (1.0 - fill * 0.7);
        if (distSq > allowedDistance * allowedDistance) {
            rejected++;
            return false;
        }
        spawnsThisTick++;
        spawned++;
        return true;
    }

    public static long getRejected() {
        return rejected;
    }

    public static long getSpawned() {
        return spawned;
    }

    public static void reset() {
        rejected = 0L;
        spawned = 0L;
        java.util.Arrays.fill(spawnsPerTick, 0);
        liveParticles = 0;
    }

    public static String getStats() {
        return String.format("Particles: %d live / %d budget | %d rejected",
                liveParticles, effectiveLimit(), rejected);
    }
}
