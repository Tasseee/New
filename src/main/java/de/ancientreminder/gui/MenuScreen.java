package de.ancientreminder.gui;

import de.ancientreminder.Calibration;
import de.ancientreminder.Config;
import de.ancientreminder.Data;
import de.ancientreminder.Hud;
import de.ancientreminder.NetherSession;
import de.ancientreminder.RatingLevel;
import de.ancientreminder.Stats;
import de.ancientreminder.Target;
import de.ancientreminder.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Modernes Hauptmenü: Seitenleiste mit Kategorien, rechts das Einstellungs-Panel mit
 * Schaltern, Schiebereglern, Farbfeldern und Statistik. Die Bedienung läuft über die Maus
 * (Mausrad zum Scrollen).
 */
public class MenuScreen extends Screen {
    public static final int PAGE_STATS = 6;
    public static final String VERSION = "2.1.0";

    private static final String[] PAGE_NAMES = {"Erz-Finder", "HUDs", "Nether-Scanner", "Gang", "Warnungen", "Chunks", "Statistik"};

    private static final int HEADER = 0;
    private static final int TOGGLE = 1;
    private static final int SLIDER = 2;
    private static final int CHOICE = 3;
    private static final int COLORS = 4;
    private static final int BUTTON = 5;
    private static final int TARGET = 6;
    private static final int PROGRESS = 7;
    private static final int TEXT = 8;
    private static final int CARDS = 9;
    private static final int BARLEVEL = 10;

    private static final int SIDE_W = 104;
    private static final int HEAD_H = 28;
    private static final int FOOT_H = 16;
    private static final int SL_W = 112;

    private static final class Row {
        int kind;
        String label = "";
        String help = "";
        BooleanSupplier bget;
        Consumer<Boolean> bset;
        IntSupplier iget;
        IntConsumer iset;
        int min;
        int max;
        int step = 1;
        String suffix = "";
        String[] names;
        int[] palette;
        Runnable run;
        Target target;
        String text = "";
        int rgb = 0xFFFFFF;
        Supplier<Float> frac;
        Supplier<String> valueText;
        String[][] cards;

        int height() {
            return switch (kind) {
                case HEADER -> 22;
                case CARDS -> 44;
                case TEXT -> 13;
                case BARLEVEL -> 17;
                default -> 22;
            };
        }

        boolean interactive() {
            return kind == TOGGLE || kind == SLIDER || kind == CHOICE || kind == COLORS || kind == BUTTON || kind == TARGET;
        }
    }

    private final Screen parent;
    private int page;
    private int scroll;
    private List<Row> rows;
    private boolean wasDown;
    private Row dragRow;
    private int dragTrackX;

    public MenuScreen(Screen parent) {
        this(parent, 0);
    }

    public MenuScreen(Screen parent, int page) {
        super(Component.literal("Ancient Reminder"));
        this.parent = parent;
        this.page = Math.max(0, Math.min(page, PAGE_NAMES.length - 1));
    }

    @Override
    protected void init() {
        wasDown = Minecraft.getInstance().mouseHandler.isLeftPressed();
        rows = build(page);
    }

    // ------------------------------------------------------------------ Zeilen bauen

    private static Row row(int kind, String label, String help) {
        Row r = new Row();
        r.kind = kind;
        r.label = label;
        r.help = help;
        return r;
    }

    private static Row header(String label) {
        return row(HEADER, label, "");
    }

    private static Row toggle(String label, String help, BooleanSupplier get, Consumer<Boolean> set) {
        Row r = row(TOGGLE, label, help);
        r.bget = get;
        r.bset = set;
        return r;
    }

    private static Row slider(String label, String help, int min, int max, int step, String suffix, IntSupplier get, IntConsumer set) {
        Row r = row(SLIDER, label, help);
        r.min = min;
        r.max = max;
        r.step = step;
        r.suffix = suffix;
        r.iget = get;
        r.iset = set;
        return r;
    }

    private static Row choice(String label, String help, String[] names, IntSupplier get, IntConsumer set) {
        Row r = row(CHOICE, label, help);
        r.names = names;
        r.iget = get;
        r.iset = set;
        return r;
    }

    private static Row colors(String label, String help, int[] palette, IntSupplier get, IntConsumer set) {
        Row r = row(COLORS, label, help);
        r.palette = palette;
        r.iget = get;
        r.iset = set;
        return r;
    }

    private static Row button(String label, String help, Runnable run) {
        Row r = row(BUTTON, label, help);
        r.run = run;
        return r;
    }

