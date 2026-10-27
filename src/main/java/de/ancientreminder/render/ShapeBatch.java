package de.ancientreminder.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Sammelt Linien (als dünne Vierkant-Röhren) und Flächen und zeichnet sie in einem Rutsch.
 * Aufbau nach dem offiziellen Fabric-Beispiel "Rendering in der Welt" (1.21.11).
 * Mit throughWalls = true sieht man die Formen auch durch Blöcke hindurch.
 */
public final class ShapeBatch {
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private final String name;
    private final RenderPipeline pipeline;
    private final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private BufferBuilder buffer;
    private MappableRingBuffer vertexBuffer;

    private Matrix4fc pose;
    private double camX;
    private double camY;
    private double camZ;
    private float cr;
    private float cg;
    private float cb;
    private float ca;

    public ShapeBatch(String name, boolean throughWalls) {
        this.name = name;
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("ancientreminder", "pipeline/" + name));
        if (throughWalls) builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        this.pipeline = RenderPipelines.register(builder.build());
    }

    public void setup(Vec3 cam, Matrix4fc pose) {
        this.camX = cam.x;
        this.camY = cam.y;
        this.camZ = cam.z;
        this.pose = pose;
    }

    // ------------------------------------------------------------------ Formen

    private void color(int rgb, float a) {
        cr = ((rgb >> 16) & 0xFF) / 255f;
        cg = ((rgb >> 8) & 0xFF) / 255f;
        cb = (rgb & 0xFF) / 255f;
        ca = a;
    }

    private void vert(double x, double y, double z) {
        if (buffer == null) {
            buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
        }
        buffer.addVertex(pose, (float) (x - camX), (float) (y - camY), (float) (z - camZ)).setColor(cr, cg, cb, ca);
    }

    private void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                      double x3, double y3, double z3, double x4, double y4, double z4) {
        vert(x1, y1, z1);
        vert(x2, y2, z2);
        vert(x3, y3, z3);
        vert(x4, y4, z4);
    }

    /** Eine Linie von A nach B als dünne Röhre mit Halbbreite w. */
    public void tube(double ax, double ay, double az, double bx, double by, double bz, double w, int rgb, float alpha) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) return;
        dx /= len;
        dy /= len;
        dz /= len;
        double rx = 0;
        double ry = 1;
        double rz = 0;
        if (Math.abs(dy) > 0.99) {
            rx = 1;
            ry = 0;
        }
        double ux = dy * rz - dz * ry;
        double uy = dz * rx - dx * rz;
        double uz = dx * ry - dy * rx;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux = ux / ul * w;
        uy = uy / ul * w;
        uz = uz / ul * w;
        double vx = dy * uz - dz * uy;
        double vy = dz * ux - dx * uz;
        double vz = dx * uy - dy * ux;

        double[][] c = {
                {ux + vx, uy + vy, uz + vz},
                {-ux + vx, -uy + vy, -uz + vz},
                {-ux - vx, -uy - vy, -uz - vz},
                {ux - vx, uy - vy, uz - vz}
        };
        color(rgb, alpha);
        for (int i = 0; i < 4; i++) {
            double[] p = c[i];
            double[] q = c[(i + 1) & 3];
            quad(ax + p[0], ay + p[1], az + p[2],
                    ax + q[0], ay + q[1], az + q[2],
                    bx + q[0], by + q[1], bz + q[2],
                    bx + p[0], by + p[1], bz + p[2]);
        }
    }

    public void boxOutline(AABB b, double w, int rgb, float alpha) {
        double x0 = b.minX;
        double y0 = b.minY;
        double z0 = b.minZ;
        double x1 = b.maxX;
        double y1 = b.maxY;
        double z1 = b.maxZ;
        tube(x0, y0, z0, x1, y0, z0, w, rgb, alpha);
        tube(x1, y0, z0, x1, y0, z1, w, rgb, alpha);
        tube(x1, y0, z1, x0, y0, z1, w, rgb, alpha);
        tube(x0, y0, z1, x0, y0, z0, w, rgb, alpha);
        tube(x0, y1, z0, x1, y1, z0, w, rgb, alpha);
        tube(x1, y1, z0, x1, y1, z1, w, rgb, alpha);
        tube(x1, y1, z1, x0, y1, z1, w, rgb, alpha);
        tube(x0, y1, z1, x0, y1, z0, w, rgb, alpha);
        tube(x0, y0, z0, x0, y1, z0, w, rgb, alpha);
        tube(x1, y0, z0, x1, y1, z0, w, rgb, alpha);
        tube(x1, y0, z1, x1, y1, z1, w, rgb, alpha);
        tube(x0, y0, z1, x0, y1, z1, w, rgb, alpha);
    }

    public void filledBox(AABB b, int rgb, float alpha) {
        double minX = b.minX;
        double minY = b.minY;
        double minZ = b.minZ;
        double maxX = b.maxX;
        double maxY = b.maxY;
        double maxZ = b.maxZ;
        color(rgb, alpha);
        quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        quad(maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ);
        quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
        quad(maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ);
        quad(minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ);
        quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
    }

    // ------------------------------------------------------------------ Zeichnen

    /** Baut den gesammelten Puffer und zeichnet ihn. Muss pro Frame einmal aufgerufen werden. */
    public void flush(Minecraft client) {
        if (buffer == null) return;
        MeshData built = buffer.buildOrThrow();
        buffer = null;
        MeshData.DrawState drawState = built.drawState();
        VertexFormat format = drawState.format();

        GpuBuffer vertices = upload(drawState, format, built);
        draw(client, built, drawState, vertices, format);
        vertexBuffer.rotate();
    }

    private GpuBuffer upload(MeshData.DrawState drawState, VertexFormat format, MeshData built) {
        int size = drawState.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> "ancientreminder " + name,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), view.data());
        }
        return vertexBuffer.currentBuffer();
    }

    private void draw(Minecraft client, MeshData built, MeshData.DrawState drawState, GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;
        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            built.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
            indexType = built.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = seq.getBuffer(drawState.indexCount());
            indexType = seq.type();
        }

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "ancientreminder " + name + " draw",
                client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }
        built.close();
    }

    public void close() {
        allocator.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
