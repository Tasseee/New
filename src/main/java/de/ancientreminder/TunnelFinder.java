package de.ancientreminder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Sucht einen 2 Blöcke hohen, geraden Gang auf Ancient-Höhe, der komplett in festem Gestein liegt
 * und weit weg von Lava ist. Optional bevorzugt er Gänge direkt an einer Chunkgrenze.
 * Nutzt Summentabellen, damit die Suche schnell bleibt.
 */
public final class TunnelFinder {
    public record Tunnel(AABB box, BlockPos start, BlockPos end, Direction dir, boolean border) {}

    private static final Direction[] DIRS = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

    private TunnelFinder() {}

    public static Tunnel find(ClientLevel lvl, LocalPlayer p) {
        Config c = Config.I;
        int ty = c.targetY;
        int len = c.tunnelLength;
        int safety = c.tunnelLavaSafety;
        int maxDist = c.tunnelMaxDist;
        int px = p.getBlockX();
        int pz = p.getBlockZ();
        int ext = maxDist + len + safety + 2;
        int x0 = px - ext;
        int z0 = pz - ext;
        int n = ext * 2 + 1;

        int[][] openSum = new int[n + 1][n + 1];
        int[][] lavaSum = new int[n + 1][n + 1];
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int wx = x0 + i;
                int wz = z0 + j;
                boolean open = false;
                boolean lava = false;
                if (!lvl.hasChunk(wx >> 4, wz >> 4)) {
                    open = true;
                    lava = true;
                } else {
                    for (int y = ty - 1; y <= ty + 2; y++) {
                        BlockState st = lvl.getBlockState(mp.set(wx, y, wz));
                        if (st.isAir() || !st.getFluidState().isEmpty()) open = true;
                    }
                    for (int y = ty - safety; y <= ty + 1 + safety; y++) {
                        if (lvl.getBlockState(mp.set(wx, y, wz)).getFluidState().is(FluidTags.LAVA)) lava = true;
                    }
                }
                openSum[i + 1][j + 1] = openSum[i][j + 1] + openSum[i + 1][j] - openSum[i][j] + (open ? 1 : 0);
                lavaSum[i + 1][j + 1] = lavaSum[i][j + 1] + lavaSum[i + 1][j] - lavaSum[i][j] + (lava ? 1 : 0);
            }
        }

        Tunnel bestAny = null;
        Tunnel bestBorder = null;
        double distAny = Double.MAX_VALUE;
        double distBorder = Double.MAX_VALUE;
        for (int sx = px - maxDist; sx <= px + maxDist; sx++) {
            for (int sz = pz - maxDist; sz <= pz + maxDist; sz++) {
                for (Direction dir : DIRS) {
                    int ex = sx + dir.getStepX() * (len - 1);
                    int ez = sz + dir.getStepZ() * (len - 1);
                    int minX = Math.min(sx, ex);
                    int maxX = Math.max(sx, ex);
                    int minZ = Math.min(sz, ez);
                    int maxZ = Math.max(sz, ez);
                    if (rect(openSum, x0, z0, n, minX - 1, minZ - 1, maxX + 1, maxZ + 1) != 0) continue;
                    if (rect(lavaSum, x0, z0, n, minX - safety, minZ - safety, maxX + safety, maxZ + safety) != 0) continue;
                    double cx = (minX + maxX) / 2.0 + 0.5;
                    double cz = (minZ + maxZ) / 2.0 + 0.5;
                    double d = (cx - p.getX()) * (cx - p.getX()) + (cz - p.getZ()) * (cz - p.getZ());
                    if (d < 9) continue;
                    boolean border = onBorder(dir, sx, sz);
                    AABB box = new AABB(minX, ty, minZ, maxX + 1, ty + 2, maxZ + 1);
                    Tunnel t = new Tunnel(box, new BlockPos(sx, ty, sz), new BlockPos(ex, ty, ez), dir, border);
                    if (d < distAny) {
                        distAny = d;
                        bestAny = t;
                    }
                    if (border && d < distBorder) {
                        distBorder = d;
                        bestBorder = t;
                    }
                }
            }
        }
        return switch (c.tunnelBorder) {
            case 2 -> bestBorder;
            case 1 -> bestBorder != null ? bestBorder : bestAny;
            default -> bestAny;
        };
    }

    /** Liegt der Gang in der Blockreihe direkt neben einer Chunkgrenze (Reihe 0 oder 15)? */
    private static boolean onBorder(Direction dir, int sx, int sz) {
        int row = dir.getAxis() == Direction.Axis.X ? (sz & 15) : (sx & 15);
        return row == 0 || row == 15;
    }

    /** Summe über ein Rechteck in Weltkoordinaten (außerhalb des Gitters zählt als belegt). */
    private static int rect(int[][] sum, int x0, int z0, int n, int ax, int az, int bx, int bz) {
        int i1 = ax - x0;
        int j1 = az - z0;
        int i2 = bx - x0;
        int j2 = bz - z0;
        if (i1 < 0 || j1 < 0 || i2 >= n || j2 >= n) return 1;
        return sum[i2 + 1][j2 + 1] - sum[i1][j2 + 1] - sum[i2 + 1][j1] + sum[i1][j1];
    }
}