    private static Row progress(String label, String help, Supplier<Float> frac, Supplier<String> value, int rgb) {
        Row r = row(PROGRESS, label, help);
        r.frac = frac;
        r.valueText = value;
        r.rgb = rgb;
        return r;
    }

    private static Row text(String text, int rgb) {
        Row r = row(TEXT, "", "");
        r.text = text;
        r.rgb = rgb;
        return r;
    }

    private List<Row> build(int p) {
        Config c = Config.I;
        List<Row> l = new ArrayList<>();
        switch (p) {
            case 0 -> {
                l.add(header("Allgemein"));
                l.add(toggle("Erz-Finder", "Sucht offen liegende Erze in deiner Nähe.", () -> c.oreEnabled, v -> c.oreEnabled = v));
                l.add(slider("Radius", "Wie weit nach Erzen gesucht wird.", 16, 96, 8, " Blöcke", () -> c.oreRadius, v -> c.oreRadius = v));
                l.add(slider("Scan-Intervall", "Abstand zwischen zwei Scans (20 Ticks = 1 s).", 10, 100, 10, " Ticks", () -> c.oreScanTicks, v -> c.oreScanTicks = v));
                l.add(toggle("Chat-Nachricht", "Meldet neue Funde mit Koordinaten im Chat.", () -> c.oreMessage, v -> c.oreMessage = v));
                l.add(toggle("Sound", "Spielt einen Ton bei neuen Funden.", () -> c.oreSound, v -> c.oreSound = v));
                l.add(toggle("Drops am Boden", "Markiert auch herumliegende Erz-Drops.", () -> c.oreItems, v -> c.oreItems = v));
                l.add(header("Tracer"));
                l.add(toggle("Tracer", "Linien zu den Erzen, auch durch Wände.", () -> c.tracers, v -> c.tracers = v));
                l.add(slider("Max. Tracer", "Wie viele Linien gleichzeitig (die nächsten zuerst).", 1, 64, 1, "", () -> c.maxTracers, v -> c.maxTracers = v));
                l.add(slider("Dicke", "Dicke der Tracer-Linien.", 1, 5, 1, "", () -> c.tracerWidth, v -> c.tracerWidth = v));
                l.add(choice("Start", "Wo die Linien beginnen.", new String[]{"Fadenkreuz", "Füße"}, () -> c.tracerStart, v -> c.tracerStart = v));
                l.add(slider("Deckkraft", "Wie durchsichtig die Linien sind.", 10, 100, 10, " %", () -> c.tracerAlpha, v -> c.tracerAlpha = v));
                l.add(toggle("Block-Umrisse", "Zeichnet einen Rahmen um gefundene Erze.", () -> c.oreBoxes, v -> c.oreBoxes = v));
                l.add(header("Ziele (Suche / Tracer)"));
                for (Target t : Target.values()) {
                    Row r = row(TARGET, t.label, "Suche und Tracer für " + t.label + " einzeln schalten.");
                    r.target = t;
                    l.add(r);
                }
            }
            case 1 -> {
                l.add(button("HUD-Editor öffnen", "Anzeigen per Maus verschieben, mit Einrasten.", () -> Minecraft.getInstance().setScreen(new HudEditorScreen(this))));
                l.add(header("Allgemein"));
                l.add(toggle("Alle HUDs", "Schaltet alle Anzeigen auf einmal aus.", () -> c.hudEnabled, v -> c.hudEnabled = v));
                l.add(toggle("Hintergrund", "Dunkler Kasten hinter den Anzeigen.", () -> c.hudBackground, v -> c.hudBackground = v));
                l.add(slider("Hintergrund-Deckkraft", "Wie dunkel der Kasten ist.", 0, 90, 5, " %", () -> c.hudBgAlpha, v -> c.hudBgAlpha = v));
                l.add(slider("Ø-Intervall", "Zeitraum für den Ancient-Durchschnitt.", 5, 60, 5, " Min", () -> c.statsIntervalMin, v -> c.statsIntervalMin = v));
                l.add(colors("Akzentfarbe", "Farbe für Menü, Titel und Balken.", Theme.ACCENTS, () -> c.uiAccent, v -> c.uiAccent = v));
                l.add(header("Anzeigen"));
                for (int i = 0; i < Hud.IDS.length; i++) {
                    final int idx = i;
                    l.add(toggle(Hud.TITLES[i], "Diese Anzeige ein- oder ausschalten.", () -> Hud.cfg(idx).enabled, v -> Hud.cfg(idx).enabled = v));
                }
                l.add(header("Textfarben"));
                for (int i = 0; i < Hud.IDS.length; i++) {
                    final int idx = i;
                    l.add(colors(Hud.TITLES[i], "Textfarbe dieser Anzeige.", Ui.COLORS, () -> Hud.cfg(idx).color, v -> Hud.cfg(idx).color = v));
                }
            }
            case 2 -> {
                l.add(header("Scanner"));
                l.add(toggle("Nether-Scanner", "Höhen-HUD und Spot-Bewertung nach /rtp.", () -> c.netherEnabled, v -> c.netherEnabled = v));
                l.add(slider("Ziel-Höhe", "Ab dieser Höhe startet der Scan.", 13, 17, 1, "", () -> c.targetY, v -> c.targetY = v));
                l.add(slider("Scan-Radius", "Wie viele Chunks um dich gemessen werden.", 2, 8, 1, " Chunks", () -> c.scanChunks, v -> c.scanChunks = v));
                l.add(slider("Neu-Scan alle", "So oft wird die Umgebung neu bewertet.", 30, 120, 15, " s", () -> c.rescanSeconds, v -> c.rescanSeconds = v));
                l.add(toggle("RTP-Sprung erkennen", "Erkennt auch Teleports ohne /rtp-Befehl.", () -> c.autoDetectJump, v -> c.autoDetectJump = v));
                l.add(header("Ausgabe"));
                l.add(toggle("Empfehlung", "Zeigt 'Bleiben' oder 'Neu /rtp' an.", () -> c.recommend, v -> c.recommend = v));
                l.add(toggle("Chat-Meldung", "Meldet die Bewertung im Chat.", () -> c.spotMessage, v -> c.spotMessage = v));
                l.add(toggle("Sound", "Ton bei neuer Bewertung.", () -> c.spotSound, v -> c.spotSound = v));
                l.add(header("Stufen (Grenzen in % abgebaut)"));
                l.add(slider("Sehr gut bis", "Unter diesem Wert gilt der Spot als sehr gut.", 5, 15, 1, " %", () -> c.lvlVeryGood, v -> c.lvlVeryGood = v));
                l.add(slider("Gut bis", "Unter diesem Wert gilt der Spot als gut.", 15, 35, 1, " %", () -> c.lvlGood, v -> c.lvlGood = v));
                l.add(slider("Ok bis", "Unter diesem Wert gilt der Spot als ok.", 35, 60, 1, " %", () -> c.lvlOk, v -> c.lvlOk = v));
                l.add(slider("Schlecht bis", "Darüber gilt der Spot als weg.", 60, 85, 1, " %", () -> c.lvlBad, v -> c.lvlBad = v));
                l.add(header("Kalibrierung"));
                l.add(progress("Messungen", "10x /rtp und auf Y15 gehen.",
                        () -> Calibration.samples() / (float) Config.CALIB_NEEDED,
                        () -> Calibration.samples() + "/" + Config.CALIB_NEEDED, Theme.accent()));
                l.add(button("Kalibrierung zurücksetzen", "Löscht alle Messungen, danach neu sammeln.", () -> {
                    Calibration.reset();
                    Ui.chat("Kalibrierung zurückgesetzt – 10x /rtp und auf Y" + c.targetY + " gehen.");
                }));
            }
            case 3 -> {
                l.add(header("Markierung"));
                l.add(toggle("Gang-Markierung", "Markiert bei 'Sehr gut' einen sicheren Gang.", () -> c.tunnelEnabled, v -> c.tunnelEnabled = v));
                l.add(slider("Chance bei 'Sehr gut'", "Wie oft eine Markierung erscheint.", 0, 100, 5, " %", () -> c.tunnelChance, v -> c.tunnelChance = v));
                l.add(slider("Cooldown", "Mindestabstand zwischen zwei Markierungen.", 1, 30, 1, " Min", () -> c.tunnelCooldownMin, v -> c.tunnelCooldownMin = v));
                l.add(slider("Länge", "Länge des markierten Gangs.", 6, 12, 2, " Blöcke", () -> c.tunnelLength, v -> c.tunnelLength = v));
                l.add(slider("Max. Entfernung", "Wie weit weg der Gang liegen darf.", 16, 32, 4, " Blöcke", () -> c.tunnelMaxDist, v -> c.tunnelMaxDist = v));
                l.add(header("Sicherheit & Lage"));
                l.add(slider("Lava-Abstand", "Mindestabstand des Gangs zu Lava.", 3, 6, 1, " Blöcke", () -> c.tunnelLavaSafety, v -> c.tunnelLavaSafety = v));
                l.add(choice("An Chunkgrenze", "Gang direkt neben einer Chunkgrenze bevorzugen.", new String[]{"Aus", "Bevorzugen", "Nur dort"}, () -> c.tunnelBorder, v -> c.tunnelBorder = v));
                l.add(button("Markierung löschen", "Entfernt die aktuelle Gang-Markierung.", NetherSession::clearTunnel));
            }
            case 4 -> {
                l.add(header("Lava"));
                l.add(toggle("Lava-Warnung", "Warnt, wenn beim Graben Lava auftaucht.", () -> c.lavaWarn, v -> c.lavaWarn = v));
                l.add(slider("Lava voraus ab", "Ab wie vielen Blöcken vor dir gewarnt wird.", 2, 6, 1, " Blöcken", () -> c.lavaAhead, v -> c.lavaAhead = v));
                l.add(toggle("Lava direkt daneben", "Warnt auch bei Lava neben, über oder unter dir.", () -> c.lavaNearby, v -> c.lavaNearby = v));
                l.add(toggle("Warn-Sound", "Ton bei Lava-Warnung.", () -> c.lavaSound, v -> c.lavaSound = v));
                l.add(header("Feuerresistenz"));
                l.add(toggle("Feuerresistenz-Warnung", "Warnt kurz vor dem Ablauf des Effekts.", () -> c.fireWarn, v -> c.fireWarn = v));
                l.add(slider("Warnen bei", "Restzeit, ab der gewarnt wird.", 15, 60, 15, " s", () -> c.fireWarnSeconds, v -> c.fireWarnSeconds = v));
            }
            case 5 -> {
                l.add(header("Anzeige"));
                l.add(toggle("Chunkgrenzen", "Dezente Linien an den Chunkgrenzen (Taste F9).", () -> c.chunkEnabled, v -> c.chunkEnabled = v));
                l.add(toggle("Chunk-Bewertung färben", "Im Nether nach dem Scan: grün = unberührt, rot = weg.", () -> c.chunkRating, v -> c.chunkRating = v));
                l.add(toggle("Chunk-Info HUD", "Zeigt Chunk-Koordinaten als Anzeige.", () -> Hud.cfg(5).enabled, v -> Hud.cfg(5).enabled = v));
                l.add(slider("Reichweite", "Wie viele Chunks um dich gezeichnet werden.", 1, 5, 1, " Chunks", () -> c.chunkRange, v -> c.chunkRange = v));
                l.add(header("Aussehen"));
                l.add(colors("Farbe", "Grundfarbe der Linien.", Ui.COLORS, () -> c.chunkColor, v -> c.chunkColor = v));
                l.add(slider("Deckkraft", "Je niedriger, desto dezenter.", 5, 60, 5, " %", () -> c.chunkAlpha, v -> c.chunkAlpha = v));
                l.add(slider("Dicke", "Dicke der Linien.", 1, 3, 1, "", () -> c.chunkWidth, v -> c.chunkWidth = v));
                l.add(slider("Höhe (+/-)", "Wie hoch die Linien über und unter dir reichen.", 16, 128, 16, " Blöcke", () -> c.chunkVertical, v -> c.chunkVertical = v));
                l.add(header("Verhalten"));
                l.add(toggle("Sektionslinien", "Waagrechte Linien alle 16 Blöcke im aktuellen Chunk.", () -> c.chunkSections, v -> c.chunkSections = v));
                l.add(toggle("Mit Entfernung ausblenden", "Linien werden weiter weg blasser.", () -> c.chunkFade, v -> c.chunkFade = v));
                l.add(toggle("Durch Blöcke sichtbar", "Linien auch hinter Wänden zeigen.", () -> c.chunkThroughWalls, v -> c.chunkThroughWalls = v));
                l.add(toggle("Aktuellen Chunk hervorheben", "Dein Chunk wird etwas heller gezeichnet.", () -> c.chunkHighlight, v -> c.chunkHighlight = v));
            }
            default -> buildStats(l);
        }
        return l;
    }

