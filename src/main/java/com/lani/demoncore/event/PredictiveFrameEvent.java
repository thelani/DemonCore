package com.lani.demoncore.event;

import net.neoforged.bus.api.Event;

public class PredictiveFrameEvent extends Event {

    public enum Phase {
        FRAME_START,
        FRAME_END,
        PREEMPTIVE_SLEEP_ISSUED,
        OVERRUN_PREVENTED,
        PREDICTION_UPDATED,
        DISABLED,
        BUDGET_EXCEEDED
    }

    private final Phase phase;
    private final double predictedMs;
    private final double budgetMs;
    private final double cpuMs;
    private final double gpuMs;
    private final double headroomFraction;
    private final long sleepNs;
    private final long framesPredicted;
    private final long savedFromOverrun;
    private final long microSleepsIssued;
    private final long timestamp;
    private final boolean schedulerEnabled;

    private PredictiveFrameEvent(Builder b) {
        this.phase = b.phase;
        this.predictedMs = b.predictedMs;
        this.budgetMs = b.budgetMs;
        this.cpuMs = b.cpuMs;
        this.gpuMs = b.gpuMs;
        this.headroomFraction = b.headroomFraction;
        this.sleepNs = b.sleepNs;
        this.framesPredicted = b.framesPredicted;
        this.savedFromOverrun = b.savedFromOverrun;
        this.microSleepsIssued = b.microSleepsIssued;
        this.timestamp = System.currentTimeMillis();
        this.schedulerEnabled = b.schedulerEnabled;
    }

    public Phase getPhase() { return phase; }
    public double getPredictedMs() { return predictedMs; }
    public double getBudgetMs() { return budgetMs; }
    public double getCpuMs() { return cpuMs; }
    public double getGpuMs() { return gpuMs; }
    public double getHeadroomFraction() { return headroomFraction; }
    public long getSleepNs() { return sleepNs; }
    public long getFramesPredicted() { return framesPredicted; }
    public long getSavedFromOverrun() { return savedFromOverrun; }
    public long getMicroSleepsIssued() { return microSleepsIssued; }
    public long getTimestamp() { return timestamp; }
    public boolean isSchedulerEnabled() { return schedulerEnabled; }

    public double getOverrunMs() {
        return Math.max(0.0, predictedMs - budgetMs);
    }

    public double getBudgetUsagePct() {
        if (budgetMs <= 0.001) return 0.0;
        return Math.min(1.0, predictedMs / budgetMs);
    }

    public boolean isSevereOverrun() {
        return phase == Phase.BUDGET_EXCEEDED && getBudgetUsagePct() > 1.15;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Phase phase = Phase.FRAME_START;
        private double predictedMs;
        private double budgetMs;
        private double cpuMs;
        private double gpuMs;
        private double headroomFraction = 0.10;
        private long sleepNs;
        private long framesPredicted;
        private long savedFromOverrun;
        private long microSleepsIssued;
        private boolean schedulerEnabled = true;

        private Builder() {}

        public Builder phase(Phase p) { this.phase = p; return this; }
        public Builder predictedMs(double m) { this.predictedMs = m; return this; }
        public Builder budgetMs(double m) { this.budgetMs = m; return this; }
        public Builder cpuMs(double m) { this.cpuMs = m; return this; }
        public Builder gpuMs(double m) { this.gpuMs = m; return this; }
        public Builder headroomFraction(double f) { this.headroomFraction = f; return this; }
        public Builder sleepNs(long n) { this.sleepNs = n; return this; }
        public Builder framesPredicted(long n) { this.framesPredicted = n; return this; }
        public Builder savedFromOverrun(long n) { this.savedFromOverrun = n; return this; }
        public Builder microSleepsIssued(long n) { this.microSleepsIssued = n; return this; }
        public Builder schedulerEnabled(boolean b) { this.schedulerEnabled = b; return this; }

        public PredictiveFrameEvent build() { return new PredictiveFrameEvent(this); }
    }
}
