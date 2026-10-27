package de.ancientreminder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

/** Lava-Warnung beim Graben und Feuerresistenz-Timer. */
public final class Warnings {
    private static int tick;
    private static long lastLavaWarn;
    private static boolean hadFire;
    private static boolean warnedLow;
    private static int fireMax;

    private Warnings() {}

    /** Verbleibende Sekunden der Feuerresistenz, -1 wenn keine aktiv ist. */
    public static int fireSeconds() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return -1;
        MobEffectInstance e = p.getEffect(MobEffects.FIRE_RESISTANCE);
        if (e == null) return -1;
        if (e.isInfiniteDuration()) return 99999;
        return e.getDuration() / 20;
    }

    /** Anteil der verbleibenden Feuerresistenz (0..1) relativ zum Höchstwert dieses Effekts. */
    public static float fireFraction() {
        int sec = fireSeconds();
        if (sec < 0 || fireMax <= 0) return 0f;
        return Math.min(1f, sec / (float) fireMax);
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel lvl = mc.level;
        if (p == null || lvl == null) return;
        Config c = Config.I;
        int secNow = fireSeconds();
        if (secNow < 0) fireMax = 0;
        else if (secNow > fireMax) fireMax = secNow;
        long now = System.currentTimeMillis();

        if (c.fireWarn) {
            int sec = fireSeconds();
            if (sec >= 0) {
                hadFire = true;
                if (sec <= c.fireWarnSeconds && !warnedLow) {
                    warnedLow = true;
                    Ui.chat("§cFeuerresistenz läuft in " + sec + " s ab!");
                    Ui.warn();
                } else if (sec > c.fireWarnSeconds) {
                    warnedLow = false;
                }
            } else if (hadFire) {
                hadFire = false;
                warnedLow = false;
                if (lvl.dimension() == Level.NETHER) {
                    Ui.chat("§cFeuerresistenz abgelaufen!");
                    Ui.warn();
                }
            }
        }

        if (!c.lavaWarn || ++tick % 4 != 0) return;

        Direction dir = p.getDirection();
        Direction side = dir.getClockWise();
        BlockPos base = p.blockPosition();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        int ahead = -1;
        search:
        for (int d = 1; d <= c.lavaAhead; d++) {
            for (int s = -1; s <= 1; s++) {
                for (int dy = -1; dy <= 2; dy++) {
                    mp.set(base.getX() + dir.getStepX() * d + side.getStepX() * s,
                            base.getY() + dy,
                            base.getZ() + dir.getStepZ() * d + side.getStepZ() * s);
                    if (isLava(lvl, mp)) {
                        ahead = d;
                        break search;
                    }
                }
            }
        }

        boolean near = false;
        if (ahead < 0 && c.lavaNearby) {
            near:
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        mp.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                        if (isLava(lvl, mp)) {
                            near = true;
                            break near;
                        }
                    }
                }
            }
        }

        if ((ahead > 0 || near) && now - lastLavaWarn > 2500) {
            lastLavaWarn = now;
            String msg = ahead > 0
                    ? "§c!!! LAVA " + ahead + " Block" + (ahead == 1 ? "" : "e") + " vor dir !!!"
                    : "§c!!! Lava direkt neben dir !!!";
            Ui.bar(msg);
            Ui.chat(msg);
            if (c.lavaSound) Ui.warn();
        }
    }

    private static boolean isLava(ClientLevel lvl, BlockPos pos) {
        return lvl.getBlockState(pos).getFluidState().is(FluidTags.LAVA);
    }
}
