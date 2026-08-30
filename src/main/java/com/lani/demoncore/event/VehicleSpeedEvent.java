package com.lani.demoncore.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class VehicleSpeedEvent extends Event {

    public enum Phase {
        PRE_LOAD,
        POST_LOAD,
        THRESHOLD_CROSSED,
        IDLE_DETECTED
    }

    private final ServerPlayer player;
    private final Entity source;
    private final double speedBps;
    private final double previousSpeedBps;
    private final Phase phase;
    private final int chunksQueuedThisTick;
    private final long timestamp;

    public VehicleSpeedEvent(
            ServerPlayer player,
            Entity source,
            double speedBps,
            double previousSpeedBps,
            Phase phase,
            int chunksQueuedThisTick) {
        this.player = player;
        this.source = source;
        this.speedBps = speedBps;
        this.previousSpeedBps = previousSpeedBps;
        this.phase = phase;
        this.chunksQueuedThisTick = chunksQueuedThisTick;
        this.timestamp = System.currentTimeMillis();
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Entity getSource() {
        return source;
    }

    public boolean isInVehicle() {
        return source != player;
    }

    public double getSpeedBps() {
        return speedBps;
    }

    public double getPreviousSpeedBps() {
        return previousSpeedBps;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getChunksQueuedThisTick() {
        return chunksQueuedThisTick;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getSpeedDelta() {
        return speedBps - previousSpeedBps;
    }

    public boolean isAccelerating() {
        return getSpeedDelta() > 0.5;
    }

    public boolean isDecelerating() {
        return getSpeedDelta() < -0.5;
    }

    public double getSpeedKmH() {
        return speedBps * 3.6;
    }
}
