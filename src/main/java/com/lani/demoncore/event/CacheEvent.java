package com.lani.demoncore.event;

import net.neoforged.bus.api.Event;

public class CacheEvent extends Event {

    public enum CacheType {
        CHUNK_POS,
        GEOMETRY_BLOCK_ENTITY,
        GEOMETRY_ENTITY,
        AGGREGATE
    }

    public enum Action {
        HIT,
        MISS,
        EVICTION,
        TRIM,
        INIT,
        CLEAR,
        RESIZE,
        AUTO_TRIM
    }

    private final CacheType cacheType;
    private final Action action;
    private final int currentSize;
    private final int maxSize;
    private final double hitRate;
    private final long entriesAffected;
    private final double keepFraction;
    private final long timestamp;
    private final String detail;

    private CacheEvent(Builder b) {
        this.cacheType = b.cacheType;
        this.action = b.action;
        this.currentSize = b.currentSize;
        this.maxSize = b.maxSize;
        this.hitRate = b.hitRate;
        this.entriesAffected = b.entriesAffected;
        this.keepFraction = b.keepFraction;
        this.timestamp = System.currentTimeMillis();
        this.detail = b.detail;
    }

    public CacheType getCacheType() { return cacheType; }
    public Action getAction() { return action; }
    public int getCurrentSize() { return currentSize; }
    public int getMaxSize() { return maxSize; }
    public double getHitRate() { return hitRate; }
    public long getEntriesAffected() { return entriesAffected; }
    public double getKeepFraction() { return keepFraction; }
    public long getTimestamp() { return timestamp; }
    public String getDetail() { return detail; }

    public boolean isCriticalPressure() {
        return maxSize > 0 && (double) currentSize / (double) maxSize > 0.92;
    }

    public boolean isTrimAction() {
        return action == Action.TRIM || action == Action.AUTO_TRIM || action == Action.CLEAR;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private CacheType cacheType = CacheType.AGGREGATE;
        private Action action = Action.HIT;
        private int currentSize;
        private int maxSize;
        private double hitRate;
        private long entriesAffected;
        private double keepFraction = 1.0;
        private String detail = "";

        private Builder() {}

        public Builder cacheType(CacheType t) { this.cacheType = t; return this; }
        public Builder action(Action a) { this.action = a; return this; }
        public Builder currentSize(int s) { this.currentSize = s; return this; }
        public Builder maxSize(int m) { this.maxSize = m; return this; }
        public Builder hitRate(double r) { this.hitRate = r; return this; }
        public Builder entriesAffected(long n) { this.entriesAffected = n; return this; }
        public Builder keepFraction(double f) { this.keepFraction = f; return this; }
        public Builder detail(String d) { this.detail = d; return this; }

        public CacheEvent build() { return new CacheEvent(this); }
    }
}
