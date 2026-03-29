package com.mwa.n0name.movement;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.pathfinding.WalkabilityChecker;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks a pre-computed path by pressing movement keys and rotating smoothly.
 */
public class MovementController {

    public enum WalkState { IDLE, WALKING, STUCK, ARRIVED }

    private WalkState state = WalkState.IDLE;
    private List<PathNode> currentPath;
    private List<BlockPos> currentBlockPath;
    private int currentNodeIndex;
    private int stuckCounter;
    private Vec3d lastPosition;
    private Set<BlockPos> navigationGrid = Collections.emptySet();
    private boolean validateAgainstGrid = false;
    private boolean preventLedgeFall = false;
    private int ledgeMaxDrop = 1;

    // Anti-stuck phase thresholds (ticks)
    private static final int STUCK_PHASE_JUMP = 25;
    private static final int STUCK_PHASE_STRAFE = 45;
    private static final int STUCK_PHASE_JUMP_STRAFE = 65;
    private static final int STUCK_PHASE_BACK_JUMP = 80;
    private static final int STUCK_PHASE_SKIP_NODE = 95;
    private static final int STUCK_GIVE_UP = 120;

    // Node arrival (tighter thresholds inspired by mineflayer-baritone)
    private static final double NODE_ARRIVAL_H = 0.35;
    private static final double NODE_ARRIVAL_H_SQ = NODE_ARRIVAL_H * NODE_ARRIVAL_H;
    private static final double NODE_ARRIVAL_Y = 0.8;
    private static final double MIN_PROGRESS_SQ = 0.0004;
    private static final float STRAFE_ANGLE_THRESHOLD = 8.0f;
    private static final float STRAFE_MAX_CORRECTION_ANGLE = 70.0f;
    private static final double SPRINT_DIST = 1.6;
    private static final double EDGE_JUMP_THRESHOLD = 0.25;
    private static final int STEERING_LOOKAHEAD = 3;

    private final AimController aimController;

    public MovementController() {
        this.aimController = new AimController();
    }

    public AimController getAimController() { return aimController; }
    public WalkState getState() { return state; }
    public List<PathNode> getCurrentPath() { return currentPath; }
    public List<BlockPos> getCurrentBlockPath() { return currentBlockPath; }
    public int getCurrentNodeIndex() { return currentNodeIndex; }
    public void setPreventLedgeFall(boolean v) { preventLedgeFall = v; }
    public void setLedgeMaxDrop(int v) { ledgeMaxDrop = Math.max(0, Math.min(3, v)); }

    public void frameUpdate() {
        if (state == WalkState.WALKING && aimController.isActive()) {
            aimController.tick();
        }
    }

    /**
     * Start walking a given path.
     */
    public void startPath(List<PathNode> path) {
        if (path == null || path.isEmpty()) {
            state = WalkState.IDLE;
            return;
        }
        this.currentPath = path;
        List<BlockPos> blockPath = new ArrayList<>(path.size());
        for (PathNode node : path) {
            blockPath.add(node.toBlockPos());
        }
        this.currentBlockPath = blockPath;
        this.currentNodeIndex = 0;
        this.stuckCounter = 0;
        this.lastPosition = null;
        this.navigationGrid = Collections.emptySet();
        this.validateAgainstGrid = false;
        this.state = WalkState.WALKING;
        DebugLogger.log("Movement", "Starting path with " + path.size() + " nodes");
    }

    /**
     * Start walking a raw block-by-block path.
     * If scannedWalkableBlocks is provided, each next node is validated against this grid.
     */
    public void startBlockPath(List<BlockPos> blockPath, Collection<BlockPos> scannedWalkableBlocks) {
        if (blockPath == null || blockPath.isEmpty()) {
            state = WalkState.IDLE;
            return;
        }

        this.currentBlockPath = new ArrayList<>(blockPath.size());
        this.currentPath = new ArrayList<>(blockPath.size());
        for (BlockPos pos : blockPath) {
            BlockPos immutable = pos.toImmutable();
            this.currentBlockPath.add(immutable);
            this.currentPath.add(new PathNode(immutable));
        }

        if (scannedWalkableBlocks != null && !scannedWalkableBlocks.isEmpty()) {
            this.navigationGrid = new HashSet<>(scannedWalkableBlocks.size());
            for (BlockPos pos : scannedWalkableBlocks) {
                this.navigationGrid.add(pos.toImmutable());
            }
            this.validateAgainstGrid = true;
        } else {
            this.navigationGrid = Collections.emptySet();
            this.validateAgainstGrid = false;
        }

        this.currentNodeIndex = 0;
        this.stuckCounter = 0;
        this.lastPosition = null;
        this.state = WalkState.WALKING;
        DebugLogger.log("Movement", "Starting block path with " + this.currentBlockPath.size() + " nodes"
            + (validateAgainstGrid ? " (grid-validated)" : ""));
    }

