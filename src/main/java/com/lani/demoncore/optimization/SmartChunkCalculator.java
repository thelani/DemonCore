package com.lani.demoncore.optimization;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class SmartChunkCalculator {

    private SmartChunkCalculator() {}

    private static final long MAX_KEYS_IN_BUDGET = 4096L;

    public static List<ChunkPos> predictPath(Vec3 position, Vec3 motion, double speed, int budget) {
        List<ChunkPos> result = new ArrayList<>(Math.min(budget + 8, 1024));
        if (budget <= 0) return result;

        double len2 = motion.lengthSqr();
        if (len2 < 1.0E-8) return result;
        double len = Math.sqrt(len2);

        Vec3 dir = new Vec3(motion.x / len, 0.0, motion.z / len);
        if (Double.isNaN(dir.x) || Double.isNaN(dir.z)) return result;

        double lookaheadBlocks;
        if (speed < 100.0) {
            lookaheadBlocks = Math.max(32.0, speed * 2.4);
        } else if (speed < 500.0) {
            lookaheadBlocks = Math.max(64.0, speed * 1.85 + 16.0);
        } else {
            lookaheadBlocks = Math.max(96.0, Math.min(speed * 1.35, 400.0));
        }
        int steps = (int) ((lookaheadBlocks + 15.0) / 16.0);
        steps = Math.max(1, Math.min(steps, Math.min(budget, 192)));

        double perpX = -dir.z;
        double perpZ = dir.x;

        int hashSize = 1;
        int targetHash = Math.min((int) MAX_KEYS_IN_BUDGET, Math.max(128, budget * 4));
        while (hashSize < targetHash) hashSize <<= 1;
        long[] seen = new long[hashSize];
        int seenMask = hashSize - 1;
        long lastKey = Long.MIN_VALUE;
        int seenCount = 0;

        double lookDx = dir.x * 16.0;
        double lookDz = dir.z * 16.0;
        double centreX = position.x;
        double centreZ = position.z;

        double speedNorm = Math.min(1.0, speed / 1000.0);
        int baseBandwidth;
        if (speed < 80.0) {
            baseBandwidth = 1;
        } else if (speed < 250.0) {
            baseBandwidth = 2;
        } else if (speed < 700.0) {
            baseBandwidth = 3;
        } else {
            baseBandwidth = 4;
        }

        for (int i = 0; i <= steps; i++) {
            int width;
            double t = steps > 1 ? (double) i / (double) steps : 0.0;
            double bell = 1.0 - Math.abs(2.0 * t - 1.0);

            if (i == 0) {
                width = baseBandwidth + 1;
            } else if (t < 0.20) {
                width = baseBandwidth + (int) Math.round(bell * 0.8);
            } else if (t < 0.75) {
                width = baseBandwidth + 1;
            } else {
                width = Math.max(1, baseBandwidth - 1);
            }

            double tap1 = 0.5 * (1.0 - speedNorm);
            double tap2 = 1.0 - tap1;
            int wInner = (int) Math.ceil(width * tap1);
            int wOuter = width;

            double[] weights = { 0.0, 1.0, -1.0, 0.5, -0.5, 1.5, -1.5 };
            int maxW = wOuter;
            for (int wIdx = 0; wIdx < weights.length && result.size() < budget; wIdx++) {
                double w = weights[wIdx];
                if (Math.abs(w) > maxW + 0.01) continue;
                if (i != 0 && Math.abs(w) > wInner && (wIdx > 2)) {
                    if ((i & 1) == 0) continue;
                }

                double x = centreX + perpX * w * 16.0;
                double z = centreZ + perpZ * w * 16.0;
                int chunkX = (int) Math.floor(x / 16.0);
                int chunkZ = (int) Math.floor(z / 16.0);
                long key = ChunkPos.asLong(chunkX, chunkZ);
                if (key == lastKey) continue;

                int slot = (int) (fastHash(key) & seenMask);
                long existing = seen[slot];
                if (existing != 0L && existing == key) continue;
                seen[slot] = key;
                seenCount++;

                lastKey = key;
                result.add(new ChunkPos(chunkX, chunkZ));
            }

            centreX += lookDx;
            centreZ += lookDz;
        }

        if (result.size() > 2) {
            CacheSystem.registerSmartPathHit();
        }
        return result;
    }

    private static long fastHash(long z) {
        z = (z ^ (z >>> 29)) * 0x517cc1b727220a95L;
        return z | 1L;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return (z ^ (z >>> 33)) | 1L;
    }

    public static double toBlocksPerSecond(Vec3 motion) {
        return motion.length() * 20.0;
    }

    public static double toBlocksPerTick(Vec3 motion) {
        return motion.length();
    }
}
