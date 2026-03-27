package com.mwa.n0name.render;

import com.mwa.n0name.pathfinding.PathNode;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class PathRenderer {

    // Colors
    private static final float[] LINE_COLOR   = {0.2f, 0.8f, 1.0f, 0.9f};   // Cyan
    private static final float[] NODE_COLOR   = {0.3f, 0.6f, 1.0f, 0.7f};   // Blue
    private static final float[] CURRENT_COLOR = {1.0f, 1.0f, 0.2f, 0.9f};  // Yellow
    private static final float[] TARGET_COLOR = {1.0f, 0.3f, 0.3f, 0.9f};   // Red

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
        VertexConsumer consumer = consumers.getBuffer(RenderLayers.LINES);

        matrices.push();

        // Draw line strip connecting all nodes
        List<Vec3d> points = new ArrayList<>(nodes.size() + 1);

        // Add player position as first point
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            float tickDelta = RenderUtils.getTickDelta();
            double px = client.player.lastRenderX + (client.player.getX() - client.player.lastRenderX) * tickDelta;
            double py = client.player.lastRenderY + (client.player.getY() - client.player.lastRenderY) * tickDelta;
            double pz = client.player.lastRenderZ + (client.player.getZ() - client.player.lastRenderZ) * tickDelta;
            points.add(new Vec3d(px, py + 0.1, pz));
        }

        for (PathNode node : nodes) {
            points.add(new Vec3d(node.x() + 0.5, node.y() + 0.1, node.z() + 0.5));
        }

        RenderUtils.drawLineStrip(matrices, consumer, points,
            lineColor[0], lineColor[1], lineColor[2], lineColor[3], cam);

        // Draw boxes on each node
        double boxSize = 0.4;
        double boxHeight = 0.2;
        double boxOffset = (1.0 - boxSize) / 2.0;

        for (int i = 0; i < nodes.size(); i++) {
            PathNode node = nodes.get(i);
            float[] color;
            if (i == currentIndex) {
                color = currentColor;
            } else if (i == nodes.size() - 1) {
                color = targetColor;
            } else {
                color = nodeColor;
            }

            RenderUtils.drawWireframeBox(matrices, consumer,
                node.x() + boxOffset, node.y() + 0.01, node.z() + boxOffset,
                boxSize, boxHeight, boxSize,
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
        VertexConsumer consumer = consumers.getBuffer(RenderLayers.LINES);

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
}
