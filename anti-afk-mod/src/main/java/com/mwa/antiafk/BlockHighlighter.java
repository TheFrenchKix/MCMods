package com.mwa.antiafk;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Affiche des outlines sur les blocs détectés par le BlockAnalyzer.
 * Rouge = obstacle bloquant, Jaune = vide détecté.
 */
public class BlockHighlighter {

    private static class HighlightEntry {
        final BlockPos pos;
        final float r, g, b;
        int ticksRemaining;

        HighlightEntry(BlockPos pos, float r, float g, float b, int ticks) {
            this.pos = pos;
            this.r = r;
            this.g = g;
            this.b = b;
            this.ticksRemaining = ticks;
        }
    }

    private final CopyOnWriteArrayList<HighlightEntry> highlights = new CopyOnWriteArrayList<>();
    private boolean enabled = true;

    /** Ajoute un bloc bloquant (rouge). */
    public void addBlocked(BlockPos pos) {
        highlights.add(new HighlightEntry(pos, 1.0f, 0.2f, 0.2f, 40));
    }

    /** Ajoute un bloc vide (jaune). */
    public void addVoid(BlockPos pos) {
        highlights.add(new HighlightEntry(pos, 1.0f, 1.0f, 0.2f, 40));
    }

    /** Met à jour le timer des highlights (appelé chaque tick). */
    public void tick() {
        Iterator<HighlightEntry> it = highlights.iterator();
        while (it.hasNext()) {
            HighlightEntry entry = it.next();
            entry.ticksRemaining--;
            if (entry.ticksRemaining <= 0) {
                highlights.remove(entry);
            }
        }
    }

    /** Rendu des outlines (appelé par WorldRenderEvents.LAST). */
    public void render(WorldRenderContext context) {
        if (!enabled || highlights.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();

        matrices.push();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();

        for (HighlightEntry entry : highlights) {
            double x = entry.pos.getX() - cameraPos.x;
            double y = entry.pos.getY() - cameraPos.y;
            double z = entry.pos.getZ() - cameraPos.z;

            // Outline légèrement dilaté
            Box box = new Box(x - 0.002, y - 0.002, z - 0.002,
                    x + 1.002, y + 1.002, z + 1.002);

            // Alpha fade-out dans les 10 derniers ticks
            float alpha = entry.ticksRemaining < 10 ? entry.ticksRemaining / 10.0f : 0.8f;

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            drawBoxOutline(tessellator, matrix, box, entry.r, entry.g, entry.b, alpha);
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);

        matrices.pop();
    }

    /** Dessine les 12 arêtes d'une box en lignes. */
    private void drawBoxOutline(Tessellator tessellator, Matrix4f matrix, Box box,
                                float r, float g, float b, float a) {
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        int color = ((int) (a * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);

        // Bottom face
        line(buffer, matrix, x1, y1, z1, x2, y1, z1, color);
        line(buffer, matrix, x2, y1, z1, x2, y1, z2, color);
        line(buffer, matrix, x2, y1, z2, x1, y1, z2, color);
        line(buffer, matrix, x1, y1, z2, x1, y1, z1, color);

        // Top face
        line(buffer, matrix, x1, y2, z1, x2, y2, z1, color);
        line(buffer, matrix, x2, y2, z1, x2, y2, z2, color);
        line(buffer, matrix, x2, y2, z2, x1, y2, z2, color);
        line(buffer, matrix, x1, y2, z2, x1, y2, z1, color);

        // Vertical edges
        line(buffer, matrix, x1, y1, z1, x1, y2, z1, color);
        line(buffer, matrix, x2, y1, z1, x2, y2, z1, color);
        line(buffer, matrix, x2, y1, z2, x2, y2, z2, color);
        line(buffer, matrix, x1, y1, z2, x1, y2, z2, color);

        tessellator.draw();
    }

    private void line(BufferBuilder buffer, Matrix4f matrix,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      int color) {
        buffer.vertex(matrix, x1, y1, z1).color(color).next();
        buffer.vertex(matrix, x2, y2, z2).color(color).next();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void clear() {
        highlights.clear();
    }
}
