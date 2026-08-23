package com.lani.demoncore.optimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SmartChunkCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmartChunkCalculator.class);

    private static final double SPEED_LOW = 50.0;
    private static final double SPEED_MEDIUM = 200.0;
    private static final double SPEED_HIGH = 500.0;
    private static final double SPEED_EXTREME = 1000.0;
    private static final double SPEED_LUDICROUS = 2000.0;
    private static final double SPEED_INSANE = 5000.0; // YENİ: 5000+ m/s
    private static final double SPEED_BEYOND = 10000.0; // YENİ: 10000+ m/s

    private static final int CHUNK_SIZE = 16;
    
    public static Set<BlockPos> calculateChunks(Vec3 position, Vec3 velocity, double speed, int maxChunks) {
        Set<BlockPos> chunks = new LinkedHashSet<>();

        int effectiveLimit = ResourceManager.calculateChunkLimit(maxChunks);

        if (PerformanceMonitor.isLowFps()) {
            effectiveLimit = Math.max(4, effectiveLimit / 2);
        }

        if (speed < SPEED_LOW) {

            chunks.addAll(calculateMinimalArea(position, velocity, effectiveLimit));
        } else if (speed < SPEED_MEDIUM) {

            chunks.addAll(calculateBasicArea(position, velocity, effectiveLimit));
        } else if (speed < SPEED_HIGH) {

            chunks.addAll(calculateExtendedArea(position, velocity, effectiveLimit));
        } else if (speed < SPEED_EXTREME) {

            chunks.addAll(calculateWideArea(position, velocity, effectiveLimit));
        } else if (speed < SPEED_LUDICROUS) {

            chunks.addAll(calculateMaximumArea(position, velocity, effectiveLimit));
        } else if (speed < SPEED_INSANE) {

            chunks.addAll(calculateInsaneArea(position, velocity, effectiveLimit));
        } else if (speed < SPEED_BEYOND) {

            chunks.addAll(calculateBeyondArea(position, velocity, effectiveLimit));
        } else {

            chunks.addAll(calculateMaximumOverdriveArea(position, velocity, effectiveLimit));
        }

        if (chunks.size() > effectiveLimit) {
            chunks = prioritizeChunks(chunks, position, velocity, effectiveLimit);
        }
        
        return chunks;
    }
    
    private static Set<BlockPos> calculateMinimalArea(Vec3 pos, Vec3 vel, int limit) {
        Set<BlockPos> chunks = new LinkedHashSet<>();
        BlockPos currentChunk = toChunkPos(pos);
        Vec3 direction = vel.normalize();
        
        int distance = Math.min(4, limit);
        for (int i = 0; i <= distance; i++) {
            BlockPos chunk = offsetChunk(currentChunk, direction, i);
            chunks.add(chunk);
            if (chunks.size() >= limit) break;
        }
        
        return chunks;
    }
    
    private static Set<BlockPos> calculateBasicArea(Vec3 pos, Vec3 vel, int limit) {
        Set<BlockPos> chunks = new LinkedHashSet<>();
        BlockPos currentChunk = toChunkPos(pos);
        Vec3 direction = vel.normalize();

        int mainDistance = Math.min(6, limit / 2);
        for (int i = 0; i <= mainDistance; i++) {
            BlockPos chunk = offsetChunk(currentChunk, direction, i);
            chunks.add(chunk);

            if (i <= 2 && chunks.size() < limit) {
                Vec3 perpendicular = getPerpendicular(direction);
                chunks.add(offsetChunk(chunk, perpendicular, 1));
                if (chunks.size() < limit) {
                    chunks.add(offsetChunk(chunk, perpendicular, -1));
                }
            }
            
            if (chunks.size() >= limit) break;
        }
        
        return chunks;
    }
    
    private static Set<BlockPos> calculateExtendedArea(Vec3 pos, Vec3 vel, int limit) {
        Set<BlockPos> chunks = new LinkedHashSet<>();
        BlockPos currentChunk = toChunkPos(pos);
        Vec3 direction = vel.normalize();
        Vec3 perpendicular = getPerpendicular(direction);
        
        int mainDistance = Math.min(10, limit / 2);
        
        for (int i = 0; i <= mainDistance; i++) {
            BlockPos center = offsetChunk(currentChunk, direction, i);
            chunks.add(center);

            if (i <= 4 && chunks.size() < limit) {
                for (int side = -1; side <= 1; side++) {
                    if (side == 0) continue;
                    BlockPos sideChunk = offsetChunk(center, perpendicular, side);
                    chunks.add(sideChunk);
                    if (chunks.size() >= limit) return prioritizeChunks(chunks, pos, vel, limit);
                }
            }

            else if (i <= 7 && chunks.size() < limit) {
                chunks.add(offsetChunk(center, perpendicular, 1));
                if (chunks.size() < limit) {
                    chunks.add(offsetChunk(center, perpendicular, -1));
                }
            }
            
            if (chunks.size() >= limit) break;
        }
        
        return chunks;
    }
    
    private static Set<BlockPos> calculateWideArea(Vec3 pos, Vec3 vel, int limit) {
        Set<BlockPos> chunks = new LinkedHashSet<>();
        BlockPos currentChunk = toChunkPos(pos);
        Vec3 direction = vel.normalize();
        Vec3 perpendicular = getPerpendicular(direction);
        
        int mainDistance = Math.min(16, limit / 3);
        
        for (int i = 0; i <= mainDistance; i++) {
            BlockPos center = offsetChunk(currentChunk, direction, i);

            int width = (i <= 7) ? 2 : 1;
            
            for (int side = -width; side <= width; side++) {
                BlockPos chunk = offsetChunk(center, perpendicular, side);
                chunks.add(chunk);
                if (chunks.size() >= limit) return prioritizeChunks(chunks, pos, vel, limit);
            }
        }
        
        return chunks;
    }
    
    private static Set<BlockPos> calculateMaximumArea(Vec3 pos, Vec3 vel, int limit) {
        Set<BlockPos> chunks = new LinkedHashSet<>();
        BlockPos currentChunk = toChunkPos(pos);
        Vec3 direction = vel.normalize();
        Vec3 perpendicular = getPerpendicular(direction);
        
        int mainDistance = Math.min(24, limit / 3);
        
        for (int i = 0; i <= mainDistance; i++) {
            BlockPos center = offsetChunk(currentChunk, direction, i);

            int width = (i <= 6) ? 3 : (i <= 12) ? 2 : 1;
            
            for (int side = -width; side <= width; side++) {
                BlockPos chunk = offsetChunk(center, perpendicular, side);
                chunks.add(chunk);
                if (chunks.size() >= limit) return prioritizeChunks(chunks, pos, vel, limit);
            }
        }
        
        return chunks;
    }
    
    private static Set<BlockPos> calculatePredictiveArea(Vec3 pos, Vec3 vel, int limit) {
        Set<BlockPos> chunks = new LinkedHashSet<>();
        BlockPos currentChunk = toChunkPos(pos);
        Vec3 direction = vel.normalize();
        Vec3 perpendicular = getPerpendicular(direction);

        double speed = vel.length();
        int predictDistance = (int) Math.min(32, speed / 50.0); // Her 50 m/s için 1 chunk ileri
        
        for (int i = 0; i <= predictDistance; i++) {
            BlockPos center = offsetChunk(currentChunk, direction, i);

            int width;
            if (i <= 8) width = 4;
            else if (i <= 16) width = 3;
            else if (i <= 24) width = 2;
            else width = 1;
            
            for (int side = -width; side <= width; side++) {
                BlockPos chunk = offsetChunk(center, perpendicular, side);
                chunks.add(chunk);
                if (chunks.size() >= limit) return prioritizeChunks(chunks, pos, vel, limit);
            }
        }
        
        return chunks;
    }
    
    private static Set<BlockPos> prioritizeChunks(Set<BlockPos> chunks, Vec3 pos, Vec3 vel, int limit) {
        BlockPos playerChunk = toChunkPos(pos);
        Vec3 direction = vel.normalize();

        List<BlockPos> sorted = new ArrayList<>(chunks);
        sorted.sort((a, b) -> {
            double scoreA = calculateChunkScore(a, playerChunk, direction);
            double scoreB = calculateChunkScore(b, playerChunk, direction);
            return Double.compare(scoreB, scoreA); // Yüksek skor önce
        });

        Set<BlockPos> result = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            result.add(sorted.get(i));
        }
        
        return result;
    }
    
    private static double calculateChunkScore(BlockPos chunk, BlockPos playerChunk, Vec3 direction) {
        Vec3 toChunk = new Vec3(
            chunk.getX() - playerChunk.getX(),
            0,
            chunk.getZ() - playerChunk.getZ()
        ).normalize();

        double alignment = toChunk.dot(direction);

        double distance = Math.sqrt(
            Math.pow(chunk.getX() - playerChunk.getX(), 2) +
            Math.pow(chunk.getZ() - playerChunk.getZ(), 2)
        );

        return alignment * 10.0 - distance / 10.0;
    }
    
    private static BlockPos toChunkPos(Vec3 pos) {
        return new BlockPos(
            (int) Math.floor(pos.x / CHUNK_SIZE),
            0,
            (int) Math.floor(pos.z / CHUNK_SIZE)
        );
    }
    
    private static BlockPos offsetChunk(BlockPos chunk, Vec3 direction, int amount) {
        return chunk.offset(
            (int) Math.round(direction.x * amount),
            0,
            (int) Math.round(direction.z * amount)
        );
    }
    
    private static Vec3 getPerpendicular(Vec3 direction) {

        return new Vec3(-direction.z, 0, direction.x).normalize();
    }

    private static Set<BlockPos> calculateInsaneArea(Vec3 position, Vec3 velocity, int limit) {
        Set<BlockPos> chunks = new LinkedHashSet<>();
        BlockPos playerChunk = toChunkPos(position);
        Vec3 direction = velocity.normalize();

        for (int ahead = 0; ahead <= 30; ahead++) {
            BlockPos forward = offsetChunk(playerChunk, direction, ahead);
            chunks.add(forward);

            Vec3 perpendicular = getPerpendicular(direction);
            for (int side = -4; side <= 4; side++) {
                if (side == 0) continue;
                chunks.add(offsetChunk(forward, perpendicular, side));
            }
        }
        
        return chunks;
    }
    
    private static Set<BlockPos> calculateBeyondArea(Vec3 position, Vec3 velocity, int limit) {
        Set<BlockPos> chunks = new LinkedHashSet<>();
        BlockPos playerChunk = toChunkPos(position);
        Vec3 direction = velocity.normalize();

        for (int ahead = 0; ahead <= 50; ahead++) {
            BlockPos forward = offsetChunk(playerChunk, direction, ahead);
            chunks.add(forward);

            Vec3 perpendicular = getPerpendicular(direction);
            for (int side = -6; side <= 6; side++) {
                if (side == 0) continue;
                chunks.add(offsetChunk(forward, perpendicular, side));
            }
        }

        for (int back = -1; back >= -10; back--) {
            BlockPos backward = offsetChunk(playerChunk, direction, back);
            chunks.add(backward);
            
            Vec3 perpendicular = getPerpendicular(direction);
            for (int side = -3; side <= 3; side++) {
                if (side == 0) continue;
                chunks.add(offsetChunk(backward, perpendicular, side));
            }
        }
        
        return chunks;
    }
    
    private static Set<BlockPos> calculateMaximumOverdriveArea(Vec3 position, Vec3 velocity, int limit) {
        Set<BlockPos> chunks = new LinkedHashSet<>();
        BlockPos playerChunk = toChunkPos(position);
        Vec3 direction = velocity.normalize();

        for (int ahead = 0; ahead <= 80; ahead++) {
            BlockPos forward = offsetChunk(playerChunk, direction, ahead);
            chunks.add(forward);

            Vec3 perpendicular = getPerpendicular(direction);
            for (int side = -10; side <= 10; side++) {
                if (side == 0) continue;
                chunks.add(offsetChunk(forward, perpendicular, side));
            }
        }

        for (int back = -1; back >= -20; back--) {
            BlockPos backward = offsetChunk(playerChunk, direction, back);
            chunks.add(backward);
            
            Vec3 perpendicular = getPerpendicular(direction);
            for (int side = -5; side <= 5; side++) {
                if (side == 0) continue;
                chunks.add(offsetChunk(backward, perpendicular, side));
            }
        }
        
        LOGGER.info("MAXIMUM OVERDRIVE MODE: {} chunks calculated for speed {} m/s", 
            chunks.size(), velocity.length());
        
        return chunks;
    }

}
