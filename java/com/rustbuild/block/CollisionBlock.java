package com.rustbuild.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Невидимый блок-коллайдер: форму ему задают воксели элементов постройки (см. CollisionBlockEntity).
 * Так игрок упирается в стены и ходит по полам, повёрнутым на любой угол.
 */
public class CollisionBlock extends BaseEntityBlock {
    public static final MapCodec<CollisionBlock> CODEC = simpleCodec(CollisionBlock::new);

    public CollisionBlock(BlockBehaviour.Properties props) { super(props); }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .strength(-1.0f, 3600000.0f)
                .sound(SoundType.WOOD)
                .noOcclusion()
                .dynamicShape()
                .pushReaction(PushReaction.BLOCK)
                .isViewBlocking((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false)
                .noLootTable();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    private static VoxelShape shapeOf(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CollisionBlockEntity be ? be.shape() : Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeOf(level, pos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeOf(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CollisionBlockEntity(pos, state); }
}
