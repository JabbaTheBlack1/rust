package com.rustbuild.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rustbuild.RustBuild;
import com.rustbuild.build.PieceType;
import com.rustbuild.build.Tier;
import com.rustbuild.item.BuildingPlanItem;
import com.rustbuild.item.HammerItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ClientInit {
    public static final KeyMapping RADIAL_KEY =
            new KeyMapping("key.rustbuild.radial", GLFW.GLFW_KEY_R, "key.categories.rustbuild");

    private ClientInit() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientInit::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(PreviewRenderer::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::onKey);
    }

    private static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(RADIAL_KEY);
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(RustBuild.MOD_ID, "textures/gui/" + name + ".png");
    }

    /** Зажали клавишу (по умолчанию R) с планом или молотком в руке - открываем радиальное меню. */
    private static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (e.getAction() != GLFW.GLFW_PRESS || !RADIAL_KEY.matches(e.getKey(), e.getScanCode())) return;

        int keyCode = RADIAL_KEY.getKey().getValue();
        ItemStack stack = mc.player.getMainHandItem();

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
