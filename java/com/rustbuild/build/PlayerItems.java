package com.rustbuild.build;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlayerItems {
    private PlayerItems() {}

    public static int count(Player p, Tier t) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.items) if (t.matches(s)) n += s.getCount();
        for (ItemStack s : inv.offhand) if (t.matches(s)) n += s.getCount();
        return n;
    }

    public static void take(Player p, Tier t, int amount) {
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.items) {
            if (amount <= 0) return;
            if (t.matches(s)) { int d = Math.min(amount, s.getCount()); s.shrink(d); amount -= d; }
        }
        for (ItemStack s : inv.offhand) {
            if (amount <= 0) return;
            if (t.matches(s)) { int d = Math.min(amount, s.getCount()); s.shrink(d); amount -= d; }
        }
    }
}
