package com.lani.demoncore.event;

import com.lani.demoncore.optimization.BottleneckDetector;
import net.neoforged.bus.api.Event;

public class BottleneckChangedEvent extends Event {

    private final BottleneckDetector.Bottleneck previous;
    private final BottleneckDetector.Bottleneck current;
    private final double cpuShare;
    private final double gpuShare;
    private final double otherShare;
    private final String advice;
    private final long timestamp;

    public BottleneckChangedEvent(
            BottleneckDetector.Bottleneck previous,
            BottleneckDetector.Bottleneck current,
            double cpuShare,
            double gpuShare,
            double otherShare,
            String advice) {
        this.previous = previous;
        this.current = current;
        this.cpuShare = cpuShare;
        this.gpuShare = gpuShare;
        this.otherShare = otherShare;
        this.advice = advice;
        this.timestamp = System.currentTimeMillis();
    }

    public BottleneckDetector.Bottleneck getPrevious() {
        return previous;
    }

    public BottleneckDetector.Bottleneck getCurrent() {
        return current;
    }

    public double getCpuShare() {
        return cpuShare;
    }

    public double getGpuShare() {
        return gpuShare;
    }

    public double getOtherShare() {
        return otherShare;
    }

    public String getAdvice() {
        return advice;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isNewBottleneck() {
        return previous != current;
    }

    public boolean isMemoryBound() {
        return current == BottleneckDetector.Bottleneck.MEMORY;
    }

    public boolean isGpuBound() {
        return current == BottleneckDetector.Bottleneck.GPU;
    }

    public boolean isCpuBound() {
        return current == BottleneckDetector.Bottleneck.CPU_RENDER
                || current == BottleneckDetector.Bottleneck.CPU_LOGIC;
    }
}
