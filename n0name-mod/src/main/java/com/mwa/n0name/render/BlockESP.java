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

    private static final int COLUMNS_PER_TICK = 72;
    private static final int MAX_TYPES   = 40;

    private final Map<String, List<BlockPos>> scannedBlocks = new ConcurrentHashMap<>();
    private int lastSnapshotHash = 0;
    private ScanState scanState;

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int px = (int) client.player.getX();
        int py = (int) client.player.getY();
        int pz = (int) client.player.getZ();

        ModConfig config = ModConfig.getInstance();
        double blockRange = config.getBlockEspRange();
        int radiusXZ = Math.max(5, (int)(blockRange / 2.0));
        int radiusY = Math.max(2, (int)(blockRange / 5.0));

        if (scanState == null || !scanState.matches(px, py, pz, radiusXZ, radiusY)) {
            scanState = new ScanState(px, py, pz, radiusXZ, radiusY);
        }

        int processed = 0;
        while (processed < COLUMNS_PER_TICK && !scanState.isComplete()) {
            scanState.scanNextColumn(client, config);
            processed++;
        }

        if (!scanState.isComplete()) {
            return;
        }

        Map<String, List<BlockPos>> fresh = scanState.snapshot();
        scannedBlocks.clear();
        scannedBlocks.putAll(fresh);

        int snapshotHash = 17;
        for (Map.Entry<String, List<BlockPos>> entry : fresh.entrySet()) {
            snapshotHash = 31 * snapshotHash + entry.getKey().hashCode();
            snapshotHash = 31 * snapshotHash + entry.getValue().size();
        }
        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash;
            DebugLogger.log("BlockESP", "Scanned " + fresh.size() + " block types");
        }

        // Start a fresh sweep centered on the most recent player position.
        scanState = new ScanState(px, py, pz, radiusXZ, radiusY);
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

    private static final class ScanState {
        private final int cx;
        private final int cy;
        private final int cz;
        private final int radiusXZ;
        private final int radiusY;
        private final int side;
        private final int totalColumns;
        private final Map<String, List<BlockPos>> blocks = new LinkedHashMap<>();
        private int columnIndex = 0;

        private ScanState(int cx, int cy, int cz, int radiusXZ, int radiusY) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.radiusXZ = radiusXZ;
            this.radiusY = radiusY;
            this.side = radiusXZ * 2 + 1;
            this.totalColumns = side * side;
        }

        private boolean matches(int px, int py, int pz, int rxz, int ry) {
            if (rxz != radiusXZ || ry != radiusY) {
                return false;
            }
            // Keep scanning if the player is still close to sweep center.
            return Math.abs(px - cx) <= 2 && Math.abs(py - cy) <= 2 && Math.abs(pz - cz) <= 2;
        }

        private boolean isComplete() {
            return columnIndex >= totalColumns;
        }

        private void scanNextColumn(MinecraftClient client, ModConfig config) {
            if (client.player == null || client.world == null || isComplete()) return;

            int ix = columnIndex % side;
            int iz = columnIndex / side;
            columnIndex++;

            int x = cx - radiusXZ + ix;
            int z = cz - radiusXZ + iz;

            for (int y = cy - radiusY; y <= cy + radiusY; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                var state = client.world.getBlockState(pos);
                if (state.isAir()) continue;

                String id = Registries.BLOCK.getId(state.getBlock()).toString();
                if (!blocks.containsKey(id) && blocks.size() >= MAX_TYPES) continue;

                config.getOrCreateBlockEntry(id);
                blocks.computeIfAbsent(id, k -> new ArrayList<>()).add(pos.toImmutable());
            }
        }

        private Map<String, List<BlockPos>> snapshot() {
            return new LinkedHashMap<>(blocks);
        }
    }
}
