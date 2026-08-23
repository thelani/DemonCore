package com.lani.demoncore.sable;

import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages grace periods for players during Sable transitions
 * to prevent false positives in speed checks
 */
public class SableTrackingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SableTrackingManager.class);
    private static final Map<UUID, Long> gracePeriods = new ConcurrentHashMap<>();
    private static final long GRACE_PERIOD_DURATION = 3000; // 3 seconds

    /**
     * Start a grace period for a player
     * During this time, speed checks may be bypassed
     */
    public static void startGracePeriod(Player player) {
        gracePeriods.put(player.getUUID(), System.currentTimeMillis());
        LOGGER.trace("Started grace period for player {}", player.getName().getString());
    }

    /**
     * Check if a player is within their grace period
     */
    public static boolean withinGracePeriod(Player player) {
        Long startTime = gracePeriods.get(player.getUUID());
        if (startTime == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > GRACE_PERIOD_DURATION) {
            gracePeriods.remove(player.getUUID());
            return false;
        }

        return true;
    }

    /**
     * End a grace period for a player
     */
    public static void endGracePeriod(Player player) {
        gracePeriods.remove(player.getUUID());
        LOGGER.trace("Ended grace period for player {}", player.getName().getString());
    }

    /**
     * Cleanup expired grace periods
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        gracePeriods.entrySet().removeIf(entry -> 
            (now - entry.getValue()) > GRACE_PERIOD_DURATION
        );
    }

    /**
     * Reset all grace periods
     */
    public static void reset() {
        gracePeriods.clear();
        LOGGER.info("All grace periods reset");
    }
}
