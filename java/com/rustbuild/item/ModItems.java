package com.rustbuild.item;

import com.rustbuild.RustBuild;
import com.rustbuild.deploy.DeployKind;
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
    public static final RegistryObject<Item> TOOL_CUPBOARD =
            ITEMS.register("tool_cupboard", () -> new DeployableItem(new Item.Properties(), DeployKind.TOOL_CUPBOARD));
    public static final RegistryObject<Item> STORAGE_BOX =
            ITEMS.register("storage_box", () -> new DeployableItem(new Item.Properties(), DeployKind.STORAGE_BOX));
    public static final RegistryObject<Item> WORKBENCH =
            ITEMS.register("workbench", () -> new DeployableItem(new Item.Properties(), DeployKind.WORKBENCH));

    private ModItems() {}
}
