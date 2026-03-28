package com.mwa.n0name.modules;

import com.mwa.n0name.ModConfig;
import com.mwa.n0name.render.N0nameRenderLayers;
import com.mwa.n0name.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated player ESP with friend highlighting and richer labels.
 */
public class PlayerEspModule {

    private static final int COLOR_PLAYER = 0xFFFFFFFF;
    private static final int COLOR_FRIEND = 0xFF44FF44;

    public void tick() {
        // No periodic state needed for now.
    }

    public void render(WorldRenderContext context) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.isPlayerEspEnabled() && !cfg.isFriendListEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        MatrixStack matrices = context.matrices();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();
        float tickDelta = RenderUtils.getTickDelta();
        var consumers = context.consumers();
        if (consumers == null) return;

        Vec3d self = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        List<Label> labels = new ArrayList<>();

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player) continue;
            double d2 = p.squaredDistanceTo(self);
            if (d2 > 50.0 * 50.0) continue;

            boolean isFriend = cfg.isFriendListAutoNearby() || cfg.isFriend(p.getName().getString());
            if (!cfg.isPlayerEspEnabled() && !isFriend) continue;
            if (isFriend && !cfg.isFriendListEnabled()) continue;

            double ix = MathHelper.lerp(tickDelta, p.lastRenderX, p.getX());
            double iy = MathHelper.lerp(tickDelta, p.lastRenderY, p.getY());
            double iz = MathHelper.lerp(tickDelta, p.lastRenderZ, p.getZ());

            Box bb = p.getBoundingBox();
            double bw = bb.maxX - bb.minX;
            double bh = bb.maxY - bb.minY;
            double bd = bb.maxZ - bb.minZ;
            Box espBox = new Box(ix - bw / 2, iy, iz - bd / 2, ix + bw / 2, iy + bh, iz + bd / 2);

            int color = isFriend ? COLOR_FRIEND : COLOR_PLAYER;
            var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
            RenderUtils.renderEspOutlines(matrices, lineConsumer, java.util.Collections.singletonList(espBox), color, 0.9f, cam);
            RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);

            labels.add(buildLabel(p, ix, iy + bh + 0.4, iz, isFriend));
        }

        TextRenderer tr = client.textRenderer;
        for (Label label : labels) {
            matrices.push();
            matrices.translate((float)(label.x - cam.x), (float)(label.y - cam.y), (float)(label.z - cam.z));
            matrices.multiply(camera.getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);
            float tx = -tr.getWidth(label.text) / 2f;
            tr.draw(label.text, tx, 0, label.color, false,
                matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x60000000, 0xF000F0);
            matrices.pop();
        }
    }

    private Label buildLabel(PlayerEntity p, double x, double y, double z, boolean friend) {
        ModConfig cfg = ModConfig.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(p.getName().getString());
        if (cfg.isPlayerEspShowHealth()) {
            sb.append(" HP:").append((int)Math.ceil(p.getHealth()));
        }
        if (cfg.isPlayerEspShowWeapon()) {
            ItemStack held = p.getMainHandStack();
            if (!held.isEmpty()) sb.append(" W:").append(held.getName().getString());
        }
        if (cfg.isPlayerEspShowArmor()) {
            int pieces = 0;
            if (!p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) pieces++;
            if (!p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) pieces++;
            if (!p.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) pieces++;
            if (!p.getEquippedStack(EquipmentSlot.FEET).isEmpty()) pieces++;
            sb.append(" A:").append(pieces).append("/4");
        }
        return new Label(sb.toString(), x, y, z, friend ? COLOR_FRIEND : COLOR_PLAYER);
    }

    private record Label(String text, double x, double y, double z, int color) {}
}
