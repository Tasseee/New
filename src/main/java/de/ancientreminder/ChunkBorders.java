package de.ancientreminder;

import de.ancientreminder.render.ShapeBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Dezente, einstellbare Chunkgrenzen, auf Wunsch nach Spot-Bewertung eingefärbt. */
public final class ChunkBorders {
    private ChunkBorders() {}

    public static void draw(Minecraft mc, Vec3 cam, ShapeBatch depth, ShapeBatch xray) {
        Config c = Config.I;
        if (!c.chunkEnabled || mc.level == null) return;
        ShapeBatch b = c.chunkThroughWalls ? xray : depth;

        int pcx = Mth.floor(cam.x) >> 4;
        int pcz = Mth.floor(cam.z) >> 4;
        int r = c.chunkRange;
        double v = c.chunkVertical;
        double y0 = cam.y - v;
        double y1 = cam.y + v;
        int rgb = Ui.color(c.chunkColor);
        float base = c.chunkAlpha / 100f;
        double w0 = 0.006 * c.chunkWidth;
        double maxD = (r + 1) * 16.0;

        boolean rated = c.chunkRating && mc.level.dimension() == Level.NETHER && !NetherSession.chunkLevels.isEmpty();

        if (rated) {
            double in = 0.06;
            for (int cx = pcx - r; cx <= pcx + r; cx++) {
                for (int cz = pcz - r; cz <= pcz + r; cz++) {
                    RatingLevel lv = NetherSession.chunkLevels.get(LayerScanner.key(cx, cz));
                    int col = lv != null ? lv.color : rgb;
                    double xa = cx * 16.0 + in;
                    double xb = cx * 16.0 + 16.0 - in;
                    double za = cz * 16.0 + in;
                    double zb = cz * 16.0 + 16.0 - in;
                    double d = Math.hypot(cam.x - (cx * 16.0 + 8), cam.z - (cz * 16.0 + 8));
                    float a = lv != null ? Math.max(base, 0.30f) : base;
                    if (c.chunkFade) a *= (float) Mth.clamp(1.0 - d / (maxD + 8), 0.15, 1.0);
                    double w = w0 * (1 + d / 32.0);
                    b.tube(xa, y0, za, xa, y1, za, w, col, a);
                    b.tube(xb, y0, za, xb, y1, za, w, col, a);
                    b.tube(xb, y0, zb, xb, y1, zb, w, col, a);
                    b.tube(xa, y0, zb, xa, y1, zb, w, col, a);
                    if (lv != null) {
                        double hy = c.targetY + 0.02;
                        float ha = Math.min(0.9f, a * 1.5f);
                        double hw = w0 * 1.5;
                        b.tube(xa, hy, za, xb, hy, za, hw, col, ha);
                        b.tube(xb, hy, za, xb, hy, zb, hw, col, ha);
                        b.tube(xb, hy, zb, xa, hy, zb, hw, col, ha);
                        b.tube(xa, hy, zb, xa, hy, za, hw, col, ha);
                    }
                }
            }
        } else {
            for (int cx = pcx - r; cx <= pcx + r + 1; cx++) {
                for (int cz = pcz - r; cz <= pcz + r + 1; cz++) {
                    double x = cx * 16.0;
                    double z = cz * 16.0;
                    double d = Math.hypot(cam.x - x, cam.z - z);
                    boolean current = (cx == pcx || cx == pcx + 1) && (cz == pcz || cz == pcz + 1);
                    float a = base;
                    if (c.chunkFade) a *= (float) Mth.clamp(1.0 - d / maxD, 0.12, 1.0);
                    if (current && c.chunkHighlight) a = Math.min(0.9f, a * 2f);
                    b.tube(x, y0, z, x, y1, z, w0 * (1 + d / 32.0), rgb, a);
                }
            }
        }

        if (c.chunkSections) {
            double x0 = pcx * 16.0;
            double z0 = pcz * 16.0;
            double x1 = x0 + 16;
            double z1 = z0 + 16;
            float a = Math.min(0.9f, base * 0.8f);
            for (double y = Math.floor(y0 / 16.0) * 16.0; y <= y1; y += 16.0) {
                b.tube(x0, y, z0, x1, y, z0, w0, rgb, a);
                b.tube(x1, y, z0, x1, y, z1, w0, rgb, a);
                b.tube(x1, y, z1, x0, y, z1, w0, rgb, a);
                b.tube(x0, y, z1, x0, y, z0, w0, rgb, a);
            }
        }
    }
}
