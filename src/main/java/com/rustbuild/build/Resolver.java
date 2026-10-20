package com.rustbuild.build;

import com.rustbuild.block.BuildingBlock;
import com.rustbuild.block.BuildingBlock.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Определяет, ЧТО и КУДА будет поставлено при прицеливании.
 * Работает только по блокам мира, поэтому одинаково считается на клиенте (контур) и на сервере.
 */
public final class Resolver {
    private static final double REACH = 10.0;
    private static final int MAX_FILL = 16;
    private static final int NONE = Integer.MIN_VALUE;
    private static final int P = Piece.P;
    /** Четыре внутренних точки клетки: по ним определяем, есть ли в клетке плита (даже треугольная/с отверстием). */
    private static final int[][] CORNERS = {{1, 1}, {4, 1}, {1, 4}, {4, 4}};

    private Resolver() {}

    @Nullable
    public static Placement resolve(Level level, Player player, PieceType type) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos hp = hit.getBlockPos();
        BlockState hs = level.getBlockState(hp);
        boolean building = hs.getBlock() instanceof BuildingBlock;
        int floorY = building ? findFloorY(level, hp, hs) : NONE;

        return switch (type.family) {
            case FOUNDATION -> foundation(level, hit, hs, floorY, type);
            case FLOOR, ROOF -> floorLike(level, player, hit, hs, floorY, type);
            case WALL -> wall(level, hit, type, floorY);
            case STAIRS -> stairs(level, player, hit, floorY, type);
            case STEPS -> steps(level, hit, floorY, type);
        };
    }

    // ------------------------------------------------------------------ FOUNDATION / TRIANGLE_FOUNDATION
    @Nullable
    private static Placement foundation(Level level, BlockHitResult hit, BlockState hs, int floorY, PieceType type) {
        BlockPos hp = hit.getBlockPos();
        Vec3 loc = hit.getLocation();
        int gx, gz, b;
        String reason = null;
        Direction attach = null;
        int[] a = null;

        if (hs.getBlock() instanceof BuildingBlock) {
            if (floorY == NONE) return null;
            a = anchor(level, hp, floorY);
            if (a == null) return null;
            if (!hasFill(level, a[0], a[1], floorY)) reason = "not_foundation";
            attach = side(loc, a[0], a[1]);
            gx = a[0] + attach.getStepX();
            gz = a[1] + attach.getStepZ();
            b = floorY;
        } else {
            if (hit.getDirection() != Direction.UP) return null;
            gx = Math.floorDiv(hp.getX(), P);
            gz = Math.floorDiv(hp.getZ(), P);
            b = hp.getY() + 1;
            int snap = snapY(level, gx, gz, b);
            if (snap != NONE) b = snap;
        }

        Direction dir = type.isTriangle() ? triCorner(loc, gx, gz, attach, a) : Direction.NORTH;
        Piece tmp = Piece.blank(type, gx, gz, b, dir, 0);

        // Опора: заполняем пространство под фундаментом до земли
        int x0 = gx * P, z0 = gz * P, fill = 0;
        for (int[] q : tmp.slabPositions()) {
            int d = 0;
            while (d < MAX_FILL && level.getBlockState(new BlockPos(x0 + q[0], b - 1 - d, z0 + q[1])).canBeReplaced()) d++;
            fill = Math.max(fill, d);
        }
        boolean tooHigh = fill >= MAX_FILL;
        fill = Math.max(fill, 1);
        Piece piece = tmp.withFill(fill);

        if (reason == null && !slabFree(level, piece)) reason = "blocked";
        if (reason == null && tooHigh) reason = "no_ground";
        return new Placement(piece, reason == null, reason);
    }

    // ------------------------------------------------------------------ FLOOR* / ROOF*
    @Nullable
    private static Placement floorLike(Level level, Player pl, BlockHitResult hit, BlockState hs, int floorY, PieceType type) {
        if (!(hs.getBlock() instanceof BuildingBlock) || floorY == NONE) return null;
        BlockPos hp = hit.getBlockPos();
        Vec3 loc = hit.getLocation();
        boolean roof = type.family == PieceType.Family.ROOF;
        int gx, gz, b;
        Direction attach = null;
        int[] a = null;

        if (isKind(hs, Kind.WALL)) {
            // целимся в верх стены -> плита следующего этажа
            gx = cellAxis(hp.getX(), pl.getX());
            gz = cellAxis(hp.getZ(), pl.getZ());
            b = floorY + Piece.LEVEL_HEIGHT;
        } else {
            if (roof) return null;
            a = anchor(level, hp, floorY);
            if (a == null) return null;
            attach = side(loc, a[0], a[1]);
            gx = a[0] + attach.getStepX();
            gz = a[1] + attach.getStepZ();
            b = floorY;
        }

        Direction dir;
        if (type.isTriangle()) dir = triCorner(loc, gx, gz, attach, a);
        else if (roof) dir = pl.getDirection();
        else dir = Direction.NORTH;

        Piece piece = Piece.blank(type, gx, gz, b, dir, 0);
        String reason = null;
        if (roof ? !roofFree(level, piece) : !slabFree(level, piece)) reason = "blocked";
        else if (!supported(level, piece)) reason = "unsupported";
        return new Placement(piece, reason == null, reason);
    }

    // ------------------------------------------------------------------ WALL / DOORWAY / WINDOW / LOW_WALL
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
        int maxH = type.isLowWall() ? 1 : 3;
        String reason = null;
        outer:
        for (int i = 0; i <= P; i++) {
            BlockPos base = alongX ? new BlockPos(x0 + i, floorY, z0) : new BlockPos(x0, floorY, z0 + i);
            if (!isKind(level.getBlockState(base), Kind.FLOOR)) { reason = "unsupported"; break; }
            for (int h = 1; h <= maxH; h++) {
                BlockState st = level.getBlockState(base.above(h));
                boolean endPost = i == 0 || i == P;
                boolean ok = st.canBeReplaced() || (endPost && isKind(st, Kind.WALL));
                if (!ok) { reason = "blocked"; break outer; }
            }
        }
        Piece piece = Piece.blank(type, gx, gz, floorY, dir, 0);
        return new Placement(piece, reason == null, reason);
    }

    // ------------------------------------------------------------------ STAIRS / L / U / SPIRAL
    @Nullable
    private static Placement stairs(Level level, Player pl, BlockHitResult hit, int floorY, PieceType type) {
        if (floorY == NONE) return null;
        int[] a = anchor(level, hit.getBlockPos(), floorY);
        if (a == null) return null;

        Direction dir = pl.getDirection();
        int x0 = a[0] * P, z0 = a[1] * P;
        String reason = null;
        outer:
        for (int x = x0 + 1; x <= x0 + 4; x++) {
            for (int z = z0 + 1; z <= z0 + 4; z++) {
                if (!isKind(level.getBlockState(new BlockPos(x, floorY, z)), Kind.FLOOR)) { reason = "unsupported"; break outer; }
                for (int y = floorY + 1; y <= floorY + Piece.LEVEL_HEIGHT; y++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).canBeReplaced()) { reason = "blocked"; break outer; }
                }
            }
        }
        Piece piece = Piece.blank(type, a[0], a[1], floorY, dir, 0);
        return new Placement(piece, reason == null, reason);
    }

    // ------------------------------------------------------------------ FOUNDATION_STEPS
    @Nullable
    private static Placement steps(Level level, BlockHitResult hit, int floorY, PieceType type) {
        if (floorY == NONE) return null;
        int[] a = anchor(level, hit.getBlockPos(), floorY);
        if (a == null) return null;
        Direction d = side(hit.getLocation(), a[0], a[1]);
        Piece piece = Piece.blank(type, a[0], a[1], floorY, d, 0);

        String reason = null;
        if (!hasFill(level, a[0], a[1], floorY)) {
            reason = "not_foundation";
        } else {
            for (BlockEntry e : piece.entries()) {
                if (!level.getBlockState(e.pos()).canBeReplaced()) { reason = "blocked"; break; }
                if (level.getBlockState(e.pos().below()).canBeReplaced()) { reason = "unsupported"; break; }
            }
        }
        return new Placement(piece, reason == null, reason);
    }

    // ------------------------------------------------------------------ helpers
    private static boolean isKind(BlockState s, Kind k) {
        return s.getBlock() instanceof BuildingBlock && s.getValue(BuildingBlock.KIND) == k;
    }

    private static boolean hasSlab(Level level, int gx, int gz, int y) {
        for (int[] c : CORNERS) {
            if (isKind(level.getBlockState(new BlockPos(gx * P + c[0], y, gz * P + c[1])), Kind.FLOOR)) return true;
        }
        return false;
    }

    private static boolean hasFill(Level level, int gx, int gz, int y) {
        for (int[] c : CORNERS) {
            if (isKind(level.getBlockState(new BlockPos(gx * P + c[0], y - 1, gz * P + c[1])), Kind.FILL)) return true;
        }
        return false;
    }

    /** Y плиты пола, к которой относится блок постройки, в который целится игрок. */
    private static int findFloorY(Level level, BlockPos hp, BlockState hs) {
        Kind k = hs.getValue(BuildingBlock.KIND);
        if (k == Kind.FLOOR) return hp.getY();
        int step = k == Kind.WALL ? -1 : 1;
        int limit = k == Kind.WALL ? 6 : 16;
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

    /** Угол прямого угла треугольника: NORTH=СЗ, EAST=СВ, SOUTH=ЮВ, WEST=ЮЗ. */
    private static Direction triCorner(Vec3 loc, int gx, int gz, @Nullable Direction attach, @Nullable int[] a) {
        if (attach == null || a == null) {
            boolean west = loc.x < gx * P + 3.0, north = loc.z < gz * P + 3.0;
            if (north) return west ? Direction.NORTH : Direction.EAST;
            return west ? Direction.WEST : Direction.SOUTH;
        }
        boolean west = loc.x < a[0] * P + 3.0, north = loc.z < a[1] * P + 3.0;
        return switch (attach) {
            case EAST -> north ? Direction.NORTH : Direction.WEST;
            case WEST -> north ? Direction.EAST : Direction.SOUTH;
            case SOUTH -> west ? Direction.NORTH : Direction.EAST;
            default -> west ? Direction.WEST : Direction.SOUTH;
        };
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
                if (hasSlab(level, nx, nz, y) && hasFill(level, nx, nz, y)) return y;
            }
        }
        return NONE;
    }

    private static boolean terrain(BlockState s) {
        return s.canBeReplaced() || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SNOW);
    }

    private static boolean slabFree(Level level, Piece piece) {
        int x0 = piece.gx() * P, z0 = piece.gz() * P;
        for (int[] q : piece.slabPositions()) {
            BlockState s = level.getBlockState(new BlockPos(x0 + q[0], piece.baseY(), z0 + q[1]));
            if (s.getBlock() instanceof BuildingBlock) {
                boolean border = q[0] == 0 || q[0] == P || q[1] == 0 || q[1] == P;
                if (!(border && isKind(s, Kind.FLOOR))) return false;
            } else if (!terrain(s)) {
                return false;
            }
        }
        return true;
    }

    private static boolean roofFree(Level level, Piece piece) {
        for (BlockEntry e : piece.entries()) {
            BlockState s = level.getBlockState(e.pos());
            if (s.canBeReplaced()) continue;
            // общая линия с соседней крышей допустима
            if (!(s.getBlock() instanceof SlabBlock)) return false;
        }
        return true;
    }

    /** Простая проверка опоры при постановке (полную устойчивость считает Stability на сервере). */
    private static boolean supported(Level level, Piece piece) {
        if (count(level, piece.perimeterBelow()) >= 4) return true;
        int b = piece.baseY();
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : n) {
            int nx = piece.gx() + o[0], nz = piece.gz() + o[1];
            if (hasSlab(level, nx, nz, b) && count(level, Piece.blank(PieceType.FLOOR, nx, nz, b, Direction.NORTH, 0).perimeterBelow()) >= 4) {
                return true;
            }
        }
        return false;
    }

    private static int count(Level level, List<BlockPos> below) {
        int c = 0;
        for (BlockPos p : below) {
            if (!level.getBlockState(p).canBeReplaced()) c++;
        }
        return c;
    }
}
