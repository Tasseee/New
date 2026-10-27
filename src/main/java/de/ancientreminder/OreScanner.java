package de.ancientreminder;

import de.ancientreminder.render.ShapeBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sucht offen liegende Erze (und herumliegende Drops) und zeichnet Tracer dorthin. */
public final class OreScanner {
    public record Hit(Vec3 pos, BlockPos block, Target target, boolean item) {}

    public static List<Hit> hits = new ArrayList<>();
    private static final Set<Long> KNOWN = new HashSet<>();
    private static int counter;

    private OreScanner() {}

    public static void tick(Minecraft mc) {
        Config c = Config.I;
        LocalPlayer p = mc.player;
        ClientLevel lvl = mc.level;
        if (!c.oreEnabled || p == null || lvl == null) {
            hits = new ArrayList<>();
            KNOWN.clear();
            return;
        }
        if (++counter < c.oreScanTicks) return;
        counter = 0;
        scan(p, lvl);
    }

    private static void scan(LocalPlayer p, ClientLevel lvl) {
        Config c = Config.I;
        Map<Block, Target> map = new HashMap<>();
        for (Target t : Target.values()) {
            if (t.cfg().enabled) for (Block b : t.blocks) map.put(b, t);
        }
        if (map.isEmpty()) {
            hits = new ArrayList<>();
            return;
        }

        BlockPos pp = p.blockPosition();
        int r = c.oreRadius;
        long r2 = (long) r * r;
        List<Hit> found = new ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        for (int cx = (pp.getX() - r) >> 4; cx <= (pp.getX() + r) >> 4; cx++) {
            for (int cz = (pp.getZ() - r) >> 4; cz <= (pp.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = lvl.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                LevelChunkSection[] secs = chunk.getSections();
                int minSec = chunk.getMinSectionY();
                for (int si = 0; si < secs.length; si++) {
                    int secY = (minSec + si) << 4;
                    if (secY + 15 < pp.getY() - r || secY > pp.getY() + r) continue;
                    LevelChunkSection sec = secs[si];
                    if (sec == null || sec.hasOnlyAir()) continue;
                    if (!sec.maybeHas(st -> map.containsKey(st.getBlock()))) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState st = sec.getBlockState(x, y, z);
                                Target t = map.get(st.getBlock());
                                if (t == null) continue;
                                int wx = (cx << 4) + x;
                                int wy = secY + y;
                                int wz = (cz << 4) + z;
                                long dx = wx - pp.getX();
                                long dy = wy - pp.getY();
                                long dz = wz - pp.getZ();
                                if (dx * dx + dy * dy + dz * dz > r2) continue;
                                mp.set(wx, wy, wz);
                                if (!exposed(lvl, mp)) continue;
                                found.add(new Hit(new Vec3(wx + 0.5, wy + 0.5, wz + 0.5), new BlockPos(wx, wy, wz), t, false));
                            }
                        }
                    }
                }
            }
        }

        if (c.oreItems) {
            AABB area = p.getBoundingBox().inflate(r);
            for (ItemEntity e : lvl.getEntitiesOfClass(ItemEntity.class, area)) {
                ItemStack s = e.getItem();
                for (Target t : Target.values()) {
                    if (t.cfg().enabled && s.is(t.item)) {
                        found.add(new Hit(e.position(), e.blockPosition(), t, true));
                        break;
                    }
                }
            }
        }

        Vec3 pos = p.position();
        found.sort((a, b) -> Double.compare(a.pos().distanceToSqr(pos), b.pos().distanceToSqr(pos)));
        hits = found;

        // Neue Funde melden
        Set<Long> current = new HashSet<>();
        List<Hit> fresh = new ArrayList<>();
        for (Hit h : found) {
            if (h.item()) continue;
            long key = h.block().asLong();
            current.add(key);
            if (!KNOWN.contains(key)) fresh.add(h);
        }
        KNOWN.retainAll(current);
        KNOWN.addAll(current);

        if (!fresh.isEmpty()) {
            if (c.oreMessage) {
                int shown = 0;
                for (Hit h : fresh) {
                    if (shown++ >= 3) break;
                    int dist = (int) Math.round(Math.sqrt(h.pos().distanceToSqr(pos)));
                    Ui.chat(h.target().label + " offen bei " + h.block().getX() + " " + h.block().getY() + " "
                            + h.block().getZ() + " (" + dist + " Blöcke)");
                }
                if (fresh.size() > 3) Ui.chat("... und " + (fresh.size() - 3) + " weitere");
            }
            if (c.oreSound) Ui.ding();
        }
    }

    private static boolean exposed(ClientLevel lvl, BlockPos pos) {
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            n.setWithOffset(pos, d);
            if (lvl.getBlockState(n).isAir()) return true;
        }
        return false;
    }

    /** Zeichnet Tracer-Linien und Umrisse (durch Wände sichtbar). */
    public static void draw(Minecraft mc, Vec3 cam, float partial, ShapeBatch xray) {
        Config c = Config.I;
        if (!c.oreEnabled || hits.isEmpty() || mc.player == null) return;
        LocalPlayer p = mc.player;

        if (c.tracers) {
            Vec3 look = p.getViewVector(partial);
            Vec3 start = c.tracerStart == 0
                    ? new Vec3(cam.x + look.x, cam.y + look.y - 0.12, cam.z + look.z)
                    : p.getPosition(partial).add(0, 0.1, 0);
            float alpha = c.tracerAlpha / 100f;
            int drawn = 0;
            for (Hit h : hits) {
                if (drawn >= c.maxTracers) break;
                if (!h.target().cfg().tracer) continue;
                double dist = Math.sqrt(h.pos().distanceToSqr(cam));
                double w = 0.006 * c.tracerWidth * (1 + dist / 20.0);
                xray.tube(start.x, start.y, start.z, h.pos().x, h.pos().y, h.pos().z, w, h.target().color, alpha);
                drawn++;
            }
        }

        if (c.oreBoxes) {
            int drawn = 0;
            for (Hit h : hits) {
                if (drawn++ >= 64) break;
                AABB box = h.item()
                        ? new AABB(h.pos().x - 0.2, h.pos().y - 0.1, h.pos().z - 0.2, h.pos().x + 0.2, h.pos().y + 0.3, h.pos().z + 0.2)
                        : new AABB(h.block());
                xray.boxOutline(box, 0.02, h.target().color, 0.9f);
            }
        }
    }
}
