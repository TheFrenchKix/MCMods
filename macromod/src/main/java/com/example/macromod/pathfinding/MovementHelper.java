package com.example.macromod.pathfinding;

import com.example.macromod.util.BlockUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Handles smooth camera rotation and movement toward block waypoints.
 *
 * <p>Camera rotation is delegated to {@link SmoothAim} which runs once per tick,
 * providing linear-interpolated smooth aim with human-like imperfections.
 * Movement key presses are
 * decomposed into forward/strafe components so the player can navigate in all
 * four directions without waiting to be perfectly aligned.</p>
 */
public class MovementHelper {

    // ── Rotation constants ────────────────────────────────────────
    /** Lerp factor used when computing the desired yaw for {@link SmoothAim}. */
    private static final float MOVE_YAW_LERP   = 0.45f;
    /** Lerp factor for precision look-at (mining aim). Slower = smoother. */
    private static final float AIM_LERP        = 0.12f;
    /** Minimum degrees rotated per tick; prevents infinite approach at tiny angles. */
    private static final float MIN_STEP_DEG    = 0.5f;
    /**
     * Player sprints only when facing within this many degrees of its destination.
     * Outside this cone it walks so it can turn without overshooting the waypoint.
     */
    private static final float SPRINT_YAW_THRESHOLD = 35f;

    // ── Waypoint arrival ──────────────────────────────────────
    private static final double WAYPOINT_ARRIVE_RADIUS_SQ = 0.65 * 0.65;
    private static final double WAYPOINT_ARRIVE_DY        = 1.3;

    // ── Jump / stuck detection ───────────────────────────────────
    private static final long   STUCK_JUMP_MS             = 550L;
    private static final double STUCK_PROGRESS_THRESHOLD  = 0.01;

    private Vec3d lastStuckPos  = null;
    private long  stuckStart    = -1L;

    // ── Camera ───────────────────────────────────────────────────
    private final SmoothAim smoothAim;

    public MovementHelper(SmoothAim smoothAim) {
        this.smoothAim = smoothAim;
    }

    // ═════════════════════════════════════════════════════════════
    // Smooth look-at  (used for mining aim)
    // ═════════════════════════════════════════════════════════════

    /**
     * Sets the camera target toward {@code targetCenter} using lerp.
     * The actual interpolation happens inside {@link CameraController} at 100 Hz.
     *
     * @param lerpFactor fraction of remaining angle to close per call (ignored —
     *                   CameraController uses its own fixed lerp; parameter kept
     *                   for API compatibility)
     */
    public void lookAt(ClientPlayerEntity player, Vec3d targetCenter, float lerpFactor) {
        smoothAim.setTarget(targetCenter);
    }

    /** Overload for block targeting — uses eye-height offset to avoid looking at the ground. */
    public void lookAt(ClientPlayerEntity player, BlockPos target, float lerpFactor) {
        smoothAim.setBlockTarget(target, player);
    }

    /**
     * Returns true when the player is looking within {@code toleranceDeg} of {@code targetCenter}.
     */
    public boolean isLookingAt(ClientPlayerEntity player, Vec3d targetCenter, float toleranceDeg) {
        Vec3d eye = player.getEyePos();
        return Math.abs(wrapAngle(yawToward(eye, targetCenter)   - player.getYaw()))   <= toleranceDeg
            && Math.abs(            pitchToward(eye, targetCenter) - player.getPitch()) <= toleranceDeg;
    }

    /** Overload for block targeting — uses the same eye-height offset as {@link #lookAt(ClientPlayerEntity, BlockPos, float)}. */
    public boolean isLookingAt(ClientPlayerEntity player, BlockPos target, float toleranceDeg) {
        double targetY = target.getY() + (target.getY() >= (int) player.getEyePos().y ? 0.5 : 0.9);
        return isLookingAt(player, new Vec3d(target.getX() + 0.5, targetY, target.getZ() + 0.5), toleranceDeg);
    }

    // ═════════════════════════════════════════════════════════════
    // Movement
    // ═════════════════════════════════════════════════════════════

    /**
     * Points the player smoothly toward the center of {@code target} and presses
     * the appropriate directional keys based on the current yaw difference.
     * This allows strafing during turns instead of stopping until aligned.
     *
     * <p>Decomposition: forward = cos(dYaw), strafe = sin(dYaw).
     * The camera target is sent to {@link CameraController} for 100 Hz async lerp.</p>
     */
    public void moveTowards(ClientPlayerEntity player, BlockPos target) {
        Vec3d center  = Vec3d.ofCenter(target);
        float wantYaw = yawToward(player.getPos(), center);
        float dYaw    = wrapAngle(wantYaw - player.getYaw());

        // Aim at eye height toward the waypoint — not at the ground block
        Vec3d aimTarget = new Vec3d(target.getX() + 0.5, player.getEyePos().y, target.getZ() + 0.5);
        smoothAim.setTarget(aimTarget);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        // Decompose movement direction based on angle between current facing and target
        double rad    = Math.toRadians(dYaw);
        double fwd    = Math.cos(rad);   // +1 = straight ahead, -1 = straight behind
        double strafe = Math.sin(rad);   // +1 = pure right, -1 = pure left

        mc.options.forwardKey.setPressed(fwd    >  0.1);
        mc.options.backKey.setPressed   (fwd    < -0.1);
        mc.options.rightKey.setPressed  (strafe >  0.1);
        mc.options.leftKey.setPressed   (strafe < -0.1);

        // Sprint only when heading is roughly forward (within SPRINT_YAW_THRESHOLD)
        player.setSprinting(Math.abs(dYaw) < SPRINT_YAW_THRESHOLD);
    }

