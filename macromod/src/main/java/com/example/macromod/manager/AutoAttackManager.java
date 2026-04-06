package com.example.macromod.manager;

import com.example.macromod.MacroModClient;
import com.example.macromod.util.HumanReactionTime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

/**
 * Stable single-target auto-attack system.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Locks onto ONE target at a time — no jitter from rapid switching.</li>
 *   <li>Respects a minimum lock timer ({@link #MIN_LOCK_TICKS}) before considering
 *       a target release, preventing flickering on edge-of-range mobs.</li>
 *   <li>Delegates smooth rotation to the shared {@link com.example.macromod.pathfinding.SmoothAim}
 *       instance so eye movement is identical to macro waypoint aiming.</li>
 *   <li>Releases the target only when dead, truly out of range (+buffer), or LOS
 *       is lost for more than {@link #MAX_NO_LOS_TICKS} consecutive ticks.</li>
 *   <li>Aims at a randomised point within the entity bounding box (body zone), refreshed
 *       every ~600 ms, for human-like variation.</li>
 * </ul>
 *
 * <h3>Interaction with other systems</h3>
 * <p>When a target is acquired, {@code setTarget()} is called on SmoothAim every tick,
 * overriding any block/waypoint aim set by MacroExecutor.
 * When disabled or no target is found, SmoothAim is left in its current state so that
 * running macros continue to aim at their own targets.</p>
 */
@Environment(EnvType.CLIENT)
public class AutoAttackManager {

    // ── Singleton ───────────────────────────────────────────────────
    private static AutoAttackManager INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");

    public static AutoAttackManager getInstance() {
        if (INSTANCE == null) INSTANCE = new AutoAttackManager();
        return INSTANCE;
    }

    private AutoAttackManager() {}

    // ── Priority mode ────────────────────────────────────────────────
    public enum PriorityMode { NEAREST, LOWEST_HEALTH }

    // ── Tuning constants ─────────────────────────────────────────────

    /** Extra distance (blocks) beyond attackRange before target is released. */
    private static final float RELEASE_BUFFER = 1.5f;

    /** Minimum ticks to stay on a target before evaluating release conditions. */
    private static final int MIN_LOCK_TICKS = 10;

    /** Consecutive ticks without line-of-sight before releasing the target. */
    private static final int MAX_NO_LOS_TICKS = 5;

    /** How often (ticks) to push a new aim target to SmoothAim (reduces jitter from per-tick entity movement). */
    private static final int AIM_UPDATE_INTERVAL_TICKS = 2;

    /** How often (ticks) to emit a periodic debug status line. */
    private static final int DEBUG_STATUS_INTERVAL = 20;

    /**
     * Attack cooldown threshold — only swing when MC cooldown is ≥ this fraction.
     * 0.9 = attack at 90 % charge, giving consistent DPS without "ghost" swings.
     */
    private static final float ATTACK_COOLDOWN_THRESHOLD = 0.9f;

    // ── Settings (configurable from GUI) ─────────────────────────────
    private float attackRange = 5.0f;
    private PriorityMode priorityMode = PriorityMode.NEAREST;

    // ── State ─────────────────────────────────────────────────────────
    private boolean enabled = false;
    private volatile LivingEntity currentTarget = null;
    private int targetLockTicks = 0;
    private int noLosTicks = 0;
    private int aimUpdateTick = 0;
    private int debugTick = 0;

    // ── Config API ───────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }

    public float getAttackRange() { return attackRange; }
    public void setAttackRange(float range) { this.attackRange = Math.max(2.0f, range); }

    public PriorityMode getPriorityMode() { return priorityMode; }
    public void setPriorityMode(PriorityMode mode) { this.priorityMode = mode; }

    /** Returns the currently locked target, or {@code null} if none. */
    public LivingEntity getCurrentTarget() { return currentTarget; }

    public void toggle() {
        if (enabled) disable(); else enable();
    }

    public void enable() {
        enabled = true;
        currentTarget = null;
        targetLockTicks = 0;
        noLosTicks = 0;
        aimUpdateTick = 0;
        debugTick = 0;
    }

    public void disable() {
        enabled = false;
        currentTarget = null;
        // Release SmoothAim so macros can resume aiming at their own targets
        var sa = MacroModClient.getSmoothAim();
        if (sa != null) sa.clearTarget();
        releaseForward();
    }

    // ── Tick: called every END_CLIENT_TICK ───────────────────────────

