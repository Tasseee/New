package de.ancientreminder;

import de.ancientreminder.gui.HudEditorScreen;
import de.ancientreminder.gui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Alle HUD-Anzeigen im modernen Look: Titelzeile, Akzentstreifen und Fortschrittsbalken. */
public final class Hud {
    /** Eine Textzeile; bar >= 0 zeichnet darunter einen Fortschrittsbalken (0..1). */
    public record Line(String text, int rgb, float bar, int barRgb) {
        public Line(String text, int rgb) {
            this(text, rgb, -1f, rgb);
        }

        public Line(String text, int rgb, float bar) {
            this(text, rgb, bar, rgb);
        }
    }

    public static final String[] IDS = {"ancient", "rtp", "height", "rating", "fire", "chunk"};
    public static final String[] TITLES = {"Ancient-Statistik", "RTP-Statistik", "Höhen-Anzeige", "Spot-Bewertung", "Feuerresistenz", "Chunk-Info"};
    private static final int[] DEF_X = {6, 6, 6, 6, 6, 6};
    private static final int[] DEF_Y = {6, 62, 118, 150, 250, 274};
    private static final int MUTED = 0xA0A8B4;

    private Hud() {}

    public static Config.WidgetCfg cfg(int i) {
        return Config.I.widgets.get(IDS[i]);
    }

    public static int x(int i) {
        int v = cfg(i).x;
        return v < 0 ? DEF_X[i] : v;
    }

    public static int y(int i) {
        int v = cfg(i).y;
        return v < 0 ? DEF_Y[i] : v;
    }

    public static void resetPositions() {
        for (int i = 0; i < IDS.length; i++) {
            cfg(i).x = -1;
            cfg(i).y = -1;
        }
        Config.save();
    }

