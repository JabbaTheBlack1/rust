package com.rustbuild.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rustbuild.RustBuild;
import com.rustbuild.block.CollisionBlock;
import com.rustbuild.block.ModMenus;
import com.rustbuild.build.PieceType;
import com.rustbuild.build.Tier;
import com.rustbuild.entity.ModEntities;
import com.rustbuild.item.BuildingPlanItem;
import com.rustbuild.item.HammerItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ClientInit {
    public static final KeyMapping RADIAL_KEY =
            new KeyMapping("key.rustbuild.radial", GLFW.GLFW_KEY_R, "key.categories.rustbuild");
    public static final KeyMapping ROT_LEFT =
            new KeyMapping("key.rustbuild.rot_left", GLFW.GLFW_KEY_Z, "key.categories.rustbuild");
    public static final KeyMapping ROT_RIGHT =
            new KeyMapping("key.rustbuild.rot_right", GLFW.GLFW_KEY_X, "key.categories.rustbuild");

    private ClientInit() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientInit::registerKeys);
        modBus.addListener(ClientInit::clientSetup);
        modBus.addListener(ClientInit::registerRenderers);
        MinecraftForge.EVENT_BUS.addListener(PreviewRenderer::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::onKey);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::onHighlight);
    }

    private static void clientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(ModMenus.TOOL_CUPBOARD.get(), ToolCupboardScreen::new));
    }

    private static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(RADIAL_KEY);
        e.register(ROT_LEFT);
        e.register(ROT_RIGHT);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.PIECE.get(), PieceRenderer::new);
    }

    /** Невидимые блоки-коллайдеры не должны обводиться чёрной рамкой. */
    private static void onHighlight(RenderHighlightEvent.Block e) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(e.getTarget().getBlockPos()).getBlock() instanceof CollisionBlock) {
            e.setCanceled(true);
        }
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(RustBuild.MOD_ID, "textures/gui/" + name + ".png");
    }

    private static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || e.getAction() != GLFW.GLFW_PRESS) return;
        ItemStack stack = mc.player.getMainHandItem();

        // поворот элемента (свободный угол): Z / X, с Shift - по 1 градусу, иначе по 5
        boolean left = ROT_LEFT.matches(e.getKey(), e.getScanCode());
        boolean right = ROT_RIGHT.matches(e.getKey(), e.getScanCode());
        if ((left || right) && stack.getItem() instanceof BuildingPlanItem) {
            int step = Screen.hasShiftDown() ? 1 : 5;
            mc.player.connection.sendCommand("rustbuild rot " + (left ? -step : step));
            return;
        }

        if (!RADIAL_KEY.matches(e.getKey(), e.getScanCode())) return;
        int keyCode = RADIAL_KEY.getKey().getValue();

        if (stack.getItem() instanceof BuildingPlanItem) {
            List<RadialScreen.Entry> entries = new ArrayList<>();
            for (PieceType t : PieceType.values()) {
                entries.add(new RadialScreen.Entry(t.displayName(), icon("piece_" + t.id)));
            }
            mc.setScreen(new RadialScreen(Component.translatable("menu.rustbuild.pieces"), entries,
                    BuildingPlanItem.getPiece(stack).ordinal(), "rustbuild piece", keyCode));
        } else if (stack.getItem() instanceof HammerItem) {
            List<RadialScreen.Entry> entries = new ArrayList<>();
            for (Tier t : Tier.values()) {
                entries.add(new RadialScreen.Entry(t.displayName(), icon("tier_" + t.id)));
            }
            mc.setScreen(new RadialScreen(Component.translatable("menu.rustbuild.tiers"), entries,
                    HammerItem.getTier(stack).ordinal(), "rustbuild tier", keyCode));
        }
    }
}
