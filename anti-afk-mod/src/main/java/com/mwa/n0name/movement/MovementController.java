package com.mwa.n0name.movement;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.pathfinding.WalkabilityChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Walks a pre-computed path by pressing movement keys and rotating smoothly.
 */
public class MovementController {

    public enum WalkState { IDLE, WALKING, STUCK, ARRIVED }

    private WalkState state = WalkState.IDLE;
    private List<PathNode> currentPath;
    private int currentNodeIndex;
    private int stuckCounter;
    private Vec3d lastPosition;

    private static final int STUCK_THRESHOLD = 60;      // 3 seconds
    private static final double NODE_REACH_DIST = 0.7;  // blocks XZ distance to consider node reached
    private static final double NODE_REACH_Y = 1.5;     // Y tolerance

    private final AimController aimController;

    public MovementController() {
        this.aimController = new AimController();
        this.aimController.setSmoothingFactor(0.2f);
        this.aimController.setMaxRotationSpeed(12.0f);
    }

    public AimController getAimController() { return aimController; }
    public WalkState getState() { return state; }
    public List<PathNode> getCurrentPath() { return currentPath; }
    public int getCurrentNodeIndex() { return currentNodeIndex; }

    /**
     * Start walking a given path.
     */
    public void startPath(List<PathNode> path) {
        if (path == null || path.isEmpty()) {
            state = WalkState.IDLE;
            return;
        }
        this.currentPath = path;
        this.currentNodeIndex = 0;
        this.stuckCounter = 0;
        this.lastPosition = null;
        this.state = WalkState.WALKING;
        DebugLogger.log("Movement", "Starting path with " + path.size() + " nodes");
    }

    /**
     * Stop walking and release all keys.
     */
    public void stop() {
        state = WalkState.IDLE;
        currentPath = null;
        aimController.clearTarget();
        releaseAllKeys();
        DebugLogger.log("Movement", "Stopped");
    }

    /**
     * Called every client tick.
     */
    public void tick() {
        if (state != WalkState.WALKING) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || currentPath == null) {
            stop();
            return;
        }

        if (currentNodeIndex >= currentPath.size()) {
            releaseAllKeys();
            state = WalkState.ARRIVED;
            aimController.clearTarget();
            DebugLogger.log("Movement", "Arrived at destination");
            return;
        }

        PathNode targetNode = currentPath.get(currentNodeIndex);
        Vec3d targetPos = targetNode.toVec3dCenter();
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());

        // Check if current node is reached
        double dxz = Math.sqrt(
            (playerPos.x - targetPos.x) * (playerPos.x - targetPos.x) +
            (playerPos.z - targetPos.z) * (playerPos.z - targetPos.z)
        );
        double dy = Math.abs(playerPos.y - targetNode.y());

        if (dxz < NODE_REACH_DIST && dy < NODE_REACH_Y) {
            currentNodeIndex++;
            stuckCounter = 0;
            if (currentNodeIndex >= currentPath.size()) {
                releaseAllKeys();
                state = WalkState.ARRIVED;
                aimController.clearTarget();
                DebugLogger.log("Movement", "Arrived at destination");
                return;
            }
            targetNode = currentPath.get(currentNodeIndex);
            targetPos = targetNode.toVec3dCenter();
        }

        // Safety: void check ahead
        BlockPos playerBlock = player.getBlockPos();
        BlockPos targetBlock = targetNode.toBlockPos();
        if (!WalkabilityChecker.hasGroundBelow(client.world, targetBlock, 4)) {
            DebugLogger.log("Movement", "Void detected ahead, stopping");
            releaseAllKeys();
            state = WalkState.STUCK;
            aimController.clearTarget();
            return;
        }

        // Aim at target node (look at ground level of target)
        aimController.setTarget(new Vec3d(targetPos.x, targetNode.y() + 0.5, targetPos.z));
        aimController.tick();

        // Press forward key
        client.options.forwardKey.setPressed(true);

        // Jump if target is above us
        if (targetNode.y() > playerBlock.getY()) {
            client.options.jumpKey.setPressed(true);
        } else {
            client.options.jumpKey.setPressed(false);
        }

        // Stuck detection
        if (lastPosition != null) {
            double moved = playerPos.squaredDistanceTo(lastPosition);
            if (moved < 0.001) {
                stuckCounter++;
                if (stuckCounter >= STUCK_THRESHOLD) {
                    DebugLogger.log("Movement", "Stuck detected after " + STUCK_THRESHOLD + " ticks");
                    releaseAllKeys();
                    state = WalkState.STUCK;
                    aimController.clearTarget();
                    return;
                }
            } else {
                stuckCounter = 0;
            }
        }
        lastPosition = playerPos;
    }

    private void releaseAllKeys() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.options == null) return;
        c.options.forwardKey.setPressed(false);
        c.options.backKey.setPressed(false);
        c.options.leftKey.setPressed(false);
        c.options.rightKey.setPressed(false);
        c.options.jumpKey.setPressed(false);
    }
}
