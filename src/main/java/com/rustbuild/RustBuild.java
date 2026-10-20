package com.rustbuild;

import com.rustbuild.block.ModBlockEntities;
import com.rustbuild.block.ModBlocks;
import com.rustbuild.block.ModMenus;
import com.rustbuild.client.ClientInit;
import com.rustbuild.event.ServerEvents;
import com.rustbuild.item.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(RustBuild.MOD_ID)
public class RustBuild {
    public static final String MOD_ID = "rustbuild";

    public RustBuild(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModBlockEntities.TYPES.register(bus);
        ModMenus.MENUS.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.register(bus);
        }
    }

    private void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(ModItems.BUILDING_PLAN.get());
            e.accept(ModItems.HAMMER.get());
            e.accept(ModItems.TOOL_CUPBOARD.get());
        }
    }
}