    /**
     * Moves the player forward toward the center of the target block.
     */
    public void forwardToBlock(ClientPlayerEntity player, BlockPos target) {
        Vec3d center  = Vec3d.ofCenter(target);
        float wantYaw = yawToward(player.getPos(), center);
        float dYaw    = wrapAngle(wantYaw - player.getYaw());

        // Aim at eye height toward the block — not at the ground
        Vec3d aimTarget = new Vec3d(target.getX() + 0.5, player.getEyePos().y, target.getZ() + 0.5);
        smoothAim.setTarget(aimTarget);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        // Decompose movement direction based on angle between current facing and target
        double rad    = Math.toRadians(dYaw);
        double fwd    = Math.cos(rad);
        double strafe = Math.sin(rad);

        mc.options.forwardKey.setPressed(fwd    >  0.1);
        mc.options.backKey.setPressed   (fwd    < -0.1);
        mc.options.rightKey.setPressed  (strafe >  0.1);
        mc.options.leftKey.setPressed   (strafe < -0.1);

        player.setSprinting(Math.abs(dYaw) < SPRINT_YAW_THRESHOLD);
    }

    /**
     * Returns true when the player's XZ feet position is within
     * {@link #WAYPOINT_ARRIVE_RADIUS_SQ} of the block center and roughly at the
     * right altitude. This keeps the player moving through waypoints without
     * stopping.
     */
    public boolean hasReachedWaypoint(ClientPlayerEntity player, BlockPos target) {
        Vec3d pos    = player.getPos();
        Vec3d center = Vec3d.ofCenter(target);
        double dx = pos.x - center.x;
        double dz = pos.z - center.z;
        double dy = Math.abs(pos.y - target.getY());
        return (dx * dx + dz * dz) <= WAYPOINT_ARRIVE_RADIUS_SQ && dy <= WAYPOINT_ARRIVE_DY;
    }

    /**
     * Presses the jump key when the next waypoint is one block higher than the
     * player, or when the player appears horizontally stuck (hasn't moved in
     * {@link #STUCK_JUMP_MS} ms). This allows climbing up single-block steps.
     */
    public void handleJump(ClientPlayerEntity player, BlockPos nextWaypoint) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        boolean jump = false;

        // 1. Next block is 1 above current block → jump to climb
        if (nextWaypoint.getY() > player.getBlockPos().getY() && player.isOnGround()) {
            jump = true;
        }

        // 2. Horizontally stuck → try a jump to dislodge
        Vec3d pos = player.getPos();
        if (lastStuckPos == null) {
            lastStuckPos = pos;
            stuckStart   = System.currentTimeMillis();
        } else {
            double dx = pos.x - lastStuckPos.x;
            double dz = pos.z - lastStuckPos.z;
            if (dx * dx + dz * dz > STUCK_PROGRESS_THRESHOLD) {
                // Making progress — reset
                lastStuckPos = pos;
                stuckStart   = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - stuckStart > STUCK_JUMP_MS) {
                if (player.isOnGround()) {
                    jump = true;
                }
                // Reset to avoid holding jump forever
                lastStuckPos = pos;
                stuckStart   = System.currentTimeMillis();
            }
        }

        mc.options.jumpKey.setPressed(jump);
    }

    public void resetJumpState() {
        lastStuckPos = null;
        stuckStart   = -1L;
    }

    // ═════════════════════════════════════════════════════════════
    // Release
    // ═════════════════════════════════════════════════════════════

    public void releaseAllInputs() {
        resetJumpState();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
        }
        // Stop aim — clear target so camera doesn't drift
        smoothAim.clearTarget();
    }

    // ═════════════════════════════════════════════════════════════
    // Internal math helpers
    // ═════════════════════════════════════════════════════════════

    /**
     * Lerp step: moves {@code diff} degrees closer by {@code factor}, but
     * enforces a minimum step so the camera doesn't asymptotically stall, and
     * never overshoots zero.
     */
    private static float lerpStep(float diff, float factor) {
        if (Math.abs(diff) < 0.0005f) return 0f;
        float step = diff * factor;
        // Enforce minimum so tiny angles snap cleanly
        if (Math.abs(step) < MIN_STEP_DEG) {
            step = Math.copySign(Math.min(MIN_STEP_DEG, Math.abs(diff)), diff);
        }
        return step;
    }

    /** Yaw angle (degrees) from {@code from} looking toward {@code to}. */
    private static float yawToward(Vec3d from, Vec3d to) {
        return (float) (-Math.atan2(to.x - from.x, to.z - from.z) * (180.0 / Math.PI));
    }

    /** Pitch angle (degrees) from {@code from} looking toward {@code to}. */
    private static float pitchToward(Vec3d from, Vec3d to) {
        double dx   = to.x - from.x;
        double dy   = to.y - from.y;
        double dz   = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) return 0f;
        return (float) (-Math.atan2(dy, dist) * (180.0 / Math.PI));
    }

    private static float wrapAngle(float a) {
        a %= 360f;
        if (a >  180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

