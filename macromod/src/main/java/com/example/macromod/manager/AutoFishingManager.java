package com.example.macromod.manager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Random;

/**
 * Standalone auto-fishing state machine, driven by the client tick event.
 *
 * <p>Bite detection uses two independent signals:
 * <ol>
 *   <li>The bobber's Y position drops noticeably (≥0.07 blocks) within a single tick
 *       after at least {@value MIN_WAIT_MS} ms in water — the classic "dip" motion.</li>
 *   <li>Any entity (ItemEntity or LivingEntity) appears within {@value ENTITY_SCAN_RADIUS}
 *       blocks of the bobber — covers modded/mixin-spawned fish entities.</li>
 * </ol>
 *
 * <p>When a bite is detected the rod is "used" a second time (right-click) to reel in,
 * then after a short {@value RECAST_DELAY_MS} ms delay it casts again.
 */
@Environment(EnvType.CLIENT)
public class AutoFishingManager {

    // ── Timing constants ────────────────────────────────────────────
    /** Minimum time the bobber must be in water before a bite can register. */
    private static final long MIN_WAIT_MS       = 2_000L;
    /** How long to wait for the bobber entity to appear after casting. */
    private static final long CAST_TIMEOUT_MS   = 4_000L;
    /** Delay between reeling-in and the next cast. */
    private static final long RECAST_DELAY_MS   = 900L;
    /** How far the bobber must dip in one tick to count as a bite (blocks). */
    private static final double BITE_DIP_THRESHOLD = 0.07;
    /** Entity scan radius around the bobber (blocks). */
    private static final float  ENTITY_SCAN_RADIUS  = 1.5f;

    // ── Attack / kill constants ──────────────────────────────────────
    /** Time to wait for prey to spawn after reeling in. */
    private static final long   PREY_SCAN_MS        = 2_000L;
    /** Radius around last bobber pos to scan for prey (blocks). */
    private static final double PREY_SCAN_RADIUS    = 2.0;
    /** Interval between attack actions (10 game ticks). */
    private static final long   ATTACK_INTERVAL_MS  = 500L;
    /** Maximum time to spend trying to kill one target. */
    private static final long   KILL_TIMEOUT_MS     = 6_000L;
    /** How many consecutive no-LOS ticks before we skip the target. */
    private static final int    MAX_NO_LOS          = 3;
    /** Degrees to rotate per tick when smooth-aiming. */
    private static final float  AIM_STEP_DEG        = 15f;

    // ── Singleton ───────────────────────────────────────────────────
    private static AutoFishingManager INSTANCE;
    public static AutoFishingManager getInstance() {
        if (INSTANCE == null) INSTANCE = new AutoFishingManager();
        return INSTANCE;
    }

    // ── State ────────────────────────────────────────────────────────
    private enum FishState { IDLE, CASTING, WAITING, REELING, WAITING_FOR_PREY, KILLING, DELAY }

    private FishState fishState    = FishState.IDLE;
    private long      stateStartMs = 0L;
    private double    lastBobberY  = Double.NaN;
    private boolean   enabled      = false;
    private Vec3d     lastBobberPos = null;

    // ── Attack config (set from AutoFarmScreen) ───────────────────────
    private boolean attackEnabled      = false;
    private boolean attackModeDistance = false;   // false = close, true = distance
    private int     attackHotbarSlot   = -1;      // -1 = use current slot

    // ── Kill state ────────────────────────────────────────────────────
    private Entity killTarget   = null;
    private int    noLosCount   = 0;
    private long   lastAttackMs = 0L;    // Random aim point within entity bounding box (anti-cheat)
    private static final Random AIM_RAND       = new Random();
    private Vec3d  killAimPoint               = null;
    private long   killAimRefreshMs           = 0L;
    private long   killAimRefreshInterval     = 700L;
    private AutoFishingManager() {}

