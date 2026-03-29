package com.mwa.n0name.render;

import com.mwa.n0name.pathfinding.PathNode;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class PathRenderer {

    // Colors
    private static final float[] LINE_COLOR   = {1.0f, 0.3f, 0.35f, 0.85f};  // Red-coral path line
    private static final float[] NODE_COLOR   = {1.0f, 0.35f, 0.4f, 0.55f};  // Soft red nodes
    private static final float[] CURRENT_COLOR = {1.0f, 0.85f, 0.2f, 0.9f};  // Gold current
    private static final float[] TARGET_COLOR = {0.2f, 1.0f, 0.3f, 0.9f};    // Green target

    public static void renderPath(WorldRenderContext context, List<PathNode> nodes, int currentIndex) {
        renderPath(context, nodes, currentIndex, LINE_COLOR, NODE_COLOR, CURRENT_COLOR, TARGET_COLOR);
    }

    public static void renderPath(WorldRenderContext context, List<PathNode> nodes, int currentIndex,
                                   float[] lineColor, float[] nodeColor,
                                   float[] currentColor, float[] targetColor) {
        if (nodes == null || nodes.isEmpty()) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        Vec3d cam = RenderUtils.getCameraPos();
        MatrixStack matrices = context.matrices();
        VertexConsumer consumer = consumers.getBuffer(N0nameRenderLayers.LINES);

        matrices.push();

        // Only render remaining path nodes (skip already-visited)
        int renderStart = Math.max(0, currentIndex);
        List<Vec3d> points = new ArrayList<>(nodes.size() - renderStart + 1);

        // Add player position as first point
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            float tickDelta = RenderUtils.getTickDelta();
            double px = client.player.lastRenderX + (client.player.getX() - client.player.lastRenderX) * tickDelta;
            double py = client.player.lastRenderY + (client.player.getY() - client.player.lastRenderY) * tickDelta;
            double pz = client.player.lastRenderZ + (client.player.getZ() - client.player.lastRenderZ) * tickDelta;
            points.add(new Vec3d(px, py + 0.1, pz));
        }

        for (int i = renderStart; i < nodes.size(); i++) {
            PathNode node = nodes.get(i);
            points.add(new Vec3d(node.x() + 0.5, node.y() + 0.1, node.z() + 0.5));
        }

        // Catmull-Rom spline interpolation for smooth curves
        List<Vec3d> smoothPoints = catmullRomSpline(points, 8);
        RenderUtils.drawLineStrip(matrices, consumer, smoothPoints,
            lineColor[0], lineColor[1], lineColor[2], lineColor[3], cam);

        // Draw full-block outlines on each node
        for (int i = renderStart; i < nodes.size(); i++) {
            PathNode node = nodes.get(i);
            float[] color;
            if (i == currentIndex) {
                color = currentColor;
            } else if (i == nodes.size() - 1) {
                color = targetColor;
            } else {
                color = nodeColor;
            }

            // Draw full block outline at the node's block position
            RenderUtils.drawWireframeBox(matrices, consumer,
                node.x(), node.y(), node.z(),
                1.0, 1.0, 1.0,
                color[0], color[1], color[2], color[3], cam);
        }

        matrices.pop();
        RenderUtils.flushLines(consumers);
    }

    public static void renderPathBounds(WorldRenderContext context, List<PathNode> nodes, int borderColor) {
        if (nodes == null || nodes.size() < 2) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (PathNode node : nodes) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            minZ = Math.min(minZ, node.z());
            maxX = Math.max(maxX, node.x());
            maxY = Math.max(maxY, node.y());
            maxZ = Math.max(maxZ, node.z());
        }

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        Vec3d cam = RenderUtils.getCameraPos();
        MatrixStack matrices = context.matrices();
        VertexConsumer consumer = consumers.getBuffer(N0nameRenderLayers.LINES);

        float r = ((borderColor >> 16) & 0xFF) / 255f;
        float g = ((borderColor >>  8) & 0xFF) / 255f;
        float b = ( borderColor        & 0xFF) / 255f;
        float a = ((borderColor >> 24) & 0xFF) / 255f;

        matrices.push();

        RenderUtils.drawWireframeBox(matrices, consumer,
            minX - 0.1, minY - 0.1, minZ - 0.1,
            (maxX - minX) + 1.2, (maxY - minY) + 1.2, (maxZ - minZ) + 1.2,
            r, g, b, a, cam);

        matrices.pop();
        RenderUtils.flushLines(consumers);
    }

    /**
     * Catmull-Rom spline interpolation for smooth path curves.
     * Falls back to original points if fewer than 3 control points.
     */
    private static List<Vec3d> catmullRomSpline(List<Vec3d> points, int subdivisions) {
        if (points.size() < 3) return points;

        List<Vec3d> result = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d p0 = points.get(Math.max(i - 1, 0));
            Vec3d p1 = points.get(i);
            Vec3d p2 = points.get(Math.min(i + 1, points.size() - 1));
            Vec3d p3 = points.get(Math.min(i + 2, points.size() - 1));

            for (int j = 0; j < subdivisions; j++) {
                float t = (float) j / subdivisions;
                float t2 = t * t;
                float t3 = t2 * t;

                double x = 0.5 * ((2 * p1.x) + (-p0.x + p2.x) * t
                        + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2
                        + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
                double y = 0.5 * ((2 * p1.y) + (-p0.y + p2.y) * t
                        + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2
                        + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
                double z = 0.5 * ((2 * p1.z) + (-p0.z + p2.z) * t
                        + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2
                        + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);

                result.add(new Vec3d(x, y, z));
            }
        }
        // Add the last point
        result.add(points.get(points.size() - 1));
        return result;
    }
}
