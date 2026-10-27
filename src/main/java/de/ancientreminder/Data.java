package de.ancientreminder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dauerhafte Statistik: Gesamt-Ancient, RTP-Zähler und Spot-Log. */
public final class Data {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_SPOTS = 500;
    public static Data I = new Data();
    private static boolean dirty;

    public static class Spot {
        public long time;
        public int x;
        public int z;
        public String level = "";
        public double pct;
        public int ancient;
        public int seconds;
        public boolean border;
    }

    public static class LevelStat {
        public int n;
        public double avgAncient;
        public double avgMinutes;
    }

    public long totalAncient;
    public long totalRtp;
    public List<Spot> spots = new ArrayList<>();

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("ancientreminder_data.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) I = d;
                }
            }
        } catch (Exception e) {
            System.err.println("[AncientReminder] Daten konnten nicht geladen werden: " + e);
        }
        if (I.spots == null) I.spots = new ArrayList<>();
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void saveIfDirty() {
        if (dirty) save();
    }

    public static void save() {
        dirty = false;
        try {
            Files.writeString(file(), GSON.toJson(I));
        } catch (IOException e) {
            System.err.println("[AncientReminder] Daten konnten nicht gespeichert werden: " + e);
        }
    }

    public void addSpot(Spot s) {
        spots.add(s);
        while (spots.size() > MAX_SPOTS) spots.remove(0);
        markDirty();
    }

    public void clearSpots() {
        spots.clear();
        totalRtp = 0;
        markDirty();
        save();
    }

    public Map<RatingLevel, LevelStat> levelStats() {
        Map<RatingLevel, LevelStat> map = new LinkedHashMap<>();
        for (RatingLevel l : RatingLevel.values()) {
            LevelStat st = new LevelStat();
            double ancient = 0;
            double secs = 0;
            for (Spot s : spots) {
                if (l.name().equals(s.level)) {
                    st.n++;
                    ancient += s.ancient;
                    secs += s.seconds;
                }
            }
            if (st.n > 0) {
                st.avgAncient = ancient / st.n;
                st.avgMinutes = secs / st.n / 60.0;
            }
            map.put(l, st);
        }
        return map;
    }

    /** {Anzahl an Grenze, Ø an Grenze, Anzahl sonst, Ø sonst} */
    public double[] borderStats() {
        double nb = 0;
        double sb = 0;
        double no = 0;
        double so = 0;
        for (Spot s : spots) {
            if (s.border) {
                nb++;
                sb += s.ancient;
            } else {
                no++;
                so += s.ancient;
            }
        }
        return new double[]{nb, nb == 0 ? 0 : sb / nb, no, no == 0 ? 0 : so / no};
    }

    public double avgAncientPerSpot() {
        if (spots.isEmpty()) return 0;
        double sum = 0;
        for (Spot s : spots) sum += s.ancient;
        return sum / spots.size();
    }

    /** Level mit dem besten Ancient-Durchschnitt (mindestens 2 Spots), sonst null. */
    public RatingLevel bestLevel() {
        RatingLevel best = null;
        double bestAvg = -1;
        for (Map.Entry<RatingLevel, LevelStat> e : levelStats().entrySet()) {
            if (e.getValue().n >= 2 && e.getValue().avgAncient > bestAvg) {
                bestAvg = e.getValue().avgAncient;
                best = e.getKey();
            }
        }
        return best;
    }
}
