package de.ancientreminder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wandelt den gemessenen Anteil offener Blöcke (Luft) auf Ancient-Höhe in "% abgebaut" um.
 * Unter Y=31 füllt Lava natürliche Höhlen, Luft dort stammt also fast immer von Spielern.
 * Aus den Kalibrierungs-Messungen (10 RTPs) werden Grundwert und Skala gelernt.
 */
public final class Calibration {
    private Calibration() {}

    public static int samples() {
        return Config.I.calib.size();
    }

    public static boolean done() {
        return samples() >= Config.CALIB_NEEDED;
    }

    public static void reset() {
        Config.I.calib.clear();
        Config.save();
    }

    private static List<Double> sorted() {
        List<Double> s = new ArrayList<>(Config.I.calib);
        Collections.sort(s);
        return s;
    }

    /** Anteil offener Blöcke, der als "unberührt" gilt. */
    public static double baseline() {
        List<Double> s = sorted();
        if (s.size() < 3) return 0.0;
        return s.get((int) Math.floor((s.size() - 1) * 0.25));
    }

    /** Anteil offener Blöcke (über dem Grundwert), der 100 % entspricht. */
    public static double fullScale() {
        List<Double> s = sorted();
        if (s.size() < 3) return 0.10;
        double p90 = s.get((int) Math.ceil((s.size() - 1) * 0.9));
        return Math.max(0.08, Math.min(0.40, p90 - baseline()));
    }

    public static double percent(double fraction) {
        double p = (fraction - baseline()) / fullScale() * 100.0;
        return Math.max(0, Math.min(100, p));
    }

    public static RatingLevel level(double pct) {
        Config c = Config.I;
        if (pct < c.lvlVeryGood) return RatingLevel.VERY_GOOD;
        if (pct < c.lvlGood) return RatingLevel.GOOD;
        if (pct < c.lvlOk) return RatingLevel.OK;
        if (pct < c.lvlBad) return RatingLevel.BAD;
        return RatingLevel.GONE;
    }
}
