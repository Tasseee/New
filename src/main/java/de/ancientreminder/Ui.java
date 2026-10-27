package de.ancientreminder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/** Kleine Hilfsfunktionen für Chat, Sounds und Formatierung. */
public final class Ui {
    public static final String[] COLOR_NAMES = {"Weiß", "Cyan", "Grün", "Gelb", "Orange", "Rot", "Magenta", "Blau", "Grau"};
    public static final int[] COLORS = {0xFFFFFF, 0x55FFFF, 0x55FF55, 0xFFFF55, 0xFFAA00, 0xFF5555, 0xFF55FF, 0x5555FF, 0xAAAAAA};

    private Ui() {}

    public static int color(int idx) {
        return COLORS[Math.floorMod(idx, COLORS.length)];
    }

    public static void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("§6[Ancient] §f" + text), false);
    }

    public static void bar(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }

    public static void ding() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
    }

    public static void warn() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 0.7f));
    }

    public static String d1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    public static String time(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    public static String compass(double dx, double dz) {
        String[] names = {"O", "SO", "S", "SW", "W", "NW", "N", "NO"};
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        int idx = (int) Math.round(deg / 45.0);
        return names[Math.floorMod(idx, 8)];
    }
}
