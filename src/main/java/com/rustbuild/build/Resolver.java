package com.rustbuild.build;

import com.rustbuild.block.BuildingBlock;
import com.rustbuild.block.BuildingBlock.Kind;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Определяет, ЧТО и КУДА будет поставлено при прицеливании.
 * Работает только по блокам мира (без SavedData), поэтому одинаково считается
 * и на клиенте (предпросмотр), и на сервере (реальная постановка).
 */
public final class Resolver {
    private static final double REACH = 10.0;
    private static final int MAX_FILL = 8;
    private static final int NONE = Integer.MIN_VALUE;
    private static final int P = Piece.P;

    private Resolver() {}

    @Nullable
    public static Placement resolve(Level level, Player player, PieceType type) {
        HitResult hr = player.pick(REACH, 1.0F, false);
        if (hr.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult hit = (BlockHitResult) hr;
        BlockPos hp = hit.getBlockPos();
        BlockState hs = level.getBlockState(hp);
        boolean building = hs.getBlock() instanceof BuildingBlock;
        int floorY = building ? findFloorY(level, hp, hs) : NONE;

        return switch (type) {
            case FOUNDATION -> foundation(level, hit, hs, floorY);
            case FLOOR -> floor(level, player, hit, hs, floorY);
            case WALL, DOORWAY, WINDOW -> wall(level, hit, type, floorY);
            case STAIRS -> stairs(level, player, hit, floorY);
        };
    }

    // ------------------------------------------------------------------ FOUNDATION
    @Nullable
    private static Placement foundation(Level level, BlockHitResult hit, BlockState hs, int floorY) {
        BlockPos hp = hit.getBlockPos();
        int gx, gz, b;
        String reason = null;

        if (hs.getBlock() instanceof BuildingBlock) {
            if (floorY == NONE) return null;
            int[] a = anchor(level, hp, floorY);
            if (a == null) return null;
            if (!isKind(level.getBlockState(center(a[0], a[1], floorY - 1)), Kind.FILL)) reason = "not_foundation";
            Direction d = side(hit.getLocation(), a[0], a[1]);
            gx = a[0] + d.getStepX();
            gz = a[1] + d.getStepZ();
            b = floorY;
        } else {
            if (hit.getDirection() != Direction.UP) return null;
            gx = Math.floorDiv(hp.getX(), P);
            gz = Math.floorDiv(hp.getZ(), P);
            b = hp.getY() + 1;
            int snap = snapY(level, gx, gz, b);
            if (snap != NONE) b = snap;
        }

        int x0 = gx * P, z0 = gz * P;
        int fill = 0;
        for (int dx = 0; dx <= P; dx++) {
            for (int dz = 0; dz <= P; dz++) {
                int d = 0;
                while (d < MAX_FILL && level.getBlockState(new BlockPos(x0 + dx, b - 1 - d, z0 + dz)).canBeReplaced()) d++;
                fill = Math.max(fill, d);
            }
        }

        if (reason == null && !slabFree(level, x0, z0, b)) reason = "blocked";
        if (reason == null && fill >= MAX_FILL) reason = "no_ground";

        Piece piece = new Piece(PieceType.FOUNDATION, gx, gz, b, Direction.NORTH, Tier.TWIG, fill, Util.NIL_UUID, 0L);
        return new Placement(piece, reason == null, reason);
    }

    // ------------------------------------------------------------------ FLOOR
    @Nullable
    private static Placement floor(Level level, Player pl, BlockHitResult hit, BlockState hs, int floorY) {
        if (!(hs.getBlock() instanceof BuildingBlock) || floorY == NONE) return null;
        BlockPos hp = hit.getBlockPos();
        int gx, gz, b;

        if (isKind(hs, Kind.WALL)) {
            // целимся в верх стены -> пол следующего этажа
            gx = cellAxis(hp.getX(), pl.getX());
            gz = cellAxis(hp.getZ(), pl.getZ());
            b = floorY + Piece.LEVEL_HEIGHT;
        } else {
            int[] a = anchor(level, hp, floorY);
            if (a == null) return null;
            Direction d = side(hit.getLocation(), a[0], a[1]);
            gx = a[0] + d.getStepX();
            gz = a[1] + d.getStepZ();
            b = floorY;
        }

        String reason = null;
        if (!slabFree(level, gx * P, gz * P, b)) reason = "blocked";
        else if (!supported(level, gx, gz, b)) reason = "unsupported";

        Piece piece = new Piece(PieceType.FLOOR, gx, gz, b, Direction.NORTH, Tier.TWIG, 0, Util.NIL_UUID, 0L);
        return new Placement(piece, reason == null, reason);
    }

    // ------------------------------------------------------------------ WALL / DOORWAY / WINDOW
    @Nullable
    private static Placement wall(Level level, BlockHitResult hit, PieceType type, int floorY) {
        if (floorY == NONE) return null;
        int[] a = anchor(level, hit.getBlockPos(), floorY);
        if (a == null) return null;

        Direction d = side(hit.getLocation(), a[0], a[1]);
        int gx = a[0], gz = a[1];
        Direction dir;
        if (d == Direction.NORTH) dir = Direction.NORTH;
        else if (d == Direction.WEST) dir = Direction.WEST;
        else if (d == Direction.SOUTH) { gz++; dir = Direction.NORTH; }
        else { gx++; dir = Direction.WEST; }

        int x0 = gx * P, z0 = gz * P;
        boolean alongX = dir == Direction.NORTH;
        String reason = null;
        outer:
        for (int i = 0; i <= P; i++) {
            BlockPos base = alongX ? new BlockPos(x0 + i, floorY, z0) : new BlockPos(x0, floorY, z0 + i);
            if (!isKind(level.getBlockState(base), Kind.FLOOR)) { reason = "unsupported"; break; }
            for (int h = 1; h <= 3; h++) {
                BlockPos pos = base.above(h);
                BlockState st = level.getBlockState(pos);
                boolean endPost = i == 0 || i == P;
                boolean ok = st.canBeReplaced() || (endPost && isKind(st, Kind.WALL));
                if (!ok) { reason = "blocked"; break outer; }
            }
        }

        Piece piece = new Piece(type, gx, gz, floorY, dir, Tier.TWIG, 0, Util.NIL_UUID, 0L);
        return new Placement(piece, reason == null, reason);
    }

    // ------------------------------------------------------------------ STAIRS
    @Nullable
    private static Placement stairs(Level level, Player pl, BlockHitResult hit, int floorY) {
        if (floorY == NONE) return null;
        int[] a = anchor(level, hit.getBlockPos(), floorY);
        if (a == null) return null;

        Direction dir = pl.getDirection();
        int x0 = a[0] * P, z0 = a[1] * P;
        String reason = null;
        outer:
        for (int x = x0 + 1; x <= x0 + 4; x++) {
            for (int z = z0 + 1; z <= z0 + 4; z++) {
                for (int y = floorY + 1; y <= floorY + Piece.LEVEL_HEIGHT; y++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).canBeReplaced()) { reason = "blocked"; break outer; }
                }
            }
        }
        Piece piece = new Piece(PieceType.STAIRS, a[0], a[1], floorY, dir, Tier.TWIG, 0, Util.NIL_UUID, 0L);
        return new Placement(piece, reason == null, reason);
    }

    // ------------------------------------------------------------------ helpers
    private static boolean isKind(BlockState s, Kind k) {
        return s.getBlock() instanceof BuildingBlock && s.getValue(BuildingBlock.KIND) == k;
    }

    private static BlockPos center(int gx, int gz, int y) {
        return new BlockPos(gx * P + 2, y, gz * P + 2);
    }

    private static boolean hasSlab(Level level, int gx, int gz, int y) {
        return isKind(level.getBlockState(center(gx, gz, y)), Kind.FLOOR);
    }

    /** Y плиты пола, к которой относится блок постройки, в который целится игрок. */
    private static int findFloorY(Level level, BlockPos hp, BlockState hs) {
        Kind k = hs.getValue(BuildingBlock.KIND);
        if (k == Kind.FLOOR) return hp.getY();
        int step = k == Kind.WALL ? -1 : 1;
        int limit = k == Kind.WALL ? 6 : 10;
        for (int i = 1; i <= limit; i++) {
            BlockPos p = hp.offset(0, step * i, 0);
            BlockState s = level.getBlockState(p);
            if (isKind(s, Kind.FLOOR)) return p.getY();
            if (!(s.getBlock() instanceof BuildingBlock)) break;
        }
        return NONE;
    }

    /** Клетка, у которой на высоте y есть плита и которая содержит блок hp (с учётом общих линий). */
    @Nullable
    private static int[] anchor(Level level, BlockPos hp, int y) {
        int[] xs = {Math.floorDiv(hp.getX(), P), Math.floorDiv(hp.getX() - 1, P)};
        int[] zs = {Math.floorDiv(hp.getZ(), P), Math.floorDiv(hp.getZ() - 1, P)};
        for (int gx : xs) {
            for (int gz : zs) {
                if (hasSlab(level, gx, gz, y)) return new int[]{gx, gz};
            }
        }
        return null;
    }

    /** В какую сторону от центра клетки смотрит точка прицеливания. */
    private static Direction side(Vec3 loc, int gx, int gz) {
        double dx = loc.x - (gx * P + 3.0);
        double dz = loc.z - (gz * P + 3.0);
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static int cellAxis(int blockCoord, double playerCoord) {
        int c = Math.floorDiv(blockCoord, P);
        if (Math.floorMod(blockCoord, P) == 0 && playerCoord < blockCoord + 0.5) c--;
        return c;
    }

    /** Подгонка высоты нового фундамента под соседний (как "сокеты" в Rust). */
    private static int snapY(Level level, int gx, int gz, int b) {
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : n) {
            int nx = gx + o[0], nz = gz + o[1];
            for (int y = b + 2; y >= b - 2; y--) {
                if (hasSlab(level, nx, nz, y) && isKind(level.getBlockState(center(nx, nz, y - 1)), Kind.FILL)) return y;
            }
        }
        return NONE;
    }

    private static boolean terrain(BlockState s) {
        return s.canBeReplaced() || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SNOW);
    }

    private static boolean slabFree(Level level, int x0, int z0, int y) {
        for (int dx = 0; dx <= P; dx++) {
            for (int dz = 0; dz <= P; dz++) {
                BlockState s = level.getBlockState(new BlockPos(x0 + dx, y, z0 + dz));
                if (s.getBlock() instanceof BuildingBlock) {
                    boolean border = dx == 0 || dx == P || dz == 0 || dz == P;
                    if (!(border && isKind(s, Kind.FLOOR))) return false;
                } else if (!terrain(s)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Простая "устойчивость": опора под периметром или консоль в 1 клетку от опёртой плиты. */
    private static boolean supported(Level level, int gx, int gz, int b) {
        if (supportCount(level, gx, gz, b) >= 4) return true;
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : n) {
            int nx = gx + o[0], nz = gz + o[1];
            if (hasSlab(level, nx, nz, b) && supportCount(level, nx, nz, b) >= 4) return true;
        }
        return false;
    }

    private static int supportCount(Level level, int gx, int gz, int b) {
        int x0 = gx * P, z0 = gz * P, c = 0;
        for (int dx = 0; dx <= P; dx++) {
            for (int dz = 0; dz <= P; dz++) {
                if (dx != 0 && dx != P && dz != 0 && dz != P) continue;
                if (!level.getBlockState(new BlockPos(x0 + dx, b - 1, z0 + dz)).canBeReplaced()) c++;
            }
        }
        return c;
    }
}
