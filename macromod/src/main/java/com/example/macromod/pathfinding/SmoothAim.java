package com.example.macromod.pathfinding;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tick-based smooth aim system with human-like imperfections.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Called <em>once per client tick</em> from {@code ClientTickEvents.END_CLIENT_TICK}.
 *       No threads, no timers — all logic executes synchronously in the game tick.</li>
 *   <li>Rotation is driven by pure <b>linear interpolation (LERP)</b>.
 *       No easing curves, but the LERP factor is scaled linearly by angle distance
 *       to produce natural acceleration/deceleration.</li>
 *   <li>Human-like imperfections are added via:
 *     <ol>
 *       <li><b>Variable speed</b> — random ± jitter on the LERP factor each tick.</li>
 *       <li><b>Speed scaling</b> — linear ramp: fast when far, slow when close.</li>
 *       <li><b>Micro-jitter</b> — tiny random angular noise after the lerp step.
 *           Intensity is inversely proportional to speed (more wobble when nearly on-target).</li>
 *     </ol>
 *   </li>
 *   <li>Yaw wrapping is handled via shortest-arc math so the camera always turns
 *       the shorter way around (no spinning through 360°).</li>
 *   <li>Per-tick rotation is clamped to {@link #MAX_YAW_STEP} / {@link #MAX_PITCH_STEP}
 *       so the aim can never teleport even at very large angles.</li>
 * </ul>
 *
 * <h3>Sub-tick smoothness</h3>
 * <p>An internal {@link ScheduledExecutorService} runs the rotation lerp every
 * {@value #LOOP_INTERVAL_MS} ms ({@code scheduleWithFixedDelay}) so the camera
 * updates ~5× per game tick instead of once, eliminating the coarse 50 ms
 * stepping that creates jitter at low angular speeds.  The game-thread
 * {@link #tick(ClientPlayerEntity)} method just updates the target; the
 * background thread does all rotation writes.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Created once; the sub-tick loop starts automatically.
 * SmoothAim smoothAim = new SmoothAim();
 *
 * ClientTickEvents.END_CLIENT_TICK.register(client -> {
 *     if (client.player == null) return;
 *     // Update target once per tick for moving targets.
 *     smoothAim.tick(client.player);
 * });
 *
 * // Aim at an entity:
 * smoothAim.setTarget(entity.getEyePos());
 * // Aim at a block (avoids looking at the ground):
 * smoothAim.setBlockTarget(blockPos, player);
 * // Stop aiming:
 * smoothAim.clearTarget();
 * }</pre>
 */
public class SmoothAim {

    // ═══════════════════════════════════════════════════════════════
    // Tuning constants — adjust to taste
    // ═══════════════════════════════════════════════════════════════

    /**
     * Background loop interval in milliseconds.
     * 10 ms ≈ 100 Hz — 5× the game tick rate, giving smooth sub-tick rotation.
     * Uses {@code scheduleWithFixedDelay} so bursts never stack up.
     */
    private static final long LOOP_INTERVAL_MS = 10L;

    /**
     * Base LERP factor: fraction of the remaining angle closed each background step.
     * Scaled down from the old per-tick value because we now run 5× more often.
     * 0.035 per 10 ms × 5 steps ≈ 0.16 effective per tick — same feel, smoother motion.
     */
    private volatile float BASE_LERP = 0.035f;

    /**
     * Maximum random ± variation applied to the LERP factor each loop step.
     * Produces slight speed irregularity — a human never rotates at perfectly
     * constant speed.  Keep at ≤50% of BASE_LERP.
     */
    private volatile float LERP_VARIATION = 0.012f;

    /**
     * Angle at which the piecewise speed ramp bottoms out (slow zone boundary).
     * Below this total angle delta the aim crawls in for precision.
     */
    private volatile float SLOW_ZONE_DEG = 15f;

    /**
     * Angle at which the speed ramp reaches its maximum multiplier.
     * Above this the aim moves as fast as the clamp allows.
     */
    private volatile float FAST_ZONE_DEG = 55f;

    /** Speed multiplier at the bottom of the slow zone (0 degrees total delta). */
    private volatile float SPEED_AT_ZERO    = 0.55f;
    /** Speed multiplier at the top of the slow zone / bottom of fast zone. */
    private volatile float SPEED_AT_SLOW    = 1.0f;
    /** Speed multiplier above the fast zone. */
    private volatile float SPEED_AT_FAST    = 1.25f;

    /**
     * Maximum yaw change per background step (10 ms) in degrees.
     * 22° / 5 steps = 4.4° per step, same effective cap as the old per-tick value.
     */
    private static final float MAX_YAW_STEP   = 5.5f;

    /**
     * Maximum pitch change per background step (10 ms) in degrees.
     */
    private static final float MAX_PITCH_STEP = 4.0f;

    /**
     * Once both yaw and pitch error are below this threshold (degrees),
     * the position is snapped exactly to avoid the asymptotic stall.
     */
    private static final float SNAP_THRESHOLD = 0.35f;

    /**
     * Amplitude of the micro-jitter added <em>after</em> the lerp step (degrees).
     * Scales with {@code (1 - speedScale)} so jitter is greatest when nearly on-target
     * and absent when performing a fast, wide sweep.
     */
    private static final float JITTER_AMP_YAW   = 0.20f;
    private static final float JITTER_AMP_PITCH  = 0.12f;

    // ═══════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════

    /** Current aim target in world space. Written by game thread, read by executor. */
    private volatile Vec3d targetPoint = null;

    /** Whether the system is currently active. */
    private final AtomicBoolean active = new AtomicBoolean(false);

    private final Random rng = new Random();

    // ── Sub-tick executor ────────────────────────────────────────
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "smooth-aim");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> future;

    public SmoothAim() {
        // Start the sub-tick loop immediately; it's a no-op when no target is set.
        future = executor.scheduleWithFixedDelay(
                this::subtickStep, 0L, LOOP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set (or update) the world-space point to aim at.
     * For moving entities, call this every tick <em>before</em> {@link #tick}.
     *
     * @param target world-space position, typically {@code entity.getEyePos()}
     */
    public void setTarget(Vec3d target) {
        this.targetPoint = target;
        this.active.set(true);
    }

    /**
     * Convenience overload — aims at the entity's body center (not eye pos).
     * This fixes issues with small entities (Pig, Chicken) where eye pos is above the entity.
     * Call every tick so the aim tracks movement.
     */
    public void setTarget(Entity entity) {
        // Use the entity's body center instead of getEyePos() to avoid aiming above small entities.
        // Body center = pos + (height * 0.5)
        double centerY = entity.getY() + entity.getHeight() * 0.5;
        setTarget(new Vec3d(entity.getX(), centerY, entity.getZ()));
    }

    /**
     * Aims at a block, offsetting the target Y so the player looks at the
     * upper portion of the block face rather than the center (which can point
     * the camera at the ground when blocks are at or below foot level).
     *
     * <p>Uses {@code blockPos.y + 0.9} — just below the top face — which
     * always produces a shallower downward angle than the block center.</p>
     *
     * @param blockPos the block to aim at
     * @param player   used to further clamp the Y so we never aim below the
     *                 player's own eye level if the block is above the player
     */
    public void setBlockTarget(BlockPos blockPos, ClientPlayerEntity player) {
        double targetY = blockPos.getY() + 0.9;
        // If the block is above the player, aim at its center instead
        if (blockPos.getY() >= (int) player.getEyePos().y) {
            targetY = blockPos.getY() + 0.5;
        }
        setTarget(new Vec3d(blockPos.getX() + 0.5, targetY, blockPos.getZ() + 0.5));
    }

    /**
     * Releases the current target. The rotation loop stays running but is a no-op.
     * Rotation is NOT reset — the player keeps their current facing.
     */
    public void clearTarget() {
        this.active.set(false);
        this.targetPoint = null;
    }

    /** Returns {@code true} when a target is set and the system is active. */
    public boolean isActive() {
        return active.get();
    }

    // ═══════════════════════════════════════════════════════════════
    // Configurable tuning — readable/writable from the GUI
    // ═══════════════════════════════════════════════════════════════

    public float getBaseLerp()      { return BASE_LERP; }
    public void  setBaseLerp(float v)      { BASE_LERP      = Math.max(0.005f, Math.min(0.15f, v)); }

    public float getSpeedAtZero()   { return SPEED_AT_ZERO; }
    public void  setSpeedAtZero(float v)   { SPEED_AT_ZERO  = Math.max(0.05f, Math.min(3.0f, v)); }

    public float getSpeedAtSlow()   { return SPEED_AT_SLOW; }
    public void  setSpeedAtSlow(float v)   { SPEED_AT_SLOW  = Math.max(0.1f, Math.min(4.0f, v)); }

    public float getSpeedAtFast()   { return SPEED_AT_FAST; }
    public void  setSpeedAtFast(float v)   { SPEED_AT_FAST  = Math.max(0.1f, Math.min(5.0f, v)); }

    public float getSlowZoneDeg()   { return SLOW_ZONE_DEG; }
    public void  setSlowZoneDeg(float v)   { SLOW_ZONE_DEG  = Math.max(3f, Math.min(30f, v)); }

    public float getFastZoneDeg()   { return FAST_ZONE_DEG; }
    public void  setFastZoneDeg(float v)   { FAST_ZONE_DEG  = Math.max(10f, Math.min(90f, v)); }

    /**
     * Returns whether the player is currently within {@code toleranceDeg} of
     * the aim target on both axes.  Useful to delay an attack until on-target.
     *
     * @param player      the local player
     * @param toleranceDeg acceptable error in degrees (2–5° is typical)
     */
    public boolean isOnTarget(ClientPlayerEntity player, float toleranceDeg) {
        if (!active.get() || targetPoint == null) return false;
        Vec3d eye = player.getEyePos();
        float dYaw   = Math.abs(wrapAngle(yawToward(eye, targetPoint)   - player.getYaw()));
        float dPitch = Math.abs(        pitchToward(eye, targetPoint)    - player.getPitch());
        return dYaw <= toleranceDeg && dPitch <= toleranceDeg;
    }

    // ═══════════════════════════════════════════════════════════════
    // Game-thread tick — called once per END_CLIENT_TICK
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called once per game tick from {@code ClientTickEvents.END_CLIENT_TICK}.
     * For <em>moving</em> targets (entities), refresh the target point here so
     * the sub-tick loop always has the latest position.
     *
     * <p>For static targets (blocks, waypoints), {@link #setTarget} only needs to
     * be called once; this method can be a pass-through.</p>
     *
     * @param player the local player (used only for moving-entity refresh by callers)
     */
    public void tick(ClientPlayerEntity player) {
        // Target refresh for moving entities is done by the caller via setTarget().
        // Rotation itself happens in the sub-tick loop — nothing to do here.
    }

    // ═══════════════════════════════════════════════════════════════
    // Sub-tick rotation loop — runs every LOOP_INTERVAL_MS on executor
    // ═══════════════════════════════════════════════════════════════

    /**
     * Core rotation step executed every {@value #LOOP_INTERVAL_MS} ms by the
     * background executor.  All MC API calls that touch player rotation are
     * thread-safe in practice (volatile float fields set via setYaw/setPitch).
     */
    private void subtickStep() {
        if (!active.get() || targetPoint == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        Vec3d localTarget = targetPoint; // local copy — avoid TOCTOU
        Vec3d eye = player.getEyePos();

        // ── 1. Desired angles toward the target ───────────────────
        float wantYaw   = yawToward(eye, localTarget);
        float wantPitch = pitchToward(eye, localTarget);

        float curYaw   = player.getYaw();
        float curPitch = player.getPitch();

        // Shortest-arc deltas
        float dYaw   = wrapAngle(wantYaw   - curYaw);
        float dPitch = wantPitch            - curPitch;

        // ── 2. Snap when close enough ─────────────────────────────
        if (Math.abs(dYaw) < SNAP_THRESHOLD && Math.abs(dPitch) < SNAP_THRESHOLD) {
            player.setYaw(wantYaw);
            player.setPitch(clampPitch(wantPitch));
            return;
        }

        // ── 3. Linear speed scaling ───────────────────────────────
        float totalDelta = Math.abs(dYaw) + Math.abs(dPitch);
        float speedScale = linearSpeedScale(totalDelta);

        // ── 4. LERP factor with per-step random variation ─────────
        float variation = (rng.nextFloat() * 2f - 1f) * LERP_VARIATION;
        float lerp = (BASE_LERP + variation) * speedScale;
        lerp = clamp(lerp, 0.008f, 1.0f);

        // ── 5. Raw lerp steps ─────────────────────────────────────
        float yawStep   = dYaw   * lerp;
        float pitchStep = dPitch * lerp;

        // ── 6. Per-step speed clamp ───────────────────────────────
        yawStep   = clamp(yawStep,   -MAX_YAW_STEP,   MAX_YAW_STEP);
        pitchStep = clamp(pitchStep, -MAX_PITCH_STEP, MAX_PITCH_STEP);

        // ── 7. Apply the rotation ─────────────────────────────────
        float newYaw   = curYaw   + yawStep;
        float newPitch = clampPitch(curPitch + pitchStep);

        // ── 8. Micro-jitter near the target ───────────────────────
        float jitterScale = Math.max(0f, 1f - speedScale);
        newYaw   += (rng.nextFloat() * 2f - 1f) * JITTER_AMP_YAW   * jitterScale;
        newPitch += (rng.nextFloat() * 2f - 1f) * JITTER_AMP_PITCH * jitterScale;
        newPitch  = clampPitch(newPitch);

        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

    // ═══════════════════════════════════════════════════════════════
    // Speed scaling — piecewise linear ramp
    // ═══════════════════════════════════════════════════════════════

    /**
     * Maps total angular distance (degrees) to a speed multiplier.
     *
     * <pre>
     *  delta │  scale
     * ───────┼─────────────────────────────────────────────────────
     *    0°  │  SPEED_AT_ZERO  (0.35× — deliberate final correction)
     *   15°  │  SPEED_AT_SLOW  (1.00× — base speed)
     *   55°  │  SPEED_AT_FAST  (1.25× — fast wide sweep)
     *  >55°  │  SPEED_AT_FAST  (capped)
     * </pre>
     *
     * <p>All segments are <em>linear</em> — no easing, no smoothstep.
     * The combination of LERP + this linear ramp produces acceleration/deceleration
     * without violating the "LERP only" constraint.</p>
     */
    private float linearSpeedScale(float totalDelta) {
        if (totalDelta <= 0f) {
            return SPEED_AT_ZERO;
        } else if (totalDelta < SLOW_ZONE_DEG) {
            // Linear: SPEED_AT_ZERO → SPEED_AT_SLOW over [0, SLOW_ZONE_DEG]
            float t = totalDelta / SLOW_ZONE_DEG;
            return SPEED_AT_ZERO + t * (SPEED_AT_SLOW - SPEED_AT_ZERO);
        } else if (totalDelta < FAST_ZONE_DEG) {
            // Linear: SPEED_AT_SLOW → SPEED_AT_FAST over [SLOW_ZONE_DEG, FAST_ZONE_DEG]
            float t = (totalDelta - SLOW_ZONE_DEG) / (FAST_ZONE_DEG - SLOW_ZONE_DEG);
            return SPEED_AT_SLOW + t * (SPEED_AT_FAST - SPEED_AT_SLOW);
        } else {
            return SPEED_AT_FAST;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Math helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Computes the Minecraft yaw angle (degrees) from {@code from} looking toward {@code to}.
     * Minecraft yaw: 0 = south (+Z), 90 = west (−X), ±180 = north (−Z), −90 = east (+X).
     */
    private static float yawToward(Vec3d from, Vec3d to) {
        return (float) (-Math.atan2(to.x - from.x, to.z - from.z) * (180.0 / Math.PI));
    }

    /**
     * Computes the Minecraft pitch angle (degrees) from {@code from} looking toward {@code to}.
     * Pitch: −90 = straight up, 0 = horizontal, +90 = straight down.
     */
    private static float pitchToward(Vec3d from, Vec3d to) {
        double dx   = to.x - from.x;
        double dy   = to.y - from.y;
        double dz   = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) return 0f;
        return (float) (-Math.atan2(dy, dist) * (180.0 / Math.PI));
    }

    /**
     * Wraps an angle into [−180°, 180°] for shortest-arc arithmetic.
     * Prevents the yaw from spinning the long way around the circle.
     */
    private static float wrapAngle(float a) {
        a %= 360f;
        if (a >  180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    /** Clamps pitch to Minecraft's valid range [−90°, 90°]. */
    private static float clampPitch(float p) {
        return Math.max(-90f, Math.min(90f, p));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
