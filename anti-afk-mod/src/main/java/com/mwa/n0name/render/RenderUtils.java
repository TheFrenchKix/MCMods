package com.mwa.n0name.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class RenderUtils {

    public static Vec3d getCameraPos() {
        return MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
    }

    /**
     * Get partial tick delta for smooth rendering interpolation.
     */
    public static float getTickDelta() {
        return MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
    }

    public static void drawWireframeBox(MatrixStack matrices, VertexConsumer consumer,
                                         double wx, double wy, double wz,
                                         double sizeX, double sizeY, double sizeZ,
                                         float r, float g, float b, float a,
                                         Vec3d cam) {
        float x1 = (float)(wx - cam.x);
        float y1 = (float)(wy - cam.y);
        float z1 = (float)(wz - cam.z);
        float x2 = x1 + (float)sizeX;
        float y2 = y1 + (float)sizeY;
        float z2 = z1 + (float)sizeZ;

        MatrixStack.Entry entry = matrices.peek();

        // Bottom face
        line(entry, consumer, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(entry, consumer, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(entry, consumer, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(entry, consumer, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // Top face
        line(entry, consumer, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(entry, consumer, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(entry, consumer, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(entry, consumer, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // Vertical edges
        line(entry, consumer, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(entry, consumer, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(entry, consumer, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(entry, consumer, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    public static void drawLineStrip(MatrixStack matrices, VertexConsumer consumer,
                                      List<Vec3d> points, float r, float g, float b, float a,
                                      Vec3d cam) {
        if (points.size() < 2) return;
        MatrixStack.Entry entry = matrices.peek();
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d from = points.get(i);
            Vec3d to = points.get(i + 1);
            float fx = (float)(from.x - cam.x), fy = (float)(from.y - cam.y), fz = (float)(from.z - cam.z);
            float tx = (float)(to.x - cam.x),   ty = (float)(to.y - cam.y),   tz = (float)(to.z - cam.z);
            line(entry, consumer, fx, fy, fz, tx, ty, tz, r, g, b, a);
        }
    }

    public static void drawLine(MatrixStack matrices, VertexConsumer consumer,
                                 Vec3d from, Vec3d to,
                                 float r, float g, float b, float a,
                                 Vec3d cam) {
        MatrixStack.Entry entry = matrices.peek();
        line(entry, consumer,
             (float)(from.x - cam.x), (float)(from.y - cam.y), (float)(from.z - cam.z),
             (float)(to.x - cam.x),   (float)(to.y - cam.y),   (float)(to.z - cam.z),
             r, g, b, a);
    }

    public static void flushLines(VertexConsumerProvider consumers) {
        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayers.LINES);
        }
    }

    private static void line(MatrixStack.Entry e, VertexConsumer c,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;
        c.vertex(e, x1, y1, z1).color(r, g, b, a).normal(e, nx, ny, nz);
        c.vertex(e, x2, y2, z2).color(r, g, b, a).normal(e, nx, ny, nz);
    }
}
