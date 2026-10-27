package de.ancientreminder;

import de.ancientreminder.render.ShapeBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Ablauf nach einem /rtp im Nether:
 * Höhen-HUD bis Y=15, dann Umgebungs-Scan (jede Minute neu), Bewertung (gesamt und pro Chunk),
 * Empfehlung, Spot-Log, Kalibrierung und gelegentliche Gang-Markierung.
 */
public final class NetherSession {
    public enum Phase { IDLE, DESCENDING, AT_LEVEL }

    public record Rating(RatingLevel level, double pct, double fraction, int loaded, long time) {}

    public static Phase phase = Phase.IDLE;
    public static Rating last;
    public static String recommendation = "";
    public static TunnelFinder.Tunnel tunnel;
    public static int startY;
    public static final LayerScanner SCANNER = new LayerScanner();

    // Chunk-Bewertung
    public static Map<Long, RatingLevel> chunkLevels = new HashMap<>();
    public static boolean hasBest;
    public static int bestCx;
    public static int bestCz;
    public static double bestPct;

    private static final Random RNG = new Random();
    private static boolean rtpPending;
    private static long rtpPendingAt;
    private static Vec3 lastPos;
    private static ResourceKey<Level> lastDim;
    private static long nextScanAt;
    private static long sessionStartMs;
    private static int ancientAtStart;
    private static int startX;
    private static int startZ;
    private static boolean sampled;
    private static boolean announced;
    private static boolean tunnelRolled;
    private static boolean spotBorder;
    private static long tunnelCooldownUntil;

    private NetherSession() {}

    /** Wird aufgerufen, wenn du einen Befehl abschickst (ohne Slash). */
    public static void onCommand(String command) {
        String c = command.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("/")) c = c.substring(1);
        String rtp = Config.I.rtpCommand.toLowerCase(Locale.ROOT);
        if (c.equals(rtp) || c.startsWith(rtp + " ")) {
            rtpPending = true;
            rtpPendingAt = System.currentTimeMillis();
        }
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel lvl = mc.level;
        if (p == null || lvl == null) return;
        Config c = Config.I;
        long now = System.currentTimeMillis();

        ResourceKey<Level> dim = lvl.dimension();
        boolean nether = dim == Level.NETHER;
        if (dim != lastDim) {
            lastDim = dim;
            lastPos = null;
            if (!nether) {
                endSession();
            } else if (rtpPending && c.netherEnabled) {
                startSession(p);
            }
        }
        if (!nether) {
            if (rtpPending && now - rtpPendingAt > 30000) rtpPending = false;
            return;
        }
        if (!c.netherEnabled) {
            if (phase != Phase.IDLE) endSession();
            lastPos = p.position();
            return;
        }
        if (rtpPending && now - rtpPendingAt > 30000) rtpPending = false;

        Vec3 pos = p.position();
        if (lastPos != null && pos.distanceTo(lastPos) > c.jumpDistance && (rtpPending || c.autoDetectJump)) {
            startSession(p);
        }
        lastPos = pos;

