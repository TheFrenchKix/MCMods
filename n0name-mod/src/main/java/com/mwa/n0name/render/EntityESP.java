package com.mwa.n0name.render;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EntityESP {

    private int refreshCooldown = 0;
    private final Set<String> visibleEntityTypes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void tick() {
        if (--refreshCooldown > 0) return;
        refreshCooldown = 60;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ModConfig config = ModConfig.getInstance();
        double range = config.getEntityEspRange();
        Box rangeBox = client.player.getBoundingBox().expand(range);
        Set<String> fresh = new HashSet<>();

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            if (!entity.getBoundingBox().intersects(rangeBox)) continue;
            String typeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            fresh.add(typeId);
            config.getOrCreateEntityEntry(typeId);
        }
        visibleEntityTypes.clear();
        visibleEntityTypes.addAll(fresh);
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

        MatrixStack matrices = context.matrices();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();
        float tickDelta = RenderUtils.getTickDelta();
        double range = ModConfig.getInstance().getEntityEspRange();

        // Tracers should start at player feet, not eyes.
        Vec3d feetPos = new Vec3d(
            MathHelper.lerp(tickDelta, client.player.lastRenderX, client.player.getX()),
            MathHelper.lerp(tickDelta, client.player.lastRenderY, client.player.getY()),
            MathHelper.lerp(tickDelta, client.player.lastRenderZ, client.player.getZ())
        );

        Box rangeBox = client.player.getBoundingBox().expand(range);

        // Collect boxes grouped by color for batched rendering
        Map<Integer, List<Box>> colorBoxes = new LinkedHashMap<>();
        Map<Integer, List<Vec3d>> colorTracerTargets = new LinkedHashMap<>();
        List<EntityLabel> labels = new ArrayList<>();

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            if (!entity.getBoundingBox().intersects(rangeBox)) continue;

            String typeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            ModConfig.EspEntry entry = filters.get(typeId);
            if (entry == null || !entry.enabled) continue;

            // Smooth interpolated position
            double ix = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double iy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double iz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

            double dist = Math.sqrt(
                (ix - cam.x) * (ix - cam.x) +
                (iy - cam.y) * (iy - cam.y) +
                (iz - cam.z) * (iz - cam.z)
            );
            if (dist > range) continue;

            // Build interpolated bounding box
            Box bb = entity.getBoundingBox();
            double bw = bb.maxX - bb.minX;
            double bh = bb.maxY - bb.minY;
            double bd = bb.maxZ - bb.minZ;
            Box espBox = new Box(
                ix - bw / 2, iy, iz - bd / 2,
                ix + bw / 2, iy + bh, iz + bd / 2
            );

            int color = entry.color & 0x00FFFFFF;
            colorBoxes.computeIfAbsent(color, k -> new ArrayList<>()).add(espBox);

            // Tracer target = center of bounding box
            Vec3d center = new Vec3d(ix, iy + bh / 2, iz);
            colorTracerTargets.computeIfAbsent(color, k -> new ArrayList<>()).add(center);

            // Label data
            float alpha = (float)Math.max(0.3, 1.0 - (dist / range) * 0.7);
            labels.add(new EntityLabel(entity, ix, iy + bh + 0.3, iz, dist, alpha, color));
        }

        // Render ESP using custom layers.
        // IMPORTANT: Only one custom layer can be active at a time in the Immediate fallback slot,
        // so we do two separate passes: fills (ESP_QUADS) then outlines+tracers (ESP_LINES).
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        // --- Pass 1: filled boxes (ESP_QUADS, no depth test) ---
        {
            var quadConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_QUADS);
            for (Map.Entry<Integer, List<Box>> e : colorBoxes.entrySet()) {
                RenderUtils.renderEspFills(matrices, quadConsumer, e.getValue(), e.getKey(), 0.22f, cam);
            }
            RenderUtils.flush(consumers, N0nameRenderLayers.ESP_QUADS);
        }

        // --- Pass 2: outline boxes + tracer lines (ESP_LINES, no depth test) ---
        {
            var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
            for (Map.Entry<Integer, List<Box>> e : colorBoxes.entrySet()) {
                RenderUtils.renderEspOutlines(matrices, lineConsumer, e.getValue(), e.getKey(), 0.7f, cam);
            }
            for (Map.Entry<Integer, List<Vec3d>> e : colorTracerTargets.entrySet()) {
                RenderUtils.renderEspTracers(matrices, lineConsumer, feetPos, e.getValue(), e.getKey(), 0.5f, cam);
            }
            RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
        }

        // Render billboard name labels (uses context consumers for text)
        TextRenderer textRenderer = client.textRenderer;

        for (EntityLabel label : labels) {
            int colorArgb = ((int)(label.alpha * 255) << 24)
                | ((label.color >> 16) & 0xFF) << 16
                | ((label.color >> 8) & 0xFF) << 8
                | (label.color & 0xFF);

            String text = label.entity.getDisplayName().getString() + " (" + (int)label.dist + "m)";
            float tx = -textRenderer.getWidth(text) / 2f;

            matrices.push();
            matrices.translate((float)(label.x - cam.x), (float)(label.y - cam.y), (float)(label.z - cam.z));
            matrices.multiply(camera.getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);

            textRenderer.draw(text, tx, 0, colorArgb, false,
                matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x60000000, 0xF000F0);
            matrices.pop();
        }
    }

    public List<String> getVisibleEntityTypeIds() {
        List<String> ids = new ArrayList<>(visibleEntityTypes);
        ids.sort(String::compareTo);
        return ids;
    }

    private record EntityLabel(Entity entity, double x, double y, double z, double dist, float alpha, int color) {}
}
