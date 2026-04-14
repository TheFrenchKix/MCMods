package com.example.macromod.pathfinding;

import com.example.macromod.model.MacroConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

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
    /**
     * Player sprints only when facing within this many degrees of its destination.
     * Outside this cone it walks so it can turn without overshooting the waypoint.
     */
    private static final float SPRINT_YAW_THRESHOLD = 35f;

    // ── Steering constants (blocks / tick) ────────────────────────
    private static final double DEFAULT_MAX_SPEED = 0.23;
    private static final double DEFAULT_MAX_ACCEL = 0.18;
    private static final double DEFAULT_SLOWING_RADIUS = 2.0;
    private static final double DEFAULT_INPUT_THRESHOLD = 0.05;

    // ── Humanization (disabled by default for deterministic runs) ─
    private static final boolean DEFAULT_ENABLE_NOISE = false;
    private static final double DEFAULT_NOISE_AMPLITUDE = 0.05;
    private static final long NOISE_REFRESH_MS = 120L;

    // ── Waypoint arrival ──────────────────────────────────────
    // 0.35 block radius: tight enough to force the player near block centre
    // before advancing, preventing edge-clipping on narrow paths.
    private static final double WAYPOINT_ARRIVE_RADIUS_SQ = 0.35 * 0.35;
    private static final double WAYPOINT_ARRIVE_DY        = 0.5;

    // ── Jump / stuck detection ───────────────────────────────────
    private static final long   STUCK_JUMP_MS             = 900L;
    private static final double STUCK_PROGRESS_THRESHOLD  = 0.0025;

    private Vec3d lastStuckPos  = null;
    private long  stuckStart    = -1L;

    // ── Steering state ────────────────────────────────────────────
    private Vec3d steeringVelocity = Vec3d.ZERO;
    private Vec3d noiseOffset = Vec3d.ZERO;
    private long nextNoiseRefreshAt = 0L;
    private final Random noiseRandom = new Random();

    // ── Steering profile (per-macro, with sane defaults) ───────────
    private boolean steeringEnabled = true;
    private double maxSpeed = DEFAULT_MAX_SPEED;
    private double maxAccel = DEFAULT_MAX_ACCEL;
    private double slowingRadius = DEFAULT_SLOWING_RADIUS;
    private double inputThreshold = DEFAULT_INPUT_THRESHOLD;
    private boolean noiseEnabled = DEFAULT_ENABLE_NOISE;
    private double noiseAmplitude = DEFAULT_NOISE_AMPLITUDE;

    // ── Debug telemetry ─────────────────────────────────────────────
    private float lastYawDelta = 0f;
    private double lastDesiredSpeed = 0.0;
    private double lastCurrentSpeed = 0.0;

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
        moveTowards(player, Vec3d.ofCenter(target));
    }

    /**
     * Steering-based movement toward an arbitrary target point.
     * Uses arrival slowdown + acceleration toward desired velocity.
     */
    public void moveTowards(ClientPlayerEntity player, Vec3d targetPoint) {
        applySteeringMovement(player, targetPoint, true);
    }

    /**
     * Moves the player forward toward the center of the target block.
     */
    public void forwardToBlock(ClientPlayerEntity player, BlockPos target) {
        applySteeringMovement(player, Vec3d.ofCenter(target), false);
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
        
        // Slightly more lenient for diagonal approaches to prevent oscillation
        double radiusSq = isDiagonalApproach ? 0.50 * 0.50 : WAYPOINT_ARRIVE_RADIUS_SQ;
        
        return (dx * dx + dz * dz) <= radiusSq && dy <= WAYPOINT_ARRIVE_DY;
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
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d nextCenter = Vec3d.ofCenter(nextWaypoint);
        double toNextX = pos.x - nextCenter.x;
        double toNextZ = pos.z - nextCenter.z;
        double distToNextSq = toNextX * toNextX + toNextZ * toNextZ;
        if (lastStuckPos == null) {
            lastStuckPos = pos;
            stuckStart   = System.currentTimeMillis();
        } else {
            double dx = pos.x - lastStuckPos.x;
            double dz = pos.z - lastStuckPos.z;
            double movedSq = dx * dx + dz * dz;
            if (movedSq > STUCK_PROGRESS_THRESHOLD) {
                // Making progress — reset
                lastStuckPos = pos;
                stuckStart   = System.currentTimeMillis();
            } else if (distToNextSq > 1.2 * 1.2 && System.currentTimeMillis() - stuckStart > STUCK_JUMP_MS) {
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

    public void resetSteeringState() {
        steeringVelocity = Vec3d.ZERO;
        noiseOffset = Vec3d.ZERO;
        nextNoiseRefreshAt = 0L;
        lastYawDelta = 0f;
        lastDesiredSpeed = 0.0;
        lastCurrentSpeed = 0.0;
    }

    /**
     * Applies per-macro steering settings.
     */
    public void applyMacroConfig(MacroConfig cfg) {
        if (cfg == null) {
            steeringEnabled = true;
            maxSpeed = DEFAULT_MAX_SPEED;
            maxAccel = DEFAULT_MAX_ACCEL;
            slowingRadius = DEFAULT_SLOWING_RADIUS;
            inputThreshold = DEFAULT_INPUT_THRESHOLD;
            noiseEnabled = DEFAULT_ENABLE_NOISE;
            noiseAmplitude = DEFAULT_NOISE_AMPLITUDE;
            return;
        }

        steeringEnabled = cfg.isSteeringEnabled();
        maxSpeed = safeRange(cfg.getSteeringMaxSpeed(), 0.08, 0.40, DEFAULT_MAX_SPEED);
        maxAccel = safeRange(cfg.getSteeringAcceleration(), 0.05, 0.50, DEFAULT_MAX_ACCEL);
        slowingRadius = safeRange(cfg.getSteeringSlowingRadius(), 0.5, 6.0, DEFAULT_SLOWING_RADIUS);
        inputThreshold = DEFAULT_INPUT_THRESHOLD;
        noiseEnabled = cfg.isSteeringNoiseEnabled();
        noiseAmplitude = safeRange(cfg.getSteeringNoiseAmplitude(), 0.0, 0.20, DEFAULT_NOISE_AMPLITUDE);
    }

    public float getLastYawDelta() {
        return lastYawDelta;
    }

    public double getLastDesiredSpeed() {
        return lastDesiredSpeed;
    }

    public double getLastCurrentSpeed() {
        return lastCurrentSpeed;
    }

    // ═════════════════════════════════════════════════════════════
    // Release
    // ═════════════════════════════════════════════════════════════

    public void releaseAllInputs() {
        resetJumpState();
        resetSteeringState();
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

    private void applySteeringMovement(ClientPlayerEntity player, Vec3d baseTarget, boolean allowSprint) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d target = applyNoise(baseTarget);

        double dx = target.x - playerPos.x;
        double dz = target.z - playerPos.z;
        double distSq = dx * dx + dz * dz;
        if (distSq < 1e-6) {
            steeringVelocity = steeringVelocity.multiply(0.6, 0.0, 0.6);
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            player.setSprinting(false);
            lastYawDelta = 0f;
            lastDesiredSpeed = 0.0;
            lastCurrentSpeed = 0.0;
            return;
        }

        double distance = Math.sqrt(distSq);
        Vec3d direction = new Vec3d(dx / distance, 0.0, dz / distance);

        if (!steeringEnabled) {
            Vec3d aimTarget = new Vec3d(target.x, player.getEyePos().y, target.z);
            smoothAim.setTarget(aimTarget);

            float wantYaw = yawToward(playerPos, target);
            float dYaw = wrapAngle(wantYaw - player.getYaw());
            lastYawDelta = dYaw;

            double rad = Math.toRadians(dYaw);
            double fwd = Math.cos(rad);
            double strafe = Math.sin(rad);

            mc.options.forwardKey.setPressed(fwd > inputThreshold);
            mc.options.backKey.setPressed(fwd < -inputThreshold);
            mc.options.rightKey.setPressed(strafe > inputThreshold);
            mc.options.leftKey.setPressed(strafe < -inputThreshold);

            player.setSprinting(allowSprint && Math.abs(dYaw) < SPRINT_YAW_THRESHOLD);
            steeringVelocity = Vec3d.ZERO;
            lastDesiredSpeed = maxSpeed;
            lastCurrentSpeed = 0.0;
            return;
        }

        double speedScale = 1.0;
        if (distance < slowingRadius) {
            speedScale = clamp((float) (distance / slowingRadius), 0.18f, 1.0f);
        }

        Vec3d desiredVelocity = direction.multiply(maxSpeed * speedScale);
        Vec3d steering = desiredVelocity.subtract(steeringVelocity).multiply(maxAccel);
        steeringVelocity = steeringVelocity.add(steering);
        steeringVelocity = clampHorizontalLength(steeringVelocity, maxSpeed);

        Vec3d aimTarget = new Vec3d(target.x, player.getEyePos().y, target.z);
        smoothAim.setTarget(aimTarget);

        float wantYaw = yawToward(playerPos, target);
        float dYaw = wrapAngle(wantYaw - player.getYaw());
        lastYawDelta = dYaw;

        double yawRad = Math.toRadians(player.getYaw());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double fwd = steeringVelocity.x * forwardX + steeringVelocity.z * forwardZ;
        double strafe = steeringVelocity.x * rightX + steeringVelocity.z * rightZ;

        mc.options.forwardKey.setPressed(fwd > inputThreshold);
        mc.options.backKey.setPressed(fwd < -inputThreshold);
        mc.options.rightKey.setPressed(strafe > inputThreshold);
        mc.options.leftKey.setPressed(strafe < -inputThreshold);

        boolean canSprint = allowSprint
            && Math.abs(dYaw) < SPRINT_YAW_THRESHOLD
            && distance > 1.4
            && fwd > 0.12;
        player.setSprinting(canSprint);

        lastDesiredSpeed = desiredVelocity.horizontalLength();
        lastCurrentSpeed = steeringVelocity.horizontalLength();
    }

    private Vec3d applyNoise(Vec3d target) {
        if (!noiseEnabled) return target;

        long now = System.currentTimeMillis();
        if (now >= nextNoiseRefreshAt) {
            nextNoiseRefreshAt = now + NOISE_REFRESH_MS;
            double nx = (noiseRandom.nextDouble() - 0.5) * noiseAmplitude;
            double nz = (noiseRandom.nextDouble() - 0.5) * noiseAmplitude;
            noiseOffset = new Vec3d(nx, 0.0, nz);
        }
        return target.add(noiseOffset);
    }

    private static Vec3d clampHorizontalLength(Vec3d v, double maxLen) {
        double lenSq = v.x * v.x + v.z * v.z;
        if (lenSq <= maxLen * maxLen) return v;
        double len = Math.sqrt(lenSq);
        if (len < 1e-8) return Vec3d.ZERO;
        double scale = maxLen / len;
        return new Vec3d(v.x * scale, 0.0, v.z * scale);
    }

    // ═════════════════════════════════════════════════════════════
    // Internal math helpers
    // ═════════════════════════════════════════════════════════════

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

    private static double safeRange(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        if (value < min || value > max) return fallback;
        return value;
    }
}

