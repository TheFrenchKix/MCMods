package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.movement.MovementController;
import com.mwa.n0name.pathfinding.AStarPathfinder;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.render.PathRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;

/**
 * AutoWalk module: pathfind to a target and walk there with visual feedback.
 */
public class AutoWalkModule {

    private final MovementController movementController = new MovementController();
    private BlockPos targetPos = null;
    private List<PathNode> currentPath = Collections.emptyList();
    private boolean wasActive = false;

    public void setTarget(BlockPos target) {
        this.targetPos = target;
        repath();
    }

    public BlockPos getTarget() { return targetPos; }
    public List<PathNode> getCurrentPath() { return currentPath; }
    public MovementController getMovementController() { return movementController; }

    public void tick() {
        if (targetPos == null) return;

        movementController.tick();

        MovementController.WalkState walkState = movementController.getState();
        switch (walkState) {
            case ARRIVED -> {
                DebugLogger.log("AutoWalk", "Arrived at target " + targetPos);
                currentPath = Collections.emptyList();
                targetPos = null;
            }
            case STUCK -> {
                DebugLogger.log("AutoWalk", "Stuck, re-pathfinding...");
                repath();
            }
            case IDLE -> {
                if (!currentPath.isEmpty()) {
                    movementController.startPath(currentPath);
                }
            }
        }
    }

    private void repath() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || targetPos == null) return;

        List<PathNode> path = AStarPathfinder.findPath(
            client.world, client.player.getBlockPos(), targetPos);

        if (path.isEmpty()) {
            DebugLogger.log("AutoWalk", "No path found to " + targetPos);
            targetPos = null;
            return;
        }

        currentPath = path;
        movementController.startPath(path);
        DebugLogger.log("AutoWalk", "Path to " + targetPos + ": " + path.size() + " nodes");
    }

    public void render(WorldRenderContext context) {
        if (currentPath.isEmpty()) return;

        PathRenderer.renderPath(context, currentPath,
            movementController.getCurrentNodeIndex());
    }
}
