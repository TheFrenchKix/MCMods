package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.movement.MovementController;
import com.mwa.n0name.pathfinding.AStarPathfinder;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.render.PathRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Anti-AFK module using A* pathfinding for realistic movement.
 * Picks random nearby targets and walks to them with path rendering.
 */
public class AntiAfkModule {

    private enum State { IDLE, PATHFINDING, WALKING, PAUSING }

    private final MovementController movementController = new MovementController();
    private final Random random = new Random();

    private State state = State.IDLE;
    private int pauseCounter = 0;
    private int pauseTarget = 0;
    private boolean wasActive = false;
    private List<PathNode> currentPath = Collections.emptyList();

    // Rendering colors
    private static final float[] LINE_C    = {0.2f, 1.0f, 0.4f, 0.8f};
    private static final float[] NODE_C    = {0.3f, 0.8f, 0.3f, 0.6f};
    private static final float[] CURRENT_C = {1.0f, 1.0f, 0.0f, 0.9f};
    private static final float[] TARGET_C  = {0.0f, 1.0f, 0.0f, 0.9f};
    private static final int BOUNDS_COLOR  = 0x6644FF44;

    public AntiAfkModule() {
        // Safe-walk style behavior for AntiAFK without relying on sneak key.
        // Settings are controlled via config/GUI now
    }

    public void tick() {
        boolean active = ModConfig.getInstance().isAntiAfkEnabled();
        
        // Apply edge guard settings from config
        ModConfig cfg = ModConfig.getInstance();
        movementController.setPreventLedgeFall(cfg.isPreventLedgeFall());
        movementController.setLedgeMaxDrop(cfg.getLedgeMaxDrop());

        if (wasActive && !active) {
            movementController.stop();
            state = State.IDLE;
            currentPath = Collections.emptyList();
            DebugLogger.log("AntiAFK", "Disabled");
        }
        wasActive = active;
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        switch (state) {
            case IDLE -> findNewTarget(client, player);
            case WALKING -> handleWalking();
            case PAUSING -> handlePause();
        }
    }

    private void findNewTarget(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int radius = 5 + random.nextInt(11); // 5-15 blocks

        BlockPos target = AStarPathfinder.findRandomWalkableTarget(
            client.world, playerPos, radius, random);

        if (target == null) {
            // No walkable target found, pause and retry
            startPause(40 + random.nextInt(40));
            return;
        }

        List<PathNode> path = AStarPathfinder.findPath(client.world, playerPos, target);
        if (path.isEmpty() || path.size() < 2) {
            startPause(20 + random.nextInt(20));
            return;
        }

        currentPath = path;
        movementController.startPath(path);
        state = State.WALKING;
        DebugLogger.log("AntiAFK", "Walking to " + target + " (" + path.size() + " nodes)");
    }

    private void handleWalking() {
        movementController.tick();

        MovementController.WalkState walkState = movementController.getState();
        switch (walkState) {
            case ARRIVED -> {
                // Pause with randomized duration (2-5 seconds + variation)
                int basePause = 40 + random.nextInt(61); // 2-5 seconds
                int variation = (int)(basePause * 0.2 * (random.nextDouble() - 0.5)); // +-10%
                startPause(basePause + variation);
                DebugLogger.log("AntiAFK", "Arrived, pausing for " + (basePause + variation) + " ticks");
            }
            case STUCK -> {
                movementController.stop();
                currentPath = Collections.emptyList();
                startPause(20 + random.nextInt(20));
                DebugLogger.log("AntiAFK", "Stuck, re-pathfinding after pause");
            }
            case IDLE -> state = State.IDLE;
        }
    }

    private void handlePause() {
        if (++pauseCounter >= pauseTarget) {
            state = State.IDLE;
        }
    }

    private void startPause(int ticks) {
        state = State.PAUSING;
        pauseCounter = 0;
        pauseTarget = ticks;
    }

    public void render(WorldRenderContext context) {
        if (!ModConfig.getInstance().isAntiAfkEnabled()) return;
        if (currentPath.isEmpty()) return;

        PathRenderer.renderPath(context, currentPath,
            movementController.getCurrentNodeIndex(),
            LINE_C, NODE_C, CURRENT_C, TARGET_C);
    }
}