    // ── API ─────────────────────────────────────────────────────────
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            fishState  = FishState.IDLE;
            killTarget = null;
        }
    }

    public boolean isEnabled() { return enabled; }

    /** Configure the post-catch attack behaviour. */
    public void setAttackConfig(boolean enabled, boolean modeDistance, int hotbarSlot) {
        this.attackEnabled      = enabled;
        this.attackModeDistance = modeDistance;
        this.attackHotbarSlot   = hotbarSlot;
    }

    public boolean isAttackEnabled()      { return attackEnabled; }
    public boolean isAttackModeDistance() { return attackModeDistance; }
    public int     getAttackHotbarSlot()  { return attackHotbarSlot; }

    // ── Tick (called every client tick) ─────────────────────────────
    public void tick() {
        if (!enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null
                || client.interactionManager == null
                || client.currentScreen != null) return;   // don't fish while GUI open

        ClientPlayerEntity player = client.player;

        // Outside kill states, ensure a fishing rod is held
        if (fishState != FishState.WAITING_FOR_PREY && fishState != FishState.KILLING) {
            if (!hasRodInHand(player) && !equipRod(player)) return;
        }

        FishingBobberEntity bobber = player.fishHook;
        long now = System.currentTimeMillis();

        switch (fishState) {

            case IDLE -> {
                cast(client, player);
                fishState    = FishState.CASTING;
                stateStartMs = now;
                lastBobberY  = Double.NaN;
            }

            case CASTING -> {
                if (bobber != null) {
                    // Bobber appeared — start waiting for a bite
                    fishState    = FishState.WAITING;
                    stateStartMs = now;
                    lastBobberY  = bobber.getY();
                    lastBobberPos = bobber.getPos();
                } else if (now - stateStartMs > CAST_TIMEOUT_MS) {
                    // No bobber after timeout — try again
                    fishState = FishState.IDLE;
                }
            }

            case WAITING -> {
                if (bobber == null) {
                    // Bobber disappeared unexpectedly — delay then recast
                    fishState    = FishState.DELAY;
                    stateStartMs = now;
                    break;
                }

                boolean bite = false;

                // Track bobber position each tick for attack scan later
                double currentY = bobber.getY();
                lastBobberPos   = bobber.getPos();

                // Signal 1: position dip (bobber pulled down by fish)
                if (!Double.isNaN(lastBobberY)
                        && bobber.isTouchingWater()
                        && (now - stateStartMs) >= MIN_WAIT_MS
                        && (lastBobberY - currentY) >= BITE_DIP_THRESHOLD) {
                    bite = true;
                }
                lastBobberY = currentY;

                // Signal 2: entity appears near bobber (fish / item entity)
                if (!bite && (now - stateStartMs) >= MIN_WAIT_MS
                        && hasEntityNearBobber(client, bobber)) {
                    bite = true;
                }

                if (bite) {
                    cast(client, player);   // right-click again = reel in
                    fishState    = FishState.REELING;
                    stateStartMs = now;
                }
            }

            case REELING -> {
                // Keep updating bobber pos while it still exists
                if (bobber != null) lastBobberPos = bobber.getPos();

                if (bobber == null || now - stateStartMs > 1_500L) {
                    if (attackEnabled && lastBobberPos != null) {
                        // Transition to prey-scan before recasting
                        killTarget   = null;
                        noLosCount   = 0;
                        lastAttackMs = 0L;
                        fishState    = FishState.WAITING_FOR_PREY;
                    } else {
                        fishState    = FishState.DELAY;
                    }
                    stateStartMs = now;
                }
            }

            case WAITING_FOR_PREY -> {
                if (now - stateStartMs > PREY_SCAN_MS) {
                    // Nothing spawned — recast
                    fishState    = FishState.DELAY;
                    stateStartMs = now;
                    break;
                }
                Entity prey = findPreyNearPos(client, player);
                if (prey != null) {
                    killTarget           = prey;
                    noLosCount           = 0;
                    lastAttackMs         = 0L;
                    killAimPoint         = randomKillAim(prey);
                    killAimRefreshInterval = 600L + AIM_RAND.nextInt(600);
                    killAimRefreshMs     = now;
                    fishState            = FishState.KILLING;
                    stateStartMs         = now;
                }
            }

            case KILLING -> {
                // Target dead or gone?
                if (killTarget == null || !killTarget.isAlive()) {
                    killTarget   = null;
                    fishState    = FishState.DELAY;
                    stateStartMs = now;
                    break;
                }

                // Timeout guard (skip if we can't kill within the limit)
                if (now - stateStartMs > KILL_TIMEOUT_MS) {
                    killTarget   = null;
                    fishState    = FishState.DELAY;
                    stateStartMs = now;
                    break;
                }

                // Line-of-sight check (3 consecutive failures = skip)
                if (!hasLineOfSight(client, player, killTarget)) {
                    noLosCount++;
                    if (noLosCount >= MAX_NO_LOS) {
                        killTarget   = null;
                        fishState    = FishState.DELAY;
                        stateStartMs = now;
                    }
                    break;
                }
                noLosCount = 0;

                // Periodically re-randomize aim point within bounding box
                if (killAimPoint == null || now - killAimRefreshMs > killAimRefreshInterval) {
                    killAimPoint           = randomKillAim(killTarget);
                    killAimRefreshInterval = 600L + AIM_RAND.nextInt(600);
                    killAimRefreshMs       = now;
                }

                // Smooth aim towards randomised point
                smoothAimAt(player, killAimPoint);

                // Attack on interval (10 game ticks = 500 ms)
                if (now - lastAttackMs >= ATTACK_INTERVAL_MS) {
                    if (attackModeDistance) {
                        // Distance: equip chosen item and right-click entity
                        if (attackHotbarSlot >= 0)
                            player.getInventory().selectedSlot = attackHotbarSlot;
                        client.interactionManager.interactEntity(player, killTarget, Hand.MAIN_HAND);
                    } else {
                        // Close: standard left-click attack
                        client.interactionManager.attackEntity(player, killTarget);
                    }
                    lastAttackMs = now;
                    // Re-randomize after each hit
                    killAimPoint   = randomKillAim(killTarget);
                    killAimRefreshMs = now;
                }
            }

            case DELAY -> {
                if (now - stateStartMs >= RECAST_DELAY_MS) {
                    // Re-equip fishing rod for next cast
                    if (!hasRodInHand(player)) equipRod(player);
                    fishState = FishState.IDLE;
                }
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private boolean hasRodInHand(ClientPlayerEntity player) {
        return player.getMainHandStack().getItem() instanceof FishingRodItem;
    }

    /** Searches hotbar slots 0–8 for a fishing rod and selects it. */
    private boolean equipRod(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof FishingRodItem) {
                player.getInventory().selectedSlot = i;
                return true;
            }
        }
        return false;
    }

    /** Simulates a right-click use action with the main hand. */
    private void cast(MinecraftClient client, ClientPlayerEntity player) {
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
    }

    /**
     * Returns true if any entity named "!!!" exists within
     * {@value ENTITY_SCAN_RADIUS} blocks of the bobber.
     * Fishing mods typically spawn/rename an entity to "!!!" when a fish bites.
     */
    private boolean hasEntityNearBobber(MinecraftClient client, FishingBobberEntity bobber) {
        double r = ENTITY_SCAN_RADIUS;
        Box box = new Box(
                bobber.getX() - r, bobber.getY() - r, bobber.getZ() - r,
                bobber.getX() + r, bobber.getY() + r, bobber.getZ() + r
        );
        List<Entity> near = client.world.getOtherEntities(bobber, box,
                e -> "!!!".equals(e.getName().getString()));
        return !near.isEmpty();
    }

    /**
     * Finds the nearest living entity (not the player) within
     * {@value PREY_SCAN_RADIUS} blocks of the last known bobber position.
     * Used to detect entities that spawn after the catch.
     */
    private Entity findPreyNearPos(MinecraftClient client, ClientPlayerEntity player) {
        if (lastBobberPos == null || client.world == null) return null;
        double r = PREY_SCAN_RADIUS;
        Box box = new Box(
                lastBobberPos.x - r, lastBobberPos.y - r, lastBobberPos.z - r,
                lastBobberPos.x + r, lastBobberPos.y + r, lastBobberPos.z + r
        );
        List<LivingEntity> near = client.world.getEntitiesByClass(
                LivingEntity.class, box, e -> e != player && e.isAlive());
        if (near.isEmpty()) return null;
        double bx = lastBobberPos.x, by = lastBobberPos.y, bz = lastBobberPos.z;
        return near.stream()
                .min((a, b) -> Double.compare(
                        a.squaredDistanceTo(bx, by, bz),
                        b.squaredDistanceTo(bx, by, bz)))
                .orElse(null);
    }

    /** Raycasts from player eyes to the target's centre; true if unobstructed. */
    private boolean hasLineOfSight(MinecraftClient client,
                                    ClientPlayerEntity player, Entity target) {
        if (client.world == null) return false;
        Vec3d eyes   = player.getEyePos();
        Vec3d centre = target.getPos().add(0, target.getHeight() * 0.5, 0);
        RaycastContext ctx = new RaycastContext(
                eyes, centre,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                (Entity) player);
        return client.world.raycast(ctx).getType() == HitResult.Type.MISS;
    }

    /** Returns a random point within the entity's bounding box (body zone, avoids feet/head). */
    private static Vec3d randomKillAim(Entity e) {
        net.minecraft.util.math.Box box = e.getBoundingBox();
        double x    = box.minX + AIM_RAND.nextDouble() * (box.maxX - box.minX);
        double yMin = box.minY + (box.maxY - box.minY) * 0.20;
        double yMax = box.maxY - (box.maxY - box.minY) * 0.15;
        double y    = yMin + AIM_RAND.nextDouble() * (yMax - yMin);
        double z    = box.minZ + AIM_RAND.nextDouble() * (box.maxZ - box.minZ);
        return new Vec3d(x, y, z);
    }

    /** Smoothly rotates the player towards a world position by at most {@value AIM_STEP_DEG}°/tick. */
    private void smoothAimAt(ClientPlayerEntity player, Vec3d aimPos) {
        Vec3d eyes   = player.getEyePos();
        Vec3d delta  = aimPos.subtract(eyes).normalize();

        float targetYaw   = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float targetPitch = (float) Math.toDegrees(
                -Math.asin(Math.max(-1.0, Math.min(1.0, delta.y))));

        float dyaw   = wrapDeg(targetYaw   - player.getYaw());
        float dpitch = targetPitch - player.getPitch();

        player.setYaw(player.getYaw()
                + Math.signum(dyaw)   * Math.min(AIM_STEP_DEG, Math.abs(dyaw)));
        player.setPitch(player.getPitch()
                + Math.signum(dpitch) * Math.min(AIM_STEP_DEG, Math.abs(dpitch)));
    }

    /** Smoothly rotates the player towards {@code target} entity by at most {@value AIM_STEP_DEG}°/tick. */
    private void smoothAimAt(ClientPlayerEntity player, Entity target) {
        Vec3d eyes   = player.getEyePos();
        Vec3d centre = target.getPos().add(0, target.getHeight() * 0.5, 0);
        Vec3d delta  = centre.subtract(eyes).normalize();

        float targetYaw   = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float targetPitch = (float) Math.toDegrees(
                -Math.asin(Math.max(-1.0, Math.min(1.0, delta.y))));

        float dyaw   = wrapDeg(targetYaw   - player.getYaw());
        float dpitch = targetPitch - player.getPitch();

        player.setYaw(player.getYaw()
                + Math.signum(dyaw)   * Math.min(AIM_STEP_DEG, Math.abs(dyaw)));
        player.setPitch(player.getPitch()
                + Math.signum(dpitch) * Math.min(AIM_STEP_DEG, Math.abs(dpitch)));
    }

    private static float wrapDeg(float d) {
        d %= 360f;
        if (d >= 180f)  d -= 360f;
        if (d < -180f)  d += 360f;
        return d;
    }
}