    public static boolean visible(int i) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Config c = Config.I;
        return switch (i) {
            case 1 -> c.netherEnabled;
            case 2 -> NetherSession.phase == NetherSession.Phase.DESCENDING;
            case 3 -> c.netherEnabled && NetherSession.phase != NetherSession.Phase.IDLE;
            case 4 -> Warnings.fireSeconds() >= 0;
            default -> true;
        };
    }

    public static List<Line> lines(int i, boolean preview) {
        Config c = Config.I;
        Minecraft mc = Minecraft.getInstance();
        int col = Ui.color(cfg(i).color);
        List<Line> out = new ArrayList<>();
        switch (i) {
            case 0 -> {
                int iv = c.statsIntervalMin;
                out.add(new Line("Session: " + Stats.sessionAncient + " Ancient", col));
                out.add(new Line("Ø pro " + iv + " Min: " + Ui.d1(Stats.avgPerInterval()), col));
                out.add(new Line("Letzte " + iv + " Min: " + Stats.lastInterval(), col));
                out.add(new Line("Gesamt: " + Data.I.totalAncient, col));
                out.add(new Line("Zeit: " + Ui.time(Stats.sessionSeconds()), MUTED));
            }
            case 1 -> {
                out.add(new Line("RTPs: " + Stats.rtpSession + " (" + Ui.d1(Stats.rtpPerHour()) + "/h)", col));
                out.add(new Line("Ø Ancient/Spot: " + Ui.d1(Data.I.avgAncientPerSpot()), col));
                if (NetherSession.phase != NetherSession.Phase.IDLE || preview) {
                    out.add(new Line("Dieser Spot: " + NetherSession.ancientThisSpot() + " Ancient, "
                            + Ui.time(NetherSession.sessionSeconds()), col));
                }
                RatingLevel best = Data.I.bestLevel();
                if (best != null) {
                    Data.LevelStat st = Data.I.levelStats().get(best);
                    out.add(new Line("Bestes Level: " + best.label + " (Ø " + Ui.d1(st.avgAncient) + ")", best.color));
                }
            }
            case 2 -> {
                int y = mc.player != null ? mc.player.getBlockY() : 68;
                int left = Math.max(0, y - c.targetY);
                int span = Math.max(1, NetherSession.startY - c.targetY);
                float prog = preview ? 0.6f : Math.max(0f, Math.min(1f, 1f - left / (float) span));
                out.add(new Line("Höhe: Y " + y, col));
                out.add(new Line("Noch " + left + " Blöcke bis Y" + c.targetY, left <= 10 ? 0x55FF55 : col, prog));
            }
            case 3 -> ratingLines(out, col, preview);
            case 4 -> {
                int sec = preview ? 150 : Warnings.fireSeconds();
                int rgb = sec <= c.fireWarnSeconds ? 0xFF5555 : col;
                float frac = preview ? 0.8f : Warnings.fireFraction();
                out.add(new Line("Feuerresistenz: " + Ui.time(Math.max(0, sec)), rgb, frac));
            }
            case 5 -> {
                BlockPos pos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
                out.add(new Line("Chunk: " + (pos.getX() >> 4) + " / " + (pos.getZ() >> 4), col));
                out.add(new Line("Im Chunk: " + (pos.getX() & 15) + " " + pos.getY() + " " + (pos.getZ() & 15), MUTED));
            }
            default -> {
            }
        }
        return out;
    }

    private static void ratingLines(List<Line> out, int col, boolean preview) {
        Config c = Config.I;
        NetherSession.Rating r = NetherSession.last;
        if (r == null && preview) {
            int lc = RatingLevel.VERY_GOOD.color;
            out.add(new Line("SEHR GUT", lc));
            out.add(new Line("Abgebaut: 4 %", col, 0.04f, lc));
            out.add(new Line("Offen 0,6 % | Grund 0,2 %", MUTED));
            out.add(new Line("BLEIBEN – guter Spot", col));
            out.add(new Line("Bester Chunk: 2 Chunks NO", col));
            out.add(new Line("Neu-Scan in 0:42", col, 0.3f, Theme.accent()));
            return;
        }
        if (r == null) {
            out.add(new Line("Warte auf Scan ...", col));
        } else {
            out.add(new Line(r.level().label.toUpperCase(Locale.ROOT), r.level().color));
            out.add(new Line("Abgebaut: " + Math.round(r.pct()) + " %", col, (float) (r.pct() / 100.0), r.level().color));
            out.add(new Line("Offen " + Ui.d1(r.fraction() * 100) + " % | Grund " + Ui.d1(Calibration.baseline() * 100) + " %", MUTED));
            if (c.recommend) out.add(new Line(NetherSession.recommendation, col));
            Minecraft mc = Minecraft.getInstance();
            if (c.chunkRating && mc.player != null) {
                String info = NetherSession.bestChunkInfo(mc.player.position());
                if (!info.isEmpty()) out.add(new Line(info, col));
            }
        }
        if (NetherSession.SCANNER.isRunning()) {
            float pr = (float) NetherSession.SCANNER.progress();
            out.add(new Line("Scanne ... " + Math.round(pr * 100) + " %", col, pr, Theme.accent()));
        } else if (r != null) {
            long secs = NetherSession.secondsToNextScan();
            float frac = 1f - Math.min(1f, secs / (float) Math.max(1, c.rescanSeconds));
            out.add(new Line("Neu-Scan in " + Ui.time(secs), col, frac, Theme.accent()));
        }
        if (!Calibration.done()) {
            out.add(new Line("Kalibrierung " + Calibration.samples() + "/" + Config.CALIB_NEEDED, 0xFFAA00,
                    Calibration.samples() / (float) Config.CALIB_NEEDED));
        }
        if (NetherSession.tunnel != null && Minecraft.getInstance().player != null) {
            out.add(new Line("Gang: " + NetherSession.tunnelInfo(Minecraft.getInstance().player.position())
                    + (NetherSession.tunnel.border() ? " (Grenze)" : ""), 0x55FFFF));
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.I.hudEnabled || mc.player == null) return;
        if (mc.screen instanceof HudEditorScreen) return;
        Font font = mc.font;
        for (int i = 0; i < IDS.length; i++) {
            if (!cfg(i).enabled || !visible(i)) continue;
            draw(g, font, x(i), y(i), i, lines(i, false), 1f);
        }
    }

    public static int[] size(Font font, int idx, List<Line> lines) {
        int w = font.width(TITLES[idx].toUpperCase(Locale.ROOT)) + 12;
        int h = font.lineHeight + 7 + 4;
        for (Line l : lines) {
            w = Math.max(w, font.width(l.text()) + 12);
            h += font.lineHeight + 1 + (l.bar() >= 0 ? 5 : 0);
        }
        return new int[]{w, h};
    }

    public static void draw(GuiGraphics g, Font font, int x, int y, int idx, List<Line> lines, float alpha) {
        if (lines.isEmpty()) return;
        int[] sz = size(font, idx, lines);
        int w = sz[0];
        int h = sz[1];
        int accent = Theme.accent();
        int aFull = Math.round(255f * alpha);
        if (Config.I.hudBackground) {
            int a = Math.round(Config.I.hudBgAlpha / 100f * 255f * alpha);
            Theme.rr(g, x, y, w, h, Theme.withAlpha(0x0E1116, a));
            g.fill(x, y + 1, x + 2, y + h - 1, Theme.withAlpha(accent, aFull));
            g.fill(x + 2, y + font.lineHeight + 4, x + w - 1, y + font.lineHeight + 5, Theme.withAlpha(0xFFFFFF, Math.round(30 * alpha)));
        }
        g.drawString(font, TITLES[idx].toUpperCase(Locale.ROOT), x + 6, y + 3, Theme.withAlpha(accent, aFull), true);
        int ty = y + font.lineHeight + 7;
        for (Line l : lines) {
            g.drawString(font, l.text(), x + 6, ty, Theme.withAlpha(l.rgb(), aFull), true);
            ty += font.lineHeight + 1;
            if (l.bar() >= 0) {
                int bw = w - 12;
                g.fill(x + 6, ty, x + 6 + bw, ty + 3, Theme.withAlpha(0xFFFFFF, Math.round(40 * alpha)));
                int fw = Math.round(bw * Math.max(0f, Math.min(1f, l.bar())));
                g.fill(x + 6, ty, x + 6 + fw, ty + 3, Theme.withAlpha(l.barRgb(), aFull));
                ty += 5;
            }
        }
    }
}
