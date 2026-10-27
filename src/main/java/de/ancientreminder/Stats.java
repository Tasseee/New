package de.ancientreminder;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;

/** Zählt gefundenes Ancient Debris (Zuwachs im Inventar) und berechnet Durchschnitte. */
public final class Stats {
    public static long sessionStart = System.currentTimeMillis();
    public static int sessionAncient;
    public static int rtpSession;
    private static int prevCount = -1;
    private static final ArrayDeque<Long> EVENTS = new ArrayDeque<>();
    private static int saveTimer;

    private Stats() {}

    public static void resetSession() {
        sessionStart = System.currentTimeMillis();
        sessionAncient = 0;
        rtpSession = 0;
        prevCount = -1;
        EVENTS.clear();
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            prevCount = -1;
            return;
        }
        Inventory inv = mc.player.getInventory();
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.ANCIENT_DEBRIS)) count += s.getCount();
        }
        // Bei offenem Inventar/Kiste nur den Stand merken, damit Umlagern nicht mitzählt.
        if (prevCount >= 0 && count > prevCount && mc.screen == null) {
            int delta = count - prevCount;
            sessionAncient += delta;
            Data.I.totalAncient += delta;
            long now = System.currentTimeMillis();
            for (int i = 0; i < delta; i++) EVENTS.add(now);
            Data.markDirty();
        }
        prevCount = count;
        if (++saveTimer >= 600) {
            saveTimer = 0;
            Data.saveIfDirty();
        }
    }

    public static long sessionSeconds() {
        return (System.currentTimeMillis() - sessionStart) / 1000;
    }

    public static double avgPerInterval() {
        double minutes = Math.max(1.0 / 6.0, (System.currentTimeMillis() - sessionStart) / 60000.0);
        return sessionAncient / minutes * Config.I.statsIntervalMin;
    }

    public static int lastInterval() {
        long limit = System.currentTimeMillis() - Config.I.statsIntervalMin * 60000L;
        while (!EVENTS.isEmpty() && EVENTS.peekFirst() < limit) EVENTS.pollFirst();
        return EVENTS.size();
    }

    public static double rtpPerHour() {
        double hours = Math.max(1.0 / 60.0, sessionSeconds() / 3600.0);
        return rtpSession / hours;
    }
}
