package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;

public final class AdaptiveTickBudget {

    private AdaptiveTickBudget() {
    }

    private static volatile double currentBudgetScale = 1.0;
    private static volatile int throttledEntities;
    private static volatile int totalEntities;
    private static volatile long lastAdjustmentMs;
    private static volatile int adjustmentsUp;
    private static volatile int adjustmentsDown;

    public static void adjustBudget() {
        long now = System.currentTimeMillis();
        if (now - lastAdjustmentMs < 500L) {
            return;
        }
        lastAdjustmentMs = now;

        PerformanceMonitor.Level level = PerformanceMonitor.getServerLevel();
        double target = switch (level) {
            case EXCELLENT -> 1.0;
            case GOOD -> 0.9;
            case FAIR -> 0.75;
            case POOR -> 0.55;
            case CRITICAL -> 0.35;
        };

        if (target > currentBudgetScale) {
            currentBudgetScale = Math.min(1.0, currentBudgetScale + 0.03);
            adjustmentsUp++;
        } else if (target < currentBudgetScale) {
            currentBudgetScale = Math.max(0.2, currentBudgetScale - 0.05);
            adjustmentsDown++;
        }
    }

    public static double getBudgetScale() {
        adjustBudget();
        return currentBudgetScale;
    }

    public static void reportEntityCounts(int throttled, int total) {
        throttledEntities = throttled;
        totalEntities = total;
    }

    public static int getThrottledEntities() {
        return throttledEntities;
    }

    public static int getTotalEntities() {
        return totalEntities;
    }

    public static String getStats() {
        boolean throttleEnabled = DemonCoreConfig.getBool(DemonCoreConfig.TICK_THROTTLE_ENABLED, true);
        return String.format("TickBudget: scale %.0f%% | %d/%d entities throttled | adjustments %d up / %d down | throttle %s",
                currentBudgetScale * 100.0, throttledEntities, totalEntities,
                adjustmentsUp, adjustmentsDown, throttleEnabled ? "ON" : "OFF");
    }
}
