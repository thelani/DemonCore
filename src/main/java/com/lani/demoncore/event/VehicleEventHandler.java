package com.lani.demoncore.event;

import com.lani.demoncore.chunk.ChunkPreLoader;
import com.lani.demoncore.config.DemonCoreConfig;
import com.lani.demoncore.optimization.HardwareMonitor;
import com.lani.demoncore.optimization.PerformanceMonitor;
import com.lani.demoncore.optimization.SmartChunkCalculator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore-Vehicle");

    private static final int IDLE_TICKS_THRESHOLD = 80;

    private final ChunkPreLoader chunkLoader;

    private final Map<UUID, Double> lastSpeeds = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> idleTickCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChunkLoadTicks = new ConcurrentHashMap<>();

    private volatile long totalChunkLoadsTriggered;
    private volatile long totalIdleSkips;
    private volatile long totalAdaptiveSkips;
    private volatile long totalThresholdEventsFired;

    public VehicleEventHandler(ChunkPreLoader chunkLoader) {
        this.chunkLoader = chunkLoader;
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!DemonCoreConfig.isEnabled()
                || !DemonCoreConfig.getBool(DemonCoreConfig.CHUNK_LOADING_ENABLED, true)) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }

        Entity source = player.getVehicle() != null ? player.getVehicle() : player;
        double speed = SmartChunkCalculator.toBlocksPerSecond(source.getDeltaMovement());
        UUID id = player.getUUID();
        double prev = lastSpeeds.getOrDefault(id, 0.0);
        double threshold = DemonCoreConfig.getDouble(DemonCoreConfig.SPEED_THRESHOLD, 24.0);
        double activationSpeed = DemonCoreConfig.getDouble(DemonCoreConfig.ACTIVATION_SPEED, 20.0);

        if (speed <= 0.01) {
            int idle = idleTickCounts.merge(id, 1, Integer::sum);
            if (idle == IDLE_TICKS_THRESHOLD) {
                totalIdleSkips++;
                postEvent(player, source, 0.0, prev, VehicleSpeedEvent.Phase.IDLE_DETECTED, 0);
            }
            lastSpeeds.put(id, 0.0);
            return;
        } else {
            idleTickCounts.remove(id);
        }

        PerformanceMonitor.Level level = PerformanceMonitor.getLastOverallLevel();
        HardwareMonitor.ComponentPressure cpu = HardwareMonitor.getCpuPressure();

        double adaptiveThreshold = activationSpeed;
        if (level.ordinal() >= PerformanceMonitor.Level.POOR.ordinal()) {
            adaptiveThreshold *= 1.35;
        } else if (cpu.ordinal() >= HardwareMonitor.ComponentPressure.HIGH.ordinal()) {
            adaptiveThreshold *= 1.15;
        }

        if (speed < adaptiveThreshold) {
            lastSpeeds.put(id, speed);
            return;
        }

        boolean crossed = prev < threshold && speed >= threshold;
        if (crossed) {
            totalThresholdEventsFired++;
            postEvent(player, source, speed, prev, VehicleSpeedEvent.Phase.THRESHOLD_CROSSED, 0);
        }

        double scaledSpeed = scaleSpeedByLevel(speed, level);
        if (scaledSpeed < activationSpeed) {
            totalAdaptiveSkips++;
            lastSpeeds.put(id, speed);
            return;
        }

        int chunksQueued = chunkLoader.loadChunks(source, scaledSpeed);
        totalChunkLoadsTriggered += chunksQueued;
        lastChunkLoadTicks.put(id, PerformanceMonitor.getServerTicks());
        lastSpeeds.put(id, speed);

        postEvent(player, source, speed, prev,
                chunksQueued > 0 ? VehicleSpeedEvent.Phase.POST_LOAD : VehicleSpeedEvent.Phase.PRE_LOAD,
                chunksQueued);
    }

    private double scaleSpeedByLevel(double rawSpeed, PerformanceMonitor.Level level) {
        double scale = PerformanceMonitor.getBudgetScale(PerformanceMonitor.AggregateDomain.OVERALL);
        double vulcan = DemonCoreConfig.isVulcanMode() ? 1.25 : 1.0;
        double min = DemonCoreConfig.getDouble(DemonCoreConfig.ACTIVATION_SPEED, 20.0) * 0.8;
        return Math.max(min, rawSpeed * Math.min(1.2, scale) * vulcan);
    }

    private void postEvent(
            ServerPlayer player,
            Entity source,
            double speed,
            double prev,
            VehicleSpeedEvent.Phase phase,
            int queued) {
        try {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new VehicleSpeedEvent(player, source, speed, prev, phase, queued)
            );
        } catch (Throwable ignored) {
        }
    }

    public long getTotalChunkLoadsTriggered() { return totalChunkLoadsTriggered; }
    public long getTotalIdleSkips() { return totalIdleSkips; }
    public long getTotalAdaptiveSkips() { return totalAdaptiveSkips; }
    public long getTotalThresholdEventsFired() { return totalThresholdEventsFired; }

    public void reset() {
        lastSpeeds.clear();
        idleTickCounts.clear();
        lastChunkLoadTicks.clear();
        totalChunkLoadsTriggered = 0L;
        totalIdleSkips = 0L;
        totalAdaptiveSkips = 0L;
        totalThresholdEventsFired = 0L;
    }

    public String getStats() {
        return String.format(
                "Vehicle: %d loads | %d idle skips | %d adaptive skips | %d threshold events",
                totalChunkLoadsTriggered, totalIdleSkips, totalAdaptiveSkips, totalThresholdEventsFired);
    }
}