    private void buildStats(List<Row> l) {
        Data d = Data.I;
        Row cards = row(CARDS, "", "");
        cards.cards = new String[][]{
                {"Ancient gesamt", String.valueOf(d.totalAncient)},
                {"Session", String.valueOf(Stats.sessionAncient)},
                {"RTPs", Stats.rtpSession + " (" + Ui.d1(Stats.rtpPerHour()) + "/h)"}
        };
        l.add(cards);
        l.add(text("Ø Ancient pro Spot: " + Ui.d1(d.avgAncientPerSpot()) + "   |   Spots im Log: " + d.spots.size(), 0xC8D0DA));
        l.add(text("Kalibrierung: " + Calibration.samples() + "/" + Config.CALIB_NEEDED
                + (Calibration.done() ? " (fertig)" : " (läuft)"), Calibration.done() ? 0x55FF55 : 0xFFAA00));

        l.add(header("Ancient pro Spot nach Bewertung"));
        Map<RatingLevel, Data.LevelStat> stats = d.levelStats();
        double max = 0;
        for (Data.LevelStat st : stats.values()) max = Math.max(max, st.avgAncient);
        final double maxF = max;
        for (Map.Entry<RatingLevel, Data.LevelStat> e : stats.entrySet()) {
            Row r = row(BARLEVEL, e.getKey().label, "");
            r.rgb = e.getKey().color;
            Data.LevelStat st = e.getValue();
            r.frac = () -> maxF > 0 ? (float) (st.avgAncient / maxF) : 0f;
            r.valueText = () -> st.n == 0 ? "–" : "Ø " + Ui.d1(st.avgAncient) + " (" + st.n + " Spots)";
            l.add(r);
        }

        l.add(header("Gang an der Chunkgrenze"));
        double[] bs = d.borderStats();
        l.add(text("An der Grenze: " + (bs[0] == 0 ? "–" : "Ø " + Ui.d1(bs[1]) + " (" + (int) bs[0] + " Spots)")
                + "   |   Sonst: " + (bs[2] == 0 ? "–" : "Ø " + Ui.d1(bs[3]) + " (" + (int) bs[2] + " Spots)"), 0xC8D0DA));

        l.add(header("Letzte Spots"));
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM. HH:mm", Locale.ROOT);
        int shown = 0;
        for (int i = d.spots.size() - 1; i >= 0 && shown < 8; i--, shown++) {
            Data.Spot s = d.spots.get(i);
            RatingLevel lv = RatingLevel.valueOf(s.level);
            l.add(text(fmt.format(new Date(s.time)) + "  " + lv.label + " (" + Math.round(s.pct) + " %)  "
                    + s.ancient + " Ancient / " + Ui.time(s.seconds) + (s.border ? "  [Grenze]" : ""), lv.color));
        }
        if (d.spots.isEmpty()) l.add(text("Noch keine Spots – nach einem /rtp und einem Scan erscheinen sie hier.", 0x8A93A0));
        l.add(button("Spot-Log leeren", "Löscht alle gespeicherten Spots.", () -> {
            Data.I.clearSpots();
            Ui.chat("Spot-Log geleert.");
            rows = build(page);
            scroll = 0;
        }));
    }

