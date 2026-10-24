package com.rustbuild.deploy;

import com.rustbuild.block.ModMenus;
import com.rustbuild.build.Privilege;
import com.rustbuild.build.Tier;
import com.rustbuild.build.Xf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Свободный предмет: шкаф привилегий, ящик, верстак или любой блок/предмет, поставленный без привязки к сетке блоков
 * (произвольная позиция и угол). Коллизию даёт сетка вокселей, модель рисует DeployRenderer.
 */
public class DeployableEntity extends Entity implements MenuProvider {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(DeployableEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(DeployableEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(DeployableEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<String> AUTH = SynchedEntityData.defineId(DeployableEntity.class, EntityDataSerializers.STRING);

    public static final long UPKEEP_INTERVAL = 12000L;

    private SimpleContainer items = new SimpleContainer(0);
    private final List<UUID> authorized = new ArrayList<>();
    private final Map<UUID, String> names = new HashMap<>();
    private long protectedUntil = 0L;
    private UUID owner = new UUID(0L, 0L);

    /** Данные для меню шкафа (значения уходят клиенту как short). */
    public final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> getId() & 0xFFFF;
                case 1 -> getId() >> 16;
                default -> remainingSeconds();
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() { return 3; }
    };

    public DeployableEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, 0);
        b.define(YAW, 0f);
        b.define(STACK, ItemStack.EMPTY);
        b.define(AUTH, "");
    }

    // ---------------------------------------------------------------- состояние
    public DeployKind kind() { return DeployKind.byIndex(entityData.get(KIND)); }
    public float objectYaw() { return entityData.get(YAW); }
    public ItemStack stack() { return entityData.get(STACK); }
    public UUID owner() { return owner; }
    public SimpleContainer items() { return items; }

    public void setup(DeployKind kind, ItemStack stack, double yaw, UUID owner) {
        entityData.set(KIND, kind.ordinal());
        entityData.set(YAW, (float) yaw);
        entityData.set(STACK, stack.copyWithCount(1));
        this.owner = owner;
        this.items = new SimpleContainer(kind.slots);
        refreshBox();
    }

    public Xf xf() { return new Xf(getX(), getY(), getZ(), objectYaw()); }
    public List<com.rustbuild.build.Solid> solids() { return DeployDef.solids(kind(), stack()); }
    public AABB worldBounds() { return xf().bounds(solids()); }

    public void refreshBox() { setBoundingBox(worldBounds()); }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == KIND || key == YAW || key == STACK) refreshBox();
    }

    // ---------------------------------------------------------------- авторизация (шкаф)
    public boolean isAuthorized(UUID id) { return authorized.contains(id); }
    public int authorizedCount() { return authorized.size(); }

    public List<String> authorizedNames() {
        String s = entityData.get(AUTH);
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) for (String n : s.split("\n")) out.add(n);
        return out;
    }

    private void syncAuth() {
        List<String> n = new ArrayList<>();
        for (UUID id : authorized) n.add(names.getOrDefault(id, id.toString().substring(0, 8)));
        entityData.set(AUTH, String.join("\n", n));
    }

    public void authorize(ServerPlayer p) {
        if (!authorized.contains(p.getUUID())) {
            authorized.add(p.getUUID());
            names.put(p.getUUID(), p.getName().getString());
            syncAuth();
        }
    }

    public void deauthorize(UUID id) {
        if (authorized.remove(id)) { names.remove(id); syncAuth(); }
    }

    public void clearAuthorized() { authorized.clear(); names.clear(); syncAuth(); }

    // ---------------------------------------------------------------- содержание построек
    public static boolean isUpkeepItem(ItemStack s) {
        for (Tier t : Tier.values()) if (t.matches(s)) return true;
        return false;
    }

    public boolean isActive() { return level().getGameTime() < protectedUntil; }

    public int remainingSeconds() {
        return (int) Math.max(0L, Math.min(30000L, (protectedUntil - level().getGameTime()) / 20L));
    }

    private void upkeep(ServerLevel sl) {
        long now = sl.getGameTime();
        if (now < protectedUntil) return;
        int pieces = com.rustbuild.build.BuildingData.get(sl).piecesNear(blockPosition(), Privilege.RADIUS).size();
        if (pieces == 0) return;
        int need = Math.max(1, pieces / 10), got = 0;
        for (int i = 0; i < items.getContainerSize() && got < need; i++) {
            ItemStack s = items.getItem(i);
            if (s.isEmpty() || !isUpkeepItem(s)) continue;
            int take = Math.min(need - got, s.getCount());
            s.shrink(take);
            got += take;
        }
        if (got > 0) protectedUntil = now + UPKEEP_INTERVAL * got / need;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && kind() == DeployKind.TOOL_CUPBOARD && tickCount % 100 == 0 && level() instanceof ServerLevel sl) {
            upkeep(sl);
        }
    }

    public void dropContents() {
        Containers.dropContents(level(), blockPosition(), items);
    }

    // ---------------------------------------------------------------- взаимодействие
    @Override
    public boolean isPickable() { return true; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level() instanceof ServerLevel sl)) return InteractionResult.PASS;

        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            DeployActions.pickup(sp, sl, this);
            return InteractionResult.SUCCESS;
        }
        switch (kind()) {
            case TOOL_CUPBOARD -> sp.openMenu(this);
            case STORAGE_BOX -> sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> ChestMenu.threeRows(id, inv, items), Component.translatable("deploy.rustbuild.storage_box")));
            case WORKBENCH -> sp.openMenu(new SimpleMenuProvider((id, inv, p) ->
                    new CraftingMenu(id, inv, ContainerLevelAccess.create(level(), blockPosition())) {
                        @Override
                        public boolean stillValid(Player pl) { return true; }
                    }, Component.translatable("deploy.rustbuild.workbench")));
            default -> { }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getDisplayName() { return Component.translatable("deploy.rustbuild." + kind().id); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CupboardMenu(id, inv, items, menuData);
    }

    // ---------------------------------------------------------------- NBT
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        DeployKind k = DeployKind.byIndex(t.getInt("kind"));
        entityData.set(KIND, k.ordinal());
        entityData.set(YAW, t.getFloat("yaw"));
        entityData.set(STACK, ItemStack.parseOptional(registryAccess(), t.getCompound("stack")));
        owner = t.hasUUID("owner") ? t.getUUID("owner") : new UUID(0L, 0L);
        items = new SimpleContainer(k.slots);
        ContainerHelper.loadAllItems(t, items.getItems(), registryAccess());
        authorized.clear();
        names.clear();
        ListTag list = t.getList("auth", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            authorized.add(c.getUUID("id"));
            names.put(c.getUUID("id"), c.getString("name"));
        }
        protectedUntil = t.getLong("until");
        syncAuth();
        refreshBox();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("kind", entityData.get(KIND));
        t.putFloat("yaw", entityData.get(YAW));
        t.put("stack", stack().saveOptional(registryAccess()));
        t.putUUID("owner", owner);
        ContainerHelper.saveAllItems(t, items.getItems(), registryAccess());
        ListTag list = new ListTag();
        for (UUID id : authorized) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", id);
            c.putString("name", names.getOrDefault(id, ""));
            list.add(c);
        }
        t.put("auth", list);
        t.putLong("until", protectedUntil);
    }
}
