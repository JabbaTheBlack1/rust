package com.rustbuild.client;

import com.rustbuild.deploy.CupboardMenu;
import com.rustbuild.deploy.DeployableEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/** Меню шкафа привилегий (шкаф - свободная сущность): список авторизованных, статус защиты, склад. */
public class CupboardScreen extends AbstractContainerScreen<CupboardMenu> {
    public CupboardScreen(CupboardMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 234;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 104;
        addRenderableWidget(Button.builder(Component.translatable("gui.rustbuild.cupboard.authorize"),
                b -> press(CupboardMenu.BTN_AUTHORIZE)).bounds(x, topPos + 18, 64, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.rustbuild.cupboard.deauthorize"),
                b -> press(CupboardMenu.BTN_DEAUTHORIZE)).bounds(x, topPos + 34, 64, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.rustbuild.cupboard.clear"),
                b -> press(CupboardMenu.BTN_CLEAR)).bounds(x, topPos + 50, 64, 14).build());
    }

    private void press(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2420);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF8A5A2B);
        g.fill(leftPos + 6, topPos + 16, leftPos + 100, topPos + 70, 0xFF1A1613);
        g.renderOutline(leftPos + 6, topPos + 16, 94, 54, 0xFF4A3A2A);
        for (Slot s : menu.slots) {
            g.fill(leftPos + s.x - 1, topPos + s.y - 1, leftPos + s.x + 17, topPos + s.y + 17, 0xFF0F0D0B);
            g.renderOutline(leftPos + s.x - 1, topPos + s.y - 1, 18, 18, 0xFF3A3028);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xFFE0C090, false);
        g.drawString(font, Component.translatable("gui.rustbuild.cupboard.authorized"), 10, 19, 0xFFCD412B, false);

        DeployableEntity d = menu.entity();
        if (d != null) {
            List<String> names = d.authorizedNames();
            for (int i = 0; i < Math.min(4, names.size()); i++) g.drawString(font, names.get(i), 10, 30 + i * 9, 0xFFFFFFFF, false);
            if (names.size() > 4) g.drawString(font, "+" + (names.size() - 4), 80, 61, 0xFFAAAAAA, false);
        }
        int secs = menu.remainingSeconds();
        if (secs > 0) {
            g.drawString(font, Component.translatable("gui.rustbuild.cupboard.protected", secs / 60, String.format("%02d", secs % 60)), 8, 73, 0xFF6BD16B, false);
        } else {
            g.drawString(font, Component.translatable("gui.rustbuild.cupboard.unprotected"), 8, 73, 0xFFE05A4A, false);
        }
        g.drawString(font, Component.translatable("gui.rustbuild.cupboard.hint"), 8, 141, 0xFF9A8A7A, false);
    }
}
