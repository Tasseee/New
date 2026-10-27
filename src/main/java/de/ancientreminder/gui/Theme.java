package de.ancientreminder.gui;

import de.ancientreminder.Config;
import net.minecraft.client.gui.GuiGraphics;

/** Farben und kleine Zeichenhelfer für das moderne Design. */
public final class Theme {
    public static final int[] ACCENTS = {0x3DDCFF, 0x5CFF8A, 0xFFB347, 0xFF5CC8, 0x7C8CFF, 0xC96BFF};

    private Theme() {}

    public static int accent() {
        return ACCENTS[Math.floorMod(Config.I.uiAccent, ACCENTS.length)];
    }

    public static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int c1, int c2, float t) {
        int r = Math.round(((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
        int g = Math.round(((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
        int b = Math.round((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    public static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Rechteck mit abgeschnittenen Ecken (wirkt abgerundet). */
    public static void rr(GuiGraphics g, int x, int y, int w, int h, int argb) {
        if (w < 3 || h < 3) {
            g.fill(x, y, x + w, y + h, argb);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** Waagrechter Farbverlauf aus schmalen Streifen. */
    public static void hgrad(GuiGraphics g, int x, int y, int w, int h, int rgb1, int rgb2) {
        int steps = Math.max(1, Math.min(w, 48));
        for (int i = 0; i < steps; i++) {
            int x0 = x + w * i / steps;
            int x1 = x + w * (i + 1) / steps;
            g.fill(x0, y, x1, y + h, argb(mix(rgb1, rgb2, i / (float) steps)));
        }
    }
}
