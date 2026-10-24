package com.rustbuild.block;

import com.rustbuild.deploy.DeployKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** "Блок-модель" свободных предметов (в мире не ставится): состояние выбирает JSON-модель для DeployRenderer. */
public class DeployModelBlock extends Block {
    public static final EnumProperty<DeployKind> KIND = EnumProperty.create("kind", DeployKind.class);

    public DeployModelBlock() {
        super(BlockBehaviour.Properties.of().strength(1.0f).noOcclusion().noLootTable());
        registerDefaultState(stateDefinition.any().setValue(KIND, DeployKind.TOOL_CUPBOARD));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
