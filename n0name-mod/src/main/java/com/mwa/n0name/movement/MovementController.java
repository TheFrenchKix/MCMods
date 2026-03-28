package com.mwa.n0name.movement;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.pathfinding.WalkabilityChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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
    private boolean preventLedgeFall = false;
    private int ledgeMaxDrop = 1;

    private static final int STUCK_THRESHOLD = 60;          // 3 seconds
    private static final double NODE_REACH_DIST = 0.38;     // tighter node lock for block-by-block movement
    private static final double NODE_LOOKAHEAD_DIST = 0.55; // allow smoother node transitions
    private static final double NODE_REACH_Y = 1.25;
    private static final double MIN_PROGRESS_SQ = 0.0004;
    private static final float STRAFE_ANGLE_THRESHOLD = 8.0f;
    private static final double SPRINT_DIST = 1.6;

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
    public void setPreventLedgeFall(boolean v) { preventLedgeFall = v; }
    public void setLedgeMaxDrop(int v) { ledgeMaxDrop = Math.max(0, Math.min(3, v)); }

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
        double dxz = horizontalDistance(playerPos, targetPos);
        double dy = Math.abs(playerPos.y - targetNode.y());

        if (dxz < NODE_REACH_DIST && dy < NODE_REACH_Y) {
            if (advanceToNextNode()) return;
            targetNode = currentPath.get(currentNodeIndex);
            targetPos = targetNode.toVec3dCenter();
            dxz = horizontalDistance(playerPos, targetPos);
        }

        // If we are already close to the next node, skip ahead to avoid zig-zag around corners.
        if (currentNodeIndex + 1 < currentPath.size()) {
            PathNode nextNode = currentPath.get(currentNodeIndex + 1);
            Vec3d nextTarget = nextNode.toVec3dCenter();
            if (horizontalDistance(playerPos, nextTarget) < NODE_LOOKAHEAD_DIST
                && Math.abs(playerPos.y - nextNode.y()) < NODE_REACH_Y) {
                if (advanceToNextNode()) return;
                targetNode = currentPath.get(currentNodeIndex);
                targetPos = targetNode.toVec3dCenter();
                dxz = horizontalDistance(playerPos, targetPos);
            }
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

        // Aim at target node at eye height for natural-looking movement
        float eyeH = player.getEyeHeight(player.getPose());
        aimController.setTarget(new Vec3d(targetPos.x, targetNode.y() + eyeH, targetPos.z));
        aimController.tick();

        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);

        if (preventLedgeFall && wouldStepOffLedge(client, player, targetPos)) {
            DebugLogger.log("Movement", "Edge guard triggered, stopping before ledge");
            releaseAllKeys();
            state = WalkState.STUCK;
            aimController.clearTarget();
            return;
        }

        // Press forward key
        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(dxz > SPRINT_DIST && targetNode.y() <= playerBlock.getY() + 1);

        applyStrafeCorrection(client, player, targetPos);

        // Jump if target is above us
        if (targetNode.y() > playerBlock.getY()) {
            client.options.jumpKey.setPressed(true);
        } else {
            client.options.jumpKey.setPressed(false);
        }

        // Stuck detection
        if (lastPosition != null) {
            double moved = horizontalDistanceSquared(playerPos, lastPosition);
            if (moved < MIN_PROGRESS_SQ) {
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
        c.options.sprintKey.setPressed(false);
        c.options.backKey.setPressed(false);
        c.options.leftKey.setPressed(false);
        c.options.rightKey.setPressed(false);
        c.options.jumpKey.setPressed(false);
    }

    private boolean advanceToNextNode() {
        currentNodeIndex++;
        stuckCounter = 0;
        if (currentNodeIndex >= currentPath.size()) {
            releaseAllKeys();
            state = WalkState.ARRIVED;
            aimController.clearTarget();
            DebugLogger.log("Movement", "Arrived at destination");
            return true;
        }
        return false;
    }

    private void applyStrafeCorrection(MinecraftClient client, ClientPlayerEntity player, Vec3d targetPos) {
        double dx = targetPos.x - player.getX();
        double dz = targetPos.z - player.getZ();
        float desiredYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float yawDelta = MathHelper.wrapDegrees(desiredYaw - player.getYaw());

        if (yawDelta > STRAFE_ANGLE_THRESHOLD) {
            client.options.rightKey.setPressed(true);
        } else if (yawDelta < -STRAFE_ANGLE_THRESHOLD) {
            client.options.leftKey.setPressed(true);
        }
    }

    private double horizontalDistance(Vec3d a, Vec3d b) {
        return Math.sqrt(horizontalDistanceSquared(a, b));
    }

    private double horizontalDistanceSquared(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private boolean wouldStepOffLedge(MinecraftClient client, ClientPlayerEntity player, Vec3d targetPos) {
        Vec3d movement = new Vec3d(targetPos.x - player.getX(), 0.0, targetPos.z - player.getZ());
        if (movement.lengthSquared() < 1.0e-6) return false;

        Vec3d dir = movement.normalize();
        double probeDistance = 0.55;
        double probeX = player.getX() + dir.x * probeDistance;
        double probeZ = player.getZ() + dir.z * probeDistance;

        BlockPos probeFeet = BlockPos.ofFloored(probeX, player.getY(), probeZ);
        return !WalkabilityChecker.hasGroundBelow(client.world, probeFeet, ledgeMaxDrop);
    }
}