    // ------------------------------------------------------------------ Zeichnen

    private int maxScroll(int viewportH) {
        int sum = 0;
        int idx = rows.size();
        while (idx > 0 && sum + rows.get(idx - 1).height() <= viewportH) {
            idx--;
            sum += rows.get(idx).height();
        }
        return idx;
    }

    private String fit(String s, int maxW) {
        Font f = this.font;
        if (f.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && f.width(t + "...") > maxW) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.mouseHandler.isLeftPressed();
        boolean pressed = down && !wasDown;
        boolean released = !down && wasDown;
        wasDown = down;
        mouseXCache = mx;
        mouseYCache = my;
        if (rows == null) rows = build(page);

        Font f = this.font;
        int accent = Theme.accent();
        int pw = Math.min(this.width - 16, 470);
        int ph = Math.min(this.height - 16, 330);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        // Rahmen, Hintergrund, Kopfzeile
        Theme.rr(g, px - 1, py - 1, pw + 2, ph + 2, 0xFF2B313A);
        Theme.rr(g, px, py, pw, ph, 0xF2111418);
        Theme.hgrad(g, px + 1, py, pw - 2, 2, accent, Theme.mix(accent, 0x111418, 0.85f));
        g.drawString(f, "§lANCIENT REMINDER", px + 10, py + 11, Theme.argb(accent), true);
        String ver = "v" + VERSION;
        g.drawString(f, ver, px + pw - 10 - f.width(ver), py + 11, 0xFF6B7480, true);

        // Seitenleiste
        int sy = py + HEAD_H;
        g.fill(px, sy, px + SIDE_W, py + ph - 1, 0xF00B0E12);
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            int ey = sy + 8 + i * 22;
            boolean hov = Theme.inside(mx, my, px + 4, ey, SIDE_W - 8, 20);
            boolean act = i == page;
            if (act) {
                Theme.rr(g, px + 4, ey, SIDE_W - 8, 20, Theme.withAlpha(accent, 0x30));
                g.fill(px + 4, ey + 3, px + 6, ey + 17, Theme.argb(accent));
            } else if (hov) {
                Theme.rr(g, px + 4, ey, SIDE_W - 8, 20, 0x18FFFFFF);
            }
            g.drawString(f, PAGE_NAMES[i], px + 14, ey + 6, act ? 0xFFFFFFFF : 0xFFA0A8B4, true);
            if (pressed && hov && i != page) {
                page = i;
                scroll = 0;
                rows = build(i);
                pressed = false;
            }
        }

