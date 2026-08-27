package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DimensionChangeOptimizer {

    private DimensionChangeOptimizer() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("DemonCore/Dimension");

    private static volatile long transitions;
    private static volatile long lastTransitionMs;

    public static void onDimensionChange(String from, String to) {
        transitions++;
        lastTransitionMs = System.currentTimeMillis();

        
        ChunkPosCache.clear();

        
        FrameProfiler.reset();
        EntityCuller.reset();
        BlockEntityCuller.reset();
        ParticleBudget.reset();

        if (DemonCoreConfig.isDebug()) {
            LOGGER.info("Dimension change {} -> {}, DemonCore state reset", from, to);
        }
    }

    
    public static boolean isSettling() {
        return System.currentTimeMillis() - lastTransitionMs < 3000L;
    }

    public static long getTransitions() {
        return transitions;
    }

    public static String getStats() {
        return "Dimension transitions: " + transitions;
    }
}
