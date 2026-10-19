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
import java.util.List;
import java.util.Map;

/** Хранит все элементы построек мира (нужно для молотка: улучшение / снос). */
public class BuildingData extends SavedData {
    private static final String NAME = "rustbuild_pieces";
    private final Map<String, Piece> pieces = new HashMap<>();

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
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Piece p : pieces.values()) list.add(p.save());
        tag.put("pieces", list);
        return tag;
    }

    public void put(Piece p) { pieces.put(p.key(), p); setDirty(); }
    public void remove(Piece p) { pieces.remove(p.key()); setDirty(); }

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
                    case FILL -> p.type() == PieceType.FOUNDATION;
                    case WALL -> !p.type().isSlab();
                };
                if (ok) return p;
            }
        }
        return hits.get(0);
    }

    /** Соседние элементы, которые могут делить с данным общие блоки (углы, линии стен). */
    public List<Piece> neighbors(Piece piece) {
        List<Piece> out = new ArrayList<>();
        for (Piece p : pieces.values()) {
            if (p.key().equals(piece.key())) continue;
            if (Math.abs(p.gx() - piece.gx()) <= 1 && Math.abs(p.gz() - piece.gz()) <= 1) out.add(p);
        }
        return out;
    }
}
