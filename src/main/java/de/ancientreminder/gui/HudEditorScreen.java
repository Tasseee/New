package de.ancientreminder.gui;

import de.ancientreminder.Config;
import de.ancientreminder.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-Editor: Anzeigen mit gedrückter linker Maustaste verschieben.
 * Sie rasten an Bildschirmrand, Mitte und den anderen Anzeigen ein (Hilfslinien).
 */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 5;
    private static final int TOP_H = 24;
    private static final int BOTTOM_H = 52;

    private final Screen parent;
    private int drag = -1;
    private int offX;
    private int offY;
    private boolean wasDown;
    private int guideX = -1;
    private int guideY = -1;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD-Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        wasDown = Minecraft.getInstance().mouseHandler.isLeftPressed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        Minecraft mc = Minecraft.getInstance();
        Font f = this.font;
        int accent = Theme.accent();
        boolean down = mc.mouseHandler.isLeftPressed();
        boolean pressed = down && !wasDown;
        wasDown = down;

        // Rechtecke aller Anzeigen
        int count = Hud.IDS.length;
        int[][] rects = new int[count][4];
        for (int i = 0; i < count; i++) {
            int[] sz = Hud.size(f, i, Hud.lines(i, true));
            rects[i] = new int[]{Hud.x(i), Hud.y(i), sz[0], sz[1]};
        }

        // Obere Leiste
        g.fill(0, 0, this.width, TOP_H, 0xE0101318);
        g.fill(0, TOP_H, this.width, TOP_H + 1, Theme.withAlpha(accent, 0x90));
        g.drawString(f, "§lHUD-EDITOR", 8, 8, Theme.argb(accent), true);
        String hint = "Anzeigen ziehen – sie rasten an Rand, Mitte und anderen Anzeigen ein";
        if (f.width(hint) < this.width - 260) g.drawString(f, hint, 96, 8, 0xFF8A93A0, true);

        int bw1 = f.width("Zurücksetzen") + 20;
        int bw2 = f.width("Fertig") + 20;
        int b2x = this.width - 8 - bw2;
        int b1x = b2x - 6 - bw1;
        boolean h1 = Theme.inside(mx, my, b1x, 3, bw1, 18);
        boolean h2 = Theme.inside(mx, my, b2x, 3, bw2, 18);
        Theme.rr(g, b1x, 3, bw1, 18, h1 ? Theme.withAlpha(accent, 0x90) : 0xFF2A2F37);
        Theme.rr(g, b2x, 3, bw2, 18, h2 ? Theme.withAlpha(accent, 0x90) : 0xFF2A2F37);
        g.drawString(f, "Zurücksetzen", b1x + 10, 8, 0xFFFFFFFF, true);
        g.drawString(f, "Fertig", b2x + 10, 8, 0xFFFFFFFF, true);

        // Untere Leiste mit Schaltern
        int by = this.height - BOTTOM_H;
        g.fill(0, by, this.width, this.height, 0xE0101318);
        g.fill(0, by, this.width, by + 1, Theme.withAlpha(accent, 0x90));
        int cw = Math.min(190, (this.width - 16) / 3 - 4);
        for (int i = 0; i < count; i++) {
            int x = 8 + (i % 3) * (cw + 4);
            int y = by + 6 + (i / 3) * 22;
            boolean on = Hud.cfg(i).enabled;
            boolean hov = Theme.inside(mx, my, x, y, cw, 20);
            Theme.rr(g, x, y, cw, 20, hov ? 0xFF262B33 : 0xFF1A1E24);
            g.drawString(f, Hud.TITLES[i], x + 6, y + 6, 0xFFE6EAF0, true);
            int px = x + cw - 34;
            Theme.rr(g, px, y + 4, 28, 12, on ? Theme.argb(accent) : 0xFF3A3F47);
            Theme.rr(g, on ? px + 17 : px + 1, y + 5, 10, 10, 0xFFFFFFFF);
            if (pressed && hov) {
                Hud.cfg(i).enabled = !on;
                Config.save();
                pressed = false;
            }
        }

        if (pressed && h1) {
            Hud.resetPositions();
            pressed = false;
        }
        if (pressed && h2) {
            this.onClose();
            return;
        }

        // Ziehen starten
        if (pressed && my > TOP_H && my < by) {
            for (int i = count - 1; i >= 0; i--) {
                int[] r = rects[i];
                if (Theme.inside(mx, my, r[0], r[1], r[2], r[3])) {
                    drag = i;
                    offX = mx - r[0];
                    offY = my - r[1];
                    break;
                }
            }
        }
        if (!down && drag >= 0) {
            drag = -1;
            guideX = -1;
            guideY = -1;
            Config.save();
        }

        // Ziehen mit Einrasten
        if (drag >= 0) {
            int w = rects[drag][2];
            int h = rects[drag][3];
            int nx = mx - offX;
            int ny = my - offY;
            guideX = -1;
            guideY = -1;

            List<int[]> xs = new ArrayList<>();
            List<int[]> ys = new ArrayList<>();
            xs.add(new int[]{0, 0});
            xs.add(new int[]{this.width - w, this.width});
            xs.add(new int[]{(this.width - w) / 2, this.width / 2});
            ys.add(new int[]{TOP_H + 2, TOP_H + 2});
            ys.add(new int[]{by - h - 2, by - 2});
            ys.add(new int[]{(this.height - h) / 2, this.height / 2});
            for (int i = 0; i < count; i++) {
                if (i == drag) continue;
                int[] o = rects[i];
                xs.add(new int[]{o[0], o[0]});
                xs.add(new int[]{o[0] + o[2] - w, o[0] + o[2]});
                xs.add(new int[]{o[0] + o[2] + 3, o[0] + o[2] + 3});
                xs.add(new int[]{o[0] - w - 3, o[0] - 3});
                ys.add(new int[]{o[1], o[1]});
                ys.add(new int[]{o[1] + o[3] - h, o[1] + o[3]});
                ys.add(new int[]{o[1] + o[3] + 3, o[1] + o[3] + 3});
                ys.add(new int[]{o[1] - h - 3, o[1] - 3});
            }
            int bestDx = SNAP + 1;
            for (int[] c : xs) {
                int d = Math.abs(nx - c[0]);
                if (d < bestDx) {
                    bestDx = d;
                    guideX = c[1];
                    nx = c[0];
                }
            }
            int bestDy = SNAP + 1;
            for (int[] c : ys) {
                int d = Math.abs(ny - c[0]);
                if (d < bestDy) {
                    bestDy = d;
                    guideY = c[1];
                    ny = c[0];
                }
            }
            nx = Math.max(0, Math.min(this.width - w, nx));
            ny = Math.max(0, Math.min(this.height - h, ny));
            Hud.cfg(drag).x = nx;
            Hud.cfg(drag).y = ny;
            rects[drag][0] = nx;
            rects[drag][1] = ny;
        }

        // Hilfslinien
        if (drag >= 0) {
            int gc = Theme.withAlpha(accent, 0xA0);
            if (guideX >= 0) g.fill(guideX, TOP_H + 1, guideX + 1, by, gc);
            if (guideY >= 0) g.fill(0, guideY, this.width, guideY + 1, gc);
        }

        // Anzeigen
        for (int i = 0; i < count; i++) {
            float alpha = Hud.cfg(i).enabled ? 1f : 0.4f;
            Hud.draw(g, f, rects[i][0], rects[i][1], i, Hud.lines(i, true), alpha);
            if (i == drag || (drag < 0 && Theme.inside(mx, my, rects[i][0], rects[i][1], rects[i][2], rects[i][3]) && my > TOP_H && my < by)) {
                int x = rects[i][0];
                int y = rects[i][1];
                int w = rects[i][2];
                int h = rects[i][3];
                int oc = Theme.withAlpha(accent, 0xC0);
                g.fill(x - 1, y - 1, x + w + 1, y, oc);
                g.fill(x - 1, y + h, x + w + 1, y + h + 1, oc);
                g.fill(x - 1, y, x, y + h, oc);
                g.fill(x + w, y, x + w + 1, y + h, oc);
            }
        }
    }

    @Override
    public void onClose() {
        Config.save();
        this.minecraft.setScreen(parent);
    }
}
