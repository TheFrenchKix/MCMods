package com.mwa.n0name.render;

import com.mwa.n0name.pathfinding.PathNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class RenderUtils {

    public static Vec3d getCameraPos() {
        return MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
    }

    public static float getTickDelta() {
        return MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
    }

    // ==================== Legacy rendering (depth-tested, used by path modules) ====================

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

        line(entry, consumer, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(entry, consumer, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(entry, consumer, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(entry, consumer, x1, y1, z2, x1, y1, z1, r, g, b, a);
        line(entry, consumer, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(entry, consumer, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(entry, consumer, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(entry, consumer, x1, y2, z2, x1, y2, z1, r, g, b, a);
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
            imm.draw(N0nameRenderLayers.LINES);
        }
    }

    public static void flushEsp(VertexConsumerProvider consumers) {
        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(N0nameRenderLayers.ESP_QUADS);
            imm.draw(N0nameRenderLayers.ESP_LINES);
        }
    }

    public static void flush(VertexConsumerProvider consumers, RenderLayer layer) {
        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(layer);
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
        c.vertex(e, x1, y1, z1).color(r, g, b, a).normal(e, nx, ny, nz).lineWidth(2);
        c.vertex(e, x2, y2, z2).color(r, g, b, a).normal(e, nx, ny, nz).lineWidth(2);
    }

    // ==================== ESP Rendering (renders through walls using translucent colors) ====================

    /**
     * Render filled faces into a QUADS consumer. Must be called as a separate pass from renderEspOutlines
     * because both consumers share the same Immediate fallback slot (only one can be active at a time).
     */
    public static void renderEspFills(MatrixStack matrices, VertexConsumer consumer,
                                       List<Box> boxes, int color, float alpha,
                                       Vec3d cam) {
        if (boxes.isEmpty()) return;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        MatrixStack.Entry entry = matrices.peek();
        for (Box box : boxes) {
            addFilledBox(entry, consumer, box, cam, r, g, b, alpha);
        }
    }

    /**
     * Render outline edges into a LINES consumer. Must be called as a separate pass from renderEspFills.
     */
    public static void renderEspOutlines(MatrixStack matrices, VertexConsumer consumer,
                                          List<Box> boxes, int color, float alpha,
                                          Vec3d cam) {
        if (boxes.isEmpty()) return;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        MatrixStack.Entry entry = matrices.peek();
        for (Box box : boxes) {
            addOutlinedBox(entry, consumer, box, cam, r, g, b, alpha);
        }
    }

    /**
     * Render tracer lines from player eye to target positions.
     */
    public static void renderEspTracers(MatrixStack matrices, VertexConsumer consumer,
                                         Vec3d origin, List<Vec3d> targets,
                                         int color, float alpha,
                                         Vec3d cam) {
        if (targets.isEmpty()) return;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        MatrixStack.Entry entry = matrices.peek();
        float ox = (float)(origin.x - cam.x);
        float oy = (float)(origin.y - cam.y);
        float oz = (float)(origin.z - cam.z);

        for (Vec3d target : targets) {
            consumer.vertex(entry, ox, oy, oz).color(r, g, b, alpha).normal(entry, 0, 1, 0).lineWidth(2);
            consumer.vertex(entry,
                (float)(target.x - cam.x),
                (float)(target.y - cam.y),
                (float)(target.z - cam.z)).color(r, g, b, alpha).normal(entry, 0, 1, 0).lineWidth(2);
        }
    }

    /**
     * Render A* path with lines and node rectangles.
     */
    public static void renderEspPath(MatrixStack matrices, VertexConsumer consumer,
                                      List<PathNode> nodes, int currentIndex,
                                      Vec3d playerPos,
                                      int lineColor, int nodeColor, int currentNodeColor, int targetColor,
                                      Vec3d cam) {
        if (nodes == null || nodes.isEmpty()) return;

        MatrixStack.Entry entry = matrices.peek();

        float lr = ((lineColor >> 16) & 0xFF) / 255f;
        float lg = ((lineColor >> 8) & 0xFF) / 255f;
        float lb = (lineColor & 0xFF) / 255f;

        // Path lines: player to first node
        if (playerPos != null && !nodes.isEmpty()) {
            Vec3d first = nodes.get(0).toVec3dCenter().add(0, 0.1, 0);
            consumer.vertex(entry, (float)(playerPos.x - cam.x), (float)(playerPos.y + 0.1 - cam.y), (float)(playerPos.z - cam.z))
               .color(lr, lg, lb, 0.9f).normal(entry, 0, 1, 0).lineWidth(2);
            consumer.vertex(entry, (float)(first.x - cam.x), (float)(first.y - cam.y), (float)(first.z - cam.z))
               .color(lr, lg, lb, 0.9f).normal(entry, 0, 1, 0).lineWidth(2);
        }

        // Node-to-node lines
        for (int i = 0; i < nodes.size() - 1; i++) {
            Vec3d from = nodes.get(i).toVec3dCenter().add(0, 0.1, 0);
            Vec3d to = nodes.get(i + 1).toVec3dCenter().add(0, 0.1, 0);
            consumer.vertex(entry, (float)(from.x - cam.x), (float)(from.y - cam.y), (float)(from.z - cam.z))
               .color(lr, lg, lb, 0.9f).normal(entry, 0, 1, 0).lineWidth(2);
            consumer.vertex(entry, (float)(to.x - cam.x), (float)(to.y - cam.y), (float)(to.z - cam.z))
               .color(lr, lg, lb, 0.9f).normal(entry, 0, 1, 0).lineWidth(2);
        }

        // Node rectangles
        float rectSize = 0.4f;
        float rectHalf = rectSize / 2f;
        float rectH = 0.15f;

        for (int i = 0; i < nodes.size(); i++) {
            PathNode node = nodes.get(i);
            int color = (i == nodes.size() - 1) ? targetColor : (i == currentIndex) ? currentNodeColor : nodeColor;
            float nr = ((color >> 16) & 0xFF) / 255f;
            float ng = ((color >> 8) & 0xFF) / 255f;
            float nb = (color & 0xFF) / 255f;
            float na = (i == currentIndex) ? 0.7f : 0.4f;

            float px = (float)(node.x() + 0.5 - cam.x);
            float py = (float)(node.y() + 0.01 - cam.y);
            float pz = (float)(node.z() + 0.5 - cam.z);

            // Draw node box outline
            addOutlinedBox(entry, consumer,
                new Box(node.x() + 0.5 - rectHalf, node.y() + 0.01, node.z() + 0.5 - rectHalf,
                        node.x() + 0.5 + rectHalf, node.y() + 0.01 + rectH, node.z() + 0.5 + rectHalf),
                cam, nr, ng, nb, na);
        }
    }

    private static void addFilledBox(MatrixStack.Entry entry, VertexConsumer consumer,
                                      Box box, Vec3d cam,
                                      float r, float g, float b, float a) {
        float x1 = (float)(box.minX - cam.x), y1 = (float)(box.minY - cam.y), z1 = (float)(box.minZ - cam.z);
        float x2 = (float)(box.maxX - cam.x), y2 = (float)(box.maxY - cam.y), z2 = (float)(box.maxZ - cam.z);

        // Bottom (-Y)
        consumer.vertex(entry, x1, y1, z1).color(r, g, b, a);
        consumer.vertex(entry, x2, y1, z1).color(r, g, b, a);
        consumer.vertex(entry, x2, y1, z2).color(r, g, b, a);
        consumer.vertex(entry, x1, y1, z2).color(r, g, b, a);
        // Top (+Y)
        consumer.vertex(entry, x1, y2, z1).color(r, g, b, a);
        consumer.vertex(entry, x1, y2, z2).color(r, g, b, a);
        consumer.vertex(entry, x2, y2, z2).color(r, g, b, a);
        consumer.vertex(entry, x2, y2, z1).color(r, g, b, a);
        // North (-Z)
        consumer.vertex(entry, x1, y1, z1).color(r, g, b, a);
        consumer.vertex(entry, x1, y2, z1).color(r, g, b, a);
        consumer.vertex(entry, x2, y2, z1).color(r, g, b, a);
        consumer.vertex(entry, x2, y1, z1).color(r, g, b, a);
        // South (+Z)
        consumer.vertex(entry, x1, y1, z2).color(r, g, b, a);
        consumer.vertex(entry, x2, y1, z2).color(r, g, b, a);
        consumer.vertex(entry, x2, y2, z2).color(r, g, b, a);
        consumer.vertex(entry, x1, y2, z2).color(r, g, b, a);
        // West (-X)
        consumer.vertex(entry, x1, y1, z1).color(r, g, b, a);
        consumer.vertex(entry, x1, y1, z2).color(r, g, b, a);
        consumer.vertex(entry, x1, y2, z2).color(r, g, b, a);
        consumer.vertex(entry, x1, y2, z1).color(r, g, b, a);
        // East (+X)
        consumer.vertex(entry, x2, y1, z1).color(r, g, b, a);
        consumer.vertex(entry, x2, y2, z1).color(r, g, b, a);
        consumer.vertex(entry, x2, y2, z2).color(r, g, b, a);
        consumer.vertex(entry, x2, y1, z2).color(r, g, b, a);
    }

    private static void addOutlinedBox(MatrixStack.Entry entry, VertexConsumer consumer,
                                        Box box, Vec3d cam,
                                        float r, float g, float b, float a) {
        float x1 = (float)(box.minX - cam.x), y1 = (float)(box.minY - cam.y), z1 = (float)(box.minZ - cam.z);
        float x2 = (float)(box.maxX - cam.x), y2 = (float)(box.maxY - cam.y), z2 = (float)(box.maxZ - cam.z);

        // Bottom edges
        line(entry, consumer, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(entry, consumer, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(entry, consumer, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(entry, consumer, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // Top edges
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
}
