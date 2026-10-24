package com.rustbuild.deploy;

import com.rustbuild.block.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Меню шкафа привилегий (шкаф - свободная сущность): авторизация и склад на 24 слота. */
public class CupboardMenu extends AbstractContainerMenu {
    public static final int SLOTS = 24;
    public static final int BTN_AUTHORIZE = 0, BTN_DEAUTHORIZE = 1, BTN_CLEAR = 2;

    private final Container container;
    private final ContainerData data;
    private final Player player;

    public CupboardMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(SLOTS), new SimpleContainerData(3));
    }

    public CupboardMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(ModMenus.CUPBOARD.get(), id);
        checkContainerSize(container, SLOTS);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        this.player = inv.player;
        container.startOpen(inv.player);

        for (int r = 0; r < 3; r++) for (int c = 0; c < 8; c++) addSlot(new CupSlot(container, r * 8 + c, 16 + c * 18, 84 + r * 18, this));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 152 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 210));
        addDataSlots(data);
    }

    public int entityId() { return (data.get(1) << 16) | (data.get(0) & 0xFFFF); }
    public int remainingSeconds() { return data.get(2); }

    public DeployableEntity entity() {
        Entity e = player.level().getEntity(entityId());
        return e instanceof DeployableEntity d ? d : null;
    }

    public boolean canUse() {
        if (player.level().isClientSide) return true;
        DeployableEntity d = entity();
        return d == null || d.authorizedCount() == 0 || d.isAuthorized(player.getUUID());
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (p.level().isClientSide || !(p instanceof ServerPlayer sp)) return true;
        DeployableEntity d = entity();
        if (d == null) return false;
        switch (id) {
            case BTN_AUTHORIZE -> d.authorize(sp);
            case BTN_DEAUTHORIZE -> d.deauthorize(sp.getUUID());
            case BTN_CLEAR -> { if (d.authorizedCount() == 0 || d.isAuthorized(sp.getUUID())) d.clearAuthorized(); }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player p) {
        if (p.level().isClientSide) return true;
        DeployableEntity d = entity();
        return d != null && d.isAlive() && p.distanceToSqr(d) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack st = slot.getItem();
            result = st.copy();
            if (index < SLOTS) {
                if (!moveItemStackTo(st, SLOTS, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(st, 0, SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        }
        return result;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        container.stopOpen(p);
    }

    private static class CupSlot extends Slot {
        private final CupboardMenu menu;

        CupSlot(Container c, int i, int x, int y, CupboardMenu menu) { super(c, i, x, y); this.menu = menu; }

        @Override
        public boolean mayPlace(ItemStack s) { return menu.canUse() && DeployableEntity.isUpkeepItem(s); }

        @Override
        public boolean mayPickup(Player p) { return menu.canUse(); }
    }
}
