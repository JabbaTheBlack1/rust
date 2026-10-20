package com.rustbuild.block;

import com.rustbuild.build.Tier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;

import java.util.Locale;

/** Блок постройки. KIND нужен, чтобы определять роль блока (плита / стена / заполнение фундамента). */
public class BuildingBlock extends Block {
    public enum Kind implements StringRepresentable {
        FLOOR, WALL, FILL;

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public final Tier tier;

    public BuildingBlock(Tier tier) {
        super(BlockBehaviour.Properties.of()
                .strength(tier.hardness, tier.resistance)
                .sound(tier.sound)
                .pushReaction(PushReaction.BLOCK)
                .noLootTable());
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.WALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
