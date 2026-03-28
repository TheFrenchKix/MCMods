package com.mwa.n0name.render;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlockESP {

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
        double blockRange = config.getBlockEspRange();
        int radiusXZ = Math.max(5, (int)(blockRange / 2.0));
        int radiusY = Math.max(2, (int)(blockRange / 5.0));
        
        Map<String, List<BlockPos>> fresh = new LinkedHashMap<>();

        for (int x = px - radiusXZ; x <= px + radiusXZ; x++) {
            for (int z = pz - radiusXZ; z <= pz + radiusXZ; z++) {
                for (int y = py - radiusY; y <= py + radiusY; y++) {
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

        Map<String, ModConfig.EspEntry> filters = ModConfig.getInstance().getBlockFilters();
        if (filters.isEmpty()) return;

        boolean anyOn = false;
        for (ModConfig.EspEntry e : filters.values()) {
            if (e.enabled) { anyOn = true; break; }
        }
        if (!anyOn) return;

        Vec3d cam = RenderUtils.getCameraPos();
        MatrixStack matrices = context.matrices();

        // Group block positions by color for batched ESP rendering
        Map<Integer, List<Box>> colorBoxes = new LinkedHashMap<>();

        for (Map.Entry<String, ModConfig.EspEntry> fe : filters.entrySet()) {
            if (!fe.getValue().enabled) continue;
            List<BlockPos> positions = scannedBlocks.get(fe.getKey());
            if (positions == null) continue;

            int color = fe.getValue().color & 0x00FFFFFF;

            List<Box> boxes = colorBoxes.computeIfAbsent(color, k -> new ArrayList<>());
            for (BlockPos pos : positions) {
                boxes.add(new Box(pos.getX(), pos.getY(), pos.getZ(),
                                  pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
            }
        }

        // Render all ESP boxes through walls.
        // Two separate passes to avoid the Immediate fallback slot aliasing crash.
        var consumers = context.consumers();
        if (consumers == null) return;

        // --- Pass 1: fills ---
        {
            var quadConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_QUADS);
            for (Map.Entry<Integer, List<Box>> e : colorBoxes.entrySet()) {
                RenderUtils.renderEspFills(matrices, quadConsumer, e.getValue(), e.getKey(), 0.18f, cam);
            }
            RenderUtils.flush(consumers, N0nameRenderLayers.ESP_QUADS);
        }

        // --- Pass 2: outlines ---
        {
            var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
            for (Map.Entry<Integer, List<Box>> e : colorBoxes.entrySet()) {
                RenderUtils.renderEspOutlines(matrices, lineConsumer, e.getValue(), e.getKey(), 0.65f, cam);
            }
            RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
        }
    }

    public List<String> getVisibleBlockIds() {
        List<String> ids = new ArrayList<>(scannedBlocks.keySet());
        ids.sort(String::compareTo);
        return ids;
    }
}
