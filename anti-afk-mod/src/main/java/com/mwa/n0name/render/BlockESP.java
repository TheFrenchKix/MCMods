package com.mwa.n0name.render;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlockESP {

    private static final int RADIUS_XZ   = 12;
    private static final int RADIUS_Y    = 4;
    private static final int SCAN_INTERVAL = 40;
    private static final int MAX_TYPES   = 40;

    private int scanCooldown = 0;
    private final Map<String, List<BlockPos>> scannedBlocks = new ConcurrentHashMap<>();

    public void tick() {
        if (--scanCooldown > 0) return;
        scanCooldown = SCAN_INTERVAL;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int px = (int)client.player.getX();
        int py = (int)client.player.getY();
        int pz = (int)client.player.getZ();

        ModConfig config = ModConfig.getInstance();
        Map<String, List<BlockPos>> fresh = new LinkedHashMap<>();

        for (int x = px - RADIUS_XZ; x <= px + RADIUS_XZ; x++) {
            for (int z = pz - RADIUS_XZ; z <= pz + RADIUS_XZ; z++) {
                for (int y = py - RADIUS_Y; y <= py + RADIUS_Y; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;

                    String id = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (!fresh.containsKey(id) && fresh.size() >= MAX_TYPES) continue;

                    config.getOrCreateBlockEntry(id);
                    fresh.computeIfAbsent(id, k -> new ArrayList<>()).add(pos.toImmutable());
                }
            }
        }

        scannedBlocks.clear();
        scannedBlocks.putAll(fresh);
        DebugLogger.log("BlockESP", "Scanned " + fresh.size() + " block types");
    }

    public void render(WorldRenderContext context) {
        if (!ModConfig.getInstance().isBlockEspEnabled()) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        Map<String, ModConfig.EspEntry> filters = ModConfig.getInstance().getBlockFilters();
        if (filters.isEmpty()) return;

        boolean anyOn = false;
        for (ModConfig.EspEntry e : filters.values()) {
            if (e.enabled) { anyOn = true; break; }
        }
        if (!anyOn) return;

        Vec3d cam = RenderUtils.getCameraPos();
        MatrixStack matrices = context.matrices();
        VertexConsumer consumer = consumers.getBuffer(RenderLayers.LINES);

        matrices.push();

        for (Map.Entry<String, ModConfig.EspEntry> fe : filters.entrySet()) {
            if (!fe.getValue().enabled) continue;
            List<BlockPos> positions = scannedBlocks.get(fe.getKey());
            if (positions == null) continue;

            int argb = fe.getValue().color;
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >>  8) & 0xFF) / 255f;
            float b = ( argb        & 0xFF) / 255f;

            for (BlockPos pos : positions) {
                RenderUtils.drawWireframeBox(matrices, consumer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    1.0, 1.0, 1.0, r, g, b, 0.9f, cam);
            }
        }

        matrices.pop();
        RenderUtils.flushLines(consumers);
    }
}
