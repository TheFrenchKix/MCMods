package com.mwa.n0name.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.movement.MovementController;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.pathfinding.PathfindingService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Basic waypoint manager with 3 slots and optional auto-walk recall.
 */
public class WaypointManagerModule {

    private final BlockPos[] slots = new BlockPos[3];
    private final MovementController movementController = new MovementController();
    private int chainCurrentSlot = 1;
    private boolean chainActive = false;

    public WaypointManagerModule() {
        loadFromFile();
    }

    public void tick() {
        movementController.tick();
        MovementController.WalkState state = movementController.getState();
        if (state == MovementController.WalkState.ARRIVED && chainActive && ModConfig.getInstance().isWaypointChainEnabled()) {
            int next = findNextSlot(chainCurrentSlot);
            if (next > 0) {
                chainCurrentSlot = next;
                recallSlot(next);
                return;
            }
            chainActive = false;
        }

        if (state == MovementController.WalkState.STUCK || state == MovementController.WalkState.ARRIVED) {
            movementController.stop();
        }
    }

    public void saveCurrentToSlot(int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        int idx = toIndex(slot);
        if (idx < 0 || player == null) return;

        slots[idx] = player.getBlockPos();
        saveToFile();
        player.sendMessage(Text.literal("[n0name] Saved WP" + slot + " @ " + formatPos(slots[idx])), true);
    }

    public void clearSlot(int slot) {
        int idx = toIndex(slot);
        if (idx < 0) return;
        slots[idx] = null;
        saveToFile();
    }

    public void recallSlot(int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        int idx = toIndex(slot);
        if (idx < 0 || player == null || client.world == null) return;

        BlockPos target = slots[idx];
        if (target == null) {
            player.sendMessage(Text.literal("[n0name] WP" + slot + " is empty"), true);
            return;
        }

        List<PathNode> path = PathfindingService.findPath(client.world, player.getBlockPos(), target);
        if (path.isEmpty() || path.size() < 2) {
            player.sendMessage(Text.literal("[n0name] No path to WP" + slot), true);
            return;
        }

        movementController.startPath(path);
        chainCurrentSlot = slot;
        player.sendMessage(Text.literal("[n0name] Walking to WP" + slot + " @ " + formatPos(target)), true);
    }

    public void startChain() {
        chainActive = true;
        int start = findNextSlot(0);
        if (start > 0) {
            recallSlot(start);
        } else {
            chainActive = false;
        }
    }

    public void stopChain() {
        chainActive = false;
        movementController.stop();
    }

    public boolean isChainActive() {
        return chainActive;
    }

    public String getSlotText(int slot) {
        int idx = toIndex(slot);
        if (idx < 0) return "WP?";
        if (slots[idx] == null) return "WP" + slot + ": empty";
        return "WP" + slot + ": " + formatPos(slots[idx]);
    }

    public boolean hasSlot(int slot) {
        int idx = toIndex(slot);
        return idx >= 0 && slots[idx] != null;
    }

    private int toIndex(int slot) {
        if (slot < 1 || slot > 3) return -1;
        return slot - 1;
    }

    private int findNextSlot(int currentSlot) {
        for (int i = 1; i <= 3; i++) {
            int slot = ((currentSlot + i - 1) % 3) + 1;
            if (hasSlot(slot)) return slot;
        }
        return -1;
    }

    private String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private Path getFilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("n0name-waypoints.json");
    }

    private void loadFromFile() {
        Arrays.fill(slots, null);
        Path path = getFilePath();
        if (!Files.exists(path)) return;

        try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("slots");
            if (arr == null) return;
            for (int i = 0; i < Math.min(3, arr.size()); i++) {
                if (!arr.get(i).isJsonObject()) continue;
                JsonObject p = arr.get(i).getAsJsonObject();
                slots[i] = new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt());
            }
        } catch (Exception e) {
            DebugLogger.info("Failed to load waypoints: " + e.getMessage());
        }
    }

    private void saveToFile() {
        Path path = getFilePath();
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (BlockPos pos : slots) {
            if (pos == null) {
                arr.add(new JsonObject());
                continue;
            }
            JsonObject p = new JsonObject();
            p.addProperty("x", pos.getX());
            p.addProperty("y", pos.getY());
            p.addProperty("z", pos.getZ());
            arr.add(p);
        }
        root.add("slots", arr);

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (Exception e) {
            DebugLogger.info("Failed to save waypoints: " + e.getMessage());
        }
    }
}
