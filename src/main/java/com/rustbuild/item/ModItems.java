package com.rustbuild.item;

import com.rustbuild.RustBuild;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RustBuild.MOD_ID);

    public static final RegistryObject<Item> BUILDING_PLAN =
            ITEMS.register("building_plan", () -> new BuildingPlanItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HAMMER =
            ITEMS.register("hammer", () -> new HammerItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