    /**
     * Stop walking and release all keys.
     */
    public void stop() {
        state = WalkState.IDLE;
        currentPath = null;
        currentBlockPath = null;
        navigationGrid = Collections.emptySet();
        validateAgainstGrid = false;
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
        if (player == null || client.world == null || currentBlockPath == null) {
            stop();
            return;
        }

        if (currentNodeIndex >= getPathSize()) {
            releaseAllKeys();
            state = WalkState.ARRIVED;
            aimController.clearTarget();
            DebugLogger.log("Movement", "Arrived at destination");
            return;
        }

        // Reset jump each tick (let conditions below decide)
        client.options.jumpKey.setPressed(false);

        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        BlockPos playerBlock = player.getBlockPos();

        // --- Multi-node lookahead: advance past all reached/passed nodes ---
        while (currentNodeIndex < getPathSize()) {
            BlockPos n = currentBlockPath.get(currentNodeIndex);
            if (hasReachedNode(client, player, n) || hasPassedNode(player, n)) {
                stuckCounter = 0;
                currentNodeIndex++;
            } else {
                break;
            }
        }

        if (currentNodeIndex >= getPathSize()) {
            releaseAllKeys();
            state = WalkState.ARRIVED;
            aimController.clearTarget();
            DebugLogger.log("Movement", "Arrived at destination");
            return;
        }

        BlockPos targetBlock = selectSteeringTarget();
        Vec3d targetPos = Vec3d.ofCenter(targetBlock);

        // Block-by-block validation inspired by PathNavigateGround: revalidate next step continuously.
        if (!isNextNodeValid(client, playerBlock, targetBlock)) {
            DebugLogger.log("Movement", "Next node became invalid: " + targetBlock.toShortString());
            releaseAllKeys();
            state = WalkState.STUCK;
            aimController.clearTarget();
            return;
        }

        // Safety: void check ahead
        if (!WalkabilityChecker.hasGroundBelow(client.world, targetBlock, 4)) {
            DebugLogger.log("Movement", "Void detected ahead, stopping");
            releaseAllKeys();
            state = WalkState.STUCK;
            aimController.clearTarget();
            return;
        }

        // Aim at target node
        float eyeH = player.getEyeHeight(player.getPose());
        aimController.setTarget(new Vec3d(targetPos.x, targetBlock.getY() + eyeH, targetPos.z));

        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);

        // Edge guard
        if (preventLedgeFall && wouldStepOffLedge(client, player, targetPos)) {
            DebugLogger.log("Movement", "Edge guard triggered");
            releaseAllKeys();
            state = WalkState.STUCK;
            aimController.clearTarget();
            return;
        }

        // Forward movement + sprint
        double dxz = horizontalDistance(playerPos, targetPos);
        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(
            stuckCounter < STUCK_PHASE_JUMP
            && dxz > SPRINT_DIST
            && targetBlock.getY() <= playerBlock.getY() + 1
        );

        // Strafe correction (only when not in anti-stuck strafe phase)
        if (stuckCounter < STUCK_PHASE_STRAFE) {
            applyStrafeCorrection(client, player, targetPos);
        }

        // --- Jump logic ---
        if (stuckCounter < STUCK_PHASE_JUMP) {
            boolean needsJump = false;

            // Jump if target is above
            if (targetBlock.getY() > playerBlock.getY()) {
                needsJump = true;
            }

            // Auto-jump: solid block ahead at feet level
            if (!needsJump && shouldAutoJump(client, player, targetPos)) {
                needsJump = true;
            }

            client.options.jumpKey.setPressed(needsJump);
        }

