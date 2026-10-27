package de.ancientreminder.render;

import de.ancientreminder.ChunkBorders;
import de.ancientreminder.NetherSession;
import de.ancientreminder.OreScanner;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/** Hängt sich in das Welt-Rendering und zeichnet alle Overlays (Tracer, Chunkgrenzen, Gang). */
public final class Overlay {
    private static ShapeBatch depth;
    private static ShapeBatch xray;

    private Overlay() {}

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (depth == null) {
            depth = new ShapeBatch("depth", false);
            xray = new ShapeBatch("xray", true);
        }
        Vec3 cam = ctx.worldState().cameraRenderState.pos;
        Matrix4fc pose = ctx.matrices().last().pose();
        depth.setup(cam, pose);
        xray.setup(cam, pose);

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ChunkBorders.draw(mc, cam, depth, xray);
        OreScanner.draw(mc, cam, partial, xray);
        NetherSession.drawTunnel(xray);

        depth.flush(mc);
        xray.flush(mc);
    }

    public static void close() {
        if (depth != null) depth.close();
        if (xray != null) xray.close();
        depth = null;
        xray = null;
    }
}
