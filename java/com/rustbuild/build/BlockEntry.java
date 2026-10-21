package com.rustbuild.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record BlockEntry(BlockPos pos, BlockState state) {}
