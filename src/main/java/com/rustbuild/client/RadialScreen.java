package com.rustbuild.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Радиальное меню в стиле Rust: зажмите клавишу, наведите мышь на сектор, отпустите клавишу.
 * Выбор уходит на сервер командой /rustbuild piece|tier <индекс>.
 */
public class RadialScreen extends Screen {
    public record Entry(Component name, ResourceLocation icon) {}

    private int rIn = 40;
    private int rOut = 112;
    private static final double TWO_PI = Math.PI * 2.0;

    private final List<Entry> entries;
    private final int selected;
    private final String command;
    private final int keyCode;
    private int hover = -1;

    public RadialScreen(Component title, List<Entry> entries, int selected, String command, int keyCode) {
        super(title);
        this.entries = entries;
        this.selected = selected;
        this.command = command;
        this.keyCode = keyCode;
    }

    @Override
    protected void init() {
        super.init();
        rOut = Math.max(90, Math.min(175, height / 2 - 26));
        rIn = (int) (rOut * (entries.size() > 8 ? 0.45 : 0.36));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Дробный номер сектора: 0 = сверху по центру, дальше по часовой стрелке. */
    private double frac(double dx, double dy) {
        int n = entries.size();
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += TWO_PI;
        double shifted = (a + Math.PI / n) % TWO_PI;
        return shifted / (TWO_PI / n);
    }

    private int pixelId(int x, int y) {
        int n = entries.size();
        double dx = x + 0.5, dy = y + 0.5;
        double r = Math.sqrt(dx * dx + dy * dy);
        double f = frac(dx, dy);
        int sector = Math.min((int) f, n - 1);
        double within = f - sector;
        double edgePx = Math.min(within, 1.0 - within) * (TWO_PI / n) * r;
        return edgePx < 1.2 ? -2 : sector;
    }

    private int colorFor(int sector) {
        if (sector == hover) return 0xE6CD412B;   // оранжево-красный Rust
        if (sector == selected) return 0xD0606060;
        return 0xB41A1A1A;
    }

    private void span(GuiGraphics g, int cx, int cy, int y, int x1, int x2) {
        int runStart = x1;
        int runId = -3;
        for (int x = x1; x <= x2 + 1; x++) {
            int id = x <= x2 ? pixelId(x, y) : -3;
            if (id != runId) {
                if (runId >= 0) g.fill(cx + runStart, cy + y, cx + x, cy + y + 1, colorFor(runId));
                runStart = x;
                runId = id;
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2, cy = height / 2;
        int n = entries.size();

        double dx = mouseX - cx, dy = mouseY - cy;
        hover = Math.sqrt(dx * dx + dy * dy) > 14 ? Math.min((int) frac(dx, dy), n - 1) : -1;

        g.fill(0, 0, width, height, 0x50000000);

        for (int y = -rOut; y <= rOut; y++) {
            int xo = (int) Math.floor(Math.sqrt((double) rOut * rOut - (double) y * y));
            if (Math.abs(y) < rIn) {
                int xi = (int) Math.ceil(Math.sqrt((double) rIn * rIn - (double) y * y));
                span(g, cx, cy, y, -xo, -xi);
                span(g, cx, cy, y, xi, xo);
            } else {
                span(g, cx, cy, y, -xo, xo);
            }
        }

        double rMid = (rIn + rOut) / 2.0;
        for (int i = 0; i < n; i++) {
            double th = i * TWO_PI / n;
            int ix = (int) Math.round(cx + Math.sin(th) * rMid) - 16;
            int iy = (int) Math.round(cy - Math.cos(th) * rMid) - 16;
            g.blit(entries.get(i).icon(), ix, iy, 0, 0, 32, 32, 32, 32);
        }

        Entry shown = entries.get(hover >= 0 ? hover : selected);
        g.drawCenteredString(font, shown.name(), cx, cy - 4, 0xFFFFFFFF);
        g.drawCenteredString(font, title, cx, cy - rOut - 16, 0xFFCD412B);
    }

    private void choose(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(command + " " + index);
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hover >= 0) { choose(hover); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (!InputConstants.isKeyDown(window, keyCode)) {
            if (hover >= 0) choose(hover); else onClose();
        }
    }
}
