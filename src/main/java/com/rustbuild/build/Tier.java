package com.rustbuild.build;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.common.Tags;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** Материальные тиры как в Rust: солома -> дерево -> камень -> металл -> бронированный. */
public enum Tier {
    TWIG("twig", 4, 1.0f, 3.0f, SoundType.BAMBOO_WOOD, s -> s.is(Items.STICK), () -> Blocks.BAMBOO_STAIRS),
    WOOD("wood", 8, 4.0f, 12.0f, SoundType.WOOD, s -> s.is(ItemTags.PLANKS), () -> Blocks.OAK_STAIRS),
    STONE("stone", 8, 10.0f, 30.0f, SoundType.STONE, s -> s.is(Tags.Items.COBBLESTONES), () -> Blocks.STONE_BRICK_STAIRS),
    METAL("metal", 3, 25.0f, 60.0f, SoundType.METAL, s -> s.is(Items.IRON_INGOT), () -> Blocks.POLISHED_DIORITE_STAIRS),
    ARMORED("armored", 1, 50.0f, 1200.0f, SoundType.NETHERITE_BLOCK, s -> s.is(Items.NETHERITE_SCRAP), () -> Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS);

    public final String id;
    public final int unitCost;
    public final float hardness;
    public final float resistance;
    public final SoundType sound;
    private final Predicate<ItemStack> material;
    private final Supplier<Block> stairs;

    Tier(String id, int unitCost, float hardness, float resistance, SoundType sound,
         Predicate<ItemStack> material, Supplier<Block> stairs) {
        this.id = id;
        this.unitCost = unitCost;
        this.hardness = hardness;
        this.resistance = resistance;
        this.sound = sound;
        this.material = material;
        this.stairs = stairs;
    }

    public boolean matches(ItemStack s) { return !s.isEmpty() && material.test(s); }
    public Block stairs() { return stairs.get(); }
    public Component displayName() { return Component.translatable("tier.rustbuild." + id); }
    public Component costName() { return Component.translatable("tier.rustbuild." + id + ".cost"); }
}
