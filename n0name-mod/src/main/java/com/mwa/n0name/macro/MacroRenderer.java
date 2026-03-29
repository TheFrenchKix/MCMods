package com.mwa.n0name.macro;

import com.mwa.n0name.render.N0nameRenderLayers;
import com.mwa.n0name.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 3D visualization of macro steps.
 * - Colored nodes (boxes) per step type category
 * - Connecting lines between consecutive steps
 * - Current step highlight during execution
 */
public class MacroRenderer {

    private static final double NODE_SIZE = 0.3;
    private static final double NODE_HALF = NODE_SIZE / 2.0;

    // Colors per category (r, g, b, a)
    private static final float[] COL_MOVEMENT    = {0.27f, 1.0f, 0.27f, 0.7f};  // green
    private static final float[] COL_INTERACTION  = {1.0f, 0.87f, 0.27f, 0.7f};  // yellow
    private static final float[] COL_COMBAT       = {1.0f, 0.27f, 0.27f, 0.7f};  // red
    private static final float[] COL_SPECIAL      = {0.73f, 0.27f, 1.0f, 0.7f};  // purple
    private static final float[] COL_FLOW         = {0.27f, 0.53f, 1.0f, 0.7f};  // blue
    private static final float[] COL_LINE         = {0.8f, 0.8f, 0.8f, 0.5f};    // gray lines
    private static final float[] COL_CURRENT      = {1.0f, 1.0f, 0.2f, 0.9f};    // bright yellow highlight
    private static final float[] COL_LINE_ACTIVE  = {0.2f, 1.0f, 0.6f, 0.7f};    // green line for active path

    /**
     * Render a macro's steps as 3D nodes connected by lines.
     * If currentStepIndex >= 0, highlights the active step.
     */
    public void render(WorldRenderContext context, Macro macro, int currentStepIndex) {
        if (macro == null || macro.getSteps().isEmpty()) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;
        MatrixStack matrices = context.matrices();
        Vec3d cam = RenderUtils.getCameraPos();

        VertexConsumer lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);

        List<MacroStep> steps = macro.getSteps();

        // Draw connecting lines
        for (int i = 0; i < steps.size() - 1; i++) {
            // Skip first segment to avoid the start-line artifact near player feet.
            if (i == 0) continue;
            MacroStep from = steps.get(i);
            MacroStep to = steps.get(i + 1);
            double fromY = from.getType() == StepType.WALK ? from.getY() + 1.0 : from.getY() + 0.5;
            double toY   = to.getType()   == StepType.WALK ? to.getY()   + 1.0 : to.getY()   + 0.5;
            Vec3d fromPos = new Vec3d(from.getX() + 0.5, fromY, from.getZ() + 0.5);
            Vec3d toPos   = new Vec3d(to.getX()   + 0.5, toY,   to.getZ()   + 0.5);

            float[] lc = (i == currentStepIndex || i + 1 == currentStepIndex) ? COL_LINE_ACTIVE : COL_LINE;
            RenderUtils.drawLine(matrices, lineConsumer, fromPos, toPos, lc[0], lc[1], lc[2], lc[3], cam);
        }

        // Current step pulse animation
        float pulseScale = 1f;
        float pulseAlpha = 1f;
        if (currentStepIndex >= 0) {
            float t = (float) (System.currentTimeMillis() % 1500) / 1500f;
            pulseScale = 1.0f + 0.3f * (float) Math.sin(t * Math.PI * 2);
            pulseAlpha = 0.25f + 0.15f * (float) Math.sin(t * Math.PI * 2);
        }

        // Draw step nodes
        for (int i = 0; i < steps.size(); i++) {
            MacroStep step = steps.get(i);
            boolean isCurrent = (i == currentStepIndex);
            float[] col = isCurrent ? COL_CURRENT : categoryColor(step.getType().category);

            if (step.getType() == StepType.WALK) {
                RenderUtils.drawWireframeBox(matrices, lineConsumer,
                    step.getX(), step.getY(), step.getZ(),
                    1.0, 1.0, 1.0,
                    col[0], col[1], col[2], col[3], cam);
            } else {
                double size = isCurrent ? NODE_SIZE * 1.4 : NODE_SIZE;
                double half = size / 2.0;
                RenderUtils.drawWireframeBox(matrices, lineConsumer,
                    step.getX() + 0.5 - half, step.getY() + 0.5 - half, step.getZ() + 0.5 - half,
                    size, size, size,
                    col[0], col[1], col[2], col[3], cam);
            }

            // Pulsing glow box behind current step
            if (isCurrent) {
                double glowSize = (step.getType() == StepType.WALK ? 1.0 : NODE_SIZE) * pulseScale;
                double glowHalf = glowSize / 2.0;
                double cx = step.getX() + 0.5;
                double cy = step.getY() + (step.getType() == StepType.WALK ? 0.5 : 0.5);
                double cz = step.getZ() + 0.5;
                RenderUtils.drawWireframeBox(matrices, lineConsumer,
                    cx - glowHalf, cy - glowHalf, cz - glowHalf,
                    glowSize, glowSize, glowSize,
                    col[0], col[1], col[2], pulseAlpha, cam);
            }
        }

        RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);

        // Draw step labels (billboard text above each node)
        renderStepLabels(context, steps, currentStepIndex, cam);
    }

    /**
     * Render just the node preview for a recording in progress.
     */
    public void renderRecording(WorldRenderContext context, Macro macro) {
        render(context, macro, -1);
    }

    /**
     * Render step index and type abbreviation as billboard text above each node.
     */
    private void renderStepLabels(WorldRenderContext context, List<MacroStep> steps, int currentStepIndex, Vec3d cam) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        MatrixStack matrices = context.matrices();

        for (int i = 0; i < steps.size(); i++) {
            MacroStep step = steps.get(i);
            double wx = step.getX() + 0.5 - cam.x;
            double wy = (step.getType() == StepType.WALK ? step.getY() + 1.4 : step.getY() + 1.0) - cam.y;
            double wz = step.getZ() + 0.5 - cam.z;

            // Skip labels too far away
            double distSq = wx * wx + wy * wy + wz * wz;
            if (distSq > 40 * 40) continue;

            String typeAbbrev = switch (step.getType()) {
                case WALK -> "W";
                case MINE -> "M";
                case TELEPORT -> "T";
                case ATTACK -> "A";
                case INTERACT, CLICK -> "I";
                case USE_ITEM -> "U";
                case WAIT -> "..";
                case LOOP -> "L";
                case STOP -> "X";
                default -> "?";
            };
            String label = (i + 1) + typeAbbrev;

            matrices.push();
            matrices.translate(wx, wy, wz);

            // Billboard: face the camera
            float scale = 0.025f;
            matrices.multiply(client.gameRenderer.getCamera().getRotation());
            matrices.scale(-scale, -scale, scale);

            int textW = tr.getWidth(label);
            int color = (i == currentStepIndex) ? 0xFFFFFF44 : 0xFFCCCCCC;

            // Background pill
            Matrix4f mat = matrices.peek().getPositionMatrix();
            // Use DrawContext equivalent manually is not available in world render,
            // so just draw text with shadow which is visible enough
            tr.draw(label, -textW / 2f, 0, color, true, mat,
                    context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, 0xF000F0);

            matrices.pop();
        }
    }

    private float[] categoryColor(StepType.Category cat) {
        return switch (cat) {
            case MOVEMENT -> COL_MOVEMENT;
            case INTERACTION -> COL_INTERACTION;
            case COMBAT -> COL_COMBAT;
            case SPECIAL -> COL_SPECIAL;
            case FLOW -> COL_FLOW;
        };
    }

    // -------------------------------------------------------------------------
    // 2D minimap HUD
    // -------------------------------------------------------------------------

    private static final int HUD_X      = 4;
    private static final int HUD_Y      = 14;
    private static final int HUD_SIZE   = 72;
    private static final double HUD_RADIUS = 32.0;

    /**
     * Draw a small 2D minimap of the macro's step positions, centered on the player.
     * Call this from the HUD render hook (not world render).
     */
    public void renderHud(DrawContext ctx, TextRenderer fr, Macro macro, Vec3d playerPos, int currentStepIndex) {
        if (macro == null || macro.getSteps().isEmpty()) return;

        int sx = HUD_X, sy = HUD_Y, size = HUD_SIZE;
        int pad = 4;

        // Background
        ctx.fill(sx, sy, sx + size, sy + size, 0xAA101418);
        // Border (green accent)
        ctx.fill(sx,          sy,            sx + size,     sy + 1,        0xFF44BB66);
        ctx.fill(sx,          sy + size - 1, sx + size,     sy + size,     0xFF44BB66);
        ctx.fill(sx,          sy,            sx + 1,        sy + size,     0xFF44BB66);
        ctx.fill(sx + size-1, sy,            sx + size,     sy + size,     0xFF44BB66);

        ctx.drawTextWithShadow(fr, "Macro Map", sx + 5, sy + 3, 0xFFCCFFCC);

        int mapX    = sx + pad;
        int mapY    = sy + 12;
        int mapSize = size - pad * 2;

        List<MacroStep> steps = macro.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            MacroStep step = steps.get(i);
            Vec3d pos = new Vec3d(step.getX() + 0.5, step.getY(), step.getZ() + 0.5);
            int[] uv = hudToMap(playerPos, pos, HUD_RADIUS, mapX, mapY, mapSize);
            if (uv == null) continue;

            float[] col = (i == currentStepIndex) ? COL_CURRENT : categoryColor(step.getType().category);
            int rgba = toARGB(col);
            int dotSize = (i == currentStepIndex) ? 3 : 2;
            ctx.fill(uv[0], uv[1], uv[0] + dotSize, uv[1] + dotSize, rgba);
        }

        // Player position (white center dot)
        int cx = mapX + mapSize / 2;
        int cy = mapY + mapSize / 2;
        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
    }

    private int[] hudToMap(Vec3d center, Vec3d point, double radius, int x, int y, int size) {
        double dx = point.x - center.x;
        double dz = point.z - center.z;
        if (Math.abs(dx) > radius || Math.abs(dz) > radius) return null;
        double nx = (dx / radius) * 0.5 + 0.5;
        double nz = (dz / radius) * 0.5 + 0.5;
        int px = x + (int)(nx * size);
        int py = y + (int)(nz * size);
        if (px < x || py < y || px >= x + size || py >= y + size) return null;
        return new int[]{px, py};
    }

    private static int toARGB(float[] col) {
        int a = Math.min(255, (int)(col[3] * 255)) & 0xFF;
        int r = Math.min(255, (int)(col[0] * 255)) & 0xFF;
        int g = Math.min(255, (int)(col[1] * 255)) & 0xFF;
        int b = Math.min(255, (int)(col[2] * 255)) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
