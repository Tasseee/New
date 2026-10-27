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

/** Alle Einstellungen. Wird als config/ancientreminder.json gespeichert. */
public final class Config {
    public static final int CALIB_NEEDED = 10;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config I = new Config();

    public static class TargetCfg {
        public boolean enabled;
        public boolean tracer = true;
    }

    public static class WidgetCfg {
        public boolean enabled = true;
        public int x = -1;
        public int y = -1;
        public int color = 0;
    }

    // ---- Erz-Finder ----
    public boolean oreEnabled = true;
    public int oreRadius = 32;
    public int oreScanTicks = 20;
    public boolean oreMessage = true;
    public boolean oreSound = true;
    public boolean oreItems = true;
    public boolean tracers = true;
    public int maxTracers = 20;
    public int tracerWidth = 2;
    public int tracerStart = 0; // 0 = Fadenkreuz, 1 = Füße
    public int tracerAlpha = 70;
    public boolean oreBoxes = true;
    public Map<String, TargetCfg> targets = new LinkedHashMap<>();

    // ---- HUD ----
    public boolean hudEnabled = true;
    public boolean hudBackground = true;
    public int hudBgAlpha = 55;
    public int uiAccent = 0;
    public int statsIntervalMin = 10;
    public Map<String, WidgetCfg> widgets = new LinkedHashMap<>();

    // ---- Nether-Scanner ----
    public boolean netherEnabled = true;
    public String rtpCommand = "rtp";
    public boolean autoDetectJump = true;
    public int jumpDistance = 48;
    public int targetY = 15;
    public int scanChunks = 5;
    public int rescanSeconds = 60;
    public int layerMinY = 8;
    public int layerMaxY = 22;
    public boolean recommend = true;
    public boolean spotMessage = true;
    public boolean spotSound = true;
    public int lvlVeryGood = 10;
    public int lvlGood = 25;
    public int lvlOk = 50;
    public int lvlBad = 75;
    public List<Double> calib = new ArrayList<>();

    // ---- Gang-Markierung ----
    public boolean tunnelEnabled = true;
    public int tunnelChance = 50;
    public int tunnelCooldownMin = 10;
    public int tunnelLength = 8;
    public int tunnelLavaSafety = 4;
    public int tunnelMaxDist = 24;
    public int tunnelBorder = 1; // 0 = Aus, 1 = Bevorzugen, 2 = Nur dort

    // ---- Warnungen ----
    public boolean lavaWarn = true;
    public int lavaAhead = 3;
    public boolean lavaNearby = true;
    public boolean lavaSound = true;
    public boolean fireWarn = true;
    public int fireWarnSeconds = 30;

    // ---- Chunkgrenzen ----
    public boolean chunkEnabled = false;
    public int chunkRange = 2;
    public int chunkColor = 1;
    public int chunkAlpha = 25;
    public int chunkWidth = 1;
    public int chunkVertical = 32;
    public boolean chunkSections = true;
    public boolean chunkFade = true;
    public boolean chunkThroughWalls = false;
    public boolean chunkHighlight = true;
    public boolean chunkRating = true;

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("ancientreminder.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    Config c = GSON.fromJson(r, Config.class);
                    if (c != null) I = c;
                }
            }
        } catch (Exception e) {
            System.err.println("[AncientReminder] Config konnte nicht geladen werden: " + e);
        }
        I.normalize();
        save();
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(I));
        } catch (IOException e) {
            System.err.println("[AncientReminder] Config konnte nicht gespeichert werden: " + e);
        }
    }

    private void normalize() {
        if (targets == null) targets = new LinkedHashMap<>();
        if (widgets == null) widgets = new LinkedHashMap<>();
        if (calib == null) calib = new ArrayList<>();
        for (Target t : Target.values()) {
            targets.computeIfAbsent(t.name(), k -> {
                TargetCfg c = new TargetCfg();
                c.enabled = t.defaultEnabled;
                return c;
            });
        }
        for (String id : Hud.IDS) widgets.computeIfAbsent(id, k -> new WidgetCfg());
        if (rtpCommand == null || rtpCommand.isBlank()) rtpCommand = "rtp";
        layerMinY = Math.max(0, Math.min(layerMinY, 120));
        layerMaxY = Math.max(layerMinY, Math.min(layerMaxY, 125));
    }
}
