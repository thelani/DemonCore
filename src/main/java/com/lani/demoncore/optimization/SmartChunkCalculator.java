package com.lani.demoncore.optimization;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class SmartChunkCalculator {

    private SmartChunkCalculator() {
    }

    
    public static List<ChunkPos> predictPath(Vec3 position, Vec3 motion, double speed, int budget) {
        List<ChunkPos> result = new ArrayList<>(Math.min(budget, 256));
        if (budget <= 0) {
            return result;
        }

        Vec3 dir = motion.normalize();
        if (Double.isNaN(dir.x) || Double.isNaN(dir.z)) {
            return result;
        }

        
        double lookaheadBlocks = Math.max(48.0, speed * 2.0);
        int steps = (int) Math.ceil(lookaheadBlocks / 16.0);
        steps = Math.max(1, Math.min(steps, budget));

        
        double perpX = -dir.z;
        double perpZ = dir.x;

        long lastKey = Long.MIN_VALUE;

        for (int i = 0; i <= steps && result.size() < budget; i++) {
            double centreX = position.x + dir.x * i * 16.0;
            double centreZ = position.z + dir.z * i * 16.0;

            
            
            int width = i < 4 ? 0 : Math.min(2, i / 6);

            for (int w = -width; w <= width && result.size() < budget; w++) {
                double x = centreX + perpX * w * 16.0;
                double z = centreZ + perpZ * w * 16.0;

                int chunkX = (int) Math.floor(x / 16.0);
                int chunkZ = (int) Math.floor(z / 16.0);

                long key = ChunkPos.asLong(chunkX, chunkZ);
                if (key == lastKey) {
                    continue;
                }
                lastKey = key;

                result.add(new ChunkPos(chunkX, chunkZ));
            }
        }

        return result;
    }

    
    public static double toBlocksPerSecond(Vec3 motion) {
        return motion.length() * 20.0;
    }
}
