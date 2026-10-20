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
    //   id         цена  твёрд. взрыв.  звук                   HP     распад(тиков) ближний бой
    TWIG("twig",    4,  1.0f,   3.0f, SoundType.BAMBOO_WOOD,     30f,  72000L, 3.00f,
            s -> s.is(Items.STICK), () -> Blocks.BAMBOO_STAIRS, () -> Blocks.BAMBOO_SLAB),
    WOOD("wood",    8,  4.0f,  12.0f, SoundType.WOOD,           250f, 216000L, 1.00f,
            s -> s.is(ItemTags.PLANKS), () -> Blocks.OAK_STAIRS, () -> Blocks.OAK_SLAB),
    STONE("stone",  8, 10.0f,  30.0f, SoundType.STONE,          500f, 360000L, 0.20f,
            s -> s.is(Tags.Items.COBBLESTONES), () -> Blocks.STONE_BRICK_STAIRS, () -> Blocks.STONE_BRICK_SLAB),
    METAL("metal",  3, 25.0f,  60.0f, SoundType.METAL,         1000f, 576000L, 0.08f,
            s -> s.is(Items.IRON_INGOT), () -> Blocks.POLISHED_DIORITE_STAIRS, () -> Blocks.POLISHED_DIORITE_SLAB),
    ARMORED("armored", 1, 50.0f, 1200.0f, SoundType.NETHERITE_BLOCK, 2000f, 864000L, 0.03f,
            s -> s.is(Items.NETHERITE_SCRAP), () -> Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
            () -> Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);

    public final String id;
    public final int unitCost;
    public final float hardness;
    public final float resistance;
    public final SoundType sound;
    /** Базовая прочность (умножается на коэффициент элемента). */
    public final float hp;
    /** За сколько тиков без шкафа привилегий элемент полностью разрушится от гниения. */
    public final long decayTicks;
    /** Насколько эффективно ломать этот тир в ближнем бою. */
    public final float meleeFactor;
    private final Predicate<ItemStack> material;
    private final Supplier<Block> stairs;
    private final Supplier<Block> slab;

    Tier(String id, int unitCost, float hardness, float resistance, SoundType sound, float hp, long decayTicks,
         float meleeFactor, Predicate<ItemStack> material, Supplier<Block> stairs, Supplier<Block> slab) {
        this.id = id;
        this.unitCost = unitCost;
        this.hardness = hardness;
        this.resistance = resistance;
        this.sound = sound;
        this.hp = hp;
        this.decayTicks = decayTicks;
        this.meleeFactor = meleeFactor;
        this.material = material;
        this.stairs = stairs;
        this.slab = slab;
    }

    public boolean matches(ItemStack s) { return !s.isEmpty() && material.test(s); }
    public Block stairs() { return stairs.get(); }
    public Block slab() { return slab.get(); }
    public Component displayName() { return Component.translatable("tier.rustbuild." + id); }
    public Component costName() { return Component.translatable("tier.rustbuild." + id + ".cost"); }
}