        if (phase == Phase.DESCENDING && p.getBlockY() <= c.targetY) {
            phase = Phase.AT_LEVEL;
            nextScanAt = 0;
            Ui.chat("Y" + c.targetY + " erreicht – scanne die Umgebung ...");
        }
        if (phase == Phase.AT_LEVEL) {
            boolean inBand = Math.abs(p.getY() - c.targetY) <= 3.5;
            if (inBand && !SCANNER.isRunning() && now >= nextScanAt) {
                SCANNER.start(lvl, p.getBlockX() >> 4, p.getBlockZ() >> 4, c.scanChunks, c.layerMinY, c.layerMaxY);
            }
            LayerScanner.Result r = SCANNER.tick(8, lvl);
            if (r != null) onResult(r, lvl, p);
            checkTunnelArrival(p);
        }
    }

    private static void startSession(LocalPlayer p) {
        Config c = Config.I;
        endSession();
        phase = Phase.DESCENDING;
        rtpPending = false;
        sessionStartMs = System.currentTimeMillis();
        ancientAtStart = Stats.sessionAncient;
        startX = p.getBlockX();
        startZ = p.getBlockZ();
        startY = p.getBlockY();
        sampled = false;
        announced = false;
        tunnelRolled = false;
        spotBorder = false;
        last = null;
        recommendation = "";
        tunnel = null;
        clearChunks();
        Stats.rtpSession++;
        Data.I.totalRtp++;
        Data.markDirty();
        if (p.getBlockY() <= c.targetY) {
            phase = Phase.AT_LEVEL;
            nextScanAt = 0;
        }
    }

    public static void endSession() {
        if (phase == Phase.IDLE) return;
        long now = System.currentTimeMillis();
        long secs = (now - sessionStartMs) / 1000;
        if (last != null && secs >= 10) {
            Data.Spot s = new Data.Spot();
            s.time = now;
            s.x = startX;
            s.z = startZ;
            s.level = last.level().name();
            s.pct = last.pct();
            s.ancient = Stats.sessionAncient - ancientAtStart;
            s.seconds = (int) secs;
            s.border = spotBorder;
            Data.I.addSpot(s);
        }
        phase = Phase.IDLE;
        SCANNER.abort();
        tunnel = null;
        last = null;
        clearChunks();
    }

    private static void clearChunks() {
        chunkLevels = new HashMap<>();
        hasBest = false;
    }

    private static void onResult(LayerScanner.Result r, ClientLevel lvl, LocalPlayer p) {
        Config c = Config.I;
        long now = System.currentTimeMillis();
        if (r.loaded() < 4) {
            nextScanAt = now + 8000;
            Ui.bar("§eZu wenige Chunks geladen für den Scan – neuer Versuch gleich");
            return;
        }
        double f = r.fraction();
        if (!sampled) {
            sampled = true;
            if (!Calibration.done()) {
                c.calib.add(f);
                Config.save();
                Ui.chat("Kalibrierung: Messung " + Calibration.samples() + "/" + Config.CALIB_NEEDED + " gespeichert"
                        + (Calibration.done() ? " – fertig!" : ""));
            }
        }
        double pct = Calibration.percent(f);
        RatingLevel level = Calibration.level(pct);
        RatingLevel prev = last == null ? null : last.level();
        last = new Rating(level, pct, f, r.loaded(), now);
        nextScanAt = now + c.rescanSeconds * 1000L;
        recommendation = recommend(level);
        rateChunks(r, p);

        if (!announced || prev != level) {
            if (c.spotMessage) {
                Ui.chat("Spot: " + code(level) + level.label.toUpperCase(Locale.ROOT) + "§f (" + Math.round(pct)
                        + " % abgebaut)" + (c.recommend ? " – " + recommendation : ""));
            }
            if (c.spotSound) Ui.ding();
            announced = true;
        }
        handleTunnel(level, lvl, p, now);
    }

    /** Bewertet jeden gemessenen Chunk einzeln und merkt sich den besten. */
    private static void rateChunks(LayerScanner.Result r, LocalPlayer p) {
        Map<Long, RatingLevel> levels = new HashMap<>();
        int pcx = p.getBlockX() >> 4;
        int pcz = p.getBlockZ() >> 4;
        boolean found = false;
        double bp = 101;
        long bd = Long.MAX_VALUE;
        int bx = 0;
        int bz = 0;
        for (Map.Entry<Long, Double> e : r.chunks().entrySet()) {
            double cp = Calibration.percent(e.getValue());
            long k = e.getKey();
            levels.put(k, Calibration.level(cp));
            int cx = LayerScanner.keyX(k);
            int cz = LayerScanner.keyZ(k);
            long d = (long) (cx - pcx) * (cx - pcx) + (long) (cz - pcz) * (cz - pcz);
            if (!found || cp < bp - 0.5 || (Math.abs(cp - bp) <= 0.5 && d < bd)) {
                found = true;
                bp = cp;
                bd = d;
                bx = cx;
                bz = cz;
            }
        }
        chunkLevels = levels;
        hasBest = found;
        bestCx = bx;
        bestCz = bz;
        bestPct = bp;
    }

    public static RatingLevel bestChunkLevel() {
        return hasBest ? chunkLevels.get(LayerScanner.key(bestCx, bestCz)) : null;
    }

    /** Textzeile mit Richtung zum besten Chunk. */
    public static String bestChunkInfo(Vec3 pos) {
        if (!hasBest) return "";
        RatingLevel lv = bestChunkLevel();
        if (lv == null) return "";
        int pcx = Mth.floor(pos.x) >> 4;
        int pcz = Mth.floor(pos.z) >> 4;
        if (bestCx == pcx && bestCz == pcz) return "Bester Chunk: hier (" + lv.label + ")";
        double dx = bestCx * 16.0 + 8 - pos.x;
        double dz = bestCz * 16.0 + 8 - pos.z;
        long chunks = Math.max(1, Math.round(Math.sqrt(dx * dx + dz * dz) / 16.0));
        return "Bester Chunk: " + chunks + " Chunks " + Ui.compass(dx, dz) + " (" + lv.label + ", " + Math.round(bestPct) + " %)";
    }

    private static void handleTunnel(RatingLevel level, ClientLevel lvl, LocalPlayer p, long now) {
        Config c = Config.I;
        if (level != RatingLevel.VERY_GOOD || !c.tunnelEnabled || tunnel != null || tunnelRolled) return;
        if (now < tunnelCooldownUntil) return;
        tunnelRolled = true;
        if (RNG.nextInt(100) >= c.tunnelChance) return;
        TunnelFinder.Tunnel t = TunnelFinder.find(lvl, p);
        if (t == null) {
            Ui.chat(c.tunnelBorder == 2
                    ? "Sehr guter Spot, aber kein sicherer Gang an einer Chunkgrenze gefunden."
                    : "Sehr guter Spot, aber ich habe keinen sicheren Gang gefunden (Lava in der Nähe?)");
            return;
        }
        tunnel = t;
        spotBorder = t.border();
        tunnelCooldownUntil = now + c.tunnelCooldownMin * 60000L;
        Ui.chat("§bGang markiert§f – " + tunnelInfo(p.position()) + (t.border() ? " (an der Chunkgrenze)" : "")
                + ". Bau dich dort ein und platziere TNT.");
        Ui.ding();
    }

    private static void checkTunnelArrival(LocalPlayer p) {
        if (tunnel != null && tunnel.box().inflate(0.4).contains(p.position())) {
            tunnel = null;
            Ui.chat("Gang erreicht.");
        }
    }

    public static String tunnelInfo(Vec3 from) {
        if (tunnel == null) return "";
        Vec3 c = tunnel.box().getCenter();
        double dx = c.x - from.x;
        double dz = c.z - from.z;
        long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
        return dist + " Blöcke " + Ui.compass(dx, dz);
    }

    public static void clearTunnel() {
        tunnel = null;
    }

    public static void forceRescan() {
        nextScanAt = 0;
    }

    /** Sekunden bis zum nächsten Scan, -1 wenn gerade gescannt wird. */
    public static long secondsToNextScan() {
        if (SCANNER.isRunning()) return -1;
        return Math.max(0, (nextScanAt - System.currentTimeMillis()) / 1000);
    }

    public static void drawTunnel(ShapeBatch xray) {
        if (tunnel == null) return;
        AABB b = tunnel.box();
        xray.filledBox(b, 0x55FFFF, 0.12f);
        xray.boxOutline(b, 0.03, 0x55FFFF, 0.9f);
    }

    private static String code(RatingLevel l) {
        return switch (l) {
            case VERY_GOOD -> "§a";
            case GOOD -> "§2";
            case OK -> "§e";
            case BAD -> "§6";
            case GONE -> "§c";
        };
    }

    /** Empfehlung nach dem Scan, ergänzt durch deine bisherigen Spot-Daten. */
    public static String recommend(RatingLevel level) {
        Data.LevelStat st = Data.I.levelStats().get(level);
        boolean known = st != null && st.n >= 3;
        String avg = known ? " (Ø " + Ui.d1(st.avgAncient) + " Ancient/Spot, n=" + st.n + ")" : "";
        return switch (level) {
            case VERY_GOOD, GOOD -> {
                if (known && st.n >= 5 && st.avgAncient < 0.5) {
                    yield "Level brachte bisher kaum Ancient – im Zweifel neu /rtp" + avg;
                }
                yield "BLEIBEN – guter Spot" + avg;
            }
            case OK -> "Kurz testen, sonst neu /rtp" + avg;
            case BAD -> "Besser neu /rtp" + avg;
            case GONE -> "Neu /rtp – hier ist alles weg";
        };
    }

    public static long sessionSeconds() {
        return phase == Phase.IDLE ? 0 : (System.currentTimeMillis() - sessionStartMs) / 1000;
    }

    public static int ancientThisSpot() {
        return Stats.sessionAncient - ancientAtStart;
    }
}
