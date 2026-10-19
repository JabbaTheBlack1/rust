package com.rustbuild.build;

import com.rustbuild.block.BuildingBlock;
import com.rustbuild.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Один элемент постройки.
 *
 * Сетка: шаг клетки P = 5 блоков. Внутри клетки 4x4 свободных блока,
 * по границам клетки (линии x0, x0+5 / z0, z0+5) проходят стены, общие для соседних клеток.
 * Плита пола/фундамента занимает 6x6 (включая линии стен).
 * Высота этажа = 4 (плита + 3 блока стены).
 */
public record Piece(PieceType type, int gx, int gz, int baseY, Direction dir,
                    Tier tier, int fill, UUID owner, long created) {

    public static final int P = 5;
    public static final int LEVEL_HEIGHT = 4;

    public Piece withTier(Tier t) { return new Piece(type, gx, gz, baseY, dir, t, fill, owner, created); }
    public Piece withOwner(UUID o, long time) { return new Piece(type, gx, gz, baseY, dir, tier, fill, o, time); }

    public String key() {
        String cat = type.isSlab() ? "s" : type.isWall() ? "w" : "t";
        String d = cat.equals("s") ? "" : "," + dir.getName();
        return cat + ":" + gx + "," + gz + "," + baseY + d;
    }

    public boolean near(BlockPos p) {
        int x0 = gx * P, z0 = gz * P;
        return p.getX() >= x0 - 1 && p.getX() <= x0 + P + 1
                && p.getZ() >= z0 - 1 && p.getZ() <= z0 + P + 1
                && p.getY() >= baseY - fill - 1 && p.getY() <= baseY + LEVEL_HEIGHT + 2;
    }

    public boolean contains(BlockPos pos) {
        for (BlockEntry e : entries()) {
            if (e.pos().equals(pos)) return true;
        }
        return false;
    }

    private boolean opening(int i, int h) {
        if (i != 2 && i != 3) return false;
        return (type == PieceType.DOORWAY && h <= 2) || (type == PieceType.WINDOW && h == 2);
    }

    /** Все блоки, из которых состоит элемент. */
    public List<BlockEntry> entries() {
        List<BlockEntry> out = new ArrayList<>();
        BuildingBlock block = ModBlocks.get(tier);
        BlockState floor = block.defaultBlockState().setValue(BuildingBlock.KIND, BuildingBlock.Kind.FLOOR);
        BlockState wall = block.defaultBlockState().setValue(BuildingBlock.KIND, BuildingBlock.Kind.WALL);
        BlockState fillState = block.defaultBlockState().setValue(BuildingBlock.KIND, BuildingBlock.Kind.FILL);
        int x0 = gx * P, z0 = gz * P;

        switch (type) {
            case FOUNDATION, FLOOR -> {
                for (int dx = 0; dx <= P; dx++) {
                    for (int dz = 0; dz <= P; dz++) {
                        out.add(new BlockEntry(new BlockPos(x0 + dx, baseY, z0 + dz), floor));
                        if (type == PieceType.FOUNDATION) {
                            for (int d = 1; d <= fill; d++) {
                                out.add(new BlockEntry(new BlockPos(x0 + dx, baseY - d, z0 + dz), fillState));
                            }
                        }
                    }
                }
            }
            case WALL, DOORWAY, WINDOW -> {
                boolean alongX = dir == Direction.NORTH;
                for (int i = 0; i <= P; i++) {
                    for (int h = 1; h <= 3; h++) {
                        if (opening(i, h)) continue;
                        BlockPos pos = alongX
                                ? new BlockPos(x0 + i, baseY + h, z0)
                                : new BlockPos(x0, baseY + h, z0 + i);
                        out.add(new BlockEntry(pos, wall));
                    }
                }
            }
            case STAIRS -> {
                BlockState stair = tier.stairs().defaultBlockState().setValue(StairBlock.FACING, dir);
                for (int s = 0; s < 4; s++) {
                    for (int w = 0; w < 4; w++) {
                        int x, z;
                        switch (dir) {
                            case EAST -> { x = x0 + 1 + s; z = z0 + 1 + w; }
                            case WEST -> { x = x0 + 4 - s; z = z0 + 1 + w; }
                            case SOUTH -> { x = x0 + 1 + w; z = z0 + 1 + s; }
                            default -> { x = x0 + 1 + w; z = z0 + 4 - s; }
                        }
                        for (int h = 1; h <= s; h++) {
                            out.add(new BlockEntry(new BlockPos(x, baseY + h, z), wall));
                        }
                        out.add(new BlockEntry(new BlockPos(x, baseY + 1 + s, z), stair));
                    }
                }
            }
        }
        return out;
    }

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
        return t;
    }

    public static Piece load(CompoundTag t) {
        return new Piece(PieceType.valueOf(t.getString("type")), t.getInt("gx"), t.getInt("gz"), t.getInt("y"),
                Direction.from3DDataValue(t.getInt("dir")), Tier.valueOf(t.getString("tier")),
                t.getInt("fill"), t.getUUID("owner"), t.getLong("created"));
    }
}
