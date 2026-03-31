package com.cpathfinder.render;

import com.cpathfinder.PathState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 3D line renderer — registered on WorldRenderEvents.END_MAIN.
 *
 * Draws a green line connecting every consecutive pair of path nodes.
 * Uses RenderTypes.lines() (net.minecraft.client.renderer.rendertype.RenderTypes)
 * and PoseStack.Pose for automatic matrix transforms.
 *
 * In MC 1.21.11 with Mojang mappings:
 *  - RenderType/RenderTypes moved to the .rendertype sub-package
 *  - Camera.position() replaces Camera.getPosition()
 *  - WorldRenderEvents + WorldRenderContext are in the .world sub-package
 *  - AFTER_TRANSLUCENT was removed; END_MAIN fires after translucent pass
 */
public final class PathRenderer {

    // Line colour RGBA
    private static final int R = 0, G = 220, B = 0, A = 210;

    private PathRenderer() {}

    public static void register() {
        WorldRenderEvents.END_MAIN.register(PathRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        List<BlockPos> path = PathState.getPath();
        if (path == null || path.size() < 2) return;

        MultiBufferSource consumers = context.consumers();
        if (consumers == null) return;

        PoseStack matrices = context.matrices();
        if (matrices == null) return;

        // Camera position — camera() was removed in this API version; renamed to position()
        Camera camera  = context.gameRenderer().getMainCamera();
        Vec3   camPos  = camera.position();

        matrices.pushPose();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderType      lineType = RenderTypes.lines();
        VertexConsumer  buffer   = consumers.getBuffer(lineType);
        PoseStack.Pose  pose   = matrices.last();

        for (int i = 0; i < path.size() - 1; i++) {
            BlockPos from = path.get(i);
            BlockPos to   = path.get(i + 1);

            // Draw slightly above block surface to stay visible
            float x1 = from.getX() + 0.5f, y1 = from.getY() + 0.12f, z1 = from.getZ() + 0.5f;
            float x2 = to  .getX() + 0.5f, y2 = to  .getY() + 0.12f, z2 = to  .getZ() + 0.5f;

            // Normalised direction — required by LINES vertex format as the normal attribute
            float ndx = x2 - x1, ndy = y2 - y1, ndz = z2 - z1;
            float len = (float) Math.sqrt(ndx * ndx + ndy * ndy + ndz * ndz);
            if (len > 1e-5f) { ndx /= len; ndy /= len; ndz /= len; }

            buffer.addVertex(pose, x1, y1, z1).setColor(R, G, B, A).setNormal(ndx, ndy, ndz);
            buffer.addVertex(pose, x2, y2, z2).setColor(R, G, B, A).setNormal(ndx, ndy, ndz);
        }

        matrices.popPose();
    }
}
