package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.config.ModConfig;
import com.example.macromod.manager.AutoAttackManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ESP renderer for entities, blocks, and the current attack target.
 * Registered on {@code WorldRenderEvents.END_MAIN} for proper vertex flushing.
 */
public class ESPRenderer {

    /**
     * Lines render layer with NO_DEPTH_TEST — draws through walls.
     * Built from RENDERTYPE_LINES_SNIPPET so it uses the same shaders/vertex
     * format as vanilla lines, with depth test disabled and depth write off.
     */
    private static final RenderLayer ESP_LAYER = RenderLayer.of(
            "macromod_esp",
            RenderSetup.builder(
                    RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withLocation("esp_lines")
                            .build()
            ).translucent().expectedBufferSize(1536).build()
    );

    /** How often (ticks) to re-scan blocks for the block ESP cache. */
    private static final int BLOCK_SCAN_INTERVAL = 20;

    private int tickCounter = 0;
    private final List<BlockPos> cachedBlockPositions = new ArrayList<>();

    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ModConfig cfg = MacroModClient.getConfigManager().getConfig();

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        MatrixStack matrices = context.matrices();
        if (matrices == null) return;

        Vec3d camPos = context.gameRenderer().getCamera().getCameraPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        VertexConsumer vc = consumers.getBuffer(ESP_LAYER);

        // Get tick delta for entity position interpolation
        float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);

        // ── 1. Target ESP (red box around current attack target) ──
        if (cfg.isTargetEspEnabled()) {
            LivingEntity target = AutoAttackManager.getInstance().getCurrentTarget();
            if (target != null && target.isAlive()) {
                Box box = lerpedBox(target, tickDelta).expand(0.05);
                drawBoxOutline(matrices, vc, box, 1.0f, 0.0f, 0.0f, 1.0f);
            }
        }

        // ── 2. Entities ESP (green boxes around whitelisted entities) ──
        if (cfg.isEntitiesEspEnabled()) {
            Set<String> whitelist = new HashSet<>(cfg.getEntityWhitelist());
            LivingEntity attackTarget = AutoAttackManager.getInstance().getCurrentTarget();
            for (Entity entity : client.world.getEntities()) {
                if (entity == client.player) continue;
                if (!(entity instanceof LivingEntity le)) continue;
                if (!le.isAlive()) continue;
                if (attackTarget != null && entity == attackTarget) continue;

                String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                if (whitelist.contains(entityId)) {
                    Box box = lerpedBox(entity, tickDelta).expand(0.03);
                    drawBoxOutline(matrices, vc, box, 0.0f, 1.0f, 0.3f, 0.7f);
                }
            }
        }

        // ── 3. Block ESP (magenta boxes around whitelisted blocks) ──
        if (cfg.isBlockEspEnabled()) {
            tickCounter++;
            if (tickCounter >= BLOCK_SCAN_INTERVAL) {
                tickCounter = 0;
                scanBlocks(client, cfg);
            }
            for (BlockPos pos : cachedBlockPositions) {
                Box box = new Box(
                        pos.getX() - 0.002, pos.getY() - 0.002, pos.getZ() - 0.002,
                        pos.getX() + 1.002, pos.getY() + 1.002, pos.getZ() + 1.002);
                drawBoxOutline(matrices, vc, box, 0.9f, 0.2f, 1.0f, 0.8f);
            }
        } else {
            cachedBlockPositions.clear();
            tickCounter = 0;
        }

        matrices.pop();
    }

    /**
     * Returns a bounding box at the entity's render-interpolated position.
     * Uses {@code getCameraPosVec(tickDelta)} for smooth frame-accurate placement.
     */
    private static Box lerpedBox(Entity entity, float tickDelta) {
        Vec3d eye = entity.getCameraPosVec(tickDelta);
        double eyeH = entity.getStandingEyeHeight();
        double feetY = eye.y - eyeH;
        float w = entity.getWidth() / 2f;
        float h = entity.getHeight();
        return new Box(eye.x - w, feetY, eye.z - w,
                       eye.x + w, feetY + h, eye.z + w);
    }

    /** Scans nearby blocks for whitelisted block IDs. */
    private void scanBlocks(MinecraftClient client, ModConfig cfg) {
        cachedBlockPositions.clear();
        if (client.player == null || client.world == null) return;

        Set<Identifier> whitelist = new HashSet<>();
        for (String id : cfg.getBlockWhitelist()) {
            whitelist.add(Identifier.of(id));
        }
        if (whitelist.isEmpty()) return;

        BlockPos center = client.player.getBlockPos();
        int r = cfg.getBlockEspRadius();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Identifier blockId = Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock());
                    if (whitelist.contains(blockId)) {
                        cachedBlockPositions.add(pos);
                    }
                }
            }
        }
    }

    // ── Box outline helper ──────────────────────────────────────────

    private static void drawBoxOutline(MatrixStack matrices, VertexConsumer vc,
                                        Box box, float r, float g, float b, float a) {
        drawBoxOutline(matrices, vc,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                r, g, b, a);
    }

    private static void drawBoxOutline(MatrixStack matrices, VertexConsumer vc,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2,
                                        float r, float g, float b, float a) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        // Bottom face
        addLine(m, vc, x1, y1, z1, x2, y1, z1, r, g, b, a);
        addLine(m, vc, x2, y1, z1, x2, y1, z2, r, g, b, a);
        addLine(m, vc, x2, y1, z2, x1, y1, z2, r, g, b, a);
        addLine(m, vc, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // Top face
        addLine(m, vc, x1, y2, z1, x2, y2, z1, r, g, b, a);
        addLine(m, vc, x2, y2, z1, x2, y2, z2, r, g, b, a);
        addLine(m, vc, x2, y2, z2, x1, y2, z2, r, g, b, a);
        addLine(m, vc, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // Vertical edges
        addLine(m, vc, x1, y1, z1, x1, y2, z1, r, g, b, a);
        addLine(m, vc, x2, y1, z1, x2, y2, z1, r, g, b, a);
        addLine(m, vc, x2, y1, z2, x2, y2, z2, r, g, b, a);
        addLine(m, vc, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void addLine(Matrix4f m, VertexConsumer vc,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        float dx = (float)(x2 - x1), dy = (float)(y2 - y1), dz = (float)(z2 - z1);
        float len = Math.max((float) Math.sqrt(dx * dx + dy * dy + dz * dz), 1e-4f);
        vc.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(dx / len, dy / len, dz / len).lineWidth(2.0f);
        vc.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(dx / len, dy / len, dz / len).lineWidth(2.0f);
    }
}
