package com.cpathfinder.movement;

import com.cpathfinder.PathState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Smooth movement controller — registered on ClientTickEvents.END_CLIENT_TICK.
 *
 * Strategy (per tick):
 *  1. Compute the horizontal vector (dx, dz) from player feet to waypoint centre.
 *  2. Derive yaw via Math.atan2 so the player always faces the target directly
 *     (no stair-stepping between X and Z axes).
 *  3. Advance the player position by SPEED blocks along that vector (capped at
 *     the remaining distance to avoid overshooting).
 *  4. Handle vertical transitions: if the next waypoint is higher and the player
 *     is on the ground, inject an upward impulse (vanilla jump speed = 0.42).
 *  5. Advance to the next waypoint once the horizontal distance falls below the
 *     arrival threshold, and stop when the full path is traversed.
 */
public class MovementHandler {

    /** Horizontal movement speed in blocks per tick (≈ 0.18 × 20 = 3.6 b/s). */
    private static final double SPEED          = 0.18;
    /** Arrival threshold: switch to next waypoint when within this many blocks. */
    private static final double H_THRESHOLD    = 0.35;
    /** Vertical threshold to consider a waypoint "reached" height-wise. */
    private static final double V_THRESHOLD    = 1.5;

    private int pathIndex = 0;

    /** Call after a new path is posted to reset the waypoint cursor. */
    public void reset() {
        pathIndex = 0;
    }

    /**
     * Must be called every client tick (END_CLIENT_TICK).
     */
    public void tick(Minecraft client) {
        if (!PathState.isNavigating()) return;
        if (client.player == null) return;

        List<BlockPos> path = PathState.getPath();
        if (path == null || path.isEmpty()) return;

        // All waypoints consumed → destination reached
        if (pathIndex >= path.size()) {
            PathState.clear();
            pathIndex = 0;
            client.player.displayClientMessage(
                    Component.literal("[Goto] Destination atteinte !")
                            .withStyle(ChatFormatting.GREEN), false);
            return;
        }

        LocalPlayer player = client.player;
        BlockPos    target = path.get(pathIndex);

        // Target position: centre of block (X,Z) at feet Y
        double tx = target.getX() + 0.5;
        double ty = target.getY();
        double tz = target.getZ() + 0.5;

        double dx = tx - player.getX();
        double dy = ty - player.getY();
        double dz = tz - player.getZ();

        double horizDist = Math.sqrt(dx * dx + dz * dz);

        // ── Arrival check ──────────────────────────────────────────────────────
        if (horizDist < H_THRESHOLD && Math.abs(dy) < V_THRESHOLD) {
            pathIndex++;
            return;
        }

        // ── Horizontal movement ────────────────────────────────────────────────
        if (horizDist > 0.001) {
            // Yaw angle that faces the target directly (handles diagonals)
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            player.setYRot(yaw);
            player.yHeadRot = yaw;

            // Step towards target, capped at remaining distance
            double step  = Math.min(SPEED, horizDist);
            double stepX = (dx / horizDist) * step;
            double stepZ = (dz / horizDist) * step;

            // Advance X and Z; leave Y to vanilla physics (gravity + jump)
            player.setPos(player.getX() + stepX, player.getY(), player.getZ() + stepZ);
        }

        // ── Vertical movement: jump when next waypoint is above ────────────────
        if (dy > 0.5 && player.onGround()) {
            Vec3 motion = player.getDeltaMovement();
            // Inject vanilla jump impulse (0.42 b/tick upward)
            player.setDeltaMovement(motion.x, 0.42, motion.z);
        }
    }
}
