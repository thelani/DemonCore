package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public final class TickThrottleSystem {

    private TickThrottleSystem() {
    }

    private static volatile long skipped;
    private static volatile long ticked;

    
    public static boolean shouldSkip(Entity entity) {
        if (entity == null || !DemonCoreConfig.isEnabled()
                || !DemonCoreConfig.getBool(DemonCoreConfig.TICK_THROTTLE_ENABLED, true)
                || DemonCoreConfig.isVulcanMode()
                || com.lani.demoncore.compat.ModCompat.hasTickOptimizer()) {
            return false;
        }

        if (!isThrottleCandidate(entity)) {
            ticked++;
            return false;
        }

        int distance = DemonCoreConfig.getInt(DemonCoreConfig.TICK_THROTTLE_DISTANCE, 64);
        double nearestSq = nearestPlayerDistanceSq(entity);

        double nearSq = (double) distance * distance;
        if (nearestSq <= nearSq) {
            ticked++;
            return false;
        }

        int maxFactor = DemonCoreConfig.getInt(DemonCoreConfig.TICK_THROTTLE_MAX_FACTOR, 4);
        if (maxFactor <= 1) {
            ticked++;
            return false;
        }

        
        double ratio = Math.sqrt(nearestSq) / distance;
        int factor = (int) Math.min(maxFactor, 1 + Math.floor(ratio));
        if (factor <= 1) {
            ticked++;
            return false;
        }

        
        
        boolean skip = ((entity.tickCount + entity.getId()) % factor) != 0;
        if (skip) {
            skipped++;
        } else {
            ticked++;
        }
        return skip;
    }

    private static boolean isThrottleCandidate(Entity entity) {
        if (entity instanceof Player) {
            return false;
        }
        if (entity.isVehicle() || entity.isPassenger()) {
            return false;
        }
        
        if (entity.isOnFire() || entity.isInWater() || entity.isInLava()) {
            return false;
        }

        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            return DemonCoreConfig.getBool(DemonCoreConfig.TICK_THROTTLE_ITEMS, true);
        }

        if (entity instanceof LivingEntity living) {
            
            if (living.getLastHurtByMob() != null || living.hurtTime > 0 || living.deathTime > 0) {
                return false;
            }
            if (living instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
                return false;
            }
            return true;
        }

        return false;
    }

    private static double nearestPlayerDistanceSq(Entity entity) {
        Level level = entity.level();
        List<? extends Player> players = level.players();
        if (players.isEmpty()) {
            return Double.MAX_VALUE;
        }

        double best = Double.MAX_VALUE;
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player.isSpectator()) {
                continue;
            }
            double d = player.distanceToSqr(entity.getX(), entity.getY(), entity.getZ());
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    public static long getSkipped() {
        return skipped;
    }

    public static long getTicked() {
        return ticked;
    }

    public static double getSkipRate() {
        long total = skipped + ticked;
        return total == 0L ? 0.0 : (double) skipped / (double) total;
    }

    public static void reset() {
        skipped = 0L;
        ticked = 0L;
    }

    public static String getStats() {
        return String.format("Tick throttle: %d skipped / %d ticked (%.1f%% saved)",
                skipped, ticked, getSkipRate() * 100.0);
    }
}
