package com.rustbuild.block;

import com.rustbuild.RustBuild;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RustBuild.MOD_ID);

    public static final RegistryObject<MenuType<ToolCupboardMenu>> TOOL_CUPBOARD =
            MENUS.register("tool_cupboard", () -> new MenuType<>(ToolCupboardMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
