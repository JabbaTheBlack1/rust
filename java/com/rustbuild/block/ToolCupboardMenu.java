package com.rustbuild.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/** Меню шкафа: 24 слота склада + кнопки "авторизоваться / снять авторизацию / очистить список". */
public class ToolCupboardMenu extends AbstractContainerMenu {
    public static final int SLOTS = ToolCupboardBlockEntity.SLOTS;
    public static final int BTN_AUTHORIZE = 0;
    public static final int BTN_DEAUTHORIZE = 1;
    public static final int BTN_CLEAR = 2;

    private final Container container;
    private final ContainerData data;
    private final Player player;

    /** Клиентский конструктор. */
    public ToolCupboardMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(SLOTS), new SimpleContainerData(7));
    }

    public ToolCupboardMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(ModMenus.TOOL_CUPBOARD.get(), id);
        checkContainerSize(container, SLOTS);
        checkContainerDataCount(data, 7);
        this.container = container;
        this.data = data;
        this.player = inv.player;
        container.startOpen(inv.player);

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 8; c++) {
                addSlot(new CupboardSlot(container, r * 8 + c, 16 + c * 18, 84 + r * 18, this));
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 152 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, 8 + c * 18, 210));
        }
        addDataSlots(data);
    }

    /** Позиция шкафа (собирается из 16-битных половин). */
    public BlockPos pos() {
        return new BlockPos((data.get(1) << 16) | (data.get(0) & 0xFFFF),
                (data.get(3) << 16) | (data.get(2) & 0xFFFF),
                (data.get(5) << 16) | (data.get(4) & 0xFFFF));
    }

    public int remainingSeconds() { return data.get(6); }

    private ToolCupboardBlockEntity entity() {
        return player.level().getBlockEntity(pos()) instanceof ToolCupboardBlockEntity be ? be : null;
    }

    /** Можно ли игроку работать со складом: нужно быть в списке авторизованных (или список пуст). */
    public boolean canUse() {
        ToolCupboardBlockEntity be = entity();
        if (be == null) return true;
        return be.authorizedCount() == 0 || be.isAuthorized(player.getUUID());
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (p.level().isClientSide || !(p instanceof ServerPlayer sp)) return true;
        ToolCupboardBlockEntity be = entity();
        if (be == null) return false;
        switch (id) {
            case BTN_AUTHORIZE -> be.authorize(sp);
            case BTN_DEAUTHORIZE -> be.deauthorize(sp.getUUID());
            case BTN_CLEAR -> {
                if (be.authorizedCount() == 0 || be.isAuthorized(sp.getUUID())) be.clearAuthorized();
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player p) {
        if (p.level().isClientSide) return true;
        BlockPos at = pos();
        return p.level().getBlockEntity(at) instanceof ToolCupboardBlockEntity
                && p.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) <= 64.0;
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
            if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        container.stopOpen(p);
    }

    private static class CupboardSlot extends Slot {
        private final ToolCupboardMenu menu;

        CupboardSlot(Container c, int index, int x, int y, ToolCupboardMenu menu) {
            super(c, index, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack s) { return menu.canUse() && ToolCupboardBlockEntity.isUpkeepItem(s); }

        @Override
        public boolean mayPickup(Player p) { return menu.canUse(); }
    }
}
