package com.example.macromod.pathfinding;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Millisecond-based smooth aim system with human-like imperfections.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Called <em>once per client tick</em> from {@code ClientTickEvents.END_CLIENT_TICK}.
 *       Uses delta-time (milliseconds) between ticks for frame-rate independent rotation.</li>
 *   <li>Rotation is driven by <b>linear interpolation (LERP)</b> scaled by elapsed time.
 *       Produces consistent aiming speed regardless of FPS.</li>
 *   <li>Human-like imperfections via variable speed (random ± on LERP factor).</li>
 *   <li>Yaw wrapping is handled via shortest-arc math so the camera always turns
 *       the shorter way around (no spinning through 360°).</li>
 *   <li>Per-frame rotation is clamped to {@link #MAX_YAW_SPEED} / {@link #MAX_PITCH_SPEED}
 *       (in degrees/millisecond) for consistent aiming across all frame rates.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * SmoothAim smoothAim = new SmoothAim();
 *
 * ClientTickEvents.END_CLIENT_TICK.register(client -> {
 *     if (client.player == null) return;
 *     smoothAim.tick(client.player);
 * });
 *
 * smoothAim.setTarget(entity.getEyePos());
 * smoothAim.setBlockTarget(blockPos, player);
 * smoothAim.clearTarget();
 * }</pre>
 */
public class SmoothAim {

    // ═══════════════════════════════════════════════════════════════
    // Tuning constants (millisecond-based)
    // ═══════════════════════════════════════════════════════════════

    /** LERP rate: fraction of remaining angle closed per millisecond. */
    private static final float LERP_RATE_PER_MS = 0.00035f;

    /** Random ± variation on LERP for slight speed irregularity. */
    private static final float LERP_VARIATION = 0.00004f;

    /** Maximum yaw rotation speed in degrees per millisecond (~14°/sec). */
    private static final float MAX_YAW_SPEED = 0.014f;

    /** Maximum pitch rotation speed in degrees per millisecond (~10°/sec). */
    private static final float MAX_PITCH_SPEED = 0.010f;

    /** Snap to target when both errors are below this threshold (degrees). */
    private static final float SNAP_THRESHOLD = 0.3f;

    /** Maximum delta-time to accept — caps large jumps after lag spikes (ms). */
    private static final long MAX_DELTA_MS = 100L;

    // ═══════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════

    private Vec3d targetPoint = null;
    private boolean active = false;
    private long lastTickTimeMs = 0L;
    private final Random rng = new Random();

    public SmoothAim() {}

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    public void setTarget(Vec3d target) {
        this.targetPoint = target;
        this.active = true;
    }

    public void setTarget(Entity entity) {
        setTarget(entity.getEyePos());
    }

    public void setBlockTarget(BlockPos blockPos, ClientPlayerEntity player) {
        double targetY = blockPos.getY() + 0.9;
        if (blockPos.getY() >= (int) player.getEyePos().y) {
            targetY = blockPos.getY() + 0.5;
        }
        setTarget(new Vec3d(blockPos.getX() + 0.5, targetY, blockPos.getZ() + 0.5));
    }

    public void clearTarget() {
        this.active = false;
        this.targetPoint = null;
        this.lastTickTimeMs = 0L;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isOnTarget(ClientPlayerEntity player, float toleranceDeg) {
        if (!active || targetPoint == null) return false;
        Vec3d eye = player.getEyePos();
        float dYaw   = Math.abs(wrapAngle(yawToward(eye, targetPoint) - player.getYaw()));
        float dPitch = Math.abs(pitchToward(eye, targetPoint) - player.getPitch());
        return dYaw <= toleranceDeg && dPitch <= toleranceDeg;
    }

    // ═══════════════════════════════════════════════════════════════
    // Game-thread tick — called once per END_CLIENT_TICK
    // ═══════════════════════════════════════════════════════════════

    public void tick(ClientPlayerEntity player) {
        if (!active || targetPoint == null || player == null) return;

        long now = System.currentTimeMillis();
        if (lastTickTimeMs == 0L) {
            lastTickTimeMs = now;
            return;
        }

        long deltaMs = Math.min(MAX_DELTA_MS, Math.max(1L, now - lastTickTimeMs));
        lastTickTimeMs = now;

        Vec3d eye = player.getEyePos();

        float wantYaw   = yawToward(eye, targetPoint);
        float wantPitch = pitchToward(eye, targetPoint);

        float curYaw   = player.getYaw();
        float curPitch = player.getPitch();

        float dYaw   = wrapAngle(wantYaw - curYaw);
        float dPitch = wantPitch - curPitch;

        // Snap when close enough
        if (Math.abs(dYaw) < SNAP_THRESHOLD && Math.abs(dPitch) < SNAP_THRESHOLD) {
            player.setYaw(wantYaw);
            player.setPitch(clampPitch(wantPitch));
            return;
        }

        // LERP factor with per-tick random variation
        float variation = (rng.nextFloat() * 2f - 1f) * LERP_VARIATION;
        float lerp = LERP_RATE_PER_MS + variation;
        lerp = clamp(lerp, 0.0001f, 1.0f);

        // Linear interpolation steps scaled by delta time
        float yawStep   = dYaw   * lerp * deltaMs;
        float pitchStep = dPitch * lerp * deltaMs;

        // Per-frame speed clamp (in degrees/ms)
        yawStep   = clamp(yawStep,   -MAX_YAW_SPEED * deltaMs,   MAX_YAW_SPEED * deltaMs);
        pitchStep = clamp(pitchStep, -MAX_PITCH_SPEED * deltaMs, MAX_PITCH_SPEED * deltaMs);

        // Apply
        player.setYaw(curYaw + yawStep);
        player.setPitch(clampPitch(curPitch + pitchStep));
    }

    // ═══════════════════════════════════════════════════════════════
    // Math helpers
    // ═══════════════════════════════════════════════════════════════

    private static float yawToward(Vec3d from, Vec3d to) {
        return (float) (-Math.atan2(to.x - from.x, to.z - from.z) * (180.0 / Math.PI));
    }

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

    private static float clampPitch(float p) {
        return Math.max(-90f, Math.min(90f, p));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
