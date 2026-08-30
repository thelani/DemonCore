package com.lani.demoncore.event;

import com.lani.demoncore.optimization.HardwareMonitor;
import net.neoforged.bus.api.Event;

public class HardwarePressureEvent extends Event {

    public enum Trigger {
        CPU_PRESSURE_CHANGED,
        RAM_PRESSURE_CHANGED,
        GPU_PRESSURE_CHANGED,
        ANY_CRITICAL,
        ALL_IDLE
    }

    private final HardwareMonitor.ComponentPressure cpuPressure;
    private final HardwareMonitor.ComponentPressure ramPressure;
    private final HardwareMonitor.ComponentPressure gpuPressure;
    private final HardwareMonitor.HardwareTier hardwareTier;
    private final Trigger trigger;
    private final double systemScore;
    private final long timestamp;

    public HardwarePressureEvent(
            HardwareMonitor.ComponentPressure cpuPressure,
            HardwareMonitor.ComponentPressure ramPressure,
            HardwareMonitor.ComponentPressure gpuPressure,
            HardwareMonitor.HardwareTier hardwareTier,
            Trigger trigger,
            double systemScore) {
        this.cpuPressure = cpuPressure;
        this.ramPressure = ramPressure;
        this.gpuPressure = gpuPressure;
        this.hardwareTier = hardwareTier;
        this.trigger = trigger;
        this.systemScore = systemScore;
        this.timestamp = System.currentTimeMillis();
    }

    public HardwareMonitor.ComponentPressure getCpuPressure() {
        return cpuPressure;
    }

    public HardwareMonitor.ComponentPressure getRamPressure() {
        return ramPressure;
    }

    public HardwareMonitor.ComponentPressure getGpuPressure() {
        return gpuPressure;
    }

    public HardwareMonitor.HardwareTier getHardwareTier() {
        return hardwareTier;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public double getSystemScore() {
        return systemScore;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isCritical() {
        return cpuPressure == HardwareMonitor.ComponentPressure.CRITICAL
                || ramPressure == HardwareMonitor.ComponentPressure.CRITICAL
                || gpuPressure == HardwareMonitor.ComponentPressure.CRITICAL;
    }

    public boolean isHighOrWorse() {
        return cpuPressure.ordinal() >= HardwareMonitor.ComponentPressure.HIGH.ordinal()
                || ramPressure.ordinal() >= HardwareMonitor.ComponentPressure.HIGH.ordinal()
                || gpuPressure.ordinal() >= HardwareMonitor.ComponentPressure.HIGH.ordinal();
    }

    public String getDominantComponentName() {
        int c = cpuPressure.ordinal();
        int r = ramPressure.ordinal();
        int g = gpuPressure.ordinal();
        if (c >= r && c >= g) return "CPU";
        if (r >= c && r >= g) return "RAM";
        return "GPU";
    }
}
