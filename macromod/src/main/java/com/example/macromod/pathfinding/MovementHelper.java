package com.example.macromod.pathfinding;

import com.example.macromod.pathfinding.oringo.OringoMovementMath;
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
    private static final float MIN_STEP_DEG    = 1.0f;
    /**
     * Player sprints only when facing within this many degrees of its destination.
     * Outside this cone it walks so it can turn without overshooting the waypoint.
     */
    private static final float SPRINT_YAW_THRESHOLD = 35f;

    // ── Waypoint arrival ──────────────────────────────────────
    // 0.50 block radius: relaxed since LOS-based target selection handles skipping.
    // Only the final destination needs precise centering (via isCenteredOnBlock).
    private static final double WAYPOINT_ARRIVE_RADIUS_SQ = 0.50 * 0.50;
    private static final double WAYPOINT_ARRIVE_DY        = 0.5;

    // ── Jump / stuck / strafe detection ──────────────────────────
    private static final long   STUCK_JUMP_MS             = 200L;
    private static final double STUCK_PROGRESS_THRESHOLD  = 0.01;
    private static final long   STRAFE_DISLODGE_MS        = 400L;

    private Vec3d lastStuckPos  = null;
    private long  stuckStart    = -1L;
    private boolean dislodgeStrafeLeft = true;

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

    // ── Direction hysteresis (prevents jittery target switching) ──
    private Vec3d lastMoveTarget = null;
    /** Ticks that the yaw has exceeded the sprint threshold consecutively. */
    private int sprintOffTicks = 0;
    private static final int SPRINT_OFF_DELAY = 3;

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
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());

        // Direction hysteresis: don't jitter between two targets that are close together
        // Only update the effective target if it moved significantly (>0.5 blocks)
        Vec3d effectiveTarget = center;
        if (lastMoveTarget != null && lastMoveTarget.squaredDistanceTo(center) < 0.25) {
            effectiveTarget = lastMoveTarget;
        } else {
            lastMoveTarget = center;
        }

        float wantYaw = yawToward(playerPos, effectiveTarget);
        float dYaw    = wrapAngle(wantYaw - player.getYaw());

        // Calculate distance to waypoint
        double dx = playerPos.x - effectiveTarget.x;
        double dz = playerPos.z - effectiveTarget.z;
        double distSq = dx * dx + dz * dz;
        
        // When very close to waypoint, tighten angle tolerance to reduce oscillation
        double decompositionThreshold = distSq < 0.2 * 0.2 ? 0.15 : 0.1;

        // Aim at eye height toward the waypoint — not at the ground block
        Vec3d aimTarget = new Vec3d(effectiveTarget.x, player.getEyePos().y, effectiveTarget.z);
        smoothAim.setTarget(aimTarget);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        OringoMovementMath.InputSolution input = resolveInputFromYawDelta(dYaw, decompositionThreshold);
        mc.options.forwardKey.setPressed(input.forward() > 0);
        mc.options.backKey.setPressed(input.forward() < 0);
        mc.options.rightKey.setPressed(input.strafe() > 0);
        mc.options.leftKey.setPressed(input.strafe() < 0);

        // Sprint stability: only toggle sprint OFF after 3 consecutive ticks
        // of misalignment, preventing flickering during minor heading adjustments
        if (Math.abs(dYaw) < SPRINT_YAW_THRESHOLD) {
            sprintOffTicks = 0;
            player.setSprinting(true);
        } else {
            sprintOffTicks++;
            if (sprintOffTicks >= SPRINT_OFF_DELAY) {
                player.setSprinting(false);
            }
        }
    }

    /**
     * Converts yaw delta into Oringo-style normalized forward/strafe intent.
     */
    private static OringoMovementMath.InputSolution resolveInputFromYawDelta(float deltaYaw, double threshold) {
        double rad = Math.toRadians(deltaYaw);
        float rawForward = (float) Math.cos(rad);
        float rawStrafe = (float) Math.sin(rad);

        if (Math.abs(rawForward) < threshold) rawForward = 0f;
        if (Math.abs(rawStrafe) < threshold) rawStrafe = 0f;

        return OringoMovementMath.normalizeInput(rawForward, rawStrafe, 0f);
    }

    /**
     * Moves the player forward toward the center of the target block.
     */
    public void forwardToBlock(ClientPlayerEntity player, BlockPos target) {
        Vec3d center  = Vec3d.ofCenter(target);
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        float wantYaw = yawToward(playerPos, center);
        float dYaw    = wrapAngle(wantYaw - player.getYaw());

        // Calculate distance to block
        double dx = playerPos.x - center.x;
        double dz = playerPos.z - center.z;
        double distSq = dx * dx + dz * dz;
        
        // When very close to target, tighten angle tolerance for diagonal stability
        double decompositionThreshold = distSq < 0.2 * 0.2 ? 0.15 : 0.1;

        // Aim at eye height toward the block — not at the ground
        Vec3d aimTarget = new Vec3d(target.getX() + 0.5, player.getEyePos().y, target.getZ() + 0.5);
        smoothAim.setTarget(aimTarget);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        OringoMovementMath.InputSolution input = resolveInputFromYawDelta(dYaw, decompositionThreshold);
        mc.options.forwardKey.setPressed(input.forward() > 0);
        mc.options.backKey.setPressed(input.forward() < 0);
        mc.options.rightKey.setPressed(input.strafe() > 0);
        mc.options.leftKey.setPressed(input.strafe() < 0);

        player.setSprinting(Math.abs(dYaw) < SPRINT_YAW_THRESHOLD);
    }

    /**
     * Returns true when the player is within the arrival radius of the block center and roughly at the right altitude.
     * @param player
     * @param target
     * @return
     */
    public void releaseForward(ClientPlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
        }
    }

    /**
     * Returns true when the player's XZ feet position is within
     * {@link #WAYPOINT_ARRIVE_RADIUS_SQ} of the block center and roughly at the
     * right altitude. This keeps the player moving through waypoints without
     * stopping.
     * 
     * For diagonal movements, uses a slightly larger radius to smooth out oscillation
     * that can occur when approaching waypoints at 45° angles.
     */
    public boolean hasReachedWaypoint(ClientPlayerEntity player, BlockPos target) {
        Vec3d pos    = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d center = Vec3d.ofCenter(target);
        double dx = pos.x - center.x;
        double dz = pos.z - center.z;
        double dy = Math.abs(pos.y - target.getY());
        
        // Detect if approaching diagonally (both dx and dz components significant)
        boolean isDiagonalApproach = Math.abs(dx) > 0.3 && Math.abs(dz) > 0.3;
        
        // More lenient for diagonal approaches to prevent oscillation
        double radiusSq = isDiagonalApproach ? 0.65 * 0.65 : WAYPOINT_ARRIVE_RADIUS_SQ;
        
        return (dx * dx + dz * dz) <= radiusSq && dy <= WAYPOINT_ARRIVE_DY;
    }

    /**
     * Handles stuck recovery via lateral strafe dislodge.
     * Jumping is fully delegated to vanilla autojump (enabled at macro start).
     * If stuck for {@link #STRAFE_DISLODGE_MS}ms, alternates left/right strafe
     * to slide around block corners.
     */
    public void handleStuckRecovery(ClientPlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        if (lastStuckPos == null) {
            lastStuckPos = pos;
            stuckStart   = System.currentTimeMillis();
        } else {
            double ddx = pos.x - lastStuckPos.x;
            double ddz = pos.z - lastStuckPos.z;
            if (ddx * ddx + ddz * ddz > STUCK_PROGRESS_THRESHOLD) {
                lastStuckPos = pos;
                stuckStart   = System.currentTimeMillis();
                dislodgeStrafeLeft = true;
            } else {
                long elapsed = System.currentTimeMillis() - stuckStart;
                if (elapsed > STRAFE_DISLODGE_MS) {
                    long strafePhase = (elapsed - STRAFE_DISLODGE_MS) / 400L;
                    dislodgeStrafeLeft = (strafePhase % 2 == 0);
                    mc.options.leftKey.setPressed(dislodgeStrafeLeft);
                    mc.options.rightKey.setPressed(!dislodgeStrafeLeft);
                    mc.options.forwardKey.setPressed(true);
                }
            }
        }
    }

    public void resetJumpState() {
        lastStuckPos = null;
        stuckStart   = -1L;
        dislodgeStrafeLeft = true;
    }

    // ═════════════════════════════════════════════════════════════
    // Release
    // ═════════════════════════════════════════════════════════════

    public void releaseAllInputs() {
        resetJumpState();
        lastMoveTarget = null;
        sprintOffTicks = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            mc.options.attackKey.setPressed(false);
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

