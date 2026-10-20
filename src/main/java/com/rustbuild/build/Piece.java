package com.rustbuild.build;

import com.rustbuild.block.BuildingBlock;
import com.rustbuild.block.ModBlocks;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Один элемент постройки.
 *
 * Сетка: шаг клетки P = 5. Внутри клетки 4x4 свободных блока, по границам (линии x0, x0+5 / z0, z0+5)
 * проходят стены, общие для соседних клеток. Плита занимает 6x6. Высота этажа = 4.
 *
 * dir: для стен - NORTH (вдоль X) или WEST (вдоль Z); для лестниц/крыши/ступеней - направление;
 *      для треугольников - угол прямого угла: NORTH=СЗ, EAST=СВ, SOUTH=ЮВ, WEST=ЮЗ.
 */
public record Piece(PieceType type, int gx, int gz, int baseY, Direction dir, Tier tier, int fill,
                    UUID owner, long created, float hp, long lastDamage, BlockState[] fillOrig) {

    public static final int P = 5;
    public static final int LEVEL_HEIGHT = 4;
    private static final int[][] RING = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {3, 1}, {3, 2}, {3, 3}, {2, 3}};

    // ------------------------------------------------------------------ фабрики / копии
    public static Piece blank(PieceType type, int gx, int gz, int baseY, Direction dir, int fill) {
        return new Piece(type, gx, gz, baseY, dir, Tier.TWIG, fill, Util.NIL_UUID, 0L,
                Tier.TWIG.hp * type.hpMult, 0L, null);
    }

    public float maxHp() { return tier.hp * type.hpMult; }

    public Piece withTier(Tier t) {
        return new Piece(type, gx, gz, baseY, dir, t, fill, owner, created, t.hp * type.hpMult, lastDamage, fillOrig);
    }
    public Piece withOwner(UUID o, long time) {
        return new Piece(type, gx, gz, baseY, dir, tier, fill, o, time, maxHp(), 0L, fillOrig);
    }
    public Piece withFill(int f) {
        return new Piece(type, gx, gz, baseY, dir, tier, f, owner, created, hp, lastDamage, fillOrig);
    }
    public Piece withHp(float h, long damageTime) {
        return new Piece(type, gx, gz, baseY, dir, tier, fill, owner, created, h, damageTime, fillOrig);
    }
    public Piece withFillOrig(BlockState[] o) {
        return new Piece(type, gx, gz, baseY, dir, tier, fill, owner, created, hp, lastDamage, o);
    }

    public BlockPos center() { return new BlockPos(gx * P + 3, baseY, gz * P + 3); }

    public String key() {
        return switch (type.family) {
            case FOUNDATION, FLOOR -> "s:" + gx + "," + gz + "," + baseY + "," + (type.isTriangle() ? dir.getName() : "sq");
            case ROOF -> "r:" + gx + "," + gz + "," + baseY + "," + (type.isTriangle() ? dir.getName() : "sq");
            case WALL -> "w:" + gx + "," + gz + "," + baseY + "," + dir.getName();
            case STAIRS -> "t:" + gx + "," + gz + "," + baseY;
            case STEPS -> "e:" + gx + "," + gz + "," + baseY + "," + dir.getName();
        };
    }

    public boolean near(BlockPos p) {
        int x0 = gx * P, z0 = gz * P;
        return p.getX() >= x0 - 1 && p.getX() <= x0 + P + 1
                && p.getZ() >= z0 - 1 && p.getZ() <= z0 + P + 1
                && p.getY() >= baseY - fill - 1 && p.getY() <= baseY + LEVEL_HEIGHT + 3;
    }

    public boolean contains(BlockPos pos) {
        for (BlockEntry e : entries()) {
            if (e.pos().equals(pos)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ маски плит
    private boolean inMask(int dx, int dz) {
        if (!type.isTriangle()) return true;
        return switch (dir) {
            case NORTH -> dx + dz <= P;
            case EAST -> dz <= dx;
            case SOUTH -> dx + dz >= P;
            default -> dx <= dz;
        };
    }

    private boolean inHole(int dx, int dz) {
        if (!type.isFrame()) return false;
        if (!type.isTriangle()) return dx >= 2 && dx <= 3 && dz >= 2 && dz <= 3;
        int i, j;
        switch (dir) {
            case NORTH -> { i = dx; j = dz; }
            case EAST -> { i = P - dx; j = dz; }
            case SOUTH -> { i = P - dx; j = P - dz; }
            default -> { i = dx; j = P - dz; }
        }
        return i >= 1 && i <= 2 && j >= 1 && j <= 2;
    }

    /** Локальные координаты (dx,dz) всех блоков плиты (без отверстий). */
    public List<int[]> slabPositions() {
        List<int[]> out = new ArrayList<>();
        for (int dx = 0; dx <= P; dx++) {
            for (int dz = 0; dz <= P; dz++) {
                if (inMask(dx, dz) && !inHole(dx, dz)) out.add(new int[]{dx, dz});
            }
        }
        return out;
    }

    private static boolean border(int dx, int dz) { return dx == 0 || dx == P || dz == 0 || dz == P; }

    /** Позиции прямо под краем плиты (там стоят стены, на которые она опирается). */
    public List<BlockPos> perimeterBelow() {
        List<BlockPos> out = new ArrayList<>();
        for (int[] q : slabPositions()) {
            if (border(q[0], q[1])) out.add(new BlockPos(gx * P + q[0], baseY - 1, gz * P + q[1]));
        }
        return out;
    }

    /** Краевые блоки самой плиты (общие с соседними плитами). */
    public List<BlockPos> borderPositions() {
        List<BlockPos> out = new ArrayList<>();
        for (int[] q : slabPositions()) {
            if (border(q[0], q[1])) out.add(new BlockPos(gx * P + q[0], baseY, gz * P + q[1]));
        }
        return out;
    }

    /** Позиции, на которые опирается стена/лестница/ступени (блоки плиты под ними). */
    public List<BlockPos> basePositions() {
        List<BlockPos> out = new ArrayList<>();
        int x0 = gx * P, z0 = gz * P;
        switch (type.family) {
            case WALL -> {
                for (int i = 0; i <= P; i++) {
                    out.add(dir == Direction.NORTH ? new BlockPos(x0 + i, baseY, z0) : new BlockPos(x0, baseY, z0 + i));
                }
            }
            case STAIRS -> {
                for (int dx = 1; dx <= 4; dx++) for (int dz = 1; dz <= 4; dz++) out.add(new BlockPos(x0 + dx, baseY, z0 + dz));
            }
            case STEPS -> {
                for (int i = 1; i <= 4; i++) {
                    out.add(switch (dir) {
                        case EAST -> new BlockPos(x0 + P, baseY, z0 + i);
                        case WEST -> new BlockPos(x0, baseY, z0 + i);
                        case SOUTH -> new BlockPos(x0 + i, baseY, z0 + P);
                        default -> new BlockPos(x0 + i, baseY, z0);
                    });
                }
            }
            default -> { }
        }
        return out;
    }

    // ------------------------------------------------------------------ геометрия лестниц
    /** Блок внутренней клетки 4x4: u - вдоль dir, v - вправо (по часовой) от dir. */
    private BlockPos cell(int u, int v, int y) {
        Direction r = dir.getClockWise();
        int sx = dir.getStepX(), sz = dir.getStepZ(), tx = r.getStepX(), tz = r.getStepZ();
        int x0 = gx * P, z0 = gz * P;
        int bx = (sx + tx > 0) ? x0 + 1 : x0 + 4;
        int bz = (sz + tz > 0) ? z0 + 1 : z0 + 4;
        return new BlockPos(bx + sx * u + tx * v, y, bz + sz * u + tz * v);
    }

    private void column(List<BlockEntry> out, BlockState wall, int u, int v, int height) {
        for (int h = 1; h <= height; h++) out.add(new BlockEntry(cell(u, v, baseY + h), wall));
    }

    private void stair(List<BlockEntry> out, int u, int v, int y, Direction facing) {
        out.add(new BlockEntry(cell(u, v, y), tier.stairs().defaultBlockState().setValue(StairBlock.FACING, facing)));
    }

    private void flightOne(List<BlockEntry> out, BlockState wall) {
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 2; v++) {
                column(out, wall, u, v, u);
                stair(out, u, v, baseY + 1 + u, dir);
            }
        }
    }

    private void stairEntries(List<BlockEntry> out, BlockState wall) {
        Direction r = dir.getClockWise();
        switch (type) {
            case STAIRS -> {
                for (int u = 0; u < 4; u++) {
                    for (int v = 0; v < 4; v++) {
                        column(out, wall, u, v, u);
                        stair(out, u, v, baseY + 1 + u, dir);
                    }
                }
            }
            case L_STAIRS -> {
                flightOne(out, wall);
                for (int u = 2; u < 4; u++) for (int v = 0; v < 2; v++) column(out, wall, u, v, 2);
                for (int u = 2; u < 4; u++) {
                    for (int v = 2; v < 4; v++) {
                        int s = v - 2;
                        column(out, wall, u, v, 2 + s);
                        stair(out, u, v, baseY + 3 + s, r);
                    }
                }
            }
            case U_STAIRS -> {
                flightOne(out, wall);
                for (int u = 2; u < 4; u++) for (int v = 0; v < 4; v++) column(out, wall, u, v, 2);
                for (int v = 2; v < 4; v++) {
                    column(out, wall, 1, v, 2);
                    stair(out, 1, v, baseY + 3, dir.getOpposite());
                    column(out, wall, 0, v, 3);
                    stair(out, 0, v, baseY + 4, dir.getOpposite());
                }
            }
            case SPIRAL_STAIRS -> {
                for (int u = 1; u <= 2; u++) for (int v = 1; v <= 2; v++) column(out, wall, u, v, 4);
                Block slab = tier.slab();
                for (int k = 0; k < RING.length; k++) {
                    int u = RING[k][0], v = RING[k][1];
                    column(out, wall, u, v, k / 2);
                    SlabType st = (k % 2 == 0) ? SlabType.BOTTOM : SlabType.TOP;
                    out.add(new BlockEntry(cell(u, v, baseY + 1 + k / 2),
                            slab.defaultBlockState().setValue(SlabBlock.TYPE, st)));
                }
            }
            default -> { }
        }
    }

    private int roofU(int dx, int dz) {
        if (type.isTriangle()) return (dir == Direction.NORTH || dir == Direction.EAST) ? P - dz : dz;
        return switch (dir) {
            case EAST -> dx;
            case WEST -> P - dx;
            case SOUTH -> dz;
            default -> P - dz;
        };
    }

    // ------------------------------------------------------------------ блоки элемента
    /** Все блоки, из которых состоит элемент (порядок детерминирован - важно для fillOrig). */
    public List<BlockEntry> entries() {
        List<BlockEntry> out = new ArrayList<>();
        BuildingBlock block = ModBlocks.get(tier);
        BlockState floor = block.defaultBlockState().setValue(BuildingBlock.KIND, BuildingBlock.Kind.FLOOR);
        BlockState wall = block.defaultBlockState().setValue(BuildingBlock.KIND, BuildingBlock.Kind.WALL);
        BlockState fillState = block.defaultBlockState().setValue(BuildingBlock.KIND, BuildingBlock.Kind.FILL);
        int x0 = gx * P, z0 = gz * P;

        switch (type.family) {
            case FOUNDATION -> {
                for (int[] q : slabPositions()) {
                    out.add(new BlockEntry(new BlockPos(x0 + q[0], baseY, z0 + q[1]), floor));
                    for (int d = 1; d <= fill; d++) {
                        out.add(new BlockEntry(new BlockPos(x0 + q[0], baseY - d, z0 + q[1]), fillState));
                    }
                }
            }
            case FLOOR -> {
                for (int[] q : slabPositions()) out.add(new BlockEntry(new BlockPos(x0 + q[0], baseY, z0 + q[1]), floor));
            }
            case ROOF -> {
                Block slab = tier.slab();
                for (int[] q : slabPositions()) {
                    int u = roofU(q[0], q[1]);
                    SlabType st = (u % 2 == 0) ? SlabType.BOTTOM : SlabType.TOP;
                    out.add(new BlockEntry(new BlockPos(x0 + q[0], baseY + u / 2, z0 + q[1]),
                            slab.defaultBlockState().setValue(SlabBlock.TYPE, st)));
                }
            }
            case WALL -> {
                boolean alongX = dir == Direction.NORTH;
                int maxH = type.isLowWall() ? 1 : 3;
                for (int i = 0; i <= P; i++) {
                    for (int h = 1; h <= maxH; h++) {
                        if (opening(i, h)) continue;
                        BlockPos pos = alongX ? new BlockPos(x0 + i, baseY + h, z0) : new BlockPos(x0, baseY + h, z0 + i);
                        out.add(new BlockEntry(pos, wall));
                    }
                }
            }
            case STAIRS -> stairEntries(out, wall);
            case STEPS -> {
                BlockState st = tier.stairs().defaultBlockState().setValue(StairBlock.FACING, dir.getOpposite());
                for (int i = 1; i <= 4; i++) {
                    BlockPos pos = switch (dir) {
                        case EAST -> new BlockPos(x0 + P + 1, baseY, z0 + i);
                        case WEST -> new BlockPos(x0 - 1, baseY, z0 + i);
                        case SOUTH -> new BlockPos(x0 + i, baseY, z0 + P + 1);
                        default -> new BlockPos(x0 + i, baseY, z0 - 1);
                    };
                    out.add(new BlockEntry(pos, st));
                }
            }
        }
        return out;
    }

    private boolean opening(int i, int h) {
        if (i != 2 && i != 3) return false;
        return (type == PieceType.DOORWAY && h <= 2) || (type == PieceType.WINDOW && h == 2);
    }

    // ------------------------------------------------------------------ предпросмотр
    private AABB lineBox(double i0, double i1, double h0, double h1) {
        double x0 = gx * P, z0 = gz * P, y = baseY;
        if (dir == Direction.NORTH) return new AABB(x0 + i0, y + h0, z0, x0 + i1, y + h1, z0 + 1);
        return new AABB(x0, y + h0, z0 + i0, x0 + 1, y + h1, z0 + i1);
    }

    private static AABB blockBox(BlockPos a, BlockPos b) {
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    private void rowBoxes(List<AABB> out, double y0, double y1) {
        double x0 = gx * P, z0 = gz * P;
        for (int dz = 0; dz <= P; dz++) {
            int min = -1, max = -1;
            for (int dx = 0; dx <= P; dx++) {
                if (!inMask(dx, dz)) continue;
                if (min < 0) min = dx;
                max = dx;
            }
            if (min >= 0) out.add(new AABB(x0 + min, y0, z0 + dz, x0 + max + 1, y1, z0 + dz + 1));
        }
    }

    /** Основные контуры элемента. */
    public List<AABB> previewBoxes() {
        List<AABB> out = new ArrayList<>();
        double x0 = gx * P, z0 = gz * P, y = baseY;
        switch (type.family) {
            case FOUNDATION, FLOOR -> {
                double low = type.family == PieceType.Family.FOUNDATION ? y - fill : y;
                if (type.isTriangle()) {
                    rowBoxes(out, low, y + 1);
                } else {
                    out.add(new AABB(x0, low, z0, x0 + P + 1, y + 1, z0 + P + 1));
                    out.add(new AABB(x0 + 1, y + 1, z0 + 1, x0 + P, y + 1, z0 + P));
                }
            }
            case ROOF -> {
                for (int u = 0; u <= P; u++) {
                    int minX = 99, maxX = -1, minZ = 99, maxZ = -1;
                    for (int[] q : slabPositions()) {
                        if (roofU(q[0], q[1]) != u) continue;
                        minX = Math.min(minX, q[0]); maxX = Math.max(maxX, q[0]);
                        minZ = Math.min(minZ, q[1]); maxZ = Math.max(maxZ, q[1]);
                    }
                    if (maxX >= 0) {
                        double yy = y + u / 2;
                        out.add(new AABB(x0 + minX, yy, z0 + minZ, x0 + maxX + 1, yy + 1, z0 + maxZ + 1));
                    }
                }
            }
            case WALL -> out.add(lineBox(0, P + 1, 1, type.isLowWall() ? 2 : 4));
            case STAIRS -> {
                if (type == PieceType.STAIRS) {
                    for (int s = 0; s < 4; s++) out.add(blockBox(cell(s, 0, baseY + 1), cell(s, 3, baseY + 1 + s)));
                } else {
                    out.add(blockBox(cell(0, 0, baseY + 1), cell(3, 3, baseY + 4)));
                }
            }
            case STEPS -> {
                List<BlockEntry> es = entries();
                out.add(blockBox(es.get(0).pos(), es.get(es.size() - 1).pos()));
            }
        }
        return out;
    }

    /** Проёмы и отверстия (рисуются другим цветом). */
    public List<AABB> previewCutouts() {
        List<AABB> out = new ArrayList<>();
        if (type == PieceType.DOORWAY) out.add(lineBox(2, 4, 1, 3));
        if (type == PieceType.WINDOW) out.add(lineBox(2, 4, 2, 3));
        if (type.isFrame()) {
            int minX = 99, maxX = -1, minZ = 99, maxZ = -1;
            for (int dx = 0; dx <= P; dx++) {
                for (int dz = 0; dz <= P; dz++) {
                    if (inMask(dx, dz) && inHole(dx, dz)) {
                        minX = Math.min(minX, dx); maxX = Math.max(maxX, dx);
                        minZ = Math.min(minZ, dz); maxZ = Math.max(maxZ, dz);
                    }
                }
            }
            if (maxX >= 0) {
                double x0 = gx * P, z0 = gz * P;
                out.add(new AABB(x0 + minX, baseY, z0 + minZ, x0 + maxX + 1, baseY + 1, z0 + maxZ + 1));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ NBT
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type.name());
        t.putInt("gx", gx);
        t.putInt("gz", gz);
        t.putInt("y", baseY);
        t.putInt("dir", dir.get3DDataValue());
        t.putString("tier", tier.name());
        t.putInt("fill", fill);
        t.putUUID("owner", owner);
        t.putLong("created", created);
        t.putFloat("hp", hp);
        t.putLong("dmg", lastDamage);
        if (fillOrig != null) {
            ListTag palette = new ListTag();
            Map<BlockState, Integer> index = new HashMap<>();
            int[] ids = new int[fillOrig.length];
            for (int i = 0; i < fillOrig.length; i++) {
                BlockState s = fillOrig[i];
                if (s == null) { ids[i] = -1; continue; }
                Integer k = index.get(s);
                if (k == null) {
                    k = palette.size();
                    index.put(s, k);
                    palette.add(NbtUtils.writeBlockState(s));
                }
                ids[i] = k;
            }
            t.put("palette", palette);
            t.putIntArray("orig", ids);
        }
        return t;
    }

    public static Piece load(CompoundTag t) {
        PieceType type = PieceType.valueOf(t.getString("type"));
        Tier tier = Tier.valueOf(t.getString("tier"));
        float hp = t.contains("hp") ? t.getFloat("hp") : tier.hp * type.hpMult;
        BlockState[] orig = null;
        if (t.contains("orig")) {
            ListTag pal = t.getList("palette", Tag.TAG_COMPOUND);
            List<BlockState> states = new ArrayList<>();
            for (int i = 0; i < pal.size(); i++) {
                states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pal.getCompound(i)));
            }
            int[] ids = t.getIntArray("orig");
            orig = new BlockState[ids.length];
            for (int i = 0; i < ids.length; i++) orig[i] = ids[i] < 0 ? null : states.get(ids[i]);
        }
        return new Piece(type, t.getInt("gx"), t.getInt("gz"), t.getInt("y"),
                Direction.from3DDataValue(t.getInt("dir")), tier, t.getInt("fill"),
                t.getUUID("owner"), t.getLong("created"), hp, t.getLong("dmg"), orig);
    }
}
