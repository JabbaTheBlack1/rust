package com.rustbuild.build;

import com.rustbuild.block.BuildingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Хранит все элементы построек мира и позиции шкафов привилегий. */
public class BuildingData extends SavedData {
    private static final String NAME = "rustbuild_pieces";
    private final Map<String, Piece> pieces = new HashMap<>();
    private final Set<BlockPos> cupboards = new HashSet<>();

    public static BuildingData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BuildingData::new, BuildingData::load, null), NAME);
    }

    public static BuildingData load(CompoundTag tag, HolderLookup.Provider registries) {
        BuildingData d = new BuildingData();
        ListTag list = tag.getList("pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Piece p = Piece.load(list.getCompound(i));
            d.pieces.put(p.key(), p);
        }
        for (long l : tag.getLongArray("tc")) d.cupboards.add(BlockPos.of(l));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Piece p : pieces.values()) list.add(p.save());
        tag.put("pieces", list);
        tag.putLongArray("tc", cupboards.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    // ---- элементы
    public void put(Piece p) { pieces.put(p.key(), p); setDirty(); }
    public void remove(Piece p) { pieces.remove(p.key()); setDirty(); }
    public List<Piece> all() { return new ArrayList<>(pieces.values()); }
    @Nullable
    public Piece get(String key) { return pieces.get(key); }

    @Nullable
    public Piece findAt(BlockPos pos, BlockState state) {
        List<Piece> hits = new ArrayList<>();
        for (Piece p : pieces.values()) {
            if (p.near(pos) && p.contains(pos)) hits.add(p);
        }
        if (hits.isEmpty()) return null;
        if (state.getBlock() instanceof BuildingBlock) {
            BuildingBlock.Kind k = state.getValue(BuildingBlock.KIND);
            for (Piece p : hits) {
                boolean ok = switch (k) {
                    case FLOOR -> p.type().isSlab();
                    case FILL -> p.type().isRoot();
                    case WALL -> !p.type().isSlab();
                };
                if (ok) return p;
            }
        }
        return hits.get(0);
    }

    /** Соседние элементы (клетки +-1), которые могут делить с данным блоки или опору. */
    public List<Piece> neighbors(Piece piece) {
        List<Piece> out = new ArrayList<>();
        String key = piece.key();
        for (Piece p : pieces.values()) {
            if (p.key().equals(key)) continue;
            if (Math.abs(p.gx() - piece.gx()) <= 1 && Math.abs(p.gz() - piece.gz()) <= 1) out.add(p);
        }
        return out;
    }

    public List<Piece> piecesNear(BlockPos pos, int radius) {
        List<Piece> out = new ArrayList<>();
        for (Piece p : pieces.values()) {
            BlockPos c = p.center();
            if (Math.abs(c.getX() - pos.getX()) <= radius && Math.abs(c.getZ() - pos.getZ()) <= radius) out.add(p);
        }
        return out;
    }

    // ---- шкафы привилегий
    public void addCupboard(BlockPos pos) { if (cupboards.add(pos.immutable())) setDirty(); }
    public void removeCupboard(BlockPos pos) { if (cupboards.remove(pos)) setDirty(); }

    public List<BlockPos> cupboardsNear(BlockPos pos, int radius) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos c : cupboards) {
            if (Math.abs(c.getX() - pos.getX()) <= radius && Math.abs(c.getZ() - pos.getZ()) <= radius
                    && Math.abs(c.getY() - pos.getY()) <= radius * 2) out.add(c);
        }
        return out;
    }
}