        // Inhalt
        int cx = px + SIDE_W + 12;
        int cw = pw - SIDE_W - 24;
        int cy = py + HEAD_H + 8;
        g.drawString(f, "§l" + PAGE_NAMES[page], cx, cy, 0xFFFFFFFF, true);
        int top = cy + 16;
        int bottom = py + ph - FOOT_H - 2;
        int maxS = maxScroll(bottom - top);
        scroll = Math.max(0, Math.min(scroll, maxS));

        String hint = null;
        int y = top;
        for (int i = scroll; i < rows.size(); i++) {
            Row r = rows.get(i);
            int h = r.height();
            if (y + h > bottom) break;
            boolean hov = r.interactive() && Theme.inside(mx, my, cx - 2, y, cw + 4, h);
            if (hov) {
                Theme.rr(g, cx - 2, y, cw + 4, h, 0x14FFFFFF);
                if (!r.help.isEmpty()) hint = r.help;
            }
            drawRow(g, r, cx, y, cw, accent);
            if (pressed && hov) {
                press(r, mx, cx, y, cw);
                pressed = false;
            }
            y += h;
        }

        // Scrollleiste und Pfeile
        if (maxS > 0) {
            int ax = cx + cw - 26;
            boolean upHov = Theme.inside(mx, my, ax, cy - 2, 12, 12);
            boolean dnHov = Theme.inside(mx, my, ax + 14, cy - 2, 12, 12);
            Theme.rr(g, ax, cy - 2, 12, 12, upHov ? Theme.withAlpha(accent, 0x70) : 0xFF22262D);
            Theme.rr(g, ax + 14, cy - 2, 12, 12, dnHov ? Theme.withAlpha(accent, 0x70) : 0xFF22262D);
            g.drawString(f, "^", ax + 4, cy, 0xFFFFFFFF, false);
            g.drawString(f, "v", ax + 18, cy, 0xFFFFFFFF, false);
            if (pressed && upHov) scroll = Math.max(0, scroll - 1);
            if (pressed && dnHov) scroll = Math.min(maxS, scroll + 1);
            int barH = bottom - top;
            int thumbH = Math.max(16, barH / (maxS + 2));
            int thumbY = top + (barH - thumbH) * scroll / maxS;
            g.fill(px + pw - 6, top, px + pw - 4, bottom, 0x22FFFFFF);
            g.fill(px + pw - 6, thumbY, px + pw - 4, thumbY + thumbH, Theme.withAlpha(accent, 0xA0));
        }

