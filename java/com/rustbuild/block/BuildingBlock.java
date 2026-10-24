package com.rustbuild.block;

import com.rustbuild.build.PieceType;
import com.rustbuild.build.Tier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * "Блок-модель": в мире он никогда не ставится. Состояние (тир + тип элемента) выбирает JSON-модель,
 * которую рисует сущность PieceEntity. Так элементы выглядят как настоящие модели, а не как кубы.
 */
public class BuildingBlock extends Block {
    public static final EnumProperty<PieceType> PIECE = EnumProperty.create("piece", PieceType.class);
    public final Tier tier;

    public BuildingBlock(Tier tier) {
        super(BlockBehaviour.Properties.of().strength(1.0f).noOcclusion().noLootTable());
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(PIECE, PieceType.FOUNDATION));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PIECE);
    }
}
