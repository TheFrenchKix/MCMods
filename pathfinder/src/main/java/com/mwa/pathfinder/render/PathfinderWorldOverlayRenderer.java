package com.mwa.pathfinder.render;

import com.mwa.pathfinder.config.PathfinderClientConfig;
import com.mwa.pathfinder.control.PathfinderController;
import com.mwa.pathfinder.pathing.IPathManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public class PathfinderWorldOverlayRenderer {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final PathfinderController controller;
    private final IPathManager pathManager;
    private final PathfinderClientConfig config;

    public PathfinderWorldOverlayRenderer(PathfinderController controller, IPathManager pathManager, PathfinderClientConfig config) {
        this.controller = controller;
        this.pathManager = pathManager;
        this.config = config;
    }

    public void render(WorldRenderContext context) {
        if (!config.overlayEnabled || context.world() == null || context.camera() == null || context.consumers() == null || context.matrixStack() == null) {
            return;
        }

        Vec3d cam = context.camera().getPos();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        if (config.overlayBlockTarget) {
            BlockPos target = controller.getLastTarget();
            if (target != null) {
                Box box = new Box(target).offset(-cam.x, -cam.y, -cam.z);
                VertexRendering.drawBox(context.matrixStack(), lines, box, 1.0f, 0.86f, 0.25f, 0.9f);
            }
        }

        if (config.overlayFollowEntity) {
            UUID followed = pathManager.getFollowedEntityId();
            if (followed != null && client.world != null) {
                Entity entity = client.world.getEntity(followed);
                if (entity != null) {
                    Box entityBox = entity.getBoundingBox().expand(0.08d).offset(-cam.x, -cam.y, -cam.z);
                    VertexRendering.drawBox(context.matrixStack(), lines, entityBox, 0.22f, 0.95f, 1.0f, 0.95f);
                }
            }
        }
    }
}
