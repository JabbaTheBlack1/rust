package com.rustbuild.block;

import com.rustbuild.build.Collision;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Хранит маски вокселей 1/4 блока: по одной маске на каждый элемент, задевающий этот блок. */
public class CollisionBlockEntity extends BlockEntity {
    private final Map<UUID, Long> masks = new HashMap<>();
    private long total = 0L;
    private VoxelShape shape = null;

    public CollisionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLLISION.get(), pos, state);
    }

    private void recompute() {
        total = 0L;
        for (long m : masks.values()) total |= m;
        shape = null;
    }

    public VoxelShape shape() {
        if (shape == null) shape = build(total);
        return shape;
    }

    private static boolean bit(long mask, int i, int j, int k) { return (mask >> Collision.bit(i, j, k) & 1L) != 0L; }

    private static VoxelShape build(long mask) {
        VoxelShape s = Shapes.empty();
        for (int j = 0; j < 4; j++) {
            for (int k = 0; k < 4; k++) {
                int i = 0;
                while (i < 4) {
                    if (bit(mask, i, j, k)) {
                        int i0 = i;
                        while (i < 4 && bit(mask, i, j, k)) i++;
                        s = Shapes.or(s, Shapes.box(i0 / 4.0, j / 4.0, k / 4.0, i / 4.0, (j + 1) / 4.0, (k + 1) / 4.0));
                    } else {
                        i++;
                    }
                }
            }
        }
        return s.optimize();
    }

    public void put(UUID id, long mask) {
        masks.put(id, mask);
        recompute();
        sync();
    }

    public void remove(UUID id) {
        if (masks.remove(id) != null) {
            recompute();
            sync();
        }
    }

    public boolean isEmpty() { return masks.isEmpty(); }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> e : masks.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putLong("m", e.getValue());
            list.add(c);
        }
        tag.put("masks", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        masks.clear();
        ListTag list = tag.getList("masks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            masks.put(c.getUUID("id"), c.getLong("m"));
        }
        recompute();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) { return saveWithoutMetadata(reg); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
