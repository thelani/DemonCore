package com.lani.demoncore.optimization;

import com.lani.demoncore.config.DemonCoreConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class VisibilityLattice {

    private VisibilityLattice() {
    }

    private static final LongSet VISIBLE_CELLS = new LongOpenHashSet(4096);
    private static final LongSet BE_CELLS = new LongOpenHashSet(4096);
    private static final LongSet ENTITY_CELLS = new LongOpenHashSet(4096);

    private static volatile long lastRebuildFrame;
    private static volatile long queries;
    private static volatile long fastSkips;
    private static int cellSize = 8;

    private static long cellKey(int cx, int cy, int cz) {
        return (long) cx & 0x1FFFFFL | ((long) cz & 0x1FFFFFL) << 21 | ((long) cy & 0xFFFFL) << 42;
    }

    private static int cellSize() {
        int s = DemonCoreConfig.getInt(DemonCoreConfig.VISIBILITY_CELL_SIZE, 8);
        cellSize = Math.max(4, s);
        return cellSize;
    }

    
    public static void rebuild(long frameId) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.VISIBILITY_LATTICE, true)) {
            return;
        }
        if (frameId == lastRebuildFrame) {
            return;
        }
        lastRebuildFrame = frameId;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Entity cam = mc.getCameraEntity();
        if (cam == null) {
            return;
        }

        Vec3 eye = cam.position();
        float yaw = cam.getYRot();
        double yawRad = Math.toRadians(-yaw);
        double fx = Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        int cs = cellSize();
        int beCullDist = 64;
        int entCullDist = DemonCoreConfig.getInt(DemonCoreConfig.LOD_BILLBOARD_DISTANCE, 72);
        int beCellRange = (beCullDist / cs) + 1;
        int entCellRange = (entCullDist / cs) + 1;

        int cxC = (int) Math.floor(eye.x / cs);
        int cyC = (int) Math.floor(eye.y / cs);
        int czC = (int) Math.floor(eye.z / cs);

        VISIBLE_CELLS.clear();
        BE_CELLS.clear();
        ENTITY_CELLS.clear();

        buildVisibleSet(cxC, cyC, czC, beCellRange, fx, fz, cs, eye.x, eye.z, beCullDist);
        buildVisibleSet(cxC, cyC, czC, entCellRange, fx, fz, cs, eye.x, eye.z, entCullDist);
    }

    private static void buildVisibleSet(int cxC, int cyC, int czC, int range,
                                         double fx, double fz, int cs,
                                         double ex, double ez, double distBlocks) {
        double distSq = distBlocks * distBlocks;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    int cx = cxC + dx;
                    int cy = cyC + dy;
                    int cz = czC + dz;
                    double cellCx = cx * cs + cs * 0.5;
                    double cellCz = cz * cs + cs * 0.5;
                    double rx = cellCx - ex;
                    double rz = cellCz - ez;
                    double dSq = rx * rx + rz * rz;
                    if (dSq > distSq) {
                        continue;
                    }
                    double dot = rx * fx + rz * fz;
                    if (dot < -4.0 && dSq > 36.0) {
                        
                        continue;
                    }
                    VISIBLE_CELLS.add(cellKey(cx, cy, cz));
                }
            }
        }
    }

    
    
    

    public static boolean blockEntityMaybeVisible(BlockEntity be) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.VISIBILITY_LATTICE, true)) {
            return true;
        }
        queries++;
        int cs = cellSize();
        BlockPos p = be.getBlockPos();
        int cx = (int) Math.floor((double) p.getX() / cs);
        int cy = (int) Math.floor((double) p.getY() / cs);
        int cz = (int) Math.floor((double) p.getZ() / cs);
        long key = cellKey(cx, cy, cz);
        BE_CELLS.add(key);
        if (!VISIBLE_CELLS.contains(key)) {
            fastSkips++;
            return false;
        }
        return true;
    }

    public static boolean entityMaybeVisible(Entity e) {
        if (!DemonCoreConfig.getBool(DemonCoreConfig.VISIBILITY_LATTICE, true)) {
            return true;
        }
        queries++;
        int cs = cellSize();
        int cx = (int) Math.floor(e.getX() / cs);
        int cy = (int) Math.floor(e.getY() / cs);
        int cz = (int) Math.floor(e.getZ() / cs);
        long key = cellKey(cx, cy, cz);
        ENTITY_CELLS.add(key);
        if (!VISIBLE_CELLS.contains(key)) {
            fastSkips++;
            return false;
        }
        return true;
    }

    
    
    

    public static double skipRate() {
        return queries == 0L ? 0.0 : (double) fastSkips / (double) queries;
    }

    public static int visibleCells() {
        return VISIBLE_CELLS.size();
    }

    public static String getStats() {
        return String.format("VisLattice: %d visible cells | %d BE cells, %d Ent cells | fast skip %.1f%% of %d queries",
                visibleCells(), BE_CELLS.size(), ENTITY_CELLS.size(), skipRate() * 100.0, queries);
    }
}
