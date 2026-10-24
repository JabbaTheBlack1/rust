package com.rustbuild.deploy;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum DeployKind implements StringRepresentable {
    TOOL_CUPBOARD("tool_cupboard", 24),
    STORAGE_BOX("storage_box", 27),
    WORKBENCH("workbench", 0),
    DECOR("decor", 0);

    public final String id;
    public final int slots;

    DeployKind(String id, int slots) {
        this.id = id;
        this.slots = slots;
    }

    @Override
    public String getSerializedName() { return id; }

    public static DeployKind byIndex(int i) {
        DeployKind[] v = values();
        return v[Math.floorMod(i, v.length)];
    }

    /** Что получится из произвольного предмета в руке: сундуки и бочки - ящик, верстак - верстак, остальное - украшение. */
    public static DeployKind forStack(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            Block b = bi.getBlock();
            if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) return STORAGE_BOX;
            if (b == Blocks.CRAFTING_TABLE) return WORKBENCH;
        }
        return DECOR;
    }
}