        // --- Stuck detection + multi-phase recovery ---
        if (lastPosition != null) {
            double moved = horizontalDistanceSquared(playerPos, lastPosition);
            if (moved < MIN_PROGRESS_SQ) {
                stuckCounter++;
                if (handleStuckRecovery(client, player)) return;
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
        if (currentNodeIndex >= getPathSize()) {
            releaseAllKeys();
            state = WalkState.ARRIVED;
            aimController.clearTarget();
            DebugLogger.log("Movement", "Arrived at destination");
            return true;
        }
        return false;
    }

    /**
     * Node arrival check with tight thresholds (inspired by mineflayer-baritone).
     * Requires onGround for upward/level nodes to prevent mid-air false arrivals.
     */
    private boolean hasReachedNode(MinecraftClient client, ClientPlayerEntity player, BlockPos node) {
        BlockPos playerBlock = player.getBlockPos();
        BlockPos targetBlock = node;

        // Check 1: Same block position
        if (playerBlock.equals(targetBlock)) return true;

        // Check 2: Tight distance check
        double dx = player.getX() - (node.getX() + 0.5);
        double dz = player.getZ() - (node.getZ() + 0.5);
        double distXZSq = dx * dx + dz * dz;
        double dy = player.getY() - node.getY();
        double absDy = Math.abs(dy);
        if (distXZSq < NODE_ARRIVAL_H_SQ && absDy < NODE_ARRIVAL_Y) {
            // For upward/level nodes, require onGround to prevent mid-air false arrivals
            if (dy <= 0.1 && !player.isOnGround()) return false;
            return true;
        }

        // Check 3: Bounding box intersection (fallback)
        Box playerBox = player.getBoundingBox();
        Box targetBox = new Box(targetBlock);
        if (playerBox.intersects(targetBox)) {
            return true;
        }

        // If another player is occupying the node hitbox, treat it as reached to avoid oscillating backward.
        if (client.world != null) {
            for (PlayerEntity other : client.world.getPlayers()) {
                if (other == player) continue;
                if (!other.isAlive()) continue;
                if (other.getBoundingBox().intersects(targetBox.expand(0.05))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Overshoot detection: has the player passed a node without reaching it?
     * Uses dot product of offset with direction to next node.
     */
    private boolean hasPassedNode(ClientPlayerEntity player, BlockPos node) {
        int nextIdx = currentNodeIndex + 1;
        if (nextIdx >= getPathSize()) return false;

        BlockPos nextNode = currentBlockPath.get(nextIdx);
        double dirX = (nextNode.getX() + 0.5) - (node.getX() + 0.5);
        double dirZ = (nextNode.getZ() + 0.5) - (node.getZ() + 0.5);
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 0.01) return false;
        dirX /= len;
        dirZ /= len;

        double offX = player.getX() - (node.getX() + 0.5);
        double offZ = player.getZ() - (node.getZ() + 0.5);
        double dot = offX * dirX + offZ * dirZ;
        return dot > 0.5;
    }

    private int getPathSize() {
        return currentBlockPath == null ? 0 : currentBlockPath.size();
    }

    /**
     * Pick a short lookahead node in same direction for smoother continuous movement.
     */
    private BlockPos selectSteeringTarget() {
        BlockPos base = currentBlockPath.get(currentNodeIndex);
        int last = Math.min(getPathSize() - 1, currentNodeIndex + STEERING_LOOKAHEAD);
        if (currentNodeIndex >= last) {
            return base;
        }

        int dirX = 0;
        int dirZ = 0;
        BlockPos prev = base;
        BlockPos best = base;

        for (int i = currentNodeIndex + 1; i <= last; i++) {
            BlockPos next = currentBlockPath.get(i);
            int stepX = Integer.compare(next.getX(), prev.getX());
            int stepZ = Integer.compare(next.getZ(), prev.getZ());

            if (i == currentNodeIndex + 1) {
                dirX = stepX;
                dirZ = stepZ;
            } else if (stepX != dirX || stepZ != dirZ) {
                break;
            }

            if (next.getY() != base.getY()) {
                break;
            }

            best = next;
            prev = next;
        }

        return best;
    }

    /**
     * PathNavigate-like runtime validation for the next node.
     * Prevents blindly walking into changed collisions/walls.
     */
    private boolean isNextNodeValid(MinecraftClient client, BlockPos playerBlock, BlockPos nextNode) {
        if (client.world == null) return false;
        if (!WalkabilityChecker.isWalkable(client.world, nextNode)) return false;

        if (validateAgainstGrid && !navigationGrid.contains(nextNode)) {
            return false;
        }

        if (currentNodeIndex > 0 && currentNodeIndex < getPathSize()) {
            BlockPos prev = currentBlockPath.get(currentNodeIndex - 1);
            if (!WalkabilityChecker.canTraverse(client.world, prev, nextNode)) {
                return false;
            }
        }

        // If close enough to next node, validate immediate transition from player block too.
        if (playerBlock.getSquaredDistance(nextNode) <= 4.0) {
            return WalkabilityChecker.canTraverse(client.world, playerBlock, nextNode);
        }

        return true;
    }

    /**
     * Multi-phase anti-stuck recovery:
     * 30+ ticks: jump | 50+: strafe | 70+: jump+strafe | 90: skip node | 120: give up.
     * Returns true if should exit tick().
     */
    private boolean handleStuckRecovery(MinecraftClient client, ClientPlayerEntity player) {
        if (stuckCounter >= STUCK_GIVE_UP) {
            DebugLogger.log("Movement", "Stuck: all recovery failed (" + stuckCounter + " ticks)");
            releaseAllKeys();
            state = WalkState.STUCK;
            aimController.clearTarget();
            return true;
        }

        if (stuckCounter == STUCK_PHASE_SKIP_NODE) {
            DebugLogger.log("Movement", "Stuck: skipping node " + currentNodeIndex);
            stuckCounter = 0;
            return advanceToNextNode();
        }

        if (stuckCounter >= STUCK_PHASE_BACK_JUMP) {
            // Walk backward + jump to dislodge from tight spots
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(true);
            client.options.jumpKey.setPressed(true);
            return false;
        }

        if (stuckCounter >= STUCK_PHASE_JUMP_STRAFE) {
            // Jump + alternating strafe
            client.options.jumpKey.setPressed(true);
            boolean goLeft = (stuckCounter / 8) % 2 == 0;
            client.options.leftKey.setPressed(goLeft);
            client.options.rightKey.setPressed(!goLeft);
            return false;
        }

        if (stuckCounter >= STUCK_PHASE_STRAFE) {
            // Alternating strafe without jump
            boolean goLeft = (stuckCounter / 6) % 2 == 0;
            client.options.leftKey.setPressed(goLeft);
            client.options.rightKey.setPressed(!goLeft);
            return false;
        }

        if (stuckCounter >= STUCK_PHASE_JUMP) {
            // Jump while continuing forward
            client.options.jumpKey.setPressed(true);
            return false;
        }

        return false;
    }

    /**
     * Edge-based jump timing: jump when near the edge of a block toward the target.
     * Inspired by mineflayer-baritone's _shouldJumpNow.
     */
    private boolean shouldJumpAtEdge(ClientPlayerEntity player, Vec3d targetPos) {
        if (!player.isOnGround()) return false;

        double dx = targetPos.x - player.getX();
        double dz = targetPos.z - player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return false;
        dx /= len;
        dz /= len;

        // Distance to block edge in movement direction
        double edgeDistX = dx > 0 ? (Math.ceil(player.getX()) - player.getX()) : (player.getX() - Math.floor(player.getX()));
        double edgeDistZ = dz > 0 ? (Math.ceil(player.getZ()) - player.getZ()) : (player.getZ() - Math.floor(player.getZ()));
        double edgeDist = Math.min(
            Math.abs(dx) > 0.1 ? edgeDistX / Math.abs(dx) : Double.MAX_VALUE,
            Math.abs(dz) > 0.1 ? edgeDistZ / Math.abs(dz) : Double.MAX_VALUE
        );

        return edgeDist <= EDGE_JUMP_THRESHOLD;
    }

    /**
     * Auto-jump: checks if there's a solid block 0.7 blocks ahead at feet level
     * with 2 blocks clear above. Inspired by mineflayer-baritone's _shouldAutoJump.
     */
    private boolean shouldAutoJump(MinecraftClient client, ClientPlayerEntity player, Vec3d targetPos) {
        if (!player.isOnGround() || client.world == null) return false;

        double dx = targetPos.x - player.getX();
        double dz = targetPos.z - player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return false;

        double probeX = player.getX() + (dx / len) * 0.7;
        double probeZ = player.getZ() + (dz / len) * 0.7;
        BlockPos probeFeet = BlockPos.ofFloored(probeX, player.getY(), probeZ);

        BlockState feetState = client.world.getBlockState(probeFeet);
        VoxelShape feetShape = feetState.getCollisionShape(client.world, probeFeet);
        if (feetShape.isEmpty()) return false;

        // Check 2 blocks above are clear
        BlockPos above1 = probeFeet.up();
        BlockPos above2 = probeFeet.up(2);
        boolean clear = client.world.getBlockState(above1).getCollisionShape(client.world, above1).isEmpty()
                     && client.world.getBlockState(above2).getCollisionShape(client.world, above2).isEmpty();
        return clear;
    }

    private void applyStrafeCorrection(MinecraftClient client, ClientPlayerEntity player, Vec3d targetPos) {
        double dx = targetPos.x - player.getX();
        double dz = targetPos.z - player.getZ();
        float desiredYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float yawDelta = MathHelper.wrapDegrees(desiredYaw - player.getYaw());

        if (Math.abs(yawDelta) > STRAFE_MAX_CORRECTION_ANGLE) {
            return;
        }

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
