package com.rustbuild.block;

import com.rustbuild.build.BuildingData;
import com.rustbuild.build.Privilege;
import com.rustbuild.build.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Шкаф привилегий: список авторизованных, склад ресурсов на содержание построек. */
public class ToolCupboardBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS = 24;
    /** Раз в 10 минут шкаф списывает ресурсы, и постройки в зоне не гниют. */
    public static final long UPKEEP_INTERVAL = 12000L;

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            ToolCupboardBlockEntity.this.setChanged();
        }
    };
    private final List<UUID> authorized = new ArrayList<>();
    private final Map<UUID, String> names = new HashMap<>();
    private long protectedUntil = 0L;

    /** Данные для меню. Значения уходят клиенту как short, поэтому координаты режем на 16-битные половины. */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> worldPosition.getX() & 0xFFFF;
                case 1 -> worldPosition.getX() >> 16;
                case 2 -> worldPosition.getY() & 0xFFFF;
                case 3 -> worldPosition.getY() >> 16;
                case 4 -> worldPosition.getZ() & 0xFFFF;
                case 5 -> worldPosition.getZ() >> 16;
                default -> remainingSeconds();
            };
        }

        @Override
        public void set(int i, int value) {}

        @Override
        public int getCount() { return 7; }
    };

    public ToolCupboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOOL_CUPBOARD.get(), pos, state);
    }

    public SimpleContainer items() { return items; }

    // ---------------------------------------------------------------- авторизация
    public boolean isAuthorized(UUID id) { return authorized.contains(id); }

    public List<String> authorizedNames() {
        List<String> out = new ArrayList<>();
        for (UUID id : authorized) out.add(names.getOrDefault(id, id.toString().substring(0, 8)));
        return out;
    }

    public int authorizedCount() { return authorized.size(); }

    public void authorize(ServerPlayer p) {
        if (!authorized.contains(p.getUUID())) {
            authorized.add(p.getUUID());
            names.put(p.getUUID(), p.getName().getString());
            sync();
        }
    }

    public void deauthorize(UUID id) {
        if (authorized.remove(id)) {
            names.remove(id);
            sync();
        }
    }

    public void clearAuthorized() {
        authorized.clear();
        names.clear();
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---------------------------------------------------------------- содержание построек
    public static boolean isUpkeepItem(ItemStack s) {
        for (Tier t : Tier.values()) {
            if (t.matches(s)) return true;
        }
        return false;
    }

    public boolean isActive() {
        return level != null && level.getGameTime() < protectedUntil;
    }

    public int remainingSeconds() {
        if (level == null) return 0;
        return (int) Math.max(0L, Math.min(30000L, (protectedUntil - level.getGameTime()) / 20L));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ToolCupboardBlockEntity be) {
        if (level.getGameTime() % 100L != 0L || !(level instanceof ServerLevel sl)) return;
        be.upkeep(sl);
    }

    private void upkeep(ServerLevel level) {
        long now = level.getGameTime();
        if (now < protectedUntil) return;
        int pieces = BuildingData.get(level).piecesNear(worldPosition, Privilege.RADIUS).size();
        if (pieces == 0) return;
        int need = Math.max(1, pieces / 10);
        int got = 0;
        for (int i = 0; i < items.getContainerSize() && got < need; i++) {
            ItemStack s = items.getItem(i);
            if (s.isEmpty() || !isUpkeepItem(s)) continue;
            int take = Math.min(need - got, s.getCount());
            s.shrink(take);
            got += take;
        }
        if (got > 0) {
            protectedUntil = now + UPKEEP_INTERVAL * got / need;
            items.setChanged();
            sync();
        }
    }

    public void dropContents() {
        if (level != null) Containers.dropContents(level, worldPosition, items);
    }

    // ---------------------------------------------------------------- меню
    @Override
    public Component getDisplayName() { return Component.translatable("block.rustbuild.tool_cupboard"); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ToolCupboardMenu(id, inv, items, data);
    }

    // ---------------------------------------------------------------- NBT / синхронизация
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ContainerHelper.saveAllItems(tag, items.getItems(), reg);
        ListTag list = new ListTag();
        for (UUID id : authorized) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", id);
            c.putString("name", names.getOrDefault(id, ""));
            list.add(c);
        }
        tag.put("auth", list);
        tag.putLong("until", protectedUntil);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        ContainerHelper.loadAllItems(tag, items.getItems(), reg);
        authorized.clear();
        names.clear();
        ListTag list = tag.getList("auth", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            UUID id = c.getUUID("id");
            authorized.add(id);
            names.put(id, c.getString("name"));
        }
        protectedUntil = tag.getLong("until");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) { return saveWithoutMetadata(reg); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
