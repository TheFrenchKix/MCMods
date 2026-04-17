package com.example.macromod.manager;

import com.example.macromod.MacroModClient;
import com.example.macromod.manager.AutoAttackManager;
import com.example.macromod.model.*;
import com.example.macromod.pathfinding.MovementHelper;
import com.example.macromod.pathfinding.PathHandler;
import com.example.macromod.pathfinding.PathFinder;
import com.example.macromod.pathfinding.SmoothAim;
import com.example.macromod.pathfinding.oringo.OringoPathModule;
import com.example.macromod.util.HumanReactionTime;

import java.util.HashSet;
import com.example.macromod.util.BlockUtils;
import com.example.macromod.util.PlayerUtils;
import com.example.macromod.util.MouseInputHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * State-machine-based macro executor. Handles pathfinding, movement, and block mining.
 * Called every client tick via ClientTickEvents.END_CLIENT_TICK.
 *
 * <p>State transitions:
 * <pre>
 * IDLE → PATHFINDING → MOVING → MINING → NEXT_STEP → PATHFINDING → ... → COMPLETED → IDLE
 * Any state → (stop) → IDLE
 * MOVING → (timeout) → ERROR → IDLE
 * </pre>
 */
@Environment(EnvType.CLIENT)
public class MacroExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");
    private final MovementHelper movementHelper;
    private final PathFinder fallbackPathFinder = new PathFinder();
    private final OringoPathModule oringoPathModule = new OringoPathModule();

    // ─── Metrics snapshot cadence ───────────────────────────────
    private static final long METRICS_SNAPSHOT_INTERVAL_MS = 5000L;
    private long lastMetricsSnapshotMs = 0L;

    // ─── Execution state ────────────────────────────────────────
    private MacroState state = MacroState.IDLE;
    private Macro currentMacro;
    private int currentStepIndex;
    private int currentBlockTargetIndex;

    // ─── Path & movement ────────────────────────────────────────
    private List<BlockPos> currentPath;
    private int currentPathIndex;
    private long moveStartMs;
    private Vec3d lastPosition;
    private long stuckSince = -1L;

    // ─── Pre-computed paths (all steps computed upfront) ─────────
    private List<List<BlockPos>> precomputedPaths;
    private int precomputeIndex;
    private BlockPos precomputeFromPos;

    // ─── Mining ─────────────────────────────────────────────────
    private boolean isMiningBlock;
    private long miningDelayEndMs;
    private boolean aimLocked = false;  // When true, don't re-aim; maintain current crosshair lock
    /** System time when aimLocked first became true. -1 when not locked. */
    private long aimLockedAtMs = -1L;
    /** Milliseconds to wait after crosshair confirms on block before pressing the attack key. */
    private static final long MINING_CLICK_DWELL_MS = 130L;
    /** Max time to persist mining before giving up (accounts for latency and slower tools). */
    private static final long MINING_TIMEOUT_MS = 6000L;
    /** Active block lock: once selected, keep mining this block until mined/skipped. */
    private BlockPos activeMiningTarget = null;
    private long lastMineTime;
    private static final Random MINE_RAND = new Random();

    // ─── Attack ─────────────────────────────────────────────────
    private long lastAttackMs = 0L;
    private LivingEntity attackTarget = null;
    private long attackChaseStartMs = -1L;
    private static final long ATTACK_CHASE_TIMEOUT_MS = 4000L;
    private static final Random ATTACK_RAND = new Random();
    private Vec3d attackAimPoint = null;
    private long attackAimRefreshMs = 0L;
    private long attackAimRefreshInterval = 700L;
    // Same-approach constants as AutoAttackManager
    private static final float ATTACK_COOLDOWN_THRESHOLD = 0.9f;
    private static final long  MACRO_ATTACK_DWELL_MS     = 150L;
    private static final int   MACRO_MIN_LOCK_TICKS      = 10;
    private static final int   MACRO_MAX_NO_LOS_TICKS    = 1;
    private static final float ATTACK_RELEASE_BUFFER     = 1.5f;
    /** Macro melee reach distance in blocks (matching AutoFishing close mode). */
    private static final float MACRO_MELEE_REACH         = 2.9f;
    /** Distance at which to start spam-clicking to build attack animation. */
    private static final float MACRO_SPAM_CLICK_DISTANCE = 4.0f;
    private long attackFirstOnTargetMs = -1L;
    private long lastSpamClickMs = -1L;
    private int  attackLockTicks  = 0;
    private int  attackNoLosTicks = 0;
    // ─── Attack chase stuck detection ───────────────────────────
    private Vec3d attackChaseLastPos  = null;
    private long  attackChaseStuckMs  = -1L;
    private boolean attackChaseJumped = false;

    // ─── Entity elimination mode (attackDanger) ─────────────────
    private boolean isEliminatingEnemies = false;

    // ─── Vanilla autojump ────────────────────────────────────────
    private boolean previousAutoJump = false;

    // ─── Pause for standalone AutoAttackManager ────────────────────
    private boolean pausedForAutoAttack = false;

    // ─── Mining LOS strafe ──────────────────────────────────────
    /** Block we are currently strafing to gain line-of-sight on. */
    private BlockPos noLOSBlock = null;
    /** System time when strafing started for {@link #noLOSBlock}. */
    private long noLOSStartMs = -1L;
    /** Initial strafe direction: true = left, false = right. */
    private boolean noLOSStrafeLeft = true;
    /** Switch strafe direction after this many ms. */
    private static final long STRAFE_SWITCH_MS  = 700L;
    /** Give up and skip the block after this many ms of strafing. */
    private static final long STRAFE_GIVE_UP_MS = 2500L;

    // ─── Chunk loading ──────────────────────────────────────────
    private long chunkWaitStartMs = -1L;
    // ─── Radius scan ───────────────────────────────────────────────
    /** Set once when entering the MINING state for a step; cleared on step advance. */
    private boolean radiusScanDone = false;
    /** Player-centered scan sphere radius (matches interaction range). */
    private static final double PLAYER_REACH_RADIUS    = 4.5;
    private static final double PLAYER_REACH_RADIUS_SQ = PLAYER_REACH_RADIUS * PLAYER_REACH_RADIUS;
    /** Integer loop bound for scan cubes (4 is enough for a 4.5 sphere and is faster than 5). */
    private static final int RADIUS_SCAN_RADIUS = 4;
    /** Vertical loop bound for scan cubes (2 greatly reduces scan cost while keeping nearby layers). */
    private static final int RADIUS_SCAN_VERTICAL = 2;
    /** Re-trigger scan when player moves this far (squared) from the last scan origin. */
    private static final double RESCAN_MOVE_THRESHOLD_SQ = 3.0 * 3.0;
    /** Minimum interval between radius scans to avoid visible frame hitches. */
    private static final long MIN_RADIUS_SCAN_INTERVAL_MS = 250L;
    /** Player block position at the last scanRadiusTargets call — drives dynamic rescan. */
    private BlockPos lastScanPlayerPos = null;
    /** Last wall-clock time a radius scan was executed. */
    private long lastRadiusScanMs = -1L;
    // ─── Statistics ─────────────────────────────────────────────
    private int blocksMinedTotal;
    private int blocksSkippedTotal;
    private long startTime;
    private double totalDistance;
    private Vec3d lastDistCheckPos;

    // ─── Robustness/perf metrics ────────────────────────────────
    private long tickCount;
    private long tickTotalNs;
    private long tickMaxNs;

    private long precomputedPathQueries;
    private long precomputedPathSuccess;
    private long precomputedPathNodesTotal;
    private long precomputedPathTotalNs;
    private long precomputedPathMaxNs;

    private long livePathQueries;
    private long livePathSuccess;
    private long livePathNodesTotal;
    private long livePathTotalNs;
    private long livePathMaxNs;

    private long repathCount;
    private long stuckRecoveries;
    private long navigationTimeouts;
    private long chunkWaitEvents;
    private long chunkWaitTimeoutSkips;
    private long chunkWaitTotalMs;

    private long autoAttackPauseCount;
    private long autoAttackPauseTotalMs;
    private long autoAttackPauseStartMs = -1L;

    private long eliminationModeCount;
    private long eliminationModeTotalMs;
    private long eliminationModeStartMs = -1L;

    private long miningSkipUnripe;
    private long miningSkipRange;
    private long miningSkipNoLos;
    private long miningSkipBlockChanged;
    private long miningSkipBlockReplaced;
    private long miningSkipTimeout;

    private long attackLostLosDrops;
    private long attackChaseStuckSkips;
    private long attackChaseJumpAttempts;

    private boolean metricsSummaryLogged = false;

    // ─── State before pause ─────────────────────────────────────
    private MacroState stateBeforePause;

    // ─── Line Farm mode ─────────────────────────────────────────
    /** Starting position for line farm (to lock crosshair). */
    private Vec3d lineFarmStartPos = null;
    /** Current direction in line farm: 0=left, 1=forward, 2=right, repeating. */
    private int lineFarmDirection = 0;
    /** How many blocks we've moved in the current direction. */
    private int lineFarmDistance = 0;
    /** Width of line farm sweep. */
    private int lineFarmWidth = 5;
    /** Whether to continue line farming. */
    private boolean isLineFarming = false;
    /** Crosshair locked position during line farm. */
    private Vec3d lineFarmCrosshairPos = null;

    public MacroExecutor(SmoothAim smoothAim) {
        this.movementHelper = new MovementHelper(smoothAim);
        // Keep fallback pathfinder quiet; this class now emits structured metrics logs.
        this.fallbackPathFinder.setDebugLogging(false);
        LOGGER.info("[macro.metrics.lifecycle] executorReady pathfinderDebug={} snapshotIntervalMs={}",
            false, METRICS_SNAPSHOT_INTERVAL_MS);
    }

    /**
     * Starts execution of a macro by name or ID.
     *
     * @param macroId   the ID or name of the macro
     * @param loopOverride  if non-null, overrides the macro's loop setting
     */
    public void start(String macroId, Boolean loopOverride) {
        if (state != MacroState.IDLE && state != MacroState.COMPLETED && state != MacroState.ERROR) {
            sendMessage("macromod.chat.already_running", Formatting.RED);
            return;
        }

        MacroManager manager = MacroModClient.getManager();
        Macro macro = manager.getByNameOrId(macroId);
        if (macro == null) {
            sendMessage("macromod.chat.macro_not_found", Formatting.RED, macroId);
            return;
        }

        if (macro.getSteps().isEmpty()) {
            sendMessage("macromod.chat.macro_not_found", Formatting.RED, macroId);
            return;
        }

        currentMacro = macro;
        currentMacro.reset();
        currentStepIndex = 0;
        currentBlockTargetIndex = 0;

        if (loopOverride != null) {
            currentMacro.getConfig().setLoop(loopOverride);
        }

        fallbackPathFinder.setMaxNodes(MacroModClient.getConfigManager().getConfig().getMaxPathNodes());

        // Reset stats
        blocksMinedTotal = 0;
        blocksSkippedTotal = 0;
        startTime = System.currentTimeMillis();
        totalDistance = 0;
        lastDistCheckPos = null;
        resetRunMetrics();

        // Pre-compute paths for all steps so navigation never stalls between steps
        precomputedPaths = new ArrayList<>(currentMacro.getSteps().size());
        for (int i = 0; i < currentMacro.getSteps().size(); i++) {
            precomputedPaths.add(null);
        }
        radiusScanDone = false;
        MinecraftClient startClient = MinecraftClient.getInstance();
        precomputeFromPos = startClient.player != null ? startClient.player.getBlockPos() : null;
        precomputeIndex = 0;

        // Enable vanilla autojump for obstacle climbing (save previous setting)
        if (startClient.options != null) {
            previousAutoJump = startClient.options.getAutoJump().getValue();
            startClient.options.getAutoJump().setValue(true);
        }

        state = MacroState.PRECOMPUTING;
        
        LOGGER.info("[macro.metrics.lifecycle] macroStart name={} loop={} steps={} onlyGround={} attackEnabled={} attackDanger={}",
            macro.getName(),
            macro.getConfig().isLoop(),
            macro.getSteps().size(),
            macro.getConfig().isOnlyGround(),
            macro.getConfig().isAttackEnabled(),
            macro.getConfig().isAttackDanger());

        if (currentMacro.getConfig().isLoop()) {
            sendMessage("macromod.chat.macro_started_loop", Formatting.GREEN, macro.getName());
        } else {
            sendMessage("macromod.chat.macro_started", Formatting.GREEN, macro.getName());
        }
    }

    /**
     * Starts line farm mode: holds left click and moves in a snake pattern until hitting a block.
     * Locks crosshair at the starting position.
     */
    public void startLineFarm() {
        if (state != MacroState.IDLE && state != MacroState.COMPLETED && state != MacroState.ERROR) {
            sendMessage("macromod.chat.already_running", Formatting.RED);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Initialize line farm state
        lineFarmStartPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        lineFarmCrosshairPos = player.getEyePos();
        lineFarmDirection = 0; // Start going left
        lineFarmDistance = 0;
        lineFarmWidth = 5; // Sweep 5 blocks wide
        isLineFarming = true;
        
        // Reset stats
        blocksMinedTotal = 0;
        blocksSkippedTotal = 0;
        state = MacroState.LINE_FARMING;
        startTime = System.currentTimeMillis();
        totalDistance = 0;
        lastDistCheckPos = null;
        resetRunMetrics();

        sendMessage("macromod.chat.macro_started", Formatting.GREEN, "Line Farm");
        LOGGER.info("[macro.metrics.lifecycle] lineFarmStart width={} direction={}", lineFarmWidth, lineFarmDirection);
    }

    /**
     * Stops the macro execution immediately, releasing all inputs.
     */
    public void stop() {
        stop("manual_stop");
    }

    private void stop(String reason) {
        if (state == MacroState.IDLE) {
            sendMessage("macromod.chat.no_active_macro", Formatting.YELLOW);
            return;
        }

        MacroState stateBeforeStop = state;

        // Restore autojump setting
        MinecraftClient stopClient = MinecraftClient.getInstance();
        if (stopClient.options != null) {
            stopClient.options.getAutoJump().setValue(previousAutoJump);
        }

        movementHelper.releaseAllInputs();
        isMiningBlock = false;
        aimLocked = false;
        aimLockedAtMs = -1L;
        pausedForAutoAttack = false;
        attackTarget = null;
        attackChaseStartMs = -1L;
        attackLockTicks = 0;
        attackNoLosTicks = 0;
        attackFirstOnTargetMs = -1L;
        attackChaseLastPos = null;
        attackChaseStuckMs = -1L;
        attackChaseJumped = false;
        isLineFarming = false;

        // Print stats if we ran for a while
        if (currentMacro != null && startTime > 0) {
            logMetricsSummary(reason);
            printStats();
        }

        state = MacroState.IDLE;
        currentMacro = null;
        currentPath = null;
        precomputedPaths = null;
        precomputeFromPos = null;
        sendMessage("macromod.chat.macro_stopped", Formatting.YELLOW);
        LOGGER.info("[macro.metrics.lifecycle] macroStop reason={} stateBeforeStop={} step={} mined={} skipped={}",
            reason, stateBeforeStop, currentStepIndex, blocksMinedTotal, blocksSkippedTotal);
    }

    /**
     * Pauses execution. The player stops moving and mining.
     */
    public void pause() {
        if (state == MacroState.IDLE || state == MacroState.PAUSED
                || state == MacroState.COMPLETED || state == MacroState.ERROR) {
            return;
        }
        stateBeforePause = state;
        state = MacroState.PAUSED;
        movementHelper.releaseAllInputs();
        isMiningBlock = false;
        aimLocked = false;
        aimLockedAtMs = -1L;
        pausedForAutoAttack = false;
        attackTarget = null;
        attackChaseStartMs = -1L;
        attackLockTicks = 0;
        attackNoLosTicks = 0;
        attackFirstOnTargetMs = -1L;
        attackChaseLastPos = null;
        attackChaseStuckMs = -1L;
        attackChaseJumped = false;
        sendMessage("macromod.chat.macro_paused", Formatting.YELLOW);
        LOGGER.info("[macro.metrics.lifecycle] macroPause stateBeforePause={} step={}",
            stateBeforePause, currentStepIndex);
    }

    /**
     * Resumes execution from a paused state.
     */
    public void resume() {
        if (state != MacroState.PAUSED) return;
        state = stateBeforePause != null ? stateBeforePause : MacroState.PATHFINDING;
        stateBeforePause = null;
        sendMessage("macromod.chat.macro_resumed", Formatting.GREEN);
        LOGGER.info("[macro.metrics.lifecycle] macroResume state={} step={}", state, currentStepIndex);
    }

    /**
     * Called every client tick. Drives the state machine forward.
     */
    public void tick() {
        if (state == MacroState.IDLE || state == MacroState.COMPLETED
                || state == MacroState.ERROR || state == MacroState.PAUSED) {
            return;
        }
        long tickStartNs = System.nanoTime();
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            ClientWorld world = client.world;
            if (player == null || world == null) return;

            // Stop macro if player dies
            if (player.isDead() || player.getHealth() <= 0) {
                LOGGER.info("[macro.metrics.robustness] playerDeathStop step={} health={}",
                    currentStepIndex, player.getHealth());
                stop("player_death");
                sendMessage("macromod.chat.macro_stopped_death", Formatting.RED);
                return;
            }

            // Track distance
            if (lastDistCheckPos != null) {
                totalDistance += new Vec3d(player.getX(), player.getY(), player.getZ()).distanceTo(lastDistCheckPos);
            }
            lastDistCheckPos = new Vec3d(player.getX(), player.getY(), player.getZ());

            // Pause macro movement whenever the standalone AutoAttackManager (K key)
            // has an active target. Resume with fresh pathfinding when target clears.
            AutoAttackManager aam = AutoAttackManager.getInstance();
            boolean aamHasTarget = aam != null && aam.isEnabled() && aam.getCurrentTarget() != null;
            if (aamHasTarget && !pausedForAutoAttack) {
                pausedForAutoAttack = true;
                autoAttackPauseCount++;
                autoAttackPauseStartMs = System.currentTimeMillis();
                movementHelper.releaseAllInputs();
                aimLocked = false;
                aimLockedAtMs = -1L;
                isMiningBlock = false;
                if (client.options != null) client.options.attackKey.setPressed(false);
                LOGGER.info("[macro.metrics.robustness] autoAttackPause state={} step={}",
                    state, currentStepIndex);
            }
            if (!aamHasTarget && pausedForAutoAttack) {
                pausedForAutoAttack = false;
                if (autoAttackPauseStartMs >= 0) {
                    autoAttackPauseTotalMs += System.currentTimeMillis() - autoAttackPauseStartMs;
                    autoAttackPauseStartMs = -1L;
                }
                if (state == MacroState.MOVING || state == MacroState.PATHFINDING
                        || state == MacroState.MINING || state == MacroState.NEXT_STEP) {
                    currentPath = null;
                    currentPathIndex = 0;
                    moveStartMs = System.currentTimeMillis();
                    stuckSince  = -1L;
                    lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
                    state = MacroState.PATHFINDING;
                    repathCount++;
                    LOGGER.info("[macro.metrics.robustness] autoAttackResume repath=true step={}", currentStepIndex);
                } else {
                    LOGGER.info("[macro.metrics.robustness] autoAttackResume repath=false step={}", currentStepIndex);
                }
            }
            if (pausedForAutoAttack) {
                return; // Suspend state machine while entity is being attacked
            }

            boolean isDuringMacro = state != MacroState.IDLE && state != MacroState.COMPLETED && state != MacroState.ERROR;
            if (isDuringMacro && currentMacro.getConfig().isAttackDanger()) {
                boolean hasEnemiesNearby = hasEntitiesInRange(player, world, currentMacro.getConfig());
                if (hasEnemiesNearby && !isEliminatingEnemies) {
                    isEliminatingEnemies = true;
                    eliminationModeCount++;
                    eliminationModeStartMs = System.currentTimeMillis();
                    LOGGER.info("[macro.metrics.robustness] eliminationStart fromState={} step={}",
                        state, currentStepIndex);
                    // Do NOT release inputs or clear path — keep moving while fighting
                    attackTarget = null;
                    attackChaseStartMs = -1L;
                }
            }

            if (isEliminatingEnemies) {
                boolean stillHasEnemies = hasEntitiesInRange(player, world, currentMacro.getConfig());
                if (!stillHasEnemies) {
                    isEliminatingEnemies = false;
                    long eliminationDurationMs = 0L;
                    if (eliminationModeStartMs >= 0) {
                        eliminationDurationMs = System.currentTimeMillis() - eliminationModeStartMs;
                        eliminationModeTotalMs += eliminationDurationMs;
                        eliminationModeStartMs = -1L;
                    }
                    attackTarget = null;
                    attackChaseStartMs = -1L;
                    stuckSince  = -1L;
                    lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
                    // Always repath after elimination: player may have drifted and old LOS assumptions are stale.
                    LOGGER.info("[macro.metrics.robustness] eliminationEnd step={} durationMs={} repath=true from={}",
                        currentStepIndex, eliminationDurationMs, player.getBlockPos());
                    currentPath = null;
                    currentPathIndex = 0;
                    if (precomputedPaths != null && currentStepIndex < precomputedPaths.size()) {
                        precomputedPaths.set(currentStepIndex, null);
                    }
                    moveStartMs = System.currentTimeMillis();
                    state = MacroState.PATHFINDING;
                    repathCount++;
                } else {
                    tickAttack(player, world, client);
                    return;
                }
            }

            if (currentMacro.getConfig().isAttackEnabled()) {
                tickAttack(player, world, client);
            }

            switch (state) {
                case PRECOMPUTING -> tickPrecomputing(player, world);
                case PATHFINDING  -> tickPathfinding(player, world);
                case MOVING       -> tickMoving(player, world);
                case MINING       -> tickMining(player, world, client);
                case LINE_FARMING -> tickLineFarming(player, world, client);
                case NEXT_STEP    -> tickNextStep();
                default -> { }
            }
        } finally {
            long tickNs = System.nanoTime() - tickStartNs;
            tickCount++;
            tickTotalNs += tickNs;
            if (tickNs > tickMaxNs) {
                tickMaxNs = tickNs;
            }
            maybeLogMetricsSnapshot();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // State tick handlers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Pre-computation phase: computes one step path per tick, storing results in
     * {@code precomputedPaths}. Transitions to PATHFINDING when all paths are ready.
     */
    private void tickPrecomputing(ClientPlayerEntity player, ClientWorld world) {
        if (precomputeIndex >= currentMacro.getSteps().size()) {
            LOGGER.info("[macro.metrics.path] precomputeDone steps={} success={}/{} avgMs={} maxMs={} avgNodes={}",
                currentMacro.getSteps().size(),
                precomputedPathSuccess,
                precomputedPathQueries,
                precomputedPathQueries > 0 ? (precomputedPathTotalNs / 1_000_000.0) / precomputedPathQueries : 0.0,
                precomputedPathMaxNs / 1_000_000.0,
                precomputedPathSuccess > 0 ? (double) precomputedPathNodesTotal / precomputedPathSuccess : 0.0);
            state = MacroState.PATHFINDING;
            return;
        }

        MacroStep step = currentMacro.getSteps().get(precomputeIndex);
        BlockPos goal  = step.getDestination();
        BlockPos from  = precomputeFromPos != null ? precomputeFromPos : player.getBlockPos();

        if (!BlockUtils.isChunkLoaded(world, goal)) {
            // Cannot path to unloaded chunk yet — leave as null, live fallback will handle it
            LOGGER.info("[macro.metrics.path] precomputeSkip step={} reason=chunk_not_loaded goal={}",
                precomputeIndex, goal);
        } else {
            List<BlockPos> path = findPathWithMetrics(from, goal, world, 500L, true, precomputeIndex);
            precomputedPaths.set(precomputeIndex, path);
        }

        precomputeFromPos = goal; // next path starts from this step's destination
        precomputeIndex++;

        if (precomputeIndex >= currentMacro.getSteps().size()) {
            LOGGER.info("[macro.metrics.path] precomputeDone steps={} success={}/{} avgMs={} maxMs={} avgNodes={}",
                currentMacro.getSteps().size(),
                precomputedPathSuccess,
                precomputedPathQueries,
                precomputedPathQueries > 0 ? (precomputedPathTotalNs / 1_000_000.0) / precomputedPathQueries : 0.0,
                precomputedPathMaxNs / 1_000_000.0,
                precomputedPathSuccess > 0 ? (double) precomputedPathNodesTotal / precomputedPathSuccess : 0.0);
            state = MacroState.PATHFINDING;
        }
    }

    private void tickPathfinding(ClientPlayerEntity player, ClientWorld world) {
        MacroStep step = getCurrentStep();
        if (step == null) {
            state = MacroState.COMPLETED;
            return;
        }

        BlockPos goal = step.getDestination();

        // Already at destination? Skip mining for nav-only steps, go straight to next step.
        if (PlayerUtils.isArrived(player, goal, currentMacro.getConfig().getArrivalRadius())) {
            if (!isCenteredOnBlock(player, goal)) {
                movementHelper.moveTowards(player, goal);
                return;
            }
            // Nav-only step (no targets to mine) → advance immediately, no input release
            if (step.getTargets().isEmpty() && step.getRadius() <= 0) {
                advanceToNextStep();
                // Flow through: if advanceToNextStep set us to PATHFINDING, run it now
                if (state == MacroState.PATHFINDING) {
                    tickPathfinding(player, world);
                }
                return;
            }
            movementHelper.releaseAllInputs();
            state = MacroState.MINING;
            currentBlockTargetIndex = 0;
            isMiningBlock = false;
            return;
        }

        // Try to retrieve pre-computed path
        List<BlockPos> path = (precomputedPaths != null && currentStepIndex < precomputedPaths.size())
                ? precomputedPaths.get(currentStepIndex) : null;

        if (path == null || path.isEmpty()) {
            // Live fallback: chunk not loaded during pre-compute, or stuck recalculation
            if (!BlockUtils.isChunkLoaded(world, goal)) {
                if (chunkWaitStartMs < 0) {
                    chunkWaitStartMs = System.currentTimeMillis();
                    chunkWaitEvents++;
                    sendMessage("macromod.chat.chunk_not_loaded", Formatting.YELLOW);
                    LOGGER.info("[macro.metrics.robustness] chunkWaitStart step={} goal={}", currentStepIndex, goal);
                } else if (System.currentTimeMillis() - chunkWaitStartMs > 5000) {
                    chunkWaitTimeoutSkips++;
                    chunkWaitTotalMs += System.currentTimeMillis() - chunkWaitStartMs;
                    LOGGER.info("[macro.metrics.robustness] chunkWaitTimeout step={} waitedMs={} goal={} action=skip",
                        currentStepIndex, System.currentTimeMillis() - chunkWaitStartMs, goal);
                    sendMessage("macromod.chat.path_not_found", Formatting.RED);
                    chunkWaitStartMs = -1L;
                    advanceToNextStep();
                }
                return;
            }
            if (chunkWaitStartMs >= 0) {
                chunkWaitTotalMs += System.currentTimeMillis() - chunkWaitStartMs;
            }
            chunkWaitStartMs = -1L;

            long timeoutMs = Math.max(1000L, currentMacro.getConfig().getMoveTimeout());
            path = findPathWithMetrics(player.getBlockPos(), goal, world, timeoutMs, false, currentStepIndex);
            if (path == null || path.isEmpty()) {
                LOGGER.info("[macro.metrics.robustness] pathNotFound step={} goal={} action=skip",
                    currentStepIndex, goal);
                sendMessage("macromod.chat.path_not_found", Formatting.RED);
                advanceToNextStep();
                return;
            }
            // Cache for re-use in case we re-enter PATHFINDING
            if (precomputedPaths != null && currentStepIndex < precomputedPaths.size()) {
                precomputedPaths.set(currentStepIndex, path);
            }
        }

        currentPath = path;
        currentPathIndex = 0;
        moveStartMs = System.currentTimeMillis();
        stuckSince = -1L;
        lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
        movementHelper.resetJumpState();
        state = MacroState.MOVING;
        // Flow through: start moving in the same tick to avoid 1-tick dead zone
        tickMoving(player, world);
    }

    private void tickMoving(ClientPlayerEntity player, ClientWorld world) {
        MacroStep step = getCurrentStep();
        if (step == null) {
            state = MacroState.COMPLETED;
            return;
        }

        float arrivalRadius = currentMacro.getConfig().getArrivalRadius();

        // Check if we've arrived at the final destination
        if (PlayerUtils.isArrived(player, step.getDestination(), arrivalRadius)) {
            BlockPos dest = step.getDestination();
            // Nav-only step → advance immediately, keep momentum
            if (step.getTargets().isEmpty() && step.getRadius() <= 0) {
                advanceToNextStep();
                return;
            }
            if (!isCenteredOnBlock(player, dest)) {
                movementHelper.moveTowards(player, dest);
                return;
            }
            movementHelper.releaseAllInputs();
            state = MacroState.MINING;
            currentBlockTargetIndex = 0;
            isMiningBlock = false;
            return;
        }

        // Timeout check per tick for movement only (not mining) to prevent getting stuck indefinitely
        long elapsedMove = System.currentTimeMillis() - moveStartMs;
        if (elapsedMove > 60000) {
            navigationTimeouts++;
            LOGGER.info("[macro.metrics.robustness] navigationTimeout step={} elapsedMs={} pathIdx={}/{}",
                currentStepIndex, elapsedMove, currentPathIndex,
                currentPath != null ? currentPath.size() : 0);
            sendMessage("macromod.chat.timeout", Formatting.RED);
            movementHelper.releaseAllInputs();
            state = MacroState.ERROR;
            return;
        }

        // Stuck detection — only repath after 5s of no progress.
        // The first 0-2s are handled by MovementHelper's strafe dislodge.
        // No releaseAllInputs here — keep moving while repathing.
        if (lastPosition != null) {
            double moved = PlayerUtils.horizontalDistanceTo(player, lastPosition);
            if (moved < 0.5) {
                if (stuckSince < 0) stuckSince = System.currentTimeMillis();
            } else {
                stuckSince = -1L;
                lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
            }
        }

        // If player is in water and stuck, hold jump to swim to the surface
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (player.isTouchingWater() && stuckSince >= 0
                    && System.currentTimeMillis() - stuckSince > 800
                    && mc.options != null) {
                mc.options.jumpKey.setPressed(true);
            } else if (mc.options != null && !attackChaseJumped) {
                mc.options.jumpKey.setPressed(false);
            }
        }

        if (stuckSince >= 0 && System.currentTimeMillis() - stuckSince > 5000) {
            stuckRecoveries++;
            repathCount++;
            LOGGER.info("[macro.metrics.robustness] movementStuck step={} stuckMs={} action=repath",
                currentStepIndex, System.currentTimeMillis() - stuckSince);
            stuckSince = -1L;
            if (precomputedPaths != null && currentStepIndex < precomputedPaths.size()) {
                precomputedPaths.set(currentStepIndex, null);
            }
            state = MacroState.PATHFINDING;
            return;
        }

        // If chasing an entity during attackDanger mode, temporarily skip path following
        boolean isChasingEntity = currentMacro.getConfig().isAttackDanger() && attackTarget != null 
                && attackTarget.isAlive() && attackTarget.getHealth() > 0;
        if (isChasingEntity) {
            // Entity chase takes priority; movement already handled by tickAttack
            return;
        }

        BlockPos dest = step.getDestination();
        BlockPos playerBlockPos = player.getBlockPos();

        // ── DIRECT MOVEMENT: if destination is walkably visible, skip entire path ──
        if (BlockUtils.hasWalkableLOS(world, playerBlockPos, dest)) {
            movementHelper.handleStuckRecovery(player);
            movementHelper.moveTowards(player, dest);
            // Advance path index to end so we don't revert to path-following next tick
            if (currentPath != null) currentPathIndex = currentPath.size();
            return;
        }

        // Path already consumed: navigate directly to destination.
        if (currentPath != null && currentPathIndex >= currentPath.size()) {
            // Nav-only step → advance immediately, keep momentum
            if (step.getTargets().isEmpty() && step.getRadius() <= 0) {
                if (PlayerUtils.isArrived(player, dest, arrivalRadius)) {
                    advanceToNextStep();
                    return;
                }
                movementHelper.moveTowards(player, dest);
                return;
            }
            if (!isCenteredOnBlock(player, dest)) {
                movementHelper.moveTowards(player, dest);
                return;
            }
            movementHelper.releaseAllInputs();
            state = MacroState.MINING;
            currentBlockTargetIndex = 0;
            isMiningBlock = false;
            return;
        }

        // ── LOS-BASED TARGET SELECTION: find the farthest visible waypoint ──
        if (currentPath != null && currentPathIndex < currentPath.size()) {
            // Scan from end of path backwards to find the farthest node we can walk to directly
            BlockPos targetWaypoint = currentPath.get(currentPathIndex);
            int bestIndex = currentPathIndex;

            for (int i = currentPath.size() - 1; i > currentPathIndex; i--) {
                BlockPos candidate = currentPath.get(i);
                if (BlockUtils.hasWalkableLOS(world, playerBlockPos, candidate)) {
                    targetWaypoint = candidate;
                    bestIndex = i;
                    break;
                }
            }

            // If the current waypoint itself is no longer visible and we found no visible
            // replacement, recompute path from current position.
            if (bestIndex == currentPathIndex
                    && !BlockUtils.hasWalkableLOS(world, playerBlockPos, currentPath.get(currentPathIndex))) {
                if (precomputedPaths != null && currentStepIndex < precomputedPaths.size()) {
                    precomputedPaths.set(currentStepIndex, null);
                }
                currentPath = null;
                currentPathIndex = 0;
                state = MacroState.PATHFINDING;
                repathCount++;
                return;
            }

            // Advance path index to the farthest visible node
            currentPathIndex = bestIndex;

            // Also advance past any waypoints we've already reached
            while (currentPathIndex < currentPath.size() - 1
                    && movementHelper.hasReachedWaypoint(player, currentPath.get(currentPathIndex))) {
                currentPathIndex++;
                targetWaypoint = currentPath.get(currentPathIndex);
            }

            // Strafe recovery for stuck situations; jumping handled by vanilla autojump
            movementHelper.handleStuckRecovery(player);
            // Smoothly steer and hold forward — no key release between waypoints
            movementHelper.moveTowards(player, targetWaypoint);
        } else {
            // No path — recalculate (keep moving, don't release inputs)
            state = MacroState.PATHFINDING;
            repathCount++;
        }
    }

    private void tickMining(ClientPlayerEntity player, ClientWorld world, MinecraftClient client) {
        MacroStep step = getCurrentStep();
        if (step == null || (step.getTargets().isEmpty() && step.getRadius() <= 0) || step.isComplete()) {
            advanceToNextStep();
            return;
        }

        boolean onlyDefinedTargets = currentMacro.getConfig().isMineOnlyDefinedTargets();

        if (step.isComplete()) {
            advanceToNextStep();
            return;
        }

        // Check mining delay
        if (System.currentTimeMillis() < miningDelayEndMs) {
            return;
        }

        // Advance past already-processed targets to keep the index valid
        while (currentBlockTargetIndex < step.getTargets().size()
                && step.getTargets().get(currentBlockTargetIndex).isProcessed()) {
            currentBlockTargetIndex++;
        }

        if (!onlyDefinedTargets) {
            long now = System.currentTimeMillis();
            boolean canScanNow = lastRadiusScanMs < 0 || now - lastRadiusScanMs >= MIN_RADIUS_SCAN_INTERVAL_MS;

            // First entry scan: run only if we are out of immediate targets, to avoid startup hitch.
            if (!radiusScanDone && currentBlockTargetIndex >= step.getTargets().size() && canScanNow) {
                radiusScanDone = true;
                if (!step.getTargets().isEmpty()) {
                    scanRadiusTargets(step, world, player);
                }
            }

            // Dynamic rescan: throttled and only useful when we have base target types.
            if (canScanNow && lastScanPlayerPos != null && !step.getTargets().isEmpty()) {
                BlockPos pp = player.getBlockPos();
                int dx = pp.getX() - lastScanPlayerPos.getX();
                int dy = pp.getY() - lastScanPlayerPos.getY();
                int dz = pp.getZ() - lastScanPlayerPos.getZ();
                if ((double)(dx*dx + dy*dy + dz*dz) > RESCAN_MOVE_THRESHOLD_SQ) {
                    scanRadiusTargets(step, world, player);
                }
            }
        }

        if (currentBlockTargetIndex >= step.getTargets().size()) {
            if (client.options != null) client.options.attackKey.setPressed(false);
            advanceToNextStep();
            return;
        }

        // Release attack key while not actively mining to avoid ghost-breaking adjacent blocks
        if (!isMiningBlock && client.options != null) {
            client.options.attackKey.setPressed(false);
        }

        mineSingleBlock(step.getTargets().get(currentBlockTargetIndex), player, world, client, step);
    }

    private void tickNextStep() {
        advanceToNextStep();
    }

    /**
     * Mine a single block. Handles all checks, aiming, and attack logic.
     * If the block is mined or becomes unreachable, advances {@code currentBlockTargetIndex}.
     */
    private void mineSingleBlock(BlockTarget target, ClientPlayerEntity player, ClientWorld world, MinecraftClient client, MacroStep step) {
        Vec3d eyePos = player.getEyePos();
        BlockPos blockPos = target.getPos();

        // Target lock: never switch focus while the current target is in progress.
        if (activeMiningTarget == null || !activeMiningTarget.equals(blockPos)) {
            activeMiningTarget = blockPos;
            isMiningBlock = false;
            aimLocked = false;
            aimLockedAtMs = -1L;
            noLOSBlock = null;
            noLOSStartMs = -1L;
            if (client.options != null) client.options.attackKey.setPressed(false);
        }

        // Block verification
        String actualBlockId = BlockUtils.getBlockId(world, blockPos);

        // If block is already air, it's already mined
        if (BlockUtils.isAir(world, blockPos)) {
            target.setMined(true);
            blocksMinedTotal++;
            currentBlockTargetIndex++;
            activeMiningTarget = null;
            isMiningBlock = false;
            aimLocked = false;
            aimLockedAtMs = -1L;
            noLOSBlock = null;
            noLOSStartMs = -1L;
            if (client.options != null) client.options.attackKey.setPressed(false);
            miningDelayEndMs = System.currentTimeMillis() + HumanReactionTime.getMiningReactionTime(currentMacro.getConfig().getMiningDelay());
            return;
        }

        // Check block type match — always skip if block changed
        if (!actualBlockId.equals(target.getBlockId())) {
            miningSkipBlockChanged++;
            target.setSkipped(true);
            blocksSkippedTotal++;
            currentBlockTargetIndex++;
            activeMiningTarget = null;
            isMiningBlock = false;
            aimLocked = false;
            aimLockedAtMs = -1L;
            noLOSBlock = null;
            noLOSStartMs = -1L;
            if (client.options != null) client.options.attackKey.setPressed(false);
            LOGGER.info("[macro.metrics.robustness] mineSkip reason=block_changed step={} block={} expected={} actual={}",
                currentStepIndex, blockPos, target.getBlockId(), actualBlockId);
            return;
        }

        // Skip unripe crops: if block has an 'age' property, only mine when at max age
        BlockState blockState = world.getBlockState(blockPos);
        for (Property<?> prop : blockState.getProperties()) {
            if ("age".equals(prop.getName()) && prop instanceof IntProperty intProp) {
                int currentAge = blockState.get(intProp);
                int maxAge = intProp.getValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                if (currentAge < maxAge) {
                    miningSkipUnripe++;
                    target.setSkipped(true);
                    blocksSkippedTotal++;
                    currentBlockTargetIndex++;
                    activeMiningTarget = null;
                    isMiningBlock = false;
                    aimLocked = false;
                    aimLockedAtMs = -1L;
                    noLOSBlock = null;
                    noLOSStartMs = -1L;
                    if (client.options != null) client.options.attackKey.setPressed(false);
                    LOGGER.info("[macro.metrics.robustness] mineSkip reason=unripe step={} block={} age={}/{}",
                        currentStepIndex, blockPos, currentAge, maxAge);
                    return;
                }
                break;
            }
        }

        // Hard range gate: permanently skip blocks truly too far to ever reach.
        Vec3d blockCenter3d = Vec3d.ofCenter(blockPos);
        if (eyePos.squaredDistanceTo(blockCenter3d) > 6.0 * 6.0) {
            miningSkipRange++;
            target.setSkipped(true);
            blocksSkippedTotal++;
            currentBlockTargetIndex++;
            activeMiningTarget = null;
            isMiningBlock = false;
            aimLocked = false;
            aimLockedAtMs = -1L;
            noLOSBlock = null;
            noLOSStartMs = -1L;
            if (client.options != null) client.options.attackKey.setPressed(false);
            LOGGER.info("[macro.metrics.robustness] mineSkip reason=out_of_range step={} block={} dist={}",
                currentStepIndex, blockPos, Math.sqrt(eyePos.squaredDistanceTo(blockCenter3d)));
            return;
        }

        // Robust visibility: find an actually visible surface point via multi-point voxel raycasts.
        Vec3d visiblePoint = visibleFaceCentre(eyePos, blockPos, player.getBlockPos().getY(), world, player);
        if (visiblePoint == null) {
            // If mining has already started, keep holding attack to avoid breaking-cycle interruptions.
            if (isMiningBlock) {
                if (client.options != null) client.options.attackKey.setPressed(true);
                MouseInputHelper.continueLeftClick(client);

                if (BlockUtils.isAir(world, blockPos)) {
                    target.setMined(true);
                    blocksMinedTotal++;
                    activeMiningTarget = null;
                    isMiningBlock = false;
                    aimLocked = false;
                    aimLockedAtMs = -1L;
                    noLOSBlock = null;
                    noLOSStartMs = -1L;
                    if (client.options != null) client.options.attackKey.setPressed(false);
                    miningDelayEndMs = System.currentTimeMillis() + HumanReactionTime.getMiningReactionTime(currentMacro.getConfig().getMiningDelay());
                    scanRadiusTargets(step, world, player);
                    currentBlockTargetIndex++;
                    return;
                }

                if (System.currentTimeMillis() - lastMineTime > MINING_TIMEOUT_MS) {
                    miningSkipTimeout++;
                    if (client.options != null) client.options.attackKey.setPressed(false);
                    target.setSkipped(true);
                    blocksSkippedTotal++;
                    currentBlockTargetIndex++;
                    activeMiningTarget = null;
                    isMiningBlock = false;
                    aimLocked = false;
                    aimLockedAtMs = -1L;
                    noLOSBlock = null;
                    noLOSStartMs = -1L;
                    LOGGER.info("[macro.metrics.robustness] mineSkip reason=mining_timeout step={} block={} elapsedMs={}",
                        currentStepIndex, blockPos, System.currentTimeMillis() - lastMineTime);
                }
                return;
            }

            long now = System.currentTimeMillis();
            if (noLOSBlock == null || !noLOSBlock.equals(blockPos)) {
                noLOSBlock = blockPos;
                noLOSStartMs = now;
            }
            if (now - noLOSStartMs > STRAFE_GIVE_UP_MS) {
                long elapsedNoLos = now - noLOSStartMs;
                miningSkipNoLos++;
                target.setSkipped(true);
                blocksSkippedTotal++;
                currentBlockTargetIndex++;
                activeMiningTarget = null;
                isMiningBlock = false;
                aimLocked = false;
                aimLockedAtMs = -1L;
                noLOSBlock = null;
                noLOSStartMs = -1L;
                if (client.options != null) client.options.attackKey.setPressed(false);
                LOGGER.info("[macro.metrics.robustness] mineSkip reason=not_visible step={} block={} elapsedMs={}",
                    currentStepIndex, blockPos, elapsedNoLos);
            }
            return;
        }
        noLOSBlock = null;
        noLOSStartMs = -1L;

        // Reach check against block AABB (not just center) to avoid false negatives on edge hits.
        double reachDist = player.getBlockInteractionRange();
        if (!isWithinMiningReach(eyePos, blockPos, reachDist)) {
            if (isMiningBlock) {
                if (client.options != null) client.options.attackKey.setPressed(true);
                MouseInputHelper.continueLeftClick(client);
            }
            return;
        }

        // Keep camera stable on the same target; only micro-correct when drift is detected.
        if (!aimLocked || !movementHelper.isLookingAt(player, visiblePoint, 2.5f)) {
            movementHelper.lookAt(player, visiblePoint, aimLocked ? 0.06f : 0.10f);
        }

        // Wait until camera is aligned
        if (!currentMacro.getConfig().isLockCrosshair()) {
            if (!movementHelper.isLookingAt(player, visiblePoint, 6.0f)) {
                return;
            }
        }

        // Lock aim as soon as we're aligned (don't require crosshairTarget — Hypixel
        // server-side rendering can make client.crosshairTarget disagree with our raycast)
        if (!aimLocked) {
            aimLocked = true;
            aimLockedAtMs = System.currentTimeMillis();
        }

        // Mine once dwell has elapsed
        boolean dwellPassed = aimLockedAtMs >= 0
                && System.currentTimeMillis() - aimLockedAtMs >= MINING_CLICK_DWELL_MS;
        if (!dwellPassed) return;

        if (client.options != null) client.options.attackKey.setPressed(true);
        MouseInputHelper.continueLeftClick(client);
        if (!isMiningBlock) {
            isMiningBlock = true;
            lastMineTime = System.currentTimeMillis();
        }

        // During mining: check if the block was replaced (Hypixel replaces blocks mid-mine).
        // Periodically re-verify the block is still the target type.
        if (isMiningBlock && System.currentTimeMillis() - lastMineTime > 200) {
            String currentBlockId = BlockUtils.getBlockId(world, blockPos);
            if (!currentBlockId.equals(target.getBlockId())) {
                miningSkipBlockReplaced++;
                target.setSkipped(true);
                blocksSkippedTotal++;
                currentBlockTargetIndex++;
                activeMiningTarget = null;
                isMiningBlock = false;
                aimLocked = false;
                aimLockedAtMs = -1L;
                noLOSBlock = null;
                noLOSStartMs = -1L;
                if (client.options != null) client.options.attackKey.setPressed(false);
                LOGGER.info("[macro.metrics.robustness] mineSkip reason=block_replaced step={} block={} expected={} actual={}",
                    currentStepIndex, blockPos, target.getBlockId(), currentBlockId);
                return;
            }
        }

        // Check if block is now air (mined successfully) — do NOT release the attack key
        // here so the next block starts breaking immediately without a click gap.
        if (BlockUtils.isAir(world, blockPos)) {
            target.setMined(true);
            blocksMinedTotal++;
            activeMiningTarget = null;
            isMiningBlock = false;
            aimLocked = false;  // Unlock aim for next block
            aimLockedAtMs = -1L;
            noLOSBlock = null;
            noLOSStartMs = -1L;
            if (client.options != null) client.options.attackKey.setPressed(false); // release immediately after mining
            miningDelayEndMs = System.currentTimeMillis() + HumanReactionTime.getMiningReactionTime(currentMacro.getConfig().getMiningDelay());
            if (!currentMacro.getConfig().isMineOnlyDefinedTargets()) {
                scanRadiusTargets(step, world, player); // player-centered rescan for newly exposed blocks
            }
            currentBlockTargetIndex++;
            return;
        }

        // Persistent mining timeout: long enough to finish legitimate breaks with latency.
        if (System.currentTimeMillis() - lastMineTime > MINING_TIMEOUT_MS) {
            miningSkipTimeout++;
            if (client.options != null) client.options.attackKey.setPressed(false);
            target.setSkipped(true);
            blocksSkippedTotal++;
            currentBlockTargetIndex++;
            activeMiningTarget = null;
            isMiningBlock = false;
            aimLocked = false;  // Unlock aim on timeout
            aimLockedAtMs = -1L;
            noLOSBlock = null;
            noLOSStartMs = -1L;
            LOGGER.info("[macro.metrics.robustness] mineSkip reason=mining_timeout step={} block={} elapsedMs={}",
                currentStepIndex, blockPos, System.currentTimeMillis() - lastMineTime);
        }
    }

    /**
     * Attacks the nearest valid entity using the same approach as {@link AutoAttackManager}:
     * SmoothAim for rotation, cooldown-progress gating, CPS limit, dwell timer,
     * LOS-required targeting, stuck detection (jump then skip), and lock-tick guards.
     */
    private void tickAttack(ClientPlayerEntity player, ClientWorld world, MinecraftClient client) {
        MacroConfig cfg = currentMacro.getConfig();
        float range = cfg.getAttackRange();

        // ── 1. Release stale / dead targets ────────────────────────────────────
        if (attackTarget != null) {
            if (attackTarget.isDead() || attackTarget.getHealth() <= 0) {
                clearAttackTarget(client);
                return;
            }
            // After minimum lock period, check range and LOS
            if (attackLockTicks >= MACRO_MIN_LOCK_TICKS) {
                double threshold = range + ATTACK_RELEASE_BUFFER;
                if (player.squaredDistanceTo(attackTarget) > threshold * threshold) {
                    clearAttackTarget(client);
                    return;
                }
                if (!attackHasLOS(client, player, attackTarget)) {
                    if (++attackNoLosTicks >= MACRO_MAX_NO_LOS_TICKS) {
                        attackLostLosDrops++;
                        LOGGER.info("[macro.metrics.robustness] attackDrop reason=lost_los target={} step={}",
                            attackTarget.getName().getString(), currentStepIndex);
                        clearAttackTarget(client);
                        return;
                    }
                } else {
                    attackNoLosTicks = 0;
                }
            }
        }

        // ── 2. Find new target — requires LOS ────────────────────────────────────
        if (attackTarget == null) {
            Box box = player.getBoundingBox().expand(range);
            List<LivingEntity> candidates = world.getEntitiesByClass(
                    LivingEntity.class, box, e -> {
                        if (e == player || e.isDead() || e.getHealth() <= 0) return false;
                        if (!attackHasLOS(client, player, e)) return false; // ignore hidden entities
                        // Always filter out irrelevant entity types
                        if (e instanceof net.minecraft.entity.player.PlayerEntity) return false;
                        if (e instanceof ArmorStandEntity) return false;
                        if (e.isInvisible()) return false;
                        // Skip entities in water or lava — chasing them would push player into fluid
                        if (e.isTouchingWater() || e.isInLava()) return false;
                        // If whitelist mode, only include whitelisted types
                        if (cfg.isAttackWhitelistOnly()) {
                            String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
                            return cfg.getAttackWhitelist().contains(id);
                        }
                        return true;
                    });
            if (candidates.isEmpty()) return;

            attackTarget = candidates.stream()
                    .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
                    .orElse(null);
            if (attackTarget == null) return;

            attackChaseStartMs    = System.currentTimeMillis();
            attackLockTicks       = 0;
            attackNoLosTicks      = 0;
            attackFirstOnTargetMs = -1L;
            attackChaseLastPos    = null;
            attackChaseStuckMs    = -1L;
            attackChaseJumped     = false;
            PathHandler pathHandler = MacroModClient.getPathHandler();
            if (pathHandler != null) pathHandler.cancelPath();
        }

        attackLockTicks++;

        // ── 3. Smooth aim via shared SmoothAim ───────────────────────────────
        var sa = MacroModClient.getSmoothAim();
        if (sa != null) sa.setTarget(bodyCenter(attackTarget));

        // ── 4. Movement + stuck detection ────────────────────────────────────
        // Use fixed melee reach distance of 2.5 blocks for consistency with AutoFishing
        double distSq   = player.squaredDistanceTo(attackTarget);
        double meleeSq  = MACRO_MELEE_REACH * MACRO_MELEE_REACH;

        if (distSq > meleeSq) {
            // Abort chase if it would require the player to enter water or lava
            if (player.isTouchingWater() || player.isInLava()) {
                LOGGER.info("[macro.metrics.robustness] attackDrop reason=fluid target={}",
                    attackTarget.getName().getString());
                clearAttackTarget(client);
                return;
            }

            // In elimination mode, attack chase is authoritative and must be allowed
            // even if previous state was MOVING (state tick is suspended while fighting).
            if (state != MacroState.MOVING || isEliminatingEnemies) {
                if (client.options != null) client.options.forwardKey.setPressed(true);
            }

            // Spam-click while chasing to keep attack cooldown primed
            {
                long now = System.currentTimeMillis();
                int effectiveCps = cfg.isRandomAttackCps() ? (7 + ATTACK_RAND.nextInt(5)) : cfg.getAttackCPS();
                long cpsIntervalMs = 1000L / Math.max(1, effectiveCps);
                if (lastSpamClickMs < 0 || now - lastSpamClickMs >= cpsIntervalMs) {
                    float cooldown = player.getAttackCooldownProgress(0f);
                    if (cooldown >= ATTACK_COOLDOWN_THRESHOLD && client.interactionManager != null) {
                        MouseInputHelper.leftClick(client);
                        lastSpamClickMs = now;
                    }
                }
            }

            // Stuck detection — track lateral movement progress
            Vec3d curPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            if (attackChaseLastPos == null) {
                attackChaseLastPos = curPos;
            } else {
                double moved = Math.sqrt(
                    Math.pow(curPos.x - attackChaseLastPos.x, 2) +
                    Math.pow(curPos.z - attackChaseLastPos.z, 2));
                if (moved > 0.15) {
                    // Making progress — reset stuck state
                    attackChaseLastPos = curPos;
                    attackChaseStuckMs = -1L;
                    if (attackChaseJumped && client.options != null) {
                        client.options.jumpKey.setPressed(false);
                        attackChaseJumped = false;
                    }
                } else {
                    // Not moving
                    if (attackChaseStuckMs < 0) attackChaseStuckMs = System.currentTimeMillis();
                    long stuckMs = System.currentTimeMillis() - attackChaseStuckMs;

                    if (stuckMs >= 1200) {
                        // Still stuck after a jump — skip this entity and try next
                        attackChaseStuckSkips++;
                        LOGGER.info("[macro.metrics.robustness] attackDrop reason=chase_stuck target={} stuckMs={}",
                            attackTarget.getName().getString(), stuckMs);
                        clearAttackTarget(client);
                        return;
                    } else if (stuckMs >= 700 && !attackChaseJumped) {
                        // In water: hold jump to swim to the surface; on land: single jump to dislodge
                        attackChaseJumpAttempts++;
                        LOGGER.info("[macro.metrics.robustness] attackChaseJumpAttempt target={} inWater={}",
                            attackTarget.getName().getString(), player.isTouchingWater());
                        if (client.options != null) client.options.jumpKey.setPressed(true);
                        attackChaseJumped = true;
                    }
                }
            }
        } else {
            // Within melee reach — only stop chasing if NOT path-following
            attackChaseStuckMs = -1L;
            attackChaseLastPos = null;
            if ((state != MacroState.MOVING || isEliminatingEnemies) && client.options != null) {
                client.options.forwardKey.setPressed(false);
                client.options.backKey.setPressed(false);
                client.options.leftKey.setPressed(false);
                client.options.rightKey.setPressed(false);
            }
            if (attackChaseJumped && client.options != null) {
                client.options.jumpKey.setPressed(false);
                attackChaseJumped = false;
            }

            // ── 5. Attack when aimed + cooldown ready + dwell elapsed + CPS ────
                // Only require angle check (6 degrees), don't require entity in exact crosshair center
            boolean lookingAtTarget = sa != null && sa.isOnTarget(player, 6f);
            long now = System.currentTimeMillis();
            int effectiveCps = cfg.isRandomAttackCps() ? (7 + ATTACK_RAND.nextInt(5)) : cfg.getAttackCPS();
            long cpsIntervalMs      = 1000L / Math.max(1, effectiveCps);
            boolean cpsReady        = now - lastAttackMs >= cpsIntervalMs;

            if (lookingAtTarget) {
                if (attackFirstOnTargetMs < 0) attackFirstOnTargetMs = System.currentTimeMillis();
            } else {
                attackFirstOnTargetMs = -1L;
            }
            boolean dwellReady = attackFirstOnTargetMs >= 0
                    && System.currentTimeMillis() - attackFirstOnTargetMs >= MACRO_ATTACK_DWELL_MS;

            if (lookingAtTarget && dwellReady && cpsReady && client.interactionManager != null) {
                MouseInputHelper.leftClick(client);
                lastAttackMs = System.currentTimeMillis();
                attackFirstOnTargetMs = -1L; // reset dwell after each click
            }
        }
    }

    /**
     * Line farm mode: hold left click and move in a snake pattern.
     * Locks crosshair at starting position.
     */
    private void tickLineFarming(ClientPlayerEntity player, ClientWorld world, MinecraftClient client) {
        if (!isLineFarming || lineFarmStartPos == null) {
            movementHelper.releaseAllInputs();
            state = MacroState.COMPLETED;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        // Lock crosshair at starting position
        if (lineFarmCrosshairPos != null) {
            movementHelper.lookAt(player, lineFarmCrosshairPos, 0.1f);
        }

        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        float playerYaw = player.getYaw();
        
        // Calculate movement direction based on pattern
        // Pattern: left → forward → right → forward → repeat
        Vec3d moveDir = null;
        BlockPos checkPos = null;
        
        switch (lineFarmDirection) {
            case 0: // Moving LEFT
                moveDir = new Vec3d(-Math.sin(Math.toRadians(playerYaw)), 0, Math.cos(Math.toRadians(playerYaw)));
                checkPos = player.getBlockPos().add(-1, 0, 0);
                break;
            case 1: // Moving FORWARD
                moveDir = new Vec3d(-Math.sin(Math.toRadians(playerYaw + 90)), 0, Math.cos(Math.toRadians(playerYaw + 90)));
                checkPos = player.getBlockPos().add(0, 0, -1);
                break;
            case 2: // Moving RIGHT
                moveDir = new Vec3d(-Math.sin(Math.toRadians(playerYaw + 180)), 0, Math.cos(Math.toRadians(playerYaw + 180)));
                checkPos = player.getBlockPos().add(1, 0, 0);
                break;
        }

        // Check if we hit a block in the current direction
        boolean hitBlock = checkPos != null && !BlockUtils.isAir(world, checkPos);
        
        if (hitBlock || lineFarmDistance >= lineFarmWidth) {
            // Switch direction
            lineFarmDistance = 0;
            lineFarmDirection = (lineFarmDirection + 1) % 3;
            if (lineFarmDirection == 0) {
                // Completed one full pattern, move forward for next sweep
                lineFarmDirection = 1; // Forward
            }
        }

        // Move in current direction
        if (moveDir != null) {
            Vec3d targetPos = playerPos.add(moveDir.multiply(0.5));
            BlockPos targetBlock = BlockPos.ofFloored(targetPos);
            movementHelper.moveTowards(player, targetBlock);
            lineFarmDistance++;
        }

        // Optionally add attack cooldown
        long now = System.currentTimeMillis();
        long attackCooldown = 100; // 10 clicks per second (more like mining speed)
        if (now - lastAttackMs >= attackCooldown) {
            MouseInputHelper.continueLeftClick(mc);
            lastAttackMs = now;
        }
    }

    /**
     * Scans for additional blocks of the same type(s) as the step's existing targets,
     * within a 4.5-block sphere of the <em>player's current position</em>.
     *
     * <ul>
     *   <li>Loop radius capped at 5 → max 1 331 iterations (41³ old → 1 331 new).</li>
     *   <li>Sphere filter (dx²+dy²+dz² ≤ PLAYER_REACH_RADIUS_SQ) eliminates ~75 %.</li>
     *   <li>Discovered candidates are sorted nearest-first before appending, so the
     *       linear cursor in {@link #tickMining} will process closest blocks first.</li>
     *   <li>Caller tracks {@link #lastScanPlayerPos} for dynamic re-trigger logic.</li>
     * </ul>
     */
    private void scanRadiusTargets(MacroStep step, ClientWorld world, ClientPlayerEntity player) {
        // Collect the block IDs we are looking for (from explicitly listed targets)
        java.util.Set<String> wantedIds = new HashSet<>();
        for (BlockTarget t : step.getTargets()) {
            wantedIds.add(t.getBlockId());
        }
        if (wantedIds.isEmpty()) return;

        // Build a set of positions already in the list to avoid duplicates
        java.util.Set<BlockPos> knownPositions = new HashSet<>();
        for (BlockTarget t : step.getTargets()) {
            knownPositions.add(t.getPos());
        }

        BlockPos center = player.getBlockPos();
        Vec3d eyePos = player.getEyePos();
        int scanRadius = RADIUS_SCAN_RADIUS; // tighter horizontal loop bound for lower scan cost
        int scanVertical = RADIUS_SCAN_VERTICAL;

        // Collect candidates into a temp list for distance-sorting
        java.util.List<BlockTarget> discovered = new java.util.ArrayList<>();
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dy = -scanVertical; dy <= scanVertical; dy++) {
                for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                    // Sphere filter: skip if outside 4.5-block radius
                    if ((double)(dx*dx + dy*dy + dz*dz) > PLAYER_REACH_RADIUS_SQ) continue;
                    BlockPos candidate = center.add(dx, dy, dz);
                    if (knownPositions.contains(candidate)) continue;
                    if (BlockUtils.isAir(world, candidate)) continue;
                    String id = BlockUtils.getBlockId(world, candidate);
                    if (wantedIds.contains(id)) {
                        discovered.add(new BlockTarget(candidate, id));
                        knownPositions.add(candidate); // prevent dups within this scan
                    }
                }
            }
        }

        // Sort by 3D distance from eye (nearest first) so linear cursor mines near blocks first
        discovered.sort(java.util.Comparator.comparingDouble(
            bt -> eyePos.squaredDistanceTo(Vec3d.ofCenter(bt.getPos()))));

        for (BlockTarget bt : discovered) {
            step.addTarget(bt);
        }

        lastScanPlayerPos = center;
        lastRadiusScanMs = System.currentTimeMillis();
        if (!discovered.isEmpty()) {
            LOGGER.info("[macro.metrics.robustness] radiusScan step={} discovered={} totalTargets={}",
                currentStepIndex, discovered.size(), step.getTargets().size());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if there are any attackable entities within attack range AND with
     * line-of-sight. Mobs behind walls are completely ignored — they must NOT
     * block movement or trigger elimination mode.
     */
    private boolean hasEntitiesInRange(ClientPlayerEntity player, ClientWorld world, MacroConfig cfg) {
        MinecraftClient client = MinecraftClient.getInstance();
        float range = cfg.getAttackRange();
        Box box = player.getBoundingBox().expand(range);
        List<LivingEntity> candidates = world.getEntitiesByClass(
            LivingEntity.class, box, e -> {
                if (e == player || e.isDead() || e.getHealth() <= 0) return false;
                // Always filter out irrelevant entity types
                if (e instanceof net.minecraft.entity.player.PlayerEntity) return false;
                if (e instanceof ArmorStandEntity) return false;
                if (e.isInvisible()) return false;
                // CRITICAL: require line-of-sight — ignore mobs behind walls
                if (!attackHasLOS(client, player, e)) return false;
                if (cfg.isAttackWhitelistOnly()) {
                    String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
                    return cfg.getAttackWhitelist().contains(id);
                }
                return true;
            }
        );
        return !candidates.isEmpty();
    }

    /**
     * Returns true when the player's XZ position is within 0.40 blocks of the
     * block center. 0.40 > WAYPOINT_ARRIVE_RADIUS (0.35), so players arriving
     * via path-following always pass immediately — no jitter. Players arriving
     * via the broad isArrived check drift gently to center without oscillating.
     */
    private static boolean isCenteredOnBlock(ClientPlayerEntity player, BlockPos dest) {
        double dx = player.getX() - (dest.getX() + 0.5);
        double dz = player.getZ() - (dest.getZ() + 0.5);
        return dx * dx + dz * dz < 0.40 * 0.40;
    }

    private void advanceToNextStep() {
        currentStepIndex++;
        radiusScanDone = false;
        lastScanPlayerPos = null;
        lastRadiusScanMs = -1L;
        aimLocked = false;  // Unlock aim for next step
        aimLockedAtMs = -1L;

        if (currentStepIndex >= currentMacro.getSteps().size()) {
            if (currentMacro.getConfig().isLoop()) {
                // Reset and loop
                currentMacro.reset();
                currentStepIndex = 0;
                if (precomputedPaths != null) {
                    for (int i = 0; i < precomputedPaths.size(); i++) {
                        precomputedPaths.set(i, null);
                    }
                }
                state = MacroState.PATHFINDING;
                LOGGER.info("[macro.metrics.lifecycle] macroLoopRestart step={} mined={} skipped={}",
                    currentStepIndex, blocksMinedTotal, blocksSkippedTotal);
            } else {
                movementHelper.releaseAllInputs();
                state = MacroState.COMPLETED;
                logMetricsSummary("completed");
                printStats();
                LOGGER.info("[macro.metrics.lifecycle] macroCompleted name={} mined={} skipped={} distance={}",
                    currentMacro.getName(), blocksMinedTotal, blocksSkippedTotal, totalDistance);
            }
        } else {
            currentBlockTargetIndex = 0;
            isMiningBlock = false;
            aimLocked = false;  // Unlock aim for new step
            aimLockedAtMs = -1L;
            state = MacroState.PATHFINDING;
        }
    }

    private void handleDanger(ClientPlayerEntity player) {
        if (PlayerUtils.isHealthLow(player)) {
            sendMessage("macromod.chat.danger_health", Formatting.RED);
        } else if (PlayerUtils.hasHostileNearby(player, 8.0)) {
            sendMessage("macromod.chat.danger_mob", Formatting.RED);
        }
        pause();
    }

    private MacroStep getCurrentStep() {
        if (currentMacro == null || currentStepIndex >= currentMacro.getSteps().size()) {
            return null;
        }
        return currentMacro.getSteps().get(currentStepIndex);
    }

    private void printStats() {
        long elapsed = System.currentTimeMillis() - startTime;
        float seconds = elapsed / 1000.0f;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.translatable("macromod.chat.stats_header").formatted(Formatting.GOLD), false);
            client.player.sendMessage(Text.translatable("macromod.chat.stats_mined", blocksMinedTotal).formatted(Formatting.WHITE), false);
            client.player.sendMessage(Text.translatable("macromod.chat.stats_skipped", blocksSkippedTotal).formatted(Formatting.GRAY), false);
            client.player.sendMessage(Text.translatable("macromod.chat.stats_time", seconds).formatted(Formatting.WHITE), false);
            client.player.sendMessage(Text.translatable("macromod.chat.stats_distance", totalDistance).formatted(Formatting.WHITE), false);
        }
    }

    private void resetRunMetrics() {
        lastMetricsSnapshotMs = 0L;

        tickCount = 0L;
        tickTotalNs = 0L;
        tickMaxNs = 0L;

        precomputedPathQueries = 0L;
        precomputedPathSuccess = 0L;
        precomputedPathNodesTotal = 0L;
        precomputedPathTotalNs = 0L;
        precomputedPathMaxNs = 0L;

        livePathQueries = 0L;
        livePathSuccess = 0L;
        livePathNodesTotal = 0L;
        livePathTotalNs = 0L;
        livePathMaxNs = 0L;

        repathCount = 0L;
        stuckRecoveries = 0L;
        navigationTimeouts = 0L;
        chunkWaitEvents = 0L;
        chunkWaitTimeoutSkips = 0L;
        chunkWaitTotalMs = 0L;
        chunkWaitStartMs = -1L;

        autoAttackPauseCount = 0L;
        autoAttackPauseTotalMs = 0L;
        autoAttackPauseStartMs = -1L;

        eliminationModeCount = 0L;
        eliminationModeTotalMs = 0L;
        eliminationModeStartMs = -1L;

        miningSkipUnripe = 0L;
        miningSkipRange = 0L;
        miningSkipNoLos = 0L;
        miningSkipBlockChanged = 0L;
        miningSkipBlockReplaced = 0L;
        miningSkipTimeout = 0L;

        attackLostLosDrops = 0L;
        attackChaseStuckSkips = 0L;
        attackChaseJumpAttempts = 0L;

        metricsSummaryLogged = false;
    }

    private List<BlockPos> findPathWithMetrics(
            BlockPos from,
            BlockPos goal,
            ClientWorld world,
            long timeoutMs,
            boolean precompute,
            int stepIndex) {
        long pathStartNs = System.nanoTime();
        List<BlockPos> path = oringoPathModule.findPath(
            from,
            goal,
            world,
            currentMacro.getConfig().isOnlyGround(),
            MacroModClient.getConfigManager().getConfig().getMaxPathNodes(),
            timeoutMs,
            MacroModClient.getPathHandler(),
            fallbackPathFinder
        );
        long elapsedNs = System.nanoTime() - pathStartNs;

        int nodes = path != null ? path.size() : 0;
        boolean success = nodes > 0;

        if (precompute) {
            precomputedPathQueries++;
            precomputedPathTotalNs += elapsedNs;
            if (elapsedNs > precomputedPathMaxNs) {
                precomputedPathMaxNs = elapsedNs;
            }
            if (success) {
                precomputedPathSuccess++;
                precomputedPathNodesTotal += nodes;
            }
        } else {
            livePathQueries++;
            livePathTotalNs += elapsedNs;
            if (elapsedNs > livePathMaxNs) {
                livePathMaxNs = elapsedNs;
            }
            if (success) {
                livePathSuccess++;
                livePathNodesTotal += nodes;
            }
        }

        LOGGER.info("[macro.metrics.path] mode={} step={} from={} goal={} success={} nodes={} durationMs={} timeoutMs={}",
            precompute ? "precompute" : "live",
            stepIndex,
            from,
            goal,
            success,
            nodes,
            elapsedNs / 1_000_000.0,
            timeoutMs);

        return path;
    }

    private void maybeLogMetricsSnapshot() {
        if (startTime <= 0 || metricsSummaryLogged) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastMetricsSnapshotMs > 0 && now - lastMetricsSnapshotMs < METRICS_SNAPSHOT_INTERVAL_MS) {
            return;
        }
        lastMetricsSnapshotMs = now;

        long elapsedMs = now - startTime;
        long totalPathQueries = precomputedPathQueries + livePathQueries;
        long totalPathSuccess = precomputedPathSuccess + livePathSuccess;
        String macroName = currentMacro != null ? currentMacro.getName() : "LineFarm";
        int totalSteps = currentMacro != null ? currentMacro.getSteps().size() : 0;

        LOGGER.info("[macro.metrics.snapshot] macro={} elapsedMs={} state={} step={}/{} mined={} skipped={} pathSuccess={}/{} preAvgMs={} liveAvgMs={} repath={} stuckRecoveries={} navTimeouts={} chunkWaitMs={} tickAvgUs={} tickMaxUs={}",
            macroName,
            elapsedMs,
            state,
            currentStepIndex,
            totalSteps,
            blocksMinedTotal,
            blocksSkippedTotal,
            totalPathSuccess,
            totalPathQueries,
            precomputedPathQueries > 0 ? (precomputedPathTotalNs / 1_000_000.0) / precomputedPathQueries : 0.0,
            livePathQueries > 0 ? (livePathTotalNs / 1_000_000.0) / livePathQueries : 0.0,
            repathCount,
            stuckRecoveries,
            navigationTimeouts,
            chunkWaitTotalMs,
            tickCount > 0 ? (tickTotalNs / 1_000.0) / tickCount : 0.0,
            tickMaxNs / 1_000.0);
    }

    private void logMetricsSummary(String reason) {
        if (metricsSummaryLogged || startTime <= 0) {
            return;
        }

        long now = System.currentTimeMillis();

        if (chunkWaitStartMs >= 0) {
            chunkWaitTotalMs += now - chunkWaitStartMs;
            chunkWaitStartMs = -1L;
        }
        if (autoAttackPauseStartMs >= 0) {
            autoAttackPauseTotalMs += now - autoAttackPauseStartMs;
            autoAttackPauseStartMs = -1L;
        }
        if (eliminationModeStartMs >= 0) {
            eliminationModeTotalMs += now - eliminationModeStartMs;
            eliminationModeStartMs = -1L;
        }

        long elapsedMs = now - startTime;
        long totalPathQueries = precomputedPathQueries + livePathQueries;
        long totalPathSuccess = precomputedPathSuccess + livePathSuccess;
        long totalPathCostNs = precomputedPathTotalNs + livePathTotalNs;
        String macroName = currentMacro != null ? currentMacro.getName() : "LineFarm";
        int totalSteps = currentMacro != null ? currentMacro.getSteps().size() : 0;
        int doneSteps = totalSteps > 0 ? Math.min(currentStepIndex, totalSteps) : 0;

        LOGGER.info("[macro.metrics.summary] reason={} macro={} elapsedMs={} stepsDone={}/{} mined={} skipped={} distance={} pathSuccess={}/{} preAvgMs={} preMaxMs={} liveAvgMs={} liveMaxMs={} totalPathCostMs={} repath={} stuckRecoveries={} navTimeouts={} chunkWaitEvents={} chunkWaitMs={} chunkWaitTimeoutSkips={} autoAttackPauseCount={} autoAttackPauseMs={} eliminationCount={} eliminationMs={} attackLostLosDrops={} attackChaseStuckSkips={} attackChaseJumpAttempts={} miningSkipUnripe={} miningSkipRange={} miningSkipNoLos={} miningSkipBlockChanged={} miningSkipBlockReplaced={} miningSkipTimeout={} tickCount={} tickAvgUs={} tickMaxUs={} tickCostPerSecondMs={}",
            reason,
            macroName,
            elapsedMs,
            doneSteps,
            totalSteps,
            blocksMinedTotal,
            blocksSkippedTotal,
            totalDistance,
            totalPathSuccess,
            totalPathQueries,
            precomputedPathQueries > 0 ? (precomputedPathTotalNs / 1_000_000.0) / precomputedPathQueries : 0.0,
            precomputedPathMaxNs / 1_000_000.0,
            livePathQueries > 0 ? (livePathTotalNs / 1_000_000.0) / livePathQueries : 0.0,
            livePathMaxNs / 1_000_000.0,
            totalPathCostNs / 1_000_000.0,
            repathCount,
            stuckRecoveries,
            navigationTimeouts,
            chunkWaitEvents,
            chunkWaitTotalMs,
            chunkWaitTimeoutSkips,
            autoAttackPauseCount,
            autoAttackPauseTotalMs,
            eliminationModeCount,
            eliminationModeTotalMs,
            attackLostLosDrops,
            attackChaseStuckSkips,
            attackChaseJumpAttempts,
            miningSkipUnripe,
            miningSkipRange,
            miningSkipNoLos,
            miningSkipBlockChanged,
            miningSkipBlockReplaced,
            miningSkipTimeout,
            tickCount,
            tickCount > 0 ? (tickTotalNs / 1_000.0) / tickCount : 0.0,
            tickMaxNs / 1_000.0,
            elapsedMs > 0 ? (tickTotalNs / 1_000_000.0) / (elapsedMs / 1000.0) : 0.0);

        metricsSummaryLogged = true;
    }

    /**
     * Returns the world-space centre of the best minable face for {@code pos}.
     *
     * Faces are ranked by priority based on the vertical offset between the block
     * and the player, then each face is checked for obstruction: if the block
     * immediately adjacent to that face is solid, it is skipped in favour of the
     * next candidate. This prevents the crosshair from being aimed at a face that
     * is physically inaccessible (e.g. ore block below the player with stone on
     * top of it — the top face is flush against the stone so line-of-sight fails;
     * a side face should be used instead).
     *
     * Priority order:
     *   block below player  → top → nearest side → second side → bottom
     *   block same/+1 level → nearest side → second side → top → bottom
     *   block 2+ above      → bottom → nearest side → second side → top
     */
    /**
     * Returns a point on the target block's surface that is actually visible from
     * the player's eyes.  Faces are tried in priority order (based on vertical offset),
     * and for each face 5 candidate points (centre + 4 inset corners) are raycast-tested.
     * The first candidate whose raycast hits the target block is returned.
     *
     * <p>Falls back to the centre of the nearest side face if no candidate passes.</p>
     */
    private static Vec3d visibleFaceCentre(Vec3d eye, BlockPos pos, int playerBlockY,
                                            net.minecraft.client.world.ClientWorld world,
                                            net.minecraft.entity.Entity entity) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        int dy = pos.getY() - playerBlockY;

        // XZ direction from block centre to eye
        double dx = eye.x - cx;
        double dz = eye.z - cz;
        boolean xDom = Math.abs(dx) >= Math.abs(dz);
        int sx = (int) Math.signum(dx); // horizontal offset toward player in X
        int sz = (int) Math.signum(dz); // horizontal offset toward player in Z

        // Six face centres (outer surface of block)
        Vec3d fTop    = new Vec3d(cx, pos.getY() + 1.0, cz);
        Vec3d fBottom = new Vec3d(cx, (double) pos.getY(), cz);
        Vec3d fSide1  = xDom ? new Vec3d(cx + sx * 0.5, cy, cz)      // nearest horizontal
                             : new Vec3d(cx, cy, cz + sz * 0.5);
        Vec3d fSide2  = xDom ? new Vec3d(cx, cy, cz + sz * 0.5)      // second horizontal
                             : new Vec3d(cx + sx * 0.5, cy, cz);

        // Adjacent block that would obstruct each face
        BlockPos aTop    = pos.up();
        BlockPos aBottom = pos.down();
        BlockPos aSide1  = xDom ? pos.add(sx, 0, 0) : pos.add(0, 0, sz);
        BlockPos aSide2  = xDom ? pos.add(0, 0, sz) : pos.add(sx, 0, 0);

        // Priority list keyed on vertical offset
        Vec3d[]    faces;
        BlockPos[] adjs;
        if (dy < 0) {
            faces = new Vec3d[]    { fTop,  fSide1, fSide2, fBottom };
            adjs  = new BlockPos[] { aTop,  aSide1, aSide2, aBottom };
        } else if (dy >= 2) {
            faces = new Vec3d[]    { fBottom, fSide1, fSide2, fTop  };
            adjs  = new BlockPos[] { aBottom, aSide1, aSide2, aTop  };
        } else {
            faces = new Vec3d[]    { fSide1, fSide2, fTop,  fBottom };
            adjs  = new BlockPos[] { aSide1, aSide2, aTop,  aBottom };
        }

        // For each face in priority order, test candidate points with raycasts
        double inset = 0.1; // pixels from true edge to avoid precision issues
        for (int i = 0; i < faces.length; i++) {
            Vec3d[] candidates = buildFaceCandidates(pos, faces[i], adjs[i], inset);
            for (Vec3d candidate : candidates) {
                net.minecraft.world.RaycastContext ctx = new net.minecraft.world.RaycastContext(
                        eye, candidate,
                        net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE,
                        entity);
                net.minecraft.util.hit.BlockHitResult hit = world.raycast(ctx);
                // Accept if the ray reached the target block
                if (hit.getBlockPos().equals(pos)) {
                    return candidate;
                }
            }
        }
        // All candidates occluded
        return null;
    }

    /**
     * Reach test against the block's AABB (1x1x1), not just its center.
     * This avoids false negatives when the visible face is in reach but the center is not.
     */
    private static boolean isWithinMiningReach(Vec3d eyePos, BlockPos blockPos, double reachDistance) {
        double minX = blockPos.getX();
        double minY = blockPos.getY();
        double minZ = blockPos.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        double closestX = Math.max(minX, Math.min(eyePos.x, maxX));
        double closestY = Math.max(minY, Math.min(eyePos.y, maxY));
        double closestZ = Math.max(minZ, Math.min(eyePos.z, maxZ));

        double dx = eyePos.x - closestX;
        double dy = eyePos.y - closestY;
        double dz = eyePos.z - closestZ;
        return dx * dx + dy * dy + dz * dz <= reachDistance * reachDistance;
    }

    /**
     * Generates 5 candidate aim points on a block face: centre + 4 inset corners.
     * The face orientation is derived from the relationship between pos and adjPos.
     */
    private static Vec3d[] buildFaceCandidates(BlockPos pos, Vec3d faceCenter,
                                                BlockPos adjPos, double inset) {
        int fdx = adjPos.getX() - pos.getX();
        int fdy = adjPos.getY() - pos.getY();
        int fdz = adjPos.getZ() - pos.getZ();

        double px = pos.getX(), py = pos.getY(), pz = pos.getZ();

        if (fdy != 0) {
            // Horizontal face (top or bottom)
            double y = faceCenter.y;
            return new Vec3d[] {
                faceCenter,
                new Vec3d(px + inset,     y, pz + inset),
                new Vec3d(px + 1 - inset, y, pz + inset),
                new Vec3d(px + inset,     y, pz + 1 - inset),
                new Vec3d(px + 1 - inset, y, pz + 1 - inset),
            };
        } else if (fdx != 0) {
            // X-axis face (east or west)
            double x = faceCenter.x;
            return new Vec3d[] {
                faceCenter,
                new Vec3d(x, py + inset,     pz + inset),
                new Vec3d(x, py + 1 - inset, pz + inset),
                new Vec3d(x, py + inset,     pz + 1 - inset),
                new Vec3d(x, py + 1 - inset, pz + 1 - inset),
            };
        } else {
            // Z-axis face (north or south)
            double z = faceCenter.z;
            return new Vec3d[] {
                faceCenter,
                new Vec3d(px + inset,     py + inset,     z),
                new Vec3d(px + 1 - inset, py + inset,     z),
                new Vec3d(px + inset,     py + 1 - inset, z),
                new Vec3d(px + 1 - inset, py + 1 - inset, z),
            };
        }
    }

    /** Returns a random point within a bounding box (biased toward center vertically). */
    private static Vec3d randomPointInBox(net.minecraft.util.math.Box box) {
        double x = box.minX + ATTACK_RAND.nextDouble() * (box.maxX - box.minX);
        // Vertical: avoid feet (bottom 20%) and head (top 15%) — aim at body
        double yMin = box.minY + (box.maxY - box.minY) * 0.20;
        double yMax = box.maxY - (box.maxY - box.minY) * 0.15;
        double y = yMin + ATTACK_RAND.nextDouble() * (yMax - yMin);
        double z = box.minZ + ATTACK_RAND.nextDouble() * (box.maxZ - box.minZ);
        return new Vec3d(x, y, z);
    }

    /** Releases the active attack target and resets all associated state. */
    private void clearAttackTarget(MinecraftClient client) {
        attackTarget          = null;
        attackChaseStartMs    = -1L;
        attackLockTicks       = 0;
        attackNoLosTicks      = 0;
        attackFirstOnTargetMs = -1L;
        attackChaseLastPos    = null;
        attackChaseStuckMs    = -1L;
        attackChaseJumped     = false;
        if (client != null && client.options != null) {
            client.options.forwardKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
            client.options.attackKey.setPressed(false);
        }
    }

    /** True if there is an unobstructed line-of-sight from the player's eyes to the entity's body center. */
    private boolean attackHasLOS(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
        if (client.world == null) return false;
        Vec3d eyes   = player.getEyePos();
        Vec3d centre = bodyCenter(target);
        net.minecraft.world.RaycastContext ctx = new net.minecraft.world.RaycastContext(
                eyes, centre,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                (net.minecraft.entity.Entity) player);
        return client.world.raycast(ctx).getType() == HitResult.Type.MISS;
    }

    /** The entity's body center — midpoint between feet and head. */
    private static Vec3d bodyCenter(LivingEntity e) {
        return new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ());
    }

    private void sendMessage(String key, Formatting color, Object... args) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.translatable(key, args).formatted(color), false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Public accessors
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the current execution state.
     */
    public MacroState getState() {
        return state;
    }

    /** Returns the active navigation path (waypoints), or null if not navigating. */
    public List<BlockPos> getCurrentPath() {
        return currentPath;
    }

    /** Returns the index of the next waypoint in the active path. */
    public int getCurrentPathIndex() {
        return currentPathIndex;
    }

    /**
     * Returns the macro currently being executed.
     */
    public Macro getCurrentMacro() {
        return currentMacro;
    }

    /**
     * Returns the current step index (0-based).
     */
    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    /**
     * Returns the total number of steps in the current macro.
     */
    public int getTotalSteps() {
        return currentMacro != null ? currentMacro.getSteps().size() : 0;
    }

    /**
     * Returns the number of blocks processed in the current step.
     */
    public int getBlocksProcessedInStep() {
        MacroStep step = getCurrentStep();
        return step != null ? step.getProcessedCount() : 0;
    }

    /**
     * Returns the total number of block targets in the current step.
     */
    public int getTotalBlocksInStep() {
        MacroStep step = getCurrentStep();
        return step != null ? step.getTargets().size() : 0;
    }

    /**
     * Returns true if a macro is actively running (not idle/completed/error).
     */
    public boolean isRunning() {
        return state != MacroState.IDLE && state != MacroState.COMPLETED && state != MacroState.ERROR;
    }

    /**
     * Calculates a block position that is a specified distance away from the entity,
     * positioned in the direction away from the player. This allows the player to
     * maintain a safe attack distance without running directly into the entity.
     *
     * @param player       The player entity
     * @param entity       The target entity
     * @param distanceBlocks The desired distance to maintain (default: 3.0)
     * @return A BlockPos that is the intercept point away from the entity
     */
    private BlockPos getInterceptPositionAwayFromEntity(ClientPlayerEntity player, LivingEntity entity, double distanceBlocks) {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());

        // Calculate direction from entity to player
        Vec3d directionToPlayer = playerPos.subtract(entityPos);
        double distanceToPlayer = directionToPlayer.length();

        if (distanceToPlayer < 0.1) {
            // Player and entity are at same position, default backwards
            return entity.getBlockPos().add(0, 0, 0);
        }

        // Normalize the direction
        Vec3d normalizedDirection = directionToPlayer.normalize();

        // Calculate intercept position: entity position + (normalized direction * distance)
        Vec3d interceptPos = entityPos.add(normalizedDirection.multiply(distanceBlocks));

        return BlockPos.ofFloored(interceptPos);
    }

    /**
     * Gets all nearby mobs within the specified range of the player.
     *
     * @param player     The player entity
     * @param world      The client world
     * @param rangeBlocks Maximum distance in blocks
     * @return A list of all living entities within range (excluding the player)
     */
    private List<LivingEntity> getNearbyMobs(ClientPlayerEntity player, ClientWorld world, float rangeBlocks) {
        if (player == null || world == null) return new ArrayList<>();
        
        Box searchBox = player.getBoundingBox().expand(rangeBlocks);
        return world.getEntitiesByClass(
            LivingEntity.class,
            searchBox,
            entity -> entity != player 
                    && !entity.isDead() 
                    && entity.getHealth() > 0
                    && !(entity instanceof net.minecraft.entity.player.PlayerEntity)
                    && !(entity instanceof ArmorStandEntity)
                    && !entity.isInvisible()
        );
    }

    /**
     * Gets nearby mobs of specific entity types.
     *
     * @param player        The player entity
     * @param world         The client world
     * @param rangeBlocks   Maximum distance in blocks
     * @param entityTypeIds List of entity IDs to match (e.g., "minecraft:zombie", "minecraft:creeper")
     * @return A list of matching entities within range
     */
    private List<LivingEntity> getNearbyMobsOfType(
            ClientPlayerEntity player,
            ClientWorld world,
            float rangeBlocks,
            List<String> entityTypeIds) {
        if (player == null || world == null || entityTypeIds == null || entityTypeIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<LivingEntity> nearby = getNearbyMobs(player, world, rangeBlocks);
        List<LivingEntity> filtered = new ArrayList<>();

        for (LivingEntity entity : nearby) {
            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (entityTypeIds.contains(entityId)) {
                filtered.add(entity);
            }
        }

        return filtered;
    }

    /**
     * Counts mobs in the given area.
     *
     * @param player     The player entity
     * @param world      The client world
     * @param rangeBlocks Maximum distance in blocks
     * @return The number of mobs within range
     */
    private int countMobsInArea(ClientPlayerEntity player, ClientWorld world, float rangeBlocks) {
        return getNearbyMobs(player, world, rangeBlocks).size();
    }

    /**
     * Gets the priority/threat level of a mob type for attack ordering.
     * Higher values = higher priority to attack.
     *
     * @param entity The living entity to evaluate
     * @return Priority level (0 = ignore, 1 = low, 2 = medium, 3 = high)
     */
    private int getMobTargetPriority(LivingEntity entity) {
        if (entity == null) return 0;

        String entityTypeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();

        // Immediate threats (attack first)
        if (entityTypeId.equals("minecraft:creeper") ||
            entityTypeId.equals("minecraft:enderman") ||
            entityTypeId.equals("minecraft:wither")) {
            return 3; // Highest priority
        }

        // Common hostile mobs
        if (entityTypeId.equals("minecraft:zombie") ||
            entityTypeId.equals("minecraft:skeleton") ||
            entityTypeId.equals("minecraft:spider") ||
            entityTypeId.equals("minecraft:cave_spider") ||
            entityTypeId.equals("minecraft:zombie_pigman") ||
            entityTypeId.equals("minecraft:blazes") ||
            entityTypeId.equals("minecraft:ghast") ||
            entityTypeId.equals("minecraft:slime")) {
            return 2; // Medium priority
        }

        // Players (only attack if aggro)
        if (entityTypeId.equals("minecraft:player")) {
            return 2; // Medium priority, configurable
        }

        // Low priority threats
        if (entityTypeId.equals("minecraft:bee") ||
            entityTypeId.equals("minecraft:iron_golem")) {
            return 1; // Low priority (usually harmless/helpful)
        }

        return 0; // Ignore others
    }

    /**
     * Checks if a mob is hostile/should be attacked.
     *
     * @param entity The living entity to check
     * @return true if the entity is a hostile mob or player, false otherwise
     */
    private boolean isHostileMob(LivingEntity entity) {
        return getMobTargetPriority(entity) > 0;
    }
}
