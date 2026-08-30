package com.lani.demoncore.event;

import net.neoforged.bus.api.Event;

public class GpuRamBalanceEvent extends Event {

    public enum Action {
        EVALUATION,
        ADJUST_UP,
        ADJUST_DOWN,
        HOLD,
        RAM_CAP_TRIGGERED,
        TARGET_HIT,
        CRITICAL_PRESSURE,
        DISABLED,
        CACHE_MULTIPLIER_APPLIED
    }

    private final Action action;
    private final double cacheMultiplier;
    private final double previousMultiplier;
    private final double gpuUtil;
    private final double ramUtil;
    private final double targetGpuUtil;
    private final double ramCap;
    private final double pidError;
    private final double pidIntegral;
    private final int upAdjustments;
    private final int downAdjustments;
    private final int holdCount;
    private final long evaluations;
    private final long timestamp;
    private final boolean balancerEnabled;
    private final String reason;

    private GpuRamBalanceEvent(Builder b) {
        this.action = b.action;
        this.cacheMultiplier = b.cacheMultiplier;
        this.previousMultiplier = b.previousMultiplier;
        this.gpuUtil = b.gpuUtil;
        this.ramUtil = b.ramUtil;
        this.targetGpuUtil = b.targetGpuUtil;
        this.ramCap = b.ramCap;
        this.pidError = b.pidError;
        this.pidIntegral = b.pidIntegral;
        this.upAdjustments = b.upAdjustments;
        this.downAdjustments = b.downAdjustments;
        this.holdCount = b.holdCount;
        this.evaluations = b.evaluations;
        this.timestamp = System.currentTimeMillis();
        this.balancerEnabled = b.balancerEnabled;
        this.reason = b.reason;
    }

    public Action getAction() { return action; }
    public double getCacheMultiplier() { return cacheMultiplier; }
    public double getPreviousMultiplier() { return previousMultiplier; }
    public double getGpuUtil() { return gpuUtil; }
    public double getRamUtil() { return ramUtil; }
    public double getTargetGpuUtil() { return targetGpuUtil; }
    public double getRamCap() { return ramCap; }
    public double getPidError() { return pidError; }
    public double getPidIntegral() { return pidIntegral; }
    public int getUpAdjustments() { return upAdjustments; }
    public int getDownAdjustments() { return downAdjustments; }
    public int getHoldCount() { return holdCount; }
    public long getEvaluations() { return evaluations; }
    public long getTimestamp() { return timestamp; }
    public boolean isBalancerEnabled() { return balancerEnabled; }
    public String getReason() { return reason; }

    public double getMultiplierDelta() {
        return cacheMultiplier - previousMultiplier;
    }

    public double getGpuHeadroom() {
        return Math.max(0.0, targetGpuUtil - gpuUtil);
    }

    public double getRamHeadroom() {
        return Math.max(0.0, ramCap - ramUtil);
    }

    public boolean isRamLimited() {
        return ramUtil > ramCap * 0.95;
    }

    public boolean isSignificantChange() {
        return Math.abs(getMultiplierDelta()) >= 0.05;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Action action = Action.EVALUATION;
        private double cacheMultiplier = 1.0;
        private double previousMultiplier = 1.0;
        private double gpuUtil;
        private double ramUtil;
        private double targetGpuUtil = 0.60;
        private double ramCap = 0.45;
        private double pidError;
        private double pidIntegral;
        private int upAdjustments;
        private int downAdjustments;
        private int holdCount;
        private long evaluations;
        private boolean balancerEnabled = true;
        private String reason = "";

        private Builder() {}

        public Builder action(Action a) { this.action = a; return this; }
        public Builder cacheMultiplier(double m) { this.cacheMultiplier = m; return this; }
        public Builder previousMultiplier(double m) { this.previousMultiplier = m; return this; }
        public Builder gpuUtil(double u) { this.gpuUtil = u; return this; }
        public Builder ramUtil(double u) { this.ramUtil = u; return this; }
        public Builder targetGpuUtil(double t) { this.targetGpuUtil = t; return this; }
        public Builder ramCap(double c) { this.ramCap = c; return this; }
        public Builder pidError(double e) { this.pidError = e; return this; }
        public Builder pidIntegral(double i) { this.pidIntegral = i; return this; }
        public Builder upAdjustments(int n) { this.upAdjustments = n; return this; }
        public Builder downAdjustments(int n) { this.downAdjustments = n; return this; }
        public Builder holdCount(int n) { this.holdCount = n; return this; }
        public Builder evaluations(long n) { this.evaluations = n; return this; }
        public Builder balancerEnabled(boolean b) { this.balancerEnabled = b; return this; }
        public Builder reason(String r) { this.reason = r; return this; }

        public GpuRamBalanceEvent build() { return new GpuRamBalanceEvent(this); }
    }
}
