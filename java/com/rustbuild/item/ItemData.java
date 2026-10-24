package com.rustbuild.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemData {
    private ItemData() {}

    public static CompoundTag tag(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static int getInt(ItemStack s, String key, int def) {
        CompoundTag t = tag(s);
        return t.contains(key) ? t.getInt(key) : def;
    }

    public static void setInt(ItemStack s, String key, int value) {
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt(key, value));
    }
}
