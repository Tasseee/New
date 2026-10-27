package de.ancientreminder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Misst in mehreren Ticks, wie viel Prozent der Blöcke auf Ancient-Höhe offen (Luft) sind,
 * insgesamt und pro Chunk. Pro Tick werden nur ein paar Chunks bearbeitet, damit nichts ruckelt.
 */
public final class LayerScanner {
    public record Result(long air, long total, int loaded, int wanted, Map<Long, Double> chunks) {
        public double fraction() {
            return total == 0 ? 0 : (double) air / total;
        }
    }

    private final ArrayDeque<int[]> queue = new ArrayDeque<>();
    private final Map<Long, Double> chunkFractions = new HashMap<>();
    private ClientLevel level;
    private int minY;
    private int maxY;
    private long air;
    private long total;
    private int wanted;
    private int done;
    private int loaded;
    private boolean running;

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int keyX(long k) {
        return (int) (k >> 32);
    }

    public static int keyZ(long k) {
        return (int) k;
    }

    public void start(ClientLevel level, int centerChunkX, int centerChunkZ, int radiusChunks, int minY, int maxY) {
        queue.clear();
        chunkFractions.clear();
        this.level = level;
        this.minY = minY;
        this.maxY = maxY;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                queue.add(new int[]{centerChunkX + dx, centerChunkZ + dz});
            }
        }
        air = 0;
        total = 0;
        wanted = queue.size();
        done = 0;
        loaded = 0;
        running = true;
    }

    public void abort() {
        running = false;
        queue.clear();
    }

    public boolean isRunning() {
        return running;
    }

    public double progress() {
        return wanted == 0 ? 0 : (double) done / wanted;
    }

    /** Gibt das Ergebnis zurück, sobald alle Chunks fertig sind, sonst null. */
    public Result tick(int chunksPerTick, ClientLevel currentLevel) {
        if (!running) return null;
        if (currentLevel != level) {
            abort();
            return null;
        }
        for (int i = 0; i < chunksPerTick && !queue.isEmpty(); i++) {
            int[] c = queue.poll();
            scanChunk(c[0], c[1]);
            done++;
        }
        if (queue.isEmpty()) {
            running = false;
            return new Result(air, total, loaded, wanted, new HashMap<>(chunkFractions));
        }
        return null;
    }

    private void scanChunk(int cx, int cz) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return;
        loaded++;
        long a = 0;
        long t = 0;
        int minSec = chunk.getMinSectionY();
        LevelChunkSection[] secs = chunk.getSections();
        for (int y = minY; y <= maxY; y++) {
            int idx = (y >> 4) - minSec;
            if (idx < 0 || idx >= secs.length) continue;
            LevelChunkSection sec = secs[idx];
            if (sec == null) continue;
            if (sec.hasOnlyAir()) {
                a += 256;
                t += 256;
                continue;
            }
            int ly = y & 15;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    t++;
                    if (sec.getBlockState(x, ly, z).isAir()) a++;
                }
            }
        }
        air += a;
        total += t;
        if (t > 0) chunkFractions.put(key(cx, cz), (double) a / t);
    }
}