    public void tick() {
        if (!enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null
                || client.interactionManager == null
                || client.currentScreen != null) return;

        ClientPlayerEntity player = client.player;

        // ── 1. Find a target if we don't have one ────────────────────
        if (currentTarget == null) {
            currentTarget = findBestTarget(client, player);
            if (currentTarget == null) {
                // Nothing in range — don't interfere with SmoothAim
                releaseForward();
                return;
            }
            targetLockTicks = 0;
            noLosTicks = 0;
            if (isDebug()) {
                LOGGER.info("[AutoAttack] New target: {} (dist={})",
                        currentTarget.getName().getString(),
                        String.format("%.1f", Math.sqrt(player.squaredDistanceTo(currentTarget))));
            }
        }

        // ── 2. Evaluate release (only after minimum lock time) ────────
        if (targetLockTicks >= MIN_LOCK_TICKS && shouldRelease(client, player, currentTarget)) {
            if (isDebug()) {
                LOGGER.info("[AutoAttack] Target released: {}", currentTarget.getName().getString());
            }
            var sa = MacroModClient.getSmoothAim();
            if (sa != null) sa.clearTarget();
            releaseForward();
            currentTarget = null;
            return;
        }

        targetLockTicks++;
        aimUpdateTick++;
        debugTick++;

        // ── 3. Smooth aim — update target every N ticks to dampen entity-movement jitter ──
        var sa = MacroModClient.getSmoothAim();
        if (sa != null && aimUpdateTick >= AIM_UPDATE_INTERVAL_TICKS) {
            sa.setTarget(bodyCenter(currentTarget));
            aimUpdateTick = 0;
        }

        // ── 4. Periodic debug status ──────────────────────────────────────
        if (isDebug() && debugTick >= DEBUG_STATUS_INTERVAL) {
            debugTick = 0;
            double dist = Math.sqrt(player.squaredDistanceTo(currentTarget));
            float cooldown = player.getAttackCooldownProgress(0f);
            boolean inRange = player.squaredDistanceTo(currentTarget) <= attackRange * attackRange;
            boolean onTarget = sa != null && sa.isOnTarget(player, 6f);
            boolean losOk = !hasLineOfSight(client, player, currentTarget) == false;
            LOGGER.info("[AutoAttack] Status: target={} dist={} cooldown={} inRange={} onTarget={} los={} lockTick={}",
                    currentTarget.getName().getString(),
                    String.format("%.2f", dist),
                    String.format("%.2f", cooldown),
                    inRange, onTarget, losOk, targetLockTicks);
        }

        // ── 5. Move forward if out of melee range (with hysteresis buffer) ──
        double distSq = player.squaredDistanceTo(currentTarget);
        double rangeSq = attackRange * attackRange;
        double releaseRangeSq = (attackRange + 0.5) * (attackRange + 0.5);
        if (distSq > releaseRangeSq) {
            client.options.forwardKey.setPressed(true);
        } else if (distSq <= rangeSq) {
            releaseForward();
        }

        // ── 6. Attack only when aimed at target and cooldown is ready ─────
        if (distSq <= rangeSq) {
            var saCheck = MacroModClient.getSmoothAim();
            boolean lookingAtTarget = saCheck != null && saCheck.isOnTarget(player, 6f);
            float cooldownProgress = player.getAttackCooldownProgress(0f);
            boolean cooldownReady = cooldownProgress >= ATTACK_COOLDOWN_THRESHOLD;

            if (isDebug() && !lookingAtTarget) {
                LOGGER.info("[AutoAttack] In range but NOT on target — yaw={} pitch={}",
                        String.format("%.2f", player.getYaw()),
                        String.format("%.2f", player.getPitch()));
            }
            if (isDebug() && lookingAtTarget && !cooldownReady) {
                LOGGER.info("[AutoAttack] On target but cooldown not ready: {}",
                        String.format("%.2f", cooldownProgress));
            }

            if (lookingAtTarget && cooldownReady) {
                if (isDebug()) {
                    LOGGER.info("[AutoAttack] Attacking {} dist={} yaw={} pitch={} cooldown={}",
                            currentTarget.getName().getString(),
                            String.format("%.2f", Math.sqrt(distSq)),
                            String.format("%.2f", player.getYaw()),
                            String.format("%.2f", player.getPitch()),
                            String.format("%.2f", cooldownProgress));
                }
                client.interactionManager.attackEntity(player, currentTarget);
            }
        }
    }

    // ── Target selection ─────────────────────────────────────────────

    private LivingEntity findBestTarget(MinecraftClient client, ClientPlayerEntity player) {
        double r = attackRange;
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        Box box = new Box(px - r, py - r, pz - r,
                          px + r, py + r, pz + r);
        List<LivingEntity> candidates = client.world.getEntitiesByClass(
                LivingEntity.class, box,
                e -> e != player
                        && e.isAlive()
                        && e.squaredDistanceTo(player) <= r * r);

        if (candidates.isEmpty()) return null;

        return switch (priorityMode) {
            case NEAREST ->
                    candidates.stream()
                            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
                            .orElse(null);
            case LOWEST_HEALTH ->
                    candidates.stream()
                            .min(Comparator.comparingDouble(LivingEntity::getHealth))
                            .orElse(null);
        };
    }

    // ── Release conditions ────────────────────────────────────────────

    private boolean shouldRelease(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
        if (!target.isAlive()) return true;

        double threshold = attackRange + RELEASE_BUFFER;
        if (player.squaredDistanceTo(target) > threshold * threshold) return true;

        if (!hasLineOfSight(client, player, target)) {
            noLosTicks++;
            if (noLosTicks >= MAX_NO_LOS_TICKS) return true;
        } else {
            noLosTicks = 0;
        }

        return false;
    }

    // ── Utilities ─────────────────────────────────────────────────────

    /**
     * True if there is an unobstructed path (collider raycast) from the player's
     * eyes to the entity's vertical midpoint.
     */
    private boolean hasLineOfSight(MinecraftClient client, ClientPlayerEntity player, Entity target) {
        if (client.world == null) return false;
        Vec3d eyes   = player.getEyePos();
        Vec3d centre = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
        RaycastContext ctx = new RaycastContext(
                eyes, centre,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                (Entity) player);
        return client.world.raycast(ctx).getType() == HitResult.Type.MISS;
    }

    /**
     * Returns the entity's body center — midpoint between feet and head,
     * based on its current position (updated every tick).
     */
    private static Vec3d bodyCenter(LivingEntity e) {
        return new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ());
    }

    private void releaseForward() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            client.options.forwardKey.setPressed(false);
        }
    }

    private boolean isDebug() {
        var cm = MacroModClient.getConfigManager();
        return cm != null && cm.getConfig().isDebugLogging();
    }
}