        // Fußzeile
        int fy = py + ph - FOOT_H;
        g.fill(px + SIDE_W, fy, px + pw - 1, py + ph - 1, 0xFF0D1014);
        String foot = hint != null ? hint : "Mausrad: scrollen   |   Esc: schließen";
        g.drawString(f, fit(foot, pw - SIDE_W - 20), px + SIDE_W + 12, fy + 4, 0xFF8A93A0, true);

        // Slider ziehen
        if (dragRow != null) {
            if (down) setSlider(dragRow, mx);
            if (released || !down) {
                dragRow = null;
                Config.save();
            }
        }
    }

    private void drawRow(GuiGraphics g, Row r, int x, int y, int w, int accent) {
        Font f = this.font;
        int ty = y + (22 - f.lineHeight) / 2 + 1;
        int textCol = 0xFFE6EAF0;
        switch (r.kind) {
            case HEADER -> {
                String t = r.label.toUpperCase(Locale.ROOT);
                g.drawString(f, t, x + 2, y + 9, Theme.argb(accent), true);
                g.fill(x + 8 + f.width(t), y + 13, x + w, y + 14, 0x30FFFFFF);
            }
            case TOGGLE -> {
                g.drawString(f, r.label, x + 4, ty, textCol, true);
                drawPill(g, x + w - 34, y + 5, r.bget.getAsBoolean(), accent);
            }
            case SLIDER -> {
                g.drawString(f, r.label, x + 4, ty, textCol, true);
                int tx = x + w - 6 - 46 - SL_W;
                int v = r.iget.getAsInt();
                float t = (v - r.min) / (float) Math.max(1, r.max - r.min);
                t = Math.max(0f, Math.min(1f, t));
                Theme.rr(g, tx, y + 9, SL_W, 4, 0xFF2A2F37);
                int fw = Math.round(SL_W * t);
                if (fw > 0) Theme.rr(g, tx, y + 9, Math.max(3, fw), 4, Theme.argb(accent));
                Theme.rr(g, tx + fw - 2, y + 6, 4, 10, 0xFFFFFFFF);
                g.drawString(f, v + r.suffix, tx + SL_W + 8, ty, 0xFFB8C0CC, true);
            }
            case CHOICE -> {
                g.drawString(f, r.label, x + 4, ty, textCol, true);
                int total = 0;
                for (String n : r.names) total += f.width(n) + 10;
                int sx = x + w - 6 - total;
                Theme.rr(g, sx, y + 3, total, 16, 0xFF22262D);
                int cur = Math.floorMod(r.iget.getAsInt(), r.names.length);
                int cxp = sx;
                for (int i = 0; i < r.names.length; i++) {
                    int sw = f.width(r.names[i]) + 10;
                    if (i == cur) Theme.rr(g, cxp, y + 3, sw, 16, Theme.argb(accent));
                    g.drawString(f, r.names[i], cxp + 5, y + 3 + (16 - f.lineHeight) / 2 + 1, i == cur ? 0xFF101318 : 0xFF9AA3AF, false);
                    cxp += sw;
                }
            }
            case COLORS -> {
                g.drawString(f, r.label, x + 4, ty, textCol, true);
                int n = r.palette.length;
                int sx = x + w - 6 - n * 14;
                int cur = Math.floorMod(r.iget.getAsInt(), n);
                for (int i = 0; i < n; i++) {
                    int bx = sx + i * 14;
                    if (i == cur) g.fill(bx - 1, y + 4, bx + 13, y + 18, 0xFFFFFFFF);
                    g.fill(bx, y + 5, bx + 12, y + 17, Theme.argb(r.palette[i]));
                }
            }
            case BUTTON -> {
                int bw = f.width(r.label) + 24;
                int bx = x + w - 6 - bw;
                boolean hov = Theme.inside(mouseXCache, mouseYCache, bx, y + 3, bw, 16);
                Theme.rr(g, bx, y + 3, bw, 16, hov ? Theme.withAlpha(accent, 0x90) : 0xFF2A2F37);
                g.drawString(f, r.label, bx + 12, y + 3 + (16 - f.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
                if (!r.help.isEmpty()) g.drawString(f, fit(r.help, w - bw - 20), x + 4, ty, 0xFF6B7480, false);
            }
            case TARGET -> {
                g.fill(x + 4, y + 7, x + 10, y + 15, Theme.argb(r.target.color));
                g.drawString(f, r.label, x + 15, ty, textCol, true);
                int p2 = x + w - 34;
                int l2 = p2 - 4 - f.width("Tracer");
                int p1 = l2 - 12 - 28;
                int l1 = p1 - 4 - f.width("Suche");
                g.drawString(f, "Suche", l1, ty, 0xFF8A93A0, false);
                drawPill(g, p1, y + 5, r.target.cfg().enabled, accent);
                g.drawString(f, "Tracer", l2, ty, 0xFF8A93A0, false);
                drawPill(g, p2, y + 5, r.target.cfg().tracer, accent);
            }
            case PROGRESS -> {
                g.drawString(f, r.label, x + 4, ty, textCol, true);
                int bw = 150;
                int bx = x + w - 6 - 40 - bw;
                Theme.rr(g, bx, y + 9, bw, 4, 0xFF2A2F37);
                float fr = Math.max(0f, Math.min(1f, r.frac.get()));
                int fw = Math.round(bw * fr);
                if (fw > 0) Theme.rr(g, bx, y + 9, Math.max(3, fw), 4, Theme.argb(r.rgb));
                g.drawString(f, r.valueText.get(), bx + bw + 8, ty, 0xFFB8C0CC, true);
            }
            case TEXT -> g.drawString(f, fit(r.text, w - 4), x + 4, y + 2, Theme.argb(r.rgb), true);
            case CARDS -> {
                int cwid = (w - 8) / 3;
                for (int i = 0; i < r.cards.length; i++) {
                    int bx = x + i * (cwid + 4);
                    Theme.rr(g, bx, y, cwid, 40, 0xFF1A1E24);
                    g.fill(bx + 1, y + 4, bx + 3, y + 36, Theme.argb(accent));
                    g.drawString(f, fit(r.cards[i][0], cwid - 14), bx + 8, y + 6, 0xFF8A93A0, true);
                    g.drawString(f, "§l" + fit(r.cards[i][1], cwid - 14), bx + 8, y + 22, 0xFFFFFFFF, true);
                }
            }
            case BARLEVEL -> {
                g.drawString(f, r.label, x + 4, y + 4, Theme.argb(r.rgb), true);
                int bx = x + 78;
                int bw = Math.max(40, w - 78 - 120);
                Theme.rr(g, bx, y + 6, bw, 5, 0xFF2A2F37);
                int fw = Math.round(bw * Math.max(0f, Math.min(1f, r.frac.get())));
                if (fw > 0) Theme.rr(g, bx, y + 6, Math.max(3, fw), 5, Theme.argb(r.rgb));
                g.drawString(f, r.valueText.get(), bx + bw + 8, y + 4, 0xFFB8C0CC, true);
            }
            default -> {
            }
        }
    }

    private int mouseXCache;
    private int mouseYCache;

    private void drawPill(GuiGraphics g, int x, int y, boolean on, int accent) {
        Theme.rr(g, x, y, 28, 12, on ? Theme.argb(accent) : 0xFF3A3F47);
        int kx = on ? x + 28 - 11 : x + 1;
        Theme.rr(g, kx, y + 1, 10, 10, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ Eingabe

    private void press(Row r, int mx, int x, int y, int w) {
        Font f = this.font;
        switch (r.kind) {
            case TOGGLE -> {
                r.bset.accept(!r.bget.getAsBoolean());
                Config.save();
            }
            case SLIDER -> {
                int tx = x + w - 6 - 46 - SL_W;
                if (mx >= tx - 6 && mx <= tx + SL_W + 6) {
                    dragRow = r;
                    dragTrackX = tx;
                    setSlider(r, mx);
                }
            }
            case CHOICE -> {
                int total = 0;
                for (String n : r.names) total += f.width(n) + 10;
                int cxp = x + w - 6 - total;
                for (int i = 0; i < r.names.length; i++) {
                    int sw = f.width(r.names[i]) + 10;
                    if (mx >= cxp && mx < cxp + sw) {
                        r.iset.accept(i);
                        Config.save();
                        break;
                    }
                    cxp += sw;
                }
            }
            case COLORS -> {
                int n = r.palette.length;
                int sx = x + w - 6 - n * 14;
                int idx = (mx - sx) / 14;
                if (mx >= sx && idx >= 0 && idx < n) {
                    r.iset.accept(idx);
                    Config.save();
                }
            }
            case BUTTON -> {
                int bw = f.width(r.label) + 24;
                int bx = x + w - 6 - bw;
                if (mx >= bx && mx < bx + bw) r.run.run();
            }
            case TARGET -> {
                int p2 = x + w - 34;
                int l2 = p2 - 4 - f.width("Tracer");
                int p1 = l2 - 12 - 28;
                if (mx >= p2 - 2 && mx < p2 + 30) {
                    r.target.cfg().tracer = !r.target.cfg().tracer;
                    Config.save();
                } else if (mx >= p1 - 2 && mx < p1 + 30) {
                    r.target.cfg().enabled = !r.target.cfg().enabled;
                    Config.save();
                }
            }
            default -> {
            }
        }
    }

    private void setSlider(Row r, int mx) {
        double t = Math.max(0.0, Math.min(1.0, (mx - dragTrackX) / (double) SL_W));
        int steps = (int) Math.round(t * (r.max - r.min) / r.step);
        int v = Math.max(r.min, Math.min(r.max, r.min + steps * r.step));
        r.iset.accept(v);
    }

    /** Mausrad zum Scrollen (bewusst ohne @Override, falls sich die Signatur ändert). */
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY > 0) scroll = Math.max(0, scroll - 1);
        else if (scrollY < 0) scroll = scroll + 1;
        return true;
    }

    @Override
    public void onClose() {
        Config.save();
        this.minecraft.setScreen(parent);
    }
}
