package com.lani.demoncore.event;

import com.lani.demoncore.optimization.PerformanceMonitor;
import net.neoforged.bus.api.Event;

public class OptimizationLevelChangeEvent extends Event {

    private final PerformanceMonitor.Level previous;
    private final PerformanceMonitor.Level current;
    private final PerformanceMonitor.AggregateDomain domain;
    private final long timestamp;

    public OptimizationLevelChangeEvent(
            PerformanceMonitor.Level previous,
            PerformanceMonitor.Level current,
            PerformanceMonitor.AggregateDomain domain) {
        this.previous = previous;
        this.current = current;
        this.domain = domain;
        this.timestamp = System.currentTimeMillis();
    }

    public PerformanceMonitor.Level getPrevious() {
        return previous;
    }

    public PerformanceMonitor.Level getCurrent() {
        return current;
    }

    public PerformanceMonitor.AggregateDomain getDomain() {
        return domain;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isImprovement() {
        return current.ordinal() < previous.ordinal();
    }

    public boolean isDegradation() {
        return current.ordinal() > previous.ordinal();
    }

    public int levelDelta() {
        return previous.ordinal() - current.ordinal();
    }

    public double getBudgetScaleDelta() {
        return current.budgetScale() - previous.budgetScale();
    }
}
