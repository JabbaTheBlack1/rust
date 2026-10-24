package com.rustbuild.block;

import com.rustbuild.RustBuild;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RustBuild.MOD_ID);

    public static final RegistryObject<BlockEntityType<ToolCupboardBlockEntity>> TOOL_CUPBOARD =
            TYPES.register("tool_cupboard", () ->
                    BlockEntityType.Builder.of(ToolCupboardBlockEntity::new, ModBlocks.TOOL_CUPBOARD.get()).build(null));

    public static final RegistryObject<BlockEntityType<CollisionBlockEntity>> COLLISION =
            TYPES.register("collision", () ->
                    BlockEntityType.Builder.of(CollisionBlockEntity::new, ModBlocks.COLLISION.get()).build(null));

    private ModBlockEntities() {}
}
