package com.rustbuild.build;

import com.rustbuild.block.CollisionBlock;
import com.rustbuild.block.CollisionBlockEntity;
import com.rustbuild.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Коллизия элементов, повёрнутых на любой угол: тело переводится в воксели 1/4 блока (маска 4x4x4 = 64 бита на блок),
 * которые хранятся в невидимых блоках-коллайдерах. Визуально элемент рисует сущность, физику даёт эта сетка.
 */
public final class Collision {
    private Collision() {}

    public static int bit(int i, int j, int k) { return i + 4 * j + 16 * k; }

    public static Map<BlockPos, Long> voxelize(Piece p) {
        Map<BlockPos, Long> out = new HashMap<>();
        AABB b = p.worldBounds().inflate(0.1);
        List<Solid> solids = p.solids();
        for (int bx = (int) Math.floor(b.minX); bx <= (int) Math.floor(b.maxX); bx++) {
            for (int by = (int) Math.floor(b.minY); by <= (int) Math.floor(b.maxY); by++) {
                for (int bz = (int) Math.floor(b.minZ); bz <= (int) Math.floor(b.maxZ); bz++) {
                    long mask = 0L;
                    for (int i = 0; i < 4; i++) {
                        for (int j = 0; j < 4; j++) {
                            for (int k = 0; k < 4; k++) {
                                Vec3 l = p.toLocal(new Vec3(bx + (i + 0.5) / 4, by + (j + 0.5) / 4, bz + (k + 0.5) / 4));
                                for (Solid s : solids) {
                                    if (s.contains(l.x, l.y, l.z, -0.05)) { mask |= 1L << bit(i, j, k); break; }
                                }
                            }
                        }
                    }
                    if (mask != 0L) out.put(new BlockPos(bx, by, bz), mask);
                }
            }
        }
        return out;
    }

    public static void apply(ServerLevel level, Piece p) {
        for (Map.Entry<BlockPos, Long> e : voxelize(p).entrySet()) {
            BlockPos pos = e.getKey();
            if (!level.isLoaded(pos)) continue;
            BlockState cur = level.getBlockState(pos);
            if (!(cur.getBlock() instanceof CollisionBlock)) {
                if (!cur.canBeReplaced() || !cur.getFluidState().isEmpty()) continue;   // земля уже твёрдая
                level.setBlock(pos, ModBlocks.COLLISION.get().defaultBlockState(), 3);
            }
            if (level.getBlockEntity(pos) instanceof CollisionBlockEntity be) be.put(p.id(), e.getValue());
        }
    }

    public static void remove(ServerLevel level, Piece p) {
        for (BlockPos pos : voxelize(p).keySet()) {
            if (!level.isLoaded(pos)) continue;
            if (!(level.getBlockState(pos).getBlock() instanceof CollisionBlock)) continue;
            if (level.getBlockEntity(pos) instanceof CollisionBlockEntity be) {
                be.remove(p.id());
                if (be.isEmpty()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
