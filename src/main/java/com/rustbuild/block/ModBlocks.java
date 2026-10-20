package com.rustbuild.block;

import com.rustbuild.RustBuild;
import com.rustbuild.build.Tier;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RustBuild.MOD_ID);
    private static final Map<Tier, RegistryObject<BuildingBlock>> BY_TIER = new EnumMap<>(Tier.class);

    public static final RegistryObject<ToolCupboardBlock> TOOL_CUPBOARD =
            BLOCKS.register("tool_cupboard", () -> new ToolCupboardBlock(ToolCupboardBlock.defaultProperties()));

    static {
        for (Tier t : Tier.values()) {
            BY_TIER.put(t, BLOCKS.register(t.id + "_building_block", () -> new BuildingBlock(t)));
        }
    }

    public static BuildingBlock get(Tier t) { return BY_TIER.get(t).get(); }

    private ModBlocks() {}
}
