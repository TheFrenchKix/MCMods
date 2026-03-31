package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.manager.MacroExecutor;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroState;
import com.example.macromod.model.MacroStep;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders the active macro's path and step destinations in the world.
 *
 * <p>Colour legend:
 * <ul>
 *   <li>Green outline = step destination (completed)</li>
 *   <li>Orange outline = current step destination</li>
 *   <li>White outline = future step destination</li>
 *   <li>Cyan node/line = waypoint already passed</li>
 *   <li>Yellow node/line = next waypoint</li>
 *   <li>Blue node/line = upcoming waypoints</li>
 * </ul>
 * Registered via {@code WorldRenderEvents.AFTER_TRANSLUCENT_RENDER}.
 */
public class PathDebugRenderer {

    public void onWorldRender(WorldRenderContext context) {
        MacroExecutor executor = MacroModClient.getExecutor();
        MacroState state = executor.getState();

        // Only render when a macro is actively running
        if (state == MacroState.IDLE || state == MacroState.COMPLETED || state == MacroState.ERROR) {
            return;
        }

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;
        VertexConsumerProvider.Immediate immediate =
                consumers instanceof VertexConsumerProvider.Immediate
                ? (VertexConsumerProvider.Immediate) consumers : null;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Macro macro = executor.getCurrentMacro();
        if (macro == null) return;

        Vec3d camPos = context.camera().getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

        // ── Step destination markers (all steps) ──────────────────
        drawStepDestinations(matrices, vc, macro.getSteps(), executor.getCurrentStepIndex());

        // ── Current path nodes + connecting lines ──────────────────
        List<BlockPos> path = executor.getCurrentPath();
        if (path != null && !path.isEmpty()) {
            int pathIdx = executor.getCurrentPathIndex();
            drawPathNodes(matrices, vc, path, pathIdx);
            drawPathLines(matrices, vc, path, pathIdx);
        }

        if (immediate != null) immediate.draw(RenderLayer.getLines());
        matrices.pop();
    }

    // ── Step destinations ──────────────────────────────────────────

    private void drawStepDestinations(MatrixStack matrices, VertexConsumer vc,
                                      List<MacroStep> steps, int currentStep) {
        for (int i = 0; i < steps.size(); i++) {
            BlockPos dest = steps.get(i).getDestination();
            // Slightly larger box so it's visible through terrain
            Box box = new Box(
                    dest.getX() - 0.08, dest.getY() - 0.08, dest.getZ() - 0.08,
                    dest.getX() + 1.08, dest.getY() + 1.08, dest.getZ() + 1.08);

            if (i < currentStep) {
                drawBoxOutline(matrices, vc, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.15f, 0.9f, 0.15f, 0.55f); // green - done
            } else if (i == currentStep) {
                drawBoxOutline(matrices, vc, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 1.0f, 0.55f, 0.0f, 1.0f);  // orange - active
            } else {
                drawBoxOutline(matrices, vc, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.85f, 0.85f, 0.85f, 0.4f); // white - future
            }
        }
    }

    // ── Path node boxes ───────────────────────────────────────────

    private void drawPathNodes(MatrixStack matrices, VertexConsumer vc,
                               List<BlockPos> path, int currentIdx) {
        for (int i = 0; i < path.size(); i++) {
            BlockPos wp = path.get(i);
            // Slightly inset box to distinguish from step destination markers
            Box box = new Box(
                    wp.getX() + 0.15, wp.getY() + 0.02, wp.getZ() + 0.15,
                    wp.getX() + 0.85, wp.getY() + 0.85, wp.getZ() + 0.85);

            if (i < currentIdx) {
                drawBoxOutline(matrices, vc, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.0f, 0.75f, 0.9f, 0.25f);  // dim cyan - passed
            } else if (i == currentIdx) {
                drawBoxOutline(matrices, vc, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 1.0f, 1.0f, 0.0f, 1.0f);    // bright yellow - next
            } else {
                drawBoxOutline(matrices, vc, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.25f, 0.45f, 1.0f, 0.55f);  // blue - upcoming
            }
        }
    }

    // ── Connecting lines ──────────────────────────────────────────

    private void drawPathLines(MatrixStack matrices, VertexConsumer vc,
                               List<BlockPos> path, int currentIdx) {
        Matrix4f posMat = matrices.peek().getPositionMatrix();

        for (int i = 0; i < path.size() - 1; i++) {
            // Raise slightly above block surface so lines are visible
            Vec3d from = Vec3d.ofCenter(path.get(i)).add(0, 0.45, 0);
            Vec3d to   = Vec3d.ofCenter(path.get(i + 1)).add(0, 0.45, 0);

            float r, g, b, a;
            if (i < currentIdx) {
                r = 0.0f; g = 0.75f; b = 0.9f; a = 0.22f;  // dim cyan
            } else if (i == currentIdx) {
                r = 1.0f; g = 1.0f;  b = 0.0f; a = 0.9f;   // yellow
            } else {
                r = 0.25f; g = 0.45f; b = 1.0f; a = 0.5f;  // blue
            }

            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double dz = to.z - from.z;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-4f) continue;
            float nx = (float) (dx / len);
            float ny = (float) (dy / len);
            float nz = (float) (dz / len);

            vc.vertex(posMat, (float) from.x, (float) from.y, (float) from.z)
              .color(r, g, b, a).normal(nx, ny, nz);
            vc.vertex(posMat, (float) to.x, (float) to.y, (float) to.z)
              .color(r, g, b, a).normal(nx, ny, nz);
        }
    }

    // ── Box outline helper (replaces removed WorldRenderer.drawBox) ────────

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
        vc.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(dx / len, dy / len, dz / len);
        vc.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(dx / len, dy / len, dz / len);
    }
}
