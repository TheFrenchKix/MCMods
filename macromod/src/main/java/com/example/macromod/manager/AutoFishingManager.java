package com.example.macromod.manager;

import com.example.macromod.MacroModClient;
import com.example.macromod.util.HumanReactionTime;
import com.example.macromod.util.MouseInputHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
    private static final float  ENTITY_SCAN_RADIUS  = 2.5f;

    // ── Attack / kill constants ──────────────────────────────────────
    /** Time to wait for prey to spawn after reeling in. */
    private static final long   PREY_SCAN_MS        = 800L;  // Reduced for faster recast when no prey spawns
    /** Radius around last bobber pos to scan for prey (blocks). */
    private static final double PREY_SCAN_RADIUS    = 4.0;
    /** Minimum dwell time while crosshair is on target before attacking. */
    private static final long   ATTACK_DWELL_MS     = 150L;
    /** Default CPS if no macro config is active. */
    private static final int    DEFAULT_ATTACK_CPS  = 10;
    /** Maximum time to spend trying to kill one target. */
    private static final long   KILL_TIMEOUT_MS     = 6_000L;
    /** Max 3D reach distance required for close-mode attack. */
    private static final float  CLOSE_REACH_DISTANCE = 2.5f;
    /** Distance at which to start spam-clicking to build attack animation. */
    private static final float  SPAM_CLICK_DISTANCE = 4.0f;
    /** How long to wait in close mode before skipping an unchanged target. */
    private static final long   CLOSE_REACH_TIMEOUT_MS = 10_000L;
    /** How many consecutive no-LOS ticks before we skip the target. */
    private static final int    MAX_NO_LOS          = 3;
    /** Degrees to rotate per tick when smooth-aiming. */
    private static final float  AIM_STEP_DEG        = 15f;
    /** Block radius around player to scan for chests. */
    private static final int    DEPOSIT_SCAN_RADIUS = 8;
    /** Max ms to wait for a chest screen to open after interacting. */
    private static final long   DEPOSIT_OPEN_WAIT_MS = 2_000L;

    // ── Singleton ───────────────────────────────────────────────────
    private static AutoFishingManager INSTANCE;
    public static AutoFishingManager getInstance() {
        if (INSTANCE == null) INSTANCE = new AutoFishingManager();
        return INSTANCE;
    }

    // ── State ────────────────────────────────────────────────────────
    private enum FishState { IDLE, CASTING, WAITING, REELING, WAITING_FOR_PREY, KILLING, DELAY, DEPOSITING, RETURNING_LOOK }

    private FishState fishState    = FishState.IDLE;
    private long      stateStartMs = 0L;
    private double    lastBobberY  = Double.NaN;
    private boolean   enabled      = false;
    private Vec3d     lastBobberPos = null;
    /** UUIDs of entities within 5 blocks of the bobber just before reel-in (bite moment). */
    private final Set<java.util.UUID> preBiteEntityUUIDs = new HashSet<>();
    /** UUIDs of entities near bobber when it first appeared (pre-cast baseline). */
    private final Set<java.util.UUID> preCastEntityUUIDs = new HashSet<>();
    // ── Attack config (set from AutoFarmScreen) ───────────────────────
    private boolean attackEnabled      = false;
    private boolean attackModeDistance = false;   // false = close, true = distance
    private int     attackHotbarSlot   = -1;      // -1 = use current slot

    // ── Kill state ────────────────────────────────────────────────────
    private Entity killTarget   = null;
    private int    noLosCount   = 0;
    private long   lastAttackMs = 0L;
    private long   lastSpamClickMs = 0L;    // Random aim point within entity bounding box (anti-cheat)
    private static final Random AIM_RAND       = new Random();
    private Vec3d  killAimPoint               = null;
    private long   killAimRefreshMs           = 0L;
    private long   killAimRefreshInterval     = 700L;
    private long   firstOnTargetMs            = -1L;
    private double killStartDistanceSq        = -1.0;
    private double killBestDistanceSq         = Double.MAX_VALUE;
    private float  killStartHealth            = -1f;
    private UUID   killTargetUUID             = null;  // stable UUID for kill target re-resolution
    // ── Teleport detection ──────────────────────────────────────────────────
    private Vec3d  prevTickPos                = null;  // position last tick for teleport detection
    // ── Saved crosshair for post-action camera return ─────────────────────
    private float  fishingLookYaw             = 0f;
    private float  fishingLookPitch           = 0f;

    // ── Reaction time per session ──────────────────────────────────────
    private long recastDelayMs = RECAST_DELAY_MS;  // Randomized per cast cycle
    private long biteReactionMs = 0L;              // Reaction time when bite detected
    private long attackIntervalWithJitter = 1000L / DEFAULT_ATTACK_CPS; // Varies per attack

    // ── AFK camera jitter ──────────────────────────────────────────────
    private boolean cameraJitter        = false;
    private long    lastJitterMs        = 0L;
    private long    nextJitterIntervalMs = 5_000L;

    // ── Auto Deposit ───────────────────────────────────────────────────
    private boolean  autoDeposit     = false;
    private int      depositPhase    = 0;
    private long     depositPhaseMs  = 0L;
    private BlockPos depositChestPos = null;

    // ── Close mode movement control ────────────────────────────────────
    private boolean closeMovement = true;  // In close mode, move toward entity (can be disabled to wait)

    private AutoFishingManager() {}

    // ── API ─────────────────────────────────────────────────────────
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            fishState    = FishState.IDLE;
            killTarget   = null;
            killTargetUUID = null;
            depositPhase = 0;
            prevTickPos  = null;
            preCastEntityUUIDs.clear();
            preBiteEntityUUIDs.clear();
            // Clear any ongoing SmoothAim target set by jitter or other aiming
            var sa = MacroModClient.getSmoothAim();
            if (sa != null) sa.clearTarget();
        }
    }

    public void setDisable() { setEnabled(false); }

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

    public void    setCameraJitter(boolean v)  { this.cameraJitter = v; }
    public boolean isCameraJitterEnabled()     { return cameraJitter; }

    public void    setAutoDeposit(boolean v)   { this.autoDeposit = v; }
    public boolean isAutoDepositEnabled()      { return autoDeposit; }

    public void    setCloseMovement(boolean v) { this.closeMovement = v; }
    public boolean isCloseMovementEnabled()    { return closeMovement; }

    // ── Tick (called every client tick) ─────────────────────────────
    public void tick() {
        if (!enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        ClientPlayerEntity player = client.player;
        long now = System.currentTimeMillis();

        // Safety: stop the feature immediately after death to avoid unintended inputs.
        if (!player.isAlive() || player.getHealth() <= 0.0f) {
            player.sendMessage(Text.literal("Auto Fishing disabled: player died.").formatted(Formatting.RED), false);
            setDisable();
            return;
        }

        // Teleport detection: a sudden position jump > 10 blocks in one tick means the server
        // teleported the player (anti-cheat correction, death, etc.). Disable immediately.
        Vec3d currentTickPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        if (prevTickPos != null && currentTickPos.squaredDistanceTo(prevTickPos) > 100.0) {
            player.sendMessage(Text.literal("Auto Fishing disabled: teleported away from start.").formatted(Formatting.RED), false);
            setDisable();
            return;
        }
        prevTickPos = currentTickPos;

        // Handle deposit state before the GUI screen check (chest screen must remain open)
        if (fishState == FishState.DEPOSITING) {
            tickDeposit(client, player, now);
            return;
        }

        if (client.currentScreen != null) return;   // don't fish while GUI open

        // Outside kill states, ensure a fishing rod is held
        if (fishState != FishState.WAITING_FOR_PREY && fishState != FishState.KILLING
            && fishState != FishState.RETURNING_LOOK) {
            if (!hasRodInHand(player) && !equipRod(player)) return;
        }

        FishingBobberEntity bobber = player.fishHook;

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
                    lastBobberPos = new Vec3d(bobber.getX(), bobber.getY(), bobber.getZ());                    
                    // Record all existing entities near the bobber (for new entity tracking)
                    // Only entities spawned AFTER this will be considered prey
                    preCastEntityUUIDs.clear();
                    if (client.world != null) {
                        double r = PREY_SCAN_RADIUS;
                        Box scanBox = new Box(
                                bobber.getX() - r, bobber.getY() - r, bobber.getZ() - r,
                                bobber.getX() + r, bobber.getY() + r, bobber.getZ() + r);
                        for (LivingEntity e : client.world.getEntitiesByClass(LivingEntity.class, scanBox, 
                                le -> le != player && le.isAlive())) {
                            preCastEntityUUIDs.add(e.getUuid());
                        }
                    }                } else if (now - stateStartMs > CAST_TIMEOUT_MS) {
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

                // ── AFK camera jitter (anti-AFK detection) ───────────────
                if (cameraJitter && now - lastJitterMs >= nextJitterIntervalMs) {
                    float jitterYaw   = player.getYaw()   + (AIM_RAND.nextFloat() - 0.5f) * 4.0f;
                    float jitterPitch = Math.max(-90f, Math.min(90f,
                            player.getPitch() + (AIM_RAND.nextFloat() - 0.5f) * 2.5f));
                    var saJitter = MacroModClient.getSmoothAim();
                    if (saJitter != null) {
                        saJitter.setTarget(directionToPoint(player.getEyePos(), jitterYaw, jitterPitch));
                    } else {
                        player.setYaw(jitterYaw);
                        player.setPitch(jitterPitch);
                    }
                    lastJitterMs         = now;
                    nextJitterIntervalMs = 4_000L + (long)(AIM_RAND.nextFloat() * 6_000L);
                }

                boolean bite = false;

                // Track bobber position each tick for attack scan later
                double currentY = bobber.getY();
                lastBobberPos   = new Vec3d(bobber.getX(), bobber.getY(), bobber.getZ());

                // Signal 1: position dip (bobber pulled down by fish — water OR lava)
                if (!Double.isNaN(lastBobberY)
                        && (bobber.isTouchingWater() || bobber.isInLava())
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
                    fishingLookYaw   = player.getYaw();
                    fishingLookPitch = player.getPitch();
                    // Snapshot all entities in 5-block radius around bobber at the bite moment.
                    // Any entity appearing AFTER this snapshot is considered prey.
                    preBiteEntityUUIDs.clear();
                    if (client.world != null) {
                        double br = 5.0;
                        Box biteBox = new Box(
                                bobber.getX() - br, bobber.getY() - br, bobber.getZ() - br,
                                bobber.getX() + br, bobber.getY() + br, bobber.getZ() + br);
                        for (LivingEntity e : client.world.getEntitiesByClass(
                                LivingEntity.class, biteBox, le -> le != player)) {
                            preBiteEntityUUIDs.add(e.getUuid());
                        }
                    }
                    cast(client, player);   // right-click again = reel in
                    fishState    = FishState.REELING;
                    stateStartMs = now;
                }
            }

            case REELING -> {
                // Keep updating bobber pos while it still exists
                if (bobber != null) lastBobberPos = new Vec3d(bobber.getX(), bobber.getY(), bobber.getZ());

                if (bobber == null || now - stateStartMs > 1_500L) {
                    if (attackEnabled && lastBobberPos != null) {
                        // Transition to prey-scan before recasting
                        killTarget   = null;
                        noLosCount   = 0;
                        lastAttackMs = 0L;
                        firstOnTargetMs = -1L;
                        fishState    = FishState.WAITING_FOR_PREY;
                    } else {
                        fishState    = FishState.DELAY;
                    }
                    stateStartMs = now;
                }
            }

            case WAITING_FOR_PREY -> {
                // Add human reaction delay to prey scanning
                long preScanDelayMs = PREY_SCAN_MS + HumanReactionTime.getReactionDelay(HumanReactionTime.ReactionProfile.SLOW);
                if (now - stateStartMs > preScanDelayMs) {
                    // Nothing spawned — recast
                    fishState    = FishState.DELAY;
                    stateStartMs = now;
                    break;
                }
                Entity prey = findPreyNearPos(client, player);
                if (prey != null) {
                    killTarget           = prey;
                    killTargetUUID       = prey.getUuid();  // track UUID for stable re-resolution
                    noLosCount           = 0;
                    lastAttackMs         = 0L;
                    firstOnTargetMs      = -1L;
                    killStartDistanceSq  = prey.squaredDistanceTo(player);
                    killBestDistanceSq   = killStartDistanceSq;
                    killStartHealth      = (prey instanceof LivingEntity le) ? le.getHealth() : -1f;
                    killAimPoint         = randomKillAim(prey);
                    killAimRefreshInterval = 600L + AIM_RAND.nextInt(600);
                    killAimRefreshMs     = now;
                    // Do NOT switch slot here — only switch when entity enters SPAM_CLICK_DISTANCE
                    fishState            = FishState.KILLING;
                    stateStartMs         = now;
                }
            }

            case KILLING -> {
                // Re-resolve kill target by UUID if the entity reference went stale
                // (e.g. chunk reload, entity re-spawn, or reference GC'd)
                if ((killTarget == null || !killTarget.isAlive()) && killTargetUUID != null && client.world != null) {
                    double sr = PREY_SCAN_RADIUS * 2.0;
                    Vec3d sp = lastBobberPos != null ? lastBobberPos : new Vec3d(player.getX(), player.getY(), player.getZ());
                    Box searchBox = new Box(sp.x - sr, sp.y - sr, sp.z - sr,
                                           sp.x + sr, sp.y + sr, sp.z + sr);
                    UUID targetUuid = killTargetUUID;
                    killTarget = client.world.getOtherEntities(null, searchBox,
                        e -> targetUuid.equals(e.getUuid()) && e.isAlive())
                        .stream().findFirst().orElse(null);
                }

                // Target dead or gone?
                if (killTarget == null || !killTarget.isAlive()) {
                    killTarget     = null;
                    killTargetUUID = null;
                    fishState      = FishState.RETURNING_LOOK;
                    stateStartMs   = now;
                    break;
                }

                // Timeout guard (distance mode only)
                if (attackModeDistance && now - stateStartMs > KILL_TIMEOUT_MS) {
                    killTarget   = null;
                    fishState    = FishState.RETURNING_LOOK;
                    stateStartMs = now;
                    break;
                }

                // Line-of-sight check (3 consecutive failures = skip)
                if (!hasLineOfSight(client, player, killTarget)) {
                    noLosCount++;
                    if (noLosCount >= MAX_NO_LOS) {
                        killTarget      = null;
                        firstOnTargetMs = -1L;
                        fishState       = FishState.RETURNING_LOOK;
                        stateStartMs    = now;
                    }
                    break;
                }
                noLosCount = 0;

                // Close mode: wait until target is really reachable in 3D (< 3 blocks).
                if (!attackModeDistance) {
                    double distSq = killTarget.squaredDistanceTo(player);
                    if (distSq < killBestDistanceSq) killBestDistanceSq = distSq;

                    boolean reachable = distSq <= (double) CLOSE_REACH_DISTANCE * CLOSE_REACH_DISTANCE;
                    float currentHealth = (killTarget instanceof LivingEntity le) ? le.getHealth() : -1f;
                    boolean tookDamage = killStartHealth >= 0f && currentHealth >= 0f && currentHealth + 0.01f < killStartHealth;
                    boolean gotCloser = killBestDistanceSq + 0.09 < killStartDistanceSq;

                    if (!reachable) {
                        if (closeMovement) {
                            // Movement enabled: timeout if entity shows no progress after 10s
                            if (now - stateStartMs >= CLOSE_REACH_TIMEOUT_MS && !tookDamage && !gotCloser) {
                                killTarget      = null;
                                killTargetUUID  = null;
                                firstOnTargetMs = -1L;
                                fishState       = FishState.RETURNING_LOOK;
                                stateStartMs    = now;
                                client.options.forwardKey.setPressed(false);
                                break;
                            }
                            client.options.forwardKey.setPressed(true);
                        } else {
                            // Movement disabled: wait indefinitely for entity to walk within reach.
                            // Monitor UUID + health each tick so we don't miss a re-spawn.
                            client.options.forwardKey.setPressed(false);
                        }
                        break;  // Not reachable yet — come back next tick
                    } else {
                        client.options.forwardKey.setPressed(false);
                    }
                }

                // Periodically re-randomize aim point within bounding box
                if (killAimPoint == null || now - killAimRefreshMs > killAimRefreshInterval) {
                    killAimPoint           = randomKillAim(killTarget);
                    killAimRefreshInterval = 600L + AIM_RAND.nextInt(600);
                    killAimRefreshMs       = now;
                }

                // Smooth aim towards randomised point using shared SmoothAim implementation.
                var sa = MacroModClient.getSmoothAim();
                if (sa == null) break;
                sa.setTarget(killAimPoint);

                // For both modes: only require angle check (6 degrees), don't require exact crosshair center
                boolean lookingAtTarget = sa.isOnTarget(player, 6f);
                if (lookingAtTarget) {
                    if (firstOnTargetMs < 0) firstOnTargetMs = now;
                } else {
                    firstOnTargetMs = -1L;
                }
                
                // Spam-click at 3.5 blocks range with CPS throttling
                double distSq = killTarget.squaredDistanceTo(player);
                double spamDistSq = SPAM_CLICK_DISTANCE * SPAM_CLICK_DISTANCE;
                // Use fixed CPS interval (10 CPS = 100ms)
                long spamCpsInterval = 100L;
                
                // Switch to attack item as soon as entity enters spam range.
                if (attackHotbarSlot >= 0 && distSq <= spamDistSq) {
                    player.getInventory().setSelectedSlot(attackHotbarSlot);
                }
                
                if (distSq <= spamDistSq) {
                    if (lastSpamClickMs == 0 || now - lastSpamClickMs >= spamCpsInterval) {
                        float cooldown = player.getAttackCooldownProgress(0.0f);
                        if (cooldown >= 0.9f) {
                            if (attackModeDistance) {
                                MouseInputHelper.rightClick(client);
                            } else {
                                MouseInputHelper.leftClick(client);
                            }
                            lastSpamClickMs = now;
                        }
                    }
                }
                
                boolean dwellReady = firstOnTargetMs >= 0 && now - firstOnTargetMs >= ATTACK_DWELL_MS;

                // Attack on CPS interval (+ jitter) when we're actually on target.
                if (dwellReady && now - lastAttackMs >= attackIntervalWithJitter) {
                    long baseInterval = 1000L / Math.max(1, getConfiguredAttackCps());
                    attackIntervalWithJitter = HumanReactionTime.getAttackIntervalWithJitter(baseInterval, 20);
                    if (attackHotbarSlot >= 0) {
                        player.getInventory().setSelectedSlot(attackHotbarSlot);
                    }
                    if (attackModeDistance) {
                        // Distance mode: equip chosen item and right-click if looking at target
                        MouseInputHelper.rightClick(client);
                    } else {
                        // Close mode: simulate a real left click
                        MouseInputHelper.leftClick(client);
                    }
                    lastAttackMs = now;
                    firstOnTargetMs = -1L;
                    // Re-randomize after each hit
                    killAimPoint   = randomKillAim(killTarget);
                    killAimRefreshMs = now;
                }
            }

            case DELAY -> {
                // Use reaction-time-based recast delay instead of fixed
                if (now - stateStartMs >= recastDelayMs) {
                    // Deposit only when player inventory is fully saturated
                    // (includes hotbar check; still deposits only non-hotbar slots).
                    if (autoDeposit && isInventoryFullySaturated(player)) {
                        depositPhase    = 0;
                        depositPhaseMs  = now;
                        depositChestPos = null;
                        fishState       = FishState.DEPOSITING;
                        break;
                    }
                    // Re-equip fishing rod for next cast
                    if (!hasRodInHand(player)) equipRod(player);
                    fishState = FishState.IDLE;
                }
            }

            case RETURNING_LOOK -> {
                // Smoothly return camera to saved fishing crosshair, then transition to DELAY.
                var sa = MacroModClient.getSmoothAim();
                if (sa == null || sa.isOnTarget(player, 3f) || now - stateStartMs > 3_000L) {
                    if (sa != null) sa.clearTarget();
                    fishState    = FishState.DELAY;
                    stateStartMs = now;
                } else {
                    sa.setTarget(directionToPoint(player.getEyePos(), fishingLookYaw, fishingLookPitch));
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
                player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        sendMessage("macromod.auto_fishing.no_rod", Formatting.RED);
        setDisable();
        return false;
    }

    /** Simulates a right-click use action with the main hand. */
    private void cast(MinecraftClient client, ClientPlayerEntity player) {
        MouseInputHelper.rightClick(client);
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
     * Finds the nearest newly-spawned living entity (not the player) within
     * {@value PREY_SCAN_RADIUS} blocks of the last known bobber position.
     * Only considers entities that did NOT exist when the bobber was cast.
     */
    private Entity findPreyNearPos(MinecraftClient client, ClientPlayerEntity player) {
        if (lastBobberPos == null || client.world == null) return null;
        double r = PREY_SCAN_RADIUS;
        Box box = new Box(
                lastBobberPos.x - r, lastBobberPos.y - r, lastBobberPos.z - r,
                lastBobberPos.x + r, lastBobberPos.y + r, lastBobberPos.z + r
        );
        // Use preBiteEntityUUIDs if available (more precise: entities that appeared
        // AFTER the bite moment). Fall back to preCastEntityUUIDs for older casts.
        Set<java.util.UUID> baseline = !preBiteEntityUUIDs.isEmpty() ? preBiteEntityUUIDs : preCastEntityUUIDs;
        List<LivingEntity> near = client.world.getEntitiesByClass(
                LivingEntity.class, box, e -> {
                    if (e == player || !e.isAlive()) return false;
                    if (e instanceof PlayerEntity) return false;
                    if (e instanceof ArmorStandEntity) return false;
                    if (e.isInvisible()) return false;
                    // Only consider NEW entities (not present at bite moment)
                    if (baseline.contains(e.getUuid())) return false;
                    return hasLineOfSight(client, player, e);
                });
        if (near.isEmpty()) return null;
        double bx = lastBobberPos.x, by = lastBobberPos.y, bz = lastBobberPos.z;
        return near.stream()
                .min((a, b) -> Double.compare(
                        a.squaredDistanceTo(bx, by, bz),
                        b.squaredDistanceTo(bx, by, bz)))
                .orElse(null);
    }

    private int getConfiguredAttackCps() {
        MacroExecutor executor = MacroModClient.getExecutor();
        if (executor != null && executor.getCurrentMacro() != null && executor.getCurrentMacro().getConfig() != null) {
            return executor.getCurrentMacro().getConfig().getAttackCPS();
        }
        return DEFAULT_ATTACK_CPS;
    }

    private static boolean isLookingAt(ClientPlayerEntity player, Vec3d target, float toleranceDeg) {
        Vec3d eyes = player.getEyePos();
        Vec3d toTarget = target.subtract(eyes);
        if (toTarget.lengthSquared() < 1.0E-8) return true;
        Vec3d dir = toTarget.normalize();

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float targetPitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, dir.y))));
        float dyaw = Math.abs(wrapDeg(targetYaw - player.getYaw()));
        float dpitch = Math.abs(targetPitch - player.getPitch());

        return dyaw <= toleranceDeg && dpitch <= toleranceDeg;
    }

    // ── Auto Deposit ──────────────────────────────────────────────────────────

    /**
     * Drives the deposit sub-state machine. Called when fishState == DEPOSITING,
     * BEFORE the currentScreen null-guard in tick(), so the chest screen stays open.
     */
    private void tickDeposit(MinecraftClient client, ClientPlayerEntity player, long now) {
        switch (depositPhase) {
            case 0 -> {
                // Find the nearest chest and try to open it
                depositChestPos = findNearestChest(client, player);
                if (depositChestPos == null) {
                    fishState    = FishState.DELAY;
                    stateStartMs = now;
                    return;
                }
                var sa = MacroModClient.getSmoothAim();
                if (sa == null) {
                    fishState    = FishState.DELAY;
                    stateStartMs = now;
                    return;
                }
                sa.setTarget(Vec3d.ofCenter(depositChestPos));
                if (!sa.isOnTarget(player, 6f)) {
                    if (now - depositPhaseMs > DEPOSIT_OPEN_WAIT_MS) {
                        depositPhase = 0;
                        fishState    = FishState.DELAY;
                        stateStartMs = now;
                    }
                    return;
                }
                if (!MouseInputHelper.isCrosshairOnBlock(client, depositChestPos)) {
                    if (now - depositPhaseMs > DEPOSIT_OPEN_WAIT_MS) {
                        depositPhase = 0;
                        fishState    = FishState.DELAY;
                        stateStartMs = now;
                    }
                    return;
                }
                MouseInputHelper.rightClick(client);
                depositPhase   = 1;
                depositPhaseMs = now;
            }
            case 1 -> {
                // Wait for chest screen and shift-click all non-hotbar items into it
                if (client.currentScreen instanceof HandledScreen<?> hs) {
                    var handler = hs.getScreenHandler();
                    int containerSize = handler.slots.size() - 36;
                    int invStart = containerSize;
                    int invEnd   = containerSize + 27;
                    for (int slot = invStart; slot < invEnd; slot++) {
                        if (!handler.getSlot(slot).getStack().isEmpty()) {
                            client.interactionManager.clickSlot(
                                    handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, player);
                        }
                    }
                    depositPhase   = 2;
                    depositPhaseMs = now;
                } else if (now - depositPhaseMs > DEPOSIT_OPEN_WAIT_MS) {
                    depositPhase = 0;
                    fishState    = FishState.DELAY;
                    stateStartMs = now;
                }
            }
            case 2 -> {
                if (client.currentScreen != null) client.currentScreen.close();
                depositPhase = 0;
                var saDep = MacroModClient.getSmoothAim();
                if (saDep != null) {
                    saDep.setTarget(directionToPoint(player.getEyePos(), fishingLookYaw, fishingLookPitch));
                    fishState = FishState.RETURNING_LOOK;
                } else {
                    fishState = FishState.DELAY;
                }
                stateStartMs = now;
            }
        }
    }

    /** Scans around the player for the nearest chest block entity within {@value DEPOSIT_SCAN_RADIUS} blocks. */
    private BlockPos findNearestChest(MinecraftClient client, ClientPlayerEntity player) {
        if (client.world == null) return null;
        BlockPos origin = player.getBlockPos();
        BlockPos best   = null;
        double bestDist = Double.MAX_VALUE;
        int r = DEPOSIT_SCAN_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int ddy = -2; ddy <= 4; ddy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = origin.add(dx, ddy, dz);
                    if (client.world.getBlockEntity(pos) instanceof ChestBlockEntity) {
                        double d = dx * dx + (double) ddy * ddy + dz * dz;
                        if (d < bestDist) { bestDist = d; best = pos; }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Returns true when all player inventory slots (0..35, including hotbar)
     * are occupied by full stacks, meaning the player cannot pick up more items.
     */
    private static boolean isInventoryFullySaturated(ClientPlayerEntity player) {
        for (int i = 0; i < 36; i++) {
            var stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) return false;
            if (stack.getCount() < stack.getMaxCount()) return false;
        }
        return true;
    }

    /** Raycasts from player eyes to the target's centre; true if unobstructed. */
    private boolean hasLineOfSight(MinecraftClient client,
                                    ClientPlayerEntity player, Entity target) {
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
     * FOV-based entity detection: Finds entities in a 4x4 rectangular zone in front of player.
     * 
     * Concept: Create a box extending forward from the player's eye position along their look direction.
     * The box expands sideways and vertically to create a "viewing frustum".
     * 
     * Parameters:
     * - forwardDistance: How far ahead to search (in blocks)
     * - width: Horizontal FOV width (4 blocks = ~53 degrees at ~4 blocks distance)
     * - height: Vertical FOV height (4 blocks)
     * 
     * Example usage:
     * {@code
     * List<Entity> entitiesInFOV = client.world.getOtherEntities(player,
     *     createFOVBox(player.getEyePos(), player.getYaw(), player.getPitch(), 5.0, 4.0, 4.0),
     *     e -> e instanceof LivingEntity && !(e instanceof PlayerEntity)
     * );
     * }
     */
    @SuppressWarnings("unused")
    private static Box createFOVBox(Vec3d eyePos, float yaw, float pitch, double forwardDistance, double width, double height) {
        // Convert yaw/pitch to forward direction vector
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        
        double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double forwardY = -Math.sin(pitchRad);
        double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);
        
        // Right vector (perpendicular to forward in horizontal plane)
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        
        // Up vector (perpendicular to forward in vertical plane)
        double upX = Math.sin(yawRad) * Math.sin(pitchRad);
        double upY = Math.cos(pitchRad);
        double upZ = -Math.cos(yawRad) * Math.sin(pitchRad);
        
        // Front face center (along look direction)
        Vec3d front = eyePos.add(forwardX * forwardDistance, forwardY * forwardDistance, forwardZ * forwardDistance);
        
        // Expand box from front center by half-width and half-height in each direction
        double halfWidth = width / 2.0;
        double halfHeight = height / 2.0;
        
        double minX = front.x - halfWidth * Math.abs(rightX) - halfHeight * Math.abs(upX);
        double maxX = front.x + halfWidth * Math.abs(rightX) + halfHeight * Math.abs(upX);
        double minY = front.y - halfHeight;
        double maxY = front.y + halfHeight;
        double minZ = front.z - halfWidth * Math.abs(rightZ) - halfHeight * Math.abs(upZ);
        double maxZ = front.z + halfWidth * Math.abs(rightZ) + halfHeight * Math.abs(upZ);
        
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
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

    private void sendMessage(String key, Formatting color, Object... args) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.translatable(key, args).formatted(color), false);
        }
    }

    /** Converts Minecraft yaw+pitch to a world-space point 100 blocks in that direction. */
    private static Vec3d directionToPoint(Vec3d eye, float yaw, float pitch) {
        double yr   = Math.toRadians(yaw);
        double pr   = Math.toRadians(pitch);
        double cosP = Math.cos(pr);
        double dx   = -Math.sin(yr) * cosP;
        double dy   = -Math.sin(pr);
        double dz   =  Math.cos(yr) * cosP;
        return eye.add(dx * 100.0, dy * 100.0, dz * 100.0);
    }

    private static float wrapDeg(float d) {
        d %= 360f;
        if (d >= 180f)  d -= 360f;
        if (d < -180f)  d += 360f;
        return d;
    }
}
