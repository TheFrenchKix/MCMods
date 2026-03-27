package com.mwa.n0name.render;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public class EntityESP {

    private static final double RANGE = 50.0;
    private int refreshCooldown = 0;

    public void tick() {
        if (--refreshCooldown > 0) return;
        refreshCooldown = 60;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ModConfig config = ModConfig.getInstance();
        Box range = client.player.getBoundingBox().expand(RANGE);

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            if (!entity.getBoundingBox().intersects(range)) continue;
            String typeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            config.getOrCreateEntityEntry(typeId);
        }
        DebugLogger.log("EntityESP", "Refreshed entity types");
    }

    public void render(WorldRenderContext context) {
        if (!ModConfig.getInstance().isEntityEspEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Map<String, ModConfig.EspEntry> filters = ModConfig.getInstance().getEntityFilters();
        boolean anyOn = false;
        for (ModConfig.EspEntry e : filters.values()) {
            if (e.enabled) { anyOn = true; break; }
        }
        if (!anyOn) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        MatrixStack matrices = context.matrices();
        TextRenderer textRenderer = client.textRenderer;
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();
        float tickDelta = RenderUtils.getTickDelta();

        Box rangeBox = client.player.getBoundingBox().expand(RANGE);
        VertexConsumer lineConsumer = consumers.getBuffer(RenderLayers.LINES);

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            if (!entity.getBoundingBox().intersects(rangeBox)) continue;

            String typeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            ModConfig.EspEntry entry = filters.get(typeId);
            if (entry == null || !entry.enabled) continue;

            // Interpolated position for smooth rendering
            double ix = entity.lastRenderX + (entity.getX() - entity.lastRenderX) * tickDelta;
            double iy = entity.lastRenderY + (entity.getY() - entity.lastRenderY) * tickDelta;
            double iz = entity.lastRenderZ + (entity.getZ() - entity.lastRenderZ) * tickDelta;

            double dist = Math.sqrt(
                (ix - cam.x) * (ix - cam.x) +
                (iy - cam.y) * (iy - cam.y) +
                (iz - cam.z) * (iz - cam.z)
            );
            if (dist > RANGE) continue;

            int c = entry.color;
            float r = ((c >> 16) & 0xFF) / 255f;
            float g = ((c >>  8) & 0xFF) / 255f;
            float b = ( c        & 0xFF) / 255f;

            // Draw wireframe box around entity bounding box
            Box bb = entity.getBoundingBox();
            double bw = bb.maxX - bb.minX;
            double bh = bb.maxY - bb.minY;
            double bd = bb.maxZ - bb.minZ;
            RenderUtils.drawWireframeBox(matrices, lineConsumer,
                ix - bw / 2, iy, iz - bd / 2,
                bw, bh, bd, r, g, b, 0.85f, cam);

            // Billboard name label above entity
            float alpha = (float)Math.max(0.3, 1.0 - (dist / RANGE) * 0.7);
            int colorArgb = ((int)(alpha * 255) << 24)
                | ((c >> 16) & 0xFF) << 16
                | ((c >>  8) & 0xFF) << 8
                | ( c        & 0xFF);

            String label = entity.getDisplayName().getString() + " (" + (int)dist + "m)";
            float tx = -textRenderer.getWidth(label) / 2f;

            double labelY = iy + bh + 0.3;

            matrices.push();
            matrices.translate((float)(ix - cam.x), (float)(labelY - cam.y), (float)(iz - cam.z));
            matrices.multiply(camera.getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);

            textRenderer.draw(label, tx, 0, colorArgb, false,
                matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x60000000, 0xF000F0);
            matrices.pop();
        }

        RenderUtils.flushLines(consumers);
    }
}
