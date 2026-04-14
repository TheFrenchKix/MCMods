package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.config.ModConfig;
import com.example.macromod.manager.AutoAttackManager;
import com.example.macromod.manager.HotspotManager;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ESP renderer for entities, blocks, and the current attack target.
 * Registered on {@code WorldRenderEvents.END_MAIN} for proper vertex flushing.
 */
public class ESPRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");

    private static final double PLAYER_HEAD_SIZE = 0.55;

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

        private static final int HOTSPOT_CIRCLE_SEGMENTS = 96;
        private static final float HOTSPOT_CIRCLE_RADIUS   = 3.5f;
        private static final double HOTSPOT_VERTICAL_OFFSET = -1.5; // Render circle at water level, slightly below the ArmorStand center Y of ~0.3 blocks.
        private static final int HOTSPOT_FILL_STEPS = 22;

    private static final int BLOCK_SCAN_INTERVAL = 20;

    private int tickCounter = 0;
    private int hotspotDebugCounter = 0;
    private final List<BlockPos> cachedBlockPositions = new ArrayList<>();

    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ModConfig cfg = MacroModClient.getConfigManager().getConfig();

        boolean targetEspEnabled = cfg.isTargetEspEnabled();
        boolean entitiesEspEnabled = cfg.isEntitiesEspEnabled();
        boolean blockEspEnabled = cfg.isBlockEspEnabled();
        boolean fairyEspEnabled = cfg.isFairySoulsEspEnabled();
        boolean hotspotEspEnabled = cfg.isHotspotEspEnabled();

        // Global fast path: nothing enabled -> no render/scans.
        if (!(targetEspEnabled || entitiesEspEnabled || blockEspEnabled || fairyEspEnabled || hotspotEspEnabled)) {
            cachedBlockPositions.clear();
            tickCounter = 0;
            return;
        }

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
        if (targetEspEnabled) {
            LivingEntity target = AutoAttackManager.getInstance().getCurrentTarget();
            if (target != null && target.isAlive()) {
                Box box = lerpedBox(target, tickDelta).expand(0.05);
                drawBoxOutline(matrices, vc, box, 1.0f, 0.0f, 0.0f, 1.0f);
            }
        }

        // ── 2. Entities ESP (green boxes around whitelisted entities) ──
        if (entitiesEspEnabled) {
            Set<String> whitelist = new HashSet<>(cfg.getEntityWhitelist());
            LivingEntity attackTarget = AutoAttackManager.getInstance().getCurrentTarget();
            for (Entity entity : client.world.getEntities()) {
                if (entity == client.player) continue;
                if (!(entity instanceof LivingEntity le)) continue;
                if (!le.isAlive()) continue;
                if (le instanceof net.minecraft.entity.player.PlayerEntity) continue;
                if (le instanceof ArmorStandEntity) continue;
                if (le.isInvisible()) continue;
                if (attackTarget != null && entity == attackTarget) continue;

                String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                if (whitelist.contains(entityId)) {
                    Box box = lerpedBox(entity, tickDelta).expand(0.03);
                    drawBoxOutline(matrices, vc, box, 0.0f, 1.0f, 0.3f, 0.7f);
                }
            }
        }

        // ── 3. Block ESP (magenta boxes around whitelisted blocks) ──
        if (blockEspEnabled) {
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

        // ── 4. Fairy Souls ESP (cyan boxes around likely Fairy Soul stands) ──
        if (fairyEspEnabled) {
            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof ArmorStandEntity ase && isLikelyFairySoulStand(ase)) {
                    // Render around the actual player_head size and orientation.
                    drawHeadBoxWithHeading(matrices, vc, ase, tickDelta, 0.0f, 1.0f, 1.0f, 0.8f);
                }
            }
        }

        // ── 5. Hotspot ESP (through-walls full circle + fill) ──
        if (hotspotEspEnabled) {
            List<Vec3d> hotspots = HotspotManager.getInstance().getHotspots();

            for (Vec3d hotspot : hotspots) {
                // Hotspot ArmorStands are elevated above water; render the circle at water level.
                Vec3d center = hotspot.add(0.0, HOTSPOT_VERTICAL_OFFSET, 0.0);
                drawFilledCircleNoDepth(matrices, vc, center, HOTSPOT_CIRCLE_RADIUS, 1.0f, 0.88f, 0.12f, 0.30f);
                drawCircleOutlineNoDepth(matrices, vc, center, HOTSPOT_CIRCLE_RADIUS, 1.0f, 0.96f, 0.22f, 0.95f);
            }
        }

        matrices.pop();
    }

    /** Renders a horizontal circle outline in XZ plane using the no-depth line layer. */
    private static void drawCircleOutlineNoDepth(MatrixStack matrices, VertexConsumer vc,
                                                 Vec3d center, float radius,
                                                 float r, float g, float b, float a) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        float cx = (float) center.x;
        float cy = (float) center.y;
        float cz = (float) center.z;

        for (int i = 0; i < HOTSPOT_CIRCLE_SEGMENTS; i++) {
            double a0 = 2.0 * Math.PI * i / HOTSPOT_CIRCLE_SEGMENTS;
            double a1 = 2.0 * Math.PI * (i + 1) / HOTSPOT_CIRCLE_SEGMENTS;

            float x0 = cx + radius * (float) Math.cos(a0);
            float z0 = cz + radius * (float) Math.sin(a0);
            float x1 = cx + radius * (float) Math.cos(a1);
            float z1 = cz + radius * (float) Math.sin(a1);

            addLine(m, vc, x0, cy, z0, x1, cy, z1, r, g, b, a);
        }
    }

    /** Simulates a filled disc through walls by drawing many concentric no-depth rings. */
    private static void drawFilledCircleNoDepth(MatrixStack matrices, VertexConsumer vc,
                                                Vec3d center, float radius,
                                                float r, float g, float b, float a) {
        for (int step = 1; step <= HOTSPOT_FILL_STEPS; step++) {
            float t = (float) step / HOTSPOT_FILL_STEPS;
            float ringRadius = radius * t;
            float ringAlpha = a * (0.35f + 0.65f * t);
            drawCircleOutlineNoDepth(matrices, vc, center, ringRadius, r, g, b, ringAlpha);
        }
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

    /**
     * Draws an oriented head box using player_head size (0.5 blocks) and armor stand heading.
     */
    private static void drawHeadBoxWithHeading(MatrixStack matrices,
                                               VertexConsumer vc,
                                               ArmorStandEntity ase,
                                               float tickDelta,
                                               float r, float g, float b, float a) {
        Box body = lerpedBox(ase, tickDelta);
        double centerX = (body.minX + body.maxX) * 0.5;
        double centerY = body.maxY - (PLAYER_HEAD_SIZE * 0.5);
        double centerZ = (body.minZ + body.maxZ) * 0.5;

        matrices.push();
        matrices.translate(centerX, centerY, centerZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-ase.getYaw()));

        double h = PLAYER_HEAD_SIZE * 0.5;
        drawBoxOutline(matrices, vc,
                -h, -h, -h,
                 h,  h,  h,
                r, g, b, a);
        matrices.pop();
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

    /**
     * Tuned from in-game debug samples:
     * - should render: invisible, normal-sized, non-marker, no arms, has baseplate, wearing player head
     * - should not render: visible stands, small stands, marker stands, armed stands, no head item
     */
    private static boolean isLikelyFairySoulStand(ArmorStandEntity ase) {
        if (!ase.isInvisible() || ase.isMarker() || ase.isSmall() || ase.shouldShowArms() || !ase.shouldShowBasePlate()) {
            return false;
        }
        
        // Must be wearing a player head
        Identifier headId = Registries.ITEM.getId(ase.getEquippedStack(EquipmentSlot.HEAD).getItem());
        return headId.toString().equals("minecraft:player_head");
    }

    // ── Filled circle helper ────────────────────────────────────────

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
