package com.rustbuild.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Источник истины по всем элементам мира и позициям шкафов привилегий. */
public class BuildingData extends SavedData {
    private static final String NAME = "rustbuild_pieces";
    private static final int FORMAT = 2;

    private final Map<UUID, Piece> pieces = new HashMap<>();
    private final Map<Long, Set<UUID>> byChunk = new HashMap<>();
    private final Set<BlockPos> cupboards = new HashSet<>();

    public static BuildingData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BuildingData::new, BuildingData::load, null), NAME);
    }

    public static BuildingData load(CompoundTag tag, HolderLookup.Provider registries) {
        BuildingData d = new BuildingData();
        if (tag.getInt("v") >= FORMAT) {   // старый (блочный) формат несовместим и пропускается
            ListTag list = tag.getList("pieces", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) d.index(Piece.load(list.getCompound(i)));
        }
        for (long l : tag.getLongArray("tc")) d.cupboards.add(BlockPos.of(l));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("v", FORMAT);
        ListTag list = new ListTag();
        for (Piece p : pieces.values()) list.add(p.save());
        tag.put("pieces", list);
        tag.putLongArray("tc", cupboards.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    // ---------------------------------------------------------------- элементы
    private static long chunkKey(double x, double z) {
        return ChunkPos.asLong(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }

    private void index(Piece p) {
        Piece old = pieces.put(p.id(), p);
        if (old != null) {
            Set<UUID> s = byChunk.get(chunkKey(old.x(), old.z()));
            if (s != null) s.remove(old.id());
        }
        byChunk.computeIfAbsent(chunkKey(p.x(), p.z()), k -> new HashSet<>()).add(p.id());
    }

    public void put(Piece p) { index(p); setDirty(); }

    public void remove(Piece p) {
        Piece old = pieces.remove(p.id());
        if (old != null) {
            Set<UUID> s = byChunk.get(chunkKey(old.x(), old.z()));
            if (s != null) s.remove(old.id());
        }
        setDirty();
    }

    @Nullable
    public Piece getPiece(UUID id) { return pieces.get(id); }

    public List<Piece> all() { return new ArrayList<>(pieces.values()); }

    /** Элементы, чья опорная точка попадает в коробку (с запасом на размеры элемента). */
    public List<Piece> inBox(AABB box) {
        AABB b = box.inflate(5.0);
        List<Piece> out = new ArrayList<>();
        int cx0 = ((int) Math.floor(b.minX)) >> 4, cx1 = ((int) Math.floor(b.maxX)) >> 4;
        int cz0 = ((int) Math.floor(b.minZ)) >> 4, cz1 = ((int) Math.floor(b.maxZ)) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                Set<UUID> ids = byChunk.get(ChunkPos.asLong(cx, cz));
                if (ids == null) continue;
                for (UUID id : ids) {
                    Piece p = pieces.get(id);
                    if (p != null && b.contains(p.x(), p.y(), p.z())) out.add(p);
                }
            }
        }
        return out;
    }

    public List<Piece> piecesNear(BlockPos pos, int radius) {
        List<Piece> out = new ArrayList<>();
        for (Piece p : pieces.values()) {
            if (Math.abs(p.x() - pos.getX()) <= radius && Math.abs(p.z() - pos.getZ()) <= radius) out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------- шкафы привилегий
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
