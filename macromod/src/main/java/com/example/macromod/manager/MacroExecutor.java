package com.example.macromod.manager;

import com.example.macromod.MacroModClient;
import com.example.macromod.manager.AutoAttackManager;
import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.model.*;
import com.example.macromod.pathfinding.MovementHelper;
import com.example.macromod.pathfinding.PathHandler;
import com.example.macromod.pathfinding.PathFinder;
import com.example.macromod.pathfinding.SmoothAim;
import com.example.macromod.util.HumanReactionTime;

import java.util.HashSet;
import com.example.macromod.pathfinding.goal.ExactGoal;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService pathfindingExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "macromod-pathfinding");
        t.setDaemon(true);
        return t;
    });

    // ─── Debug throttle ─────────────────────────────────────────
    private long lastDebugMs = 0L;
    private static final long DEBUG_INTERVAL_MS = 500L; // log at most every 500 ms
    private static final double LOOKAHEAD_CORNER_BLEND = 0.20;
    private static final double LOOKAHEAD_STRAIGHT_BLEND = 0.62;
    private static final double LOOKAHEAD_DEFAULT_BLEND = 0.45;
    private static final long MOVING_STUCK_TIMEOUT_MS = 4500L;
    private static final double MOVING_MIN_PROGRESS = 0.35;

    // ─── End-of-path centering ──────────────────────────────────
    /** True when at destination but still centering before MINING */
    private boolean isWaitingToCenter = false;

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
    /** True while the background thread is computing a path for precomputeIndex. */
    private volatile boolean precomputeBusy = false;
    /** True while the background thread is pipelining the NEXT step's path. */
    private volatile boolean pipelineBusy = false;
    /** The step index currently being pipelined (-1 = none). */
    private volatile int pipelineStepIndex = -1;

    // ─── Mining ─────────────────────────────────────────────────
    private boolean isMiningBlock;
    private long miningDelayEndMs;
    private boolean aimLocked = false;  // When true, don't re-aim; maintain current crosshair lock
    /** System time when aimLocked first became true. -1 when not locked. */
    private long aimLockedAtMs = -1L;
    /** Milliseconds to wait after crosshair confirms on block before pressing the attack key. */
    private static final long MINING_CLICK_DWELL_MS = 60L;
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
    private static final int   MACRO_MAX_NO_LOS_TICKS    = 3;
    private static final float ATTACK_RELEASE_BUFFER     = 1.5f;
    /** Macro melee reach distance in blocks (matching AutoFishing close mode). */
    private static final float MACRO_MELEE_REACH         = 2.5f;
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
    private long attackLastJumpMs = -1L;

    // ─── Entity elimination mode (attackDanger) ─────────────────
    private boolean isEliminatingEnemies = false;

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
    private static final long STRAFE_GIVE_UP_MS = 500L;

    // ─── Chunk loading ──────────────────────────────────────────
    private long chunkWaitStartMs = -1L;
    // ─── Radius scan ───────────────────────────────────────────────
    /** Set once when entering the MINING state for a step; cleared on step advance. */
    private boolean radiusScanDone = false;
    /** Player-centered scan sphere radius (matches interaction range). */
    private static final double PLAYER_REACH_RADIUS    = 4.5;
    private static final double PLAYER_REACH_RADIUS_SQ = PLAYER_REACH_RADIUS * PLAYER_REACH_RADIUS;
    /** Re-trigger scan when player moves this far (squared) from the last scan origin. */
    private static final double RESCAN_MOVE_THRESHOLD_SQ = 2.0 * 2.0;
    /** Player block position at the last scanRadiusTargets call — drives dynamic rescan. */
    private BlockPos lastScanPlayerPos = null;
    // ─── Statistics ─────────────────────────────────────────────
    private int blocksMinedTotal;
    private int blocksSkippedTotal;
    private long startTime;
    private double totalDistance;
    private Vec3d lastDistCheckPos;

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
        LOGGER.info("MacroExecutor initialized");
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

        // Pre-compute paths for all steps so navigation never stalls between steps
        precomputedPaths = new ArrayList<>(currentMacro.getSteps().size());
        for (int i = 0; i < currentMacro.getSteps().size(); i++) {
            precomputedPaths.add(null);
        }
        radiusScanDone = false;
        MinecraftClient startClient = MinecraftClient.getInstance();
        precomputeFromPos = startClient.player != null ? startClient.player.getBlockPos() : null;
        precomputeIndex = 0;
        state = MacroState.PRECOMPUTING;
        
        LOGGER.info("Starting macro '{}' (loop={})", macro.getName(), macro.getConfig().isLoop());

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
        state = MacroState.LINE_FARMING;
        startTime = System.currentTimeMillis();

        sendMessage("macromod.chat.macro_started", Formatting.GREEN, "Line Farm");
        LOGGER.info("Starting line farm mode");
    }

    /**
     * Stops the macro execution immediately, releasing all inputs.
     */
    public void stop() {
        if (state == MacroState.IDLE) {
            sendMessage("macromod.chat.no_active_macro", Formatting.YELLOW);
            return;
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
            printStats();
        }

        state = MacroState.IDLE;
        currentMacro = null;
        currentPath = null;
        precomputedPaths = null;
        precomputeFromPos = null;
        precomputeBusy = false;
        pipelineBusy = false;
        pipelineStepIndex = -1;
        sendMessage("macromod.chat.macro_stopped", Formatting.YELLOW);
        LOGGER.info("Macro stopped");
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
    }

    /**
     * Resumes execution from a paused state.
     */
    public void resume() {
        if (state != MacroState.PAUSED) return;
        state = stateBeforePause != null ? stateBeforePause : MacroState.PATHFINDING;
        stateBeforePause = null;
        sendMessage("macromod.chat.macro_resumed", Formatting.GREEN);
    }

    /**
     * Called every client tick. Drives the state machine forward.
     */
    public void tick() {
        if (state == MacroState.IDLE || state == MacroState.COMPLETED
                || state == MacroState.ERROR || state == MacroState.PAUSED) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) return;

        // Stop macro if player dies
        if (player.isDead() || player.getHealth() <= 0) {
            LOGGER.warn("Player died! Stopping macro.");
            stop();
            sendMessage("macromod.chat.macro_stopped_death", Formatting.RED);
            return;
        }

        // Track distance
        if (lastDistCheckPos != null) {
            totalDistance += new Vec3d(player.getX(), player.getY(), player.getZ()).distanceTo(lastDistCheckPos);
        }
        lastDistCheckPos = new Vec3d(player.getX(), player.getY(), player.getZ());

        // ───────────────────────────────────────────────────────────────
        // Pause macro movement whenever the standalone AutoAttackManager (K key)
        // has an active target. Resume with fresh pathfinding when target clears.
        // ───────────────────────────────────────────────────────────────
        AutoAttackManager aam = AutoAttackManager.getInstance();
        boolean aamHasTarget = aam != null && aam.isEnabled() && aam.getCurrentTarget() != null;
        if (aamHasTarget && !pausedForAutoAttack) {
            // Just acquired a target — freeze all macro movement
            pausedForAutoAttack = true;
            movementHelper.releaseAllInputs();
            aimLocked = false;
            aimLockedAtMs = -1L;
            isMiningBlock = false;
            if (client.options != null) client.options.attackKey.setPressed(false);
            LOGGER.info("[MacroExecutor] Paused for AutoAttack target");
        }
        if (!aamHasTarget && pausedForAutoAttack) {
            // Target cleared — resume macro, try snapping to existing path first
            pausedForAutoAttack = false;
            if (state == MacroState.MOVING || state == MacroState.PATHFINDING
                    || state == MacroState.MINING || state == MacroState.NEXT_STEP) {

                // Try position snapping: find nearest point on existing path
                List<BlockPos> existingPath = (precomputedPaths != null && currentStepIndex < precomputedPaths.size())
                        ? precomputedPaths.get(currentStepIndex) : null;
                int snapIdx = snapToPath(player, existingPath);

                if (snapIdx >= 0 && existingPath != null) {
                    // Snap succeeded — resume from the nearest path node
                    currentPath = existingPath;
                    currentPathIndex = snapIdx;
                    moveStartMs = System.currentTimeMillis();
                    stuckSince  = -1L;
                    lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
                    state = MacroState.MOVING;
                    LOGGER.info("[MacroExecutor] Resumed after AutoAttack, snapped to path index {}", snapIdx);
                } else {
                    // Can't snap — invalidate and re-pathfind from scratch
                    currentPath = null;
                    currentPathIndex = 0;
                    if (precomputedPaths != null && currentStepIndex < precomputedPaths.size()) {
                        precomputedPaths.set(currentStepIndex, null);
                    }
                    moveStartMs = System.currentTimeMillis();
                    stuckSince  = -1L;
                    lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
                    state = MacroState.PATHFINDING;
                    LOGGER.info("[MacroExecutor] Resumed after AutoAttack, re-pathfinding from {}", player.getBlockPos());
                }
            }
        }
        if (pausedForAutoAttack) {
            return; // Suspend state machine while entity is being attacked
        }

        // Entity attack system with attackDanger mode
        boolean isDuringMacro = state != MacroState.IDLE && state != MacroState.COMPLETED && state != MacroState.ERROR;
        if (isDuringMacro && currentMacro.getConfig().isAttackDanger()) {
            // Check if enemies are nearby
            boolean hasEnemiesNearby = hasEntitiesInRange(player, world, currentMacro.getConfig(), true);
            
            if (hasEnemiesNearby && !isEliminatingEnemies) {
                // Enter entity elimination mode — fully cancel current route
                isEliminatingEnemies = true;
                LOGGER.info("Entering entity elimination mode from state {}", state);
                movementHelper.releaseAllInputs();
                // Clear route so path-following keys don't interfere with attack movement
                currentPath = null;
                currentPathIndex = 0;
                attackTarget = null;
                attackChaseStartMs = -1L;
                PathHandler _ph = MacroModClient.getPathHandler();
                if (_ph != null) _ph.cancelPath();
            }
        }

        // Check if we should exit entity elimination mode
        if (isEliminatingEnemies) {
            boolean stillHasEnemies = hasEntitiesInRange(player, world, currentMacro.getConfig(), true);
            if (!stillHasEnemies) {
                // Exit entity elimination mode — try snap/splice before full repath
                isEliminatingEnemies = false;
                attackTarget = null;
                attackChaseStartMs = -1L;

                List<BlockPos> existingPath = (precomputedPaths != null && currentStepIndex < precomputedPaths.size())
                        ? precomputedPaths.get(currentStepIndex) : null;
                int snapIdx = snapToPath(player, existingPath);

                if (snapIdx >= 0 && existingPath != null) {
                    // Snap to nearest path node
                    currentPath = existingPath;
                    currentPathIndex = snapIdx;
                    moveStartMs = System.currentTimeMillis();
                    stuckSince  = -1L;
                    lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
                    state = MacroState.MOVING;
                    LOGGER.info("Enemies eliminated. Snapped to path index {} from {}", snapIdx, player.getBlockPos());
                } else {
                    // Full repath
                    currentPath = null;
                    currentPathIndex = 0;
                    if (precomputedPaths != null && currentStepIndex < precomputedPaths.size()) {
                        precomputedPaths.set(currentStepIndex, null);
                    }
                    moveStartMs = System.currentTimeMillis();
                    stuckSince  = -1L;
                    lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
                    state = MacroState.PATHFINDING;
                    LOGGER.info("Enemies eliminated. Re-pathfinding from {}", player.getBlockPos());
                }
            } else {
                // Still has enemies — attack current target (single target at a time)
                tickAttack(player, world, client);
                return; // Don't execute state machine while eliminating
            }
        }

        // Entity attack — active during any running state
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
    }

    // ═══════════════════════════════════════════════════════════════
    // State tick handlers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Pre-computation phase: submits each step's path computation to a background thread.
     * Polls for completion each tick without blocking the main thread.
     * Transitions to PATHFINDING when all paths are ready or first step is ready.
     */
    private void tickPrecomputing(ClientPlayerEntity player, ClientWorld world) {
        // All steps submitted and finished — transition
        if (precomputeIndex >= currentMacro.getSteps().size() && !precomputeBusy) {
            LOGGER.info("Path pre-computation done ({} steps)", currentMacro.getSteps().size());
            state = MacroState.PATHFINDING;
            return;
        }

        // Background thread still running — wait
        if (precomputeBusy) {
            // If the first step path is already available, start navigating immediately
            // while remaining steps compute in the background
            if (precomputedPaths.get(0) != null) {
                LOGGER.info("First path ready, starting navigation while computing remaining steps");
                state = MacroState.PATHFINDING;
            }
            return;
        }

        // All submitted — done
        if (precomputeIndex >= currentMacro.getSteps().size()) {
            LOGGER.info("Path pre-computation done ({} steps)", currentMacro.getSteps().size());
            state = MacroState.PATHFINDING;
            return;
        }

        MacroStep step = currentMacro.getSteps().get(precomputeIndex);
        BlockPos goal  = step.getDestination();
        BlockPos from  = precomputeFromPos != null ? precomputeFromPos : player.getBlockPos();

        if (!BlockUtils.isChunkLoaded(world, goal)) {
            LOGGER.info("Pre-compute skip step {} (chunk not loaded)", precomputeIndex);
            precomputeFromPos = goal;
            precomputeIndex++;
            return;
        }

        // Submit path computation to background thread
        final int stepIdx = precomputeIndex;
        final BlockPos fromFinal = from;
        final BlockPos goalFinal = goal;
        final boolean onlyGround = currentMacro.getConfig().isOnlyGround();
        final int maxNodes = MacroModClient.getConfigManager().getConfig().getMaxPathNodes();
        precomputeBusy = true;

        pathfindingExecutor.submit(() -> {
            try {
                List<BlockPos> path = null;
                PathHandler pathHandler = MacroModClient.getPathHandler();
                if (pathHandler != null) {
                    List<BaseBlockPos> sp = pathHandler.findPath(
                        new BaseBlockPos(fromFinal.getX(), fromFinal.getY(), fromFinal.getZ()),
                        new ExactGoal(new BaseBlockPos(goalFinal.getX(), goalFinal.getY(), goalFinal.getZ())),
                        500L
                    );
                    path = toBlockPosPath(sp);
                }
                if (path == null || path.isEmpty()) {
                    PathFinder bgPathFinder = new PathFinder();
                    bgPathFinder.setOnlyGround(onlyGround);
                    bgPathFinder.setMaxNodes(maxNodes);
                    path = bgPathFinder.findPath(fromFinal, goalFinal, world);
                }
                if (precomputedPaths != null && stepIdx < precomputedPaths.size()) {
                    precomputedPaths.set(stepIdx, path);
                }
                LOGGER.info("Pre-computed path for step {}: {} nodes", stepIdx,
                        path != null ? path.size() : 0);
            } catch (Exception e) {
                LOGGER.error("Background path computation failed for step {}", stepIdx, e);
            } finally {
                precomputeBusy = false;
            }
        });

        precomputeFromPos = goal;
        precomputeIndex++;
    }

    private void tickPathfinding(ClientPlayerEntity player, ClientWorld world) {
        MacroStep step = getCurrentStep();
        if (step == null) {
            state = MacroState.COMPLETED;
            return;
        }
        movementHelper.applyMacroConfig(currentMacro.getConfig());

        BlockPos goal = step.getDestination();

        // Already at destination? Require the player to be centered before mining.
        if (PlayerUtils.isArrived(player, goal, currentMacro.getConfig().getArrivalRadius())) {
            double cdx = player.getX() - (goal.getX() + 0.5);
            double cdz = player.getZ() - (goal.getZ() + 0.5);
            double centerDist = Math.sqrt(cdx * cdx + cdz * cdz);
            if (!isCenteredOnBlock(player, goal)) {
                long now = System.currentTimeMillis();
                if (now - lastDebugMs > DEBUG_INTERVAL_MS) {
                    lastDebugMs = now;
                    LOGGER.info("[DBG] PATHFINDING->centering: dist={} threshold=0.40 pos=({},{}) dest=({},{})",
                        String.format("%.3f", centerDist),
                        String.format("%.2f", player.getX()), String.format("%.2f", player.getZ()),
                        goal.getX(), goal.getZ());
                }
                movementHelper.moveTowards(player, goal);
                player.setSprinting(false);
                return;
            }
            LOGGER.info("[DBG] PATHFINDING->MINING: centered dist={}", String.format("%.3f", centerDist));
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
                    sendMessage("macromod.chat.chunk_not_loaded", Formatting.YELLOW);
                } else if (System.currentTimeMillis() - chunkWaitStartMs > 5000) {
                    LOGGER.warn("Chunk not loaded after 5s, skipping step");
                    sendMessage("macromod.chat.path_not_found", Formatting.RED);
                    advanceToNextStep();
                }
                return;
            }
            chunkWaitStartMs = -1L;

            PathHandler pathHandler = MacroModClient.getPathHandler();
            if (pathHandler != null) {
                long timeoutMs = 3000L; // 3s pathfinding calculation cap
                List<BaseBlockPos> sp = pathHandler.findPath(
                    new BaseBlockPos(player.getBlockPos().getX(), player.getBlockPos().getY(), player.getBlockPos().getZ()),
                    new ExactGoal(new BaseBlockPos(goal.getX(), goal.getY(), goal.getZ())),
                    timeoutMs
                );
                path = toBlockPosPath(sp);
            }
            if (path == null || path.isEmpty()) {
                fallbackPathFinder.setOnlyGround(currentMacro.getConfig().isOnlyGround());
                path = fallbackPathFinder.findPath(player.getBlockPos(), goal, world);
            }
            if (path == null || path.isEmpty()) {
                LOGGER.warn("No path to step {} destination {}", currentStepIndex, goal);
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
        movementHelper.resetSteeringState();
        state = MacroState.MOVING;
    }

    private void tickMoving(ClientPlayerEntity player, ClientWorld world) {
        MacroStep step = getCurrentStep();
        if (step == null) {
            state = MacroState.COMPLETED;
            return;
        }

        float arrivalRadius = currentMacro.getConfig().getArrivalRadius();
        movementHelper.applyMacroConfig(currentMacro.getConfig());

        // Check if we've arrived at the final destination (require centering first)
        if (PlayerUtils.isArrived(player, step.getDestination(), arrivalRadius)) {
            BlockPos dest = step.getDestination();
            double cdx = player.getX() - (dest.getX() + 0.5);
            double cdz = player.getZ() - (dest.getZ() + 0.5);
            double centerDist = Math.sqrt(cdx * cdx + cdz * cdz);
            if (!isCenteredOnBlock(player, dest)) {
                long now = System.currentTimeMillis();
                if (now - lastDebugMs > DEBUG_INTERVAL_MS) {
                    lastDebugMs = now;
                    LOGGER.info("[DBG] MOVING->centering: dist={} threshold=0.40 pos=({},{}) dest=({},{})",
                        String.format("%.3f", centerDist),
                        String.format("%.2f", player.getX()), String.format("%.2f", player.getZ()),
                        dest.getX(), dest.getZ());
                }
                movementHelper.moveTowards(player, dest);
                player.setSprinting(false);
                return;
            }
            LOGGER.info("[DBG] MOVING->MINING (isArrived): centered dist={}", String.format("%.3f", centerDist));
            movementHelper.releaseAllInputs();
            state = MacroState.MINING;
            currentBlockTargetIndex = 0;
            isMiningBlock = false;
            return;
        }

        // Timeout check per tick for movement only (not mining) to prevent getting stuck indefinitely
        long elapsedMove = System.currentTimeMillis() - moveStartMs;
        if (elapsedMove > 60000) {
            LOGGER.warn("Navigation timeout at step {} (elapsed={}ms moveStartMs={})",
                currentStepIndex, elapsedMove, moveStartMs);
            sendMessage("macromod.chat.timeout", Formatting.RED);
            movementHelper.releaseAllInputs();
            state = MacroState.ERROR;
            return;
        }
        
        // Periodic debug: position, distance to dest, elapsed time
        {
            long now = System.currentTimeMillis();
            if (now - lastDebugMs > DEBUG_INTERVAL_MS) {
                lastDebugMs = now;
                BlockPos dest = step.getDestination();
                double ddx = player.getX() - (dest.getX() + 0.5);
                double ddz = player.getZ() - (dest.getZ() + 0.5);
                double toDest = Math.sqrt(ddx * ddx + ddz * ddz);
                LOGGER.info("[DBG] MOVING step={} pathIdx={}/{} pos=({},{}) destDist={} elapsed={}ms",
                    currentStepIndex,
                    currentPathIndex, currentPath != null ? currentPath.size() : 0,
                    String.format("%.2f", player.getX()), String.format("%.2f", player.getZ()),
                    String.format("%.2f", toDest), elapsedMove);
                LOGGER.info("[DBG] STEER yawDelta={} desiredSpeed={} currentSpeed={}",
                    String.format("%.1f", movementHelper.getLastYawDelta()),
                    String.format("%.3f", movementHelper.getLastDesiredSpeed()),
                    String.format("%.3f", movementHelper.getLastCurrentSpeed()));
            }
        }

        // Stuck detection (no movement in 3 seconds)
        if (lastPosition != null) {
            double moved = PlayerUtils.horizontalDistanceTo(player, lastPosition);
            double steeringSpeed = movementHelper.getLastCurrentSpeed();
            if (moved < MOVING_MIN_PROGRESS && steeringSpeed < 0.08) {
                if (stuckSince < 0) stuckSince = System.currentTimeMillis();
            } else {
                stuckSince = -1L;
                lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
            }
        }

        if (stuckSince >= 0 && System.currentTimeMillis() - stuckSince > MOVING_STUCK_TIMEOUT_MS) {
            LOGGER.warn("Player stuck, attempting snap then recalculating path");
            sendMessage("macromod.chat.stuck", Formatting.YELLOW);
            stuckSince = -1L;

            // Try snapping forward on the existing path before full repath
            int snapIdx = snapToPath(player, currentPath);
            if (snapIdx >= 0 && snapIdx > currentPathIndex) {
                currentPathIndex = snapIdx;
                lastPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
                LOGGER.info("Stuck snap: advanced path index to {}", snapIdx);
            } else {
                // Clear cached path so it's re-computed from the current (actual) position
                if (precomputedPaths != null && currentStepIndex < precomputedPaths.size()) {
                    precomputedPaths.set(currentStepIndex, null);
                }
                state = MacroState.PATHFINDING;
                movementHelper.releaseAllInputs();
            }
            return;
        }

        // If chasing an entity during attackDanger mode, temporarily skip path following
        boolean isChasingEntity = currentMacro.getConfig().isAttackDanger() && attackTarget != null 
                && attackTarget.isAlive() && attackTarget.getHealth() > 0;
        if (isChasingEntity) {
            // Entity chase takes priority; movement already handled by tickAttack
            return;
        }

        // Path already consumed: navigate directly to block center before mining.
        // MUST be checked before the main path-following block — otherwise on
        // the second+ centering tick the outer `else { PATHFINDING }` would fire.
        if (currentPath != null && currentPathIndex >= currentPath.size()) {
            BlockPos dest = step.getDestination();
            if (!isCenteredOnBlock(player, dest)) {
                double cdx3 = player.getX() - (dest.getX() + 0.5);
                double cdz3 = player.getZ() - (dest.getZ() + 0.5);
                long now = System.currentTimeMillis();
                if (now - lastDebugMs > DEBUG_INTERVAL_MS) {
                    lastDebugMs = now;
                    LOGGER.info("[DBG] centering dist={}", String.format("%.3f", Math.sqrt(cdx3*cdx3+cdz3*cdz3)));
                }
                movementHelper.moveTowards(player, dest);
                player.setSprinting(false);
                return;
            }
            LOGGER.info("[DBG] centered -> MINING");
            movementHelper.releaseAllInputs();
            state = MacroState.MINING;
            currentBlockTargetIndex = 0;
            isMiningBlock = false;
            return;
        }

        // ── Pipeline: start computing next step's path while we're still moving ─
        pipelineNextStepPath(player, world);

        // Follow path — keep forward key held through all waypoints for smooth momentum
        if (currentPath != null && currentPathIndex < currentPath.size()) {
            BlockPos nextWaypoint = currentPath.get(currentPathIndex);

            // Start nodes are frequently at/near the player's current position.
            // Skip them quickly to avoid slowing/orbiting near the path anchor.
            while (currentPathIndex < currentPath.size() - 1
                    && isNearWaypoint(player, currentPath.get(currentPathIndex), 1.2)) {
                currentPathIndex++;
                nextWaypoint = currentPath.get(currentPathIndex);
            }

            // If we're already significantly closer to the next node than the current one,
            // advance immediately to avoid orbiting a behind-us waypoint.
            while (currentPathIndex < currentPath.size() - 1
                    && shouldAdvanceWaypoint(player, currentPath.get(currentPathIndex), currentPath.get(currentPathIndex + 1))) {
                currentPathIndex++;
                nextWaypoint = currentPath.get(currentPathIndex);
            }

            // Advance to next waypoint when player reaches the center of the current one
            if (movementHelper.hasReachedWaypoint(player, nextWaypoint)) {
                currentPathIndex++;
                if (currentPathIndex >= currentPath.size()) {
                    // End of path — the pre-block centering check handles this next tick
                    return;
                }
                nextWaypoint = currentPath.get(currentPathIndex);
            }

            // Look-ahead skip: advance the path index when the player is already past
            // 85% of the current segment — but ONLY for straight sections.
            // On corners (direction change > ~45°) we skip this so the player
            // centres on the turn block before changing heading, preventing edge clipping.
            while (currentPathIndex < currentPath.size() - 1) {
                Vec3d currCenter = net.minecraft.util.math.Vec3d.ofCenter(currentPath.get(currentPathIndex));
                Vec3d nextCenter = net.minecraft.util.math.Vec3d.ofCenter(currentPath.get(currentPathIndex + 1));
                Vec3d seg        = nextCenter.subtract(currCenter);
                Vec3d toPlayer   = new Vec3d(player.getX(), player.getY(), player.getZ()).subtract(currCenter);
                double dot       = seg.dotProduct(toPlayer);
                double segLenSq  = seg.dotProduct(seg);

                // Corner-ahead check: is there a significant direction change at nextCenter?
                boolean cornerAhead = false;
                if (currentPathIndex + 2 < currentPath.size()) {
                    Vec3d afterNext = net.minecraft.util.math.Vec3d.ofCenter(
                            currentPath.get(currentPathIndex + 2));
                    Vec3d segNext = afterNext.subtract(nextCenter);
                    double segLen   = Math.sqrt(segLenSq);
                    double nextLen  = segNext.length();
                    if (segLen > 0.001 && nextLen > 0.001) {
                        double dirDot = seg.dotProduct(segNext) / (segLen * nextLen);
                        cornerAhead = dirDot < 0.70; // > ~45° change → treat as corner
                    }
                }

                // On corners, let hasReachedWaypoint handle advancement so the
                // player is truly centred before the heading changes.
                if (cornerAhead) break;

                if (dot > segLenSq * 0.85) {   // 85% through straight segment = skip
                    currentPathIndex++;
                    nextWaypoint = currentPath.get(currentPathIndex);
                } else {
                    break;
                }
            }

            // Jump over 1-block obstacles and handle horizontal stalls
            movementHelper.handleJump(player, nextWaypoint);
            // Steer toward a lookahead point so corners are cut naturally.
            Vec3d smoothedTarget = getSmoothedTarget(currentPath, currentPathIndex, player);
            movementHelper.moveTowards(player, smoothedTarget);
        } else {
            // Path ended but we're not at the destination — recalculate
            movementHelper.releaseAllInputs();
            state = MacroState.PATHFINDING;
        }
    }

    private void tickMining(ClientPlayerEntity player, ClientWorld world, MinecraftClient client) {
        MacroStep step = getCurrentStep();
        if (step == null || (step.getTargets().isEmpty() && step.getRadius() <= 0) || step.isComplete()) {
            advanceToNextStep();
            return;
        }
        movementHelper.applyMacroConfig(currentMacro.getConfig());

        // ── Radius scan: on first entry for this step, find matching blocks nearby ───
        if (!radiusScanDone) {
            radiusScanDone = true;
            if (!step.getTargets().isEmpty()) {
                scanRadiusTargets(step, world, player);
            }
        }

        // ── Dynamic rescan: if player has moved >2 blocks since last scan, re-scan ──
        if (lastScanPlayerPos != null && !step.getTargets().isEmpty()) {
            BlockPos pp = player.getBlockPos();
            int dx = pp.getX() - lastScanPlayerPos.getX();
            int dy = pp.getY() - lastScanPlayerPos.getY();
            int dz = pp.getZ() - lastScanPlayerPos.getZ();
            if ((double)(dx*dx + dy*dy + dz*dz) > RESCAN_MOVE_THRESHOLD_SQ) {
                scanRadiusTargets(step, world, player);
            }
        }

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

        // Block verification
        String actualBlockId = BlockUtils.getBlockId(world, blockPos);

        // If block is already air, it's already mined
        if (BlockUtils.isAir(world, blockPos)) {
            target.setMined(true);
            blocksMinedTotal++;
            currentBlockTargetIndex++;
            isMiningBlock = false;
            aimLocked = false;
            aimLockedAtMs = -1L;
            miningDelayEndMs = System.currentTimeMillis() + HumanReactionTime.getMiningReactionTime(currentMacro.getConfig().getMiningDelay());
            return;
        }

        // Check block type match — always skip if block changed
        if (!actualBlockId.equals(target.getBlockId())) {
            target.setSkipped(true);
            blocksSkippedTotal++;
            currentBlockTargetIndex++;
            isMiningBlock = false;
            aimLocked = false;
            aimLockedAtMs = -1L;
            return;
        }

        // Skip unripe crops: if block has an 'age' property, only mine when at max age
        BlockState blockState = world.getBlockState(blockPos);
        for (Property<?> prop : blockState.getProperties()) {
            if ("age".equals(prop.getName()) && prop instanceof IntProperty intProp) {
                int currentAge = blockState.get(intProp);
                int maxAge = intProp.getValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                if (currentAge < maxAge) {
                    LOGGER.debug("[DBG] mine SKIP unripe: block={} age={}/{}", blockPos, currentAge, maxAge);
                    target.setSkipped(true);
                    blocksSkippedTotal++;
                    currentBlockTargetIndex++;
                    isMiningBlock = false;
                    return;
                }
                break;
            }
        }

        // 3D sphere range check: permanently skip blocks truly out of approach range
        Vec3d blockCenter3d = Vec3d.ofCenter(blockPos);
        double distSq = eyePos.squaredDistanceTo(blockCenter3d);
        if (distSq > 6.0 * 6.0) {
            LOGGER.info("[DBG] mine SKIP range 3D: block={} dist={}", blockPos,
                String.format("%.2f", Math.sqrt(distSq)));
            target.setSkipped(true);
            blocksSkippedTotal++;
            currentBlockTargetIndex++;
            isMiningBlock = false;
            return;
        }

        // Aim at the visible face center (unless aim is already locked on target)
        Vec3d blockCenter = visibleFaceCentre(eyePos, blockPos, player.getBlockPos().getY(), world);
        if (!aimLocked) {
            movementHelper.lookAt(player, blockCenter, 0.10f);
        }

        // Check if the aim point on the block is actually visible (raycast to the face point)
        net.minecraft.world.RaycastContext losCtx = new net.minecraft.world.RaycastContext(
                eyePos, blockCenter,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                (net.minecraft.entity.Entity) player);
        net.minecraft.util.hit.BlockHitResult losResult = world.raycast(losCtx);
        boolean canSee = losResult.getBlockPos().equals(blockPos);

        // If not visible, try to reposition closer before giving up.
        if (!canSee) {
            double hDist = Math.sqrt(
                Math.pow(blockPos.getX() + 0.5 - player.getX(), 2) +
                Math.pow(blockPos.getZ() + 0.5 - player.getZ(), 2));
            if (hDist > 1.5) {
                LOGGER.info("[DBG] mine NO_LOS reposition: block={} hDist={}",
                    blockPos, String.format("%.2f", hDist));
                noLOSBlock = null; // reset strafe when we're still far
                movementHelper.forwardToBlock(player, blockPos);
                return;
            }

            // Close but occluded — strafe perpendicular to the block direction
            // to peek around the obstructing geometry.
            long now = System.currentTimeMillis();
            if (noLOSBlock == null || !noLOSBlock.equals(blockPos)) {
                noLOSBlock = blockPos;
                noLOSStartMs = now;
                noLOSStrafeLeft = true;
            }

            long elapsed = now - noLOSStartMs;
            if (elapsed > STRAFE_GIVE_UP_MS) {
                LOGGER.info("[DBG] mine NO_LOS give-up: block={}", blockPos);
                noLOSBlock = null;
                target.setSkipped(true);
                blocksSkippedTotal++;
                currentBlockTargetIndex++;
                isMiningBlock = false;
                return;
            }

            // Direction from player toward block (XZ), normalized
            double toBx = blockPos.getX() + 0.5 - player.getX();
            double toBz = blockPos.getZ() + 0.5 - player.getZ();
            double toLen = Math.sqrt(toBx * toBx + toBz * toBz);
            if (toLen > 0.001) { toBx /= toLen; toBz /= toLen; }

            // Perpendicular: left = (-toBz, toBx), right = (toBz, -toBx)
            boolean strafeLeft = elapsed < STRAFE_SWITCH_MS ? noLOSStrafeLeft : !noLOSStrafeLeft;
            double sx = strafeLeft ? -toBz :  toBz;
            double sz = strafeLeft ?  toBx : -toBx;

            // Move 1 block in the strafe direction
            BlockPos strafeTarget = BlockPos.ofFloored(
                player.getX() + sx, player.getY(), player.getZ() + sz);
            LOGGER.info("[DBG] mine NO_LOS strafe {}: block={} elapsed={}ms",
                strafeLeft ? "LEFT" : "RIGHT", blockPos, elapsed);
            movementHelper.moveTowards(player, strafeTarget);
            return;
        }
        // Regained LOS — reset strafe state
        noLOSBlock = null;

        // Move forward if not within reach — keep left click held so Minecraft
        // starts breaking as soon as the crosshair lands on the block face.
        double reachDist = player.getBlockInteractionRange();
        if (!BlockUtils.isWithinReach(eyePos, blockPos, reachDist)) {
            LOGGER.info("[DBG] mine OUT_OF_REACH: block={} dist3D={} reach={}", blockPos,
                String.format("%.2f", Math.sqrt(distSq)), String.format("%.2f", reachDist));
            movementHelper.forwardToBlock(player, blockPos);
            return;
        }
        movementHelper.releaseForward(player);

        // Wait until aligned if not locked
        if (!currentMacro.getConfig().isLockCrosshair()) {
            if (!movementHelper.isLookingAt(player, blockCenter, 6.0f)) {
                return;
            }
        }

        // Verify crosshair is on target block
        HitResult crosshair = client.crosshairTarget;
        if (!(crosshair instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(blockPos)) {
            aimLocked = false;  // Crosshair lost — unlock aim to re-adjust
            aimLockedAtMs = -1L;
            long now2 = System.currentTimeMillis();
            if (now2 - lastDebugMs > DEBUG_INTERVAL_MS) {
                lastDebugMs = now2;
                String hitInfo = (crosshair instanceof BlockHitResult bhr2)
                    ? bhr2.getBlockPos().toString() : (crosshair != null ? crosshair.getType().name() : "null");
                LOGGER.info("[DBG] mine CROSSHAIR_MISS: want={} got={}", blockPos, hitInfo);
            }
            return;
        }
        // Lock aim now that crosshair is confirmed on target block
        if (!aimLocked) {
            aimLocked = true;
            aimLockedAtMs = System.currentTimeMillis(); // record first lock time for dwell
        }

        // Mine through the normal left-click path once dwell has elapsed.
        // Also hold the attack key so Minecraft's native breaking loop runs in parallel.
        boolean dwellPassed = aimLockedAtMs >= 0
                && System.currentTimeMillis() - aimLockedAtMs >= MINING_CLICK_DWELL_MS;
        if (dwellPassed) {
            if (client.options != null) client.options.attackKey.setPressed(true);
            MouseInputHelper.continueLeftClick(client);
        }
        if (!isMiningBlock) {
            isMiningBlock = true;
            lastMineTime = System.currentTimeMillis();
        }

        // During mining: check if the block was replaced (Hypixel replaces blocks mid-mine).
        // Periodically re-verify the block is still the target type.
        if (isMiningBlock && System.currentTimeMillis() - lastMineTime > 200) {
            String currentBlockId = BlockUtils.getBlockId(world, blockPos);
            if (!currentBlockId.equals(target.getBlockId())) {
                LOGGER.info("[DBG] mine BLOCK_REPLACED: block={} was {} now {}", blockPos,
                    target.getBlockId(), currentBlockId);
                target.setSkipped(true);
                blocksSkippedTotal++;
                currentBlockTargetIndex++;
                isMiningBlock = false;
                aimLocked = false;
                aimLockedAtMs = -1L;
                return;
            }
        }

        // Check if block is now air (mined successfully) — do NOT release the attack key
        // here so the next block starts breaking immediately without a click gap.
        if (BlockUtils.isAir(world, blockPos)) {
            target.setMined(true);
            blocksMinedTotal++;
            isMiningBlock = false;
            aimLocked = false;  // Unlock aim for next block
            aimLockedAtMs = -1L;
            if (client.options != null) client.options.attackKey.setPressed(false); // release immediately after mining
            miningDelayEndMs = System.currentTimeMillis() + HumanReactionTime.getMiningReactionTime(currentMacro.getConfig().getMiningDelay());
            scanRadiusTargets(step, world, player); // player-centered rescan for newly exposed blocks
            currentBlockTargetIndex++;
            return;
        }

        // Stuck mining timeout (1 second)
        if (System.currentTimeMillis() - lastMineTime > 1000) {
            LOGGER.warn("Stuck mining block at {}, skipping", blockPos);
            if (client.options != null) client.options.attackKey.setPressed(false);
            target.setSkipped(true);
            blocksSkippedTotal++;
            currentBlockTargetIndex++;
            isMiningBlock = false;
            aimLocked = false;  // Unlock aim on timeout
            aimLockedAtMs = -1L;
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
                        LOGGER.info("[MacroExecutor] tickAttack: lost LOS on {}, finding new target",
                            attackTarget.getName().getString());
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
            attackLastJumpMs      = -1L;
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
            if (client.options != null) client.options.forwardKey.setPressed(true);

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

                    if (stuckMs >= 600 && System.currentTimeMillis() - attackLastJumpMs >= 450) {
                        // Stuck while chasing: jump more aggressively, but with cooldown.
                        if (client.options != null && player.isOnGround()) {
                            client.options.jumpKey.setPressed(true);
                            attackLastJumpMs = System.currentTimeMillis();
                            attackChaseJumped = true;
                        }
                    }

                    if (stuckMs >= 2200) {
                        // Still stuck after jump attempts — skip this entity and try next.
                        LOGGER.info("[MacroExecutor] tickAttack: stuck for 2.2s chasing {}, skipping",
                            attackTarget.getName().getString());
                        clearAttackTarget(client);
                        return;
                    }
                }
            }
        } else {
            // Within melee reach — stop moving and reset stuck state
            attackChaseStuckMs = -1L;
            attackChaseLastPos = null;
            if (client.options != null) {
                client.options.forwardKey.setPressed(false);
                client.options.backKey.setPressed(false);
                client.options.leftKey.setPressed(false);
                client.options.rightKey.setPressed(false);
                if (attackChaseJumped) {
                    client.options.jumpKey.setPressed(false);
                    attackChaseJumped = false;
                }
            }

            // ── 5. Attack when aimed + cooldown ready + dwell elapsed + CPS ────
                // Only require angle check (6 degrees), don't require entity in exact crosshair center
            boolean lookingAtTarget = sa != null && sa.isOnTarget(player, 6f);
            long now = System.currentTimeMillis();
            int effectiveCps = cfg.isRandomAttackCps() ? (7 + ATTACK_RAND.nextInt(5)) : cfg.getAttackCPS();
            long cpsIntervalMs      = 1000L / Math.max(1, effectiveCps);
            boolean cpsReady        = now - lastAttackMs >= cpsIntervalMs;
            
            // Spam-click at 3.5 blocks range
            double spamDistSq = MACRO_SPAM_CLICK_DISTANCE * MACRO_SPAM_CLICK_DISTANCE;
            if (distSq <= spamDistSq) {
                if (lastSpamClickMs < 0 || now - lastSpamClickMs >= cpsIntervalMs) {
                    float cooldown = player.getAttackCooldownProgress(0f);
                    if (cooldown >= ATTACK_COOLDOWN_THRESHOLD && client.interactionManager != null) {
                        MouseInputHelper.leftClick(client);
                        lastSpamClickMs = now;
                    }
                }
            }

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
        int scanRadius = 5; // loop bound — sphere filter keeps actual range to 4.5 blocks

        // Collect candidates into a temp list for distance-sorting
        java.util.List<BlockTarget> discovered = new java.util.ArrayList<>();
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dy = -scanRadius; dy <= scanRadius; dy++) {
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
        if (!discovered.isEmpty()) {
            LOGGER.info("Radius scan found {} extra blocks for step {} (player-centered r=4.5)",
                discovered.size(), currentStepIndex);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if there are any attackable entities within attack range.
     */
    private boolean hasEntitiesInRange(ClientPlayerEntity player, ClientWorld world, MacroConfig cfg, boolean requireLos) {
        float range = cfg.getAttackRange();
        MinecraftClient client = MinecraftClient.getInstance();
        Box box = player.getBoundingBox().expand(range);
        List<LivingEntity> candidates = world.getEntitiesByClass(
            LivingEntity.class, box, e -> {
                if (e == player || e.isDead() || e.getHealth() <= 0) return false;
                if (requireLos && !attackHasLOS(client, player, e)) return false;
                // Always filter out irrelevant entity types
                if (e instanceof net.minecraft.entity.player.PlayerEntity) return false;
                if (e instanceof ArmorStandEntity) return false;
                if (e.isInvisible()) return false;
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

    private static boolean isNearWaypoint(ClientPlayerEntity player, BlockPos waypoint, double radius) {
        double dx = player.getX() - (waypoint.getX() + 0.5);
        double dz = player.getZ() - (waypoint.getZ() + 0.5);
        return dx * dx + dz * dz <= radius * radius;
    }

    private static boolean shouldAdvanceWaypoint(ClientPlayerEntity player, BlockPos current, BlockPos next) {
        Vec3d p = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d c = Vec3d.ofCenter(current);
        Vec3d n = Vec3d.ofCenter(next);

        double distCurrSq = p.squaredDistanceTo(c);
        double distNextSq = p.squaredDistanceTo(n);

        // Require meaningful difference so noisy micro-movements don't flip indices.
        return distNextSq + 0.10 < distCurrSq;
    }

    /**
     * Returns a blended target between the current and next path nodes.
     * Straight segments use stronger lookahead, while corners reduce blending
     * to avoid overcutting into obstacle edges.
     */
    private Vec3d getSmoothedTarget(List<BlockPos> path, int idx, ClientPlayerEntity player) {
        if (path == null || path.isEmpty() || idx < 0 || idx >= path.size()) {
            return new Vec3d(player.getX(), player.getY(), player.getZ());
        }

        Vec3d current = Vec3d.ofCenter(path.get(idx));
        if (idx >= path.size() - 1) {
            return current;
        }

        Vec3d next = Vec3d.ofCenter(path.get(idx + 1));
        double blend = LOOKAHEAD_DEFAULT_BLEND;

        if (idx + 2 < path.size()) {
            Vec3d afterNext = Vec3d.ofCenter(path.get(idx + 2));
            Vec3d seg = next.subtract(current);
            Vec3d segNext = afterNext.subtract(next);
            double segLen = seg.length();
            double segNextLen = segNext.length();
            if (segLen > 0.001 && segNextLen > 0.001) {
                double dirDot = seg.dotProduct(segNext) / (segLen * segNextLen);
                if (dirDot < 0.70) {
                    blend = LOOKAHEAD_CORNER_BLEND;
                } else if (dirDot > 0.95) {
                    blend = LOOKAHEAD_STRAIGHT_BLEND;
                }
            }
        }

        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        if (playerPos.distanceTo(current) < 0.9) {
            blend = Math.min(0.75, blend + 0.12);
        }

        return current.lerp(next, blend);
    }

    // ═══════════════════════════════════════════════════════════════
    // Position snapping — find nearest valid re-entry point on path
    // ═══════════════════════════════════════════════════════════════

    /**
     * Scans the current path for the nearest node to the player's actual position.
     * Returns the index of that node, or -1 if no suitable node is found.
     * Used after disruptions (combat, knockback) to resume path-following
     * from the closest valid point rather than backtracking to the start.
     */
    private int snapToPath(ClientPlayerEntity player, List<BlockPos> path) {
        if (path == null || path.isEmpty()) return -1;

        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double bestDistSq = Double.MAX_VALUE;
        int bestIdx = -1;

        // Scan forward from current index (prefer ahead over behind)
        int searchStart = Math.max(0, currentPathIndex - 3);
        int searchEnd = Math.min(path.size(), currentPathIndex + 20);

        for (int i = searchStart; i < searchEnd; i++) {
            BlockPos wp = path.get(i);
            double dx = px - (wp.getX() + 0.5);
            double dy = py - wp.getY();
            double dz = pz - (wp.getZ() + 0.5);
            double dSq = dx * dx + dy * dy + dz * dz;

            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestIdx = i;
            }
        }

        // Only snap if within 8 blocks of a path node
        if (bestDistSq > 64.0) return -1;
        return bestIdx;
    }

    // ═══════════════════════════════════════════════════════════════
    // Path splicing — join a new segment onto the existing path
    // ═══════════════════════════════════════════════════════════════

    /**
     * Attempts to splice a new path segment from the player's current position
     * onto the existing path. If the new segment reaches a point near the existing
     * path, the two are merged at the overlap. Otherwise, falls back to full repath.
     *
     * @return spliced path, or null if splicing is not possible
     */
    private List<BlockPos> splicePath(List<BlockPos> existingPath, List<BlockPos> newSegment) {
        if (existingPath == null || existingPath.isEmpty()
                || newSegment == null || newSegment.isEmpty()) {
            return null;
        }

        // Find where (if anywhere) the new segment meets the existing path
        BlockPos newEnd = newSegment.get(newSegment.size() - 1);

        int overlapIdx = -1;
        double bestOverlapDist = Double.MAX_VALUE;
        for (int i = currentPathIndex; i < existingPath.size(); i++) {
            BlockPos ep = existingPath.get(i);
            double dx = newEnd.getX() - ep.getX();
            double dz = newEnd.getZ() - ep.getZ();
            double dy = newEnd.getY() - ep.getY();
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < bestOverlapDist && dSq <= 4.0) { // within 2 blocks
                bestOverlapDist = dSq;
                overlapIdx = i;
            }
        }

        if (overlapIdx < 0) return null; // no overlap — can't splice

        // Build spliced path: newSegment + existingPath[overlapIdx+1..]
        List<BlockPos> spliced = new ArrayList<>(newSegment.size() + (existingPath.size() - overlapIdx));
        spliced.addAll(newSegment);
        for (int i = overlapIdx + 1; i < existingPath.size(); i++) {
            spliced.add(existingPath.get(i));
        }

        LOGGER.info("[Splice] Spliced {} new + {} existing nodes at overlap index {}",
                newSegment.size(), existingPath.size() - overlapIdx - 1, overlapIdx);
        return spliced;
    }

    // ═══════════════════════════════════════════════════════════════
    // Pipelined segment computation — compute next step while moving
    // ═══════════════════════════════════════════════════════════════

    /**
     * Kicks off background computation of the NEXT step's path while following the
     * current step. Called during MOVING state when the next step hasn't been computed yet.
     */
    private void pipelineNextStepPath(ClientPlayerEntity player, ClientWorld world) {
        int nextStep = currentStepIndex + 1;
        if (nextStep >= currentMacro.getSteps().size()) return; // no next step
        if (precomputedPaths == null || nextStep >= precomputedPaths.size()) return;
        if (precomputedPaths.get(nextStep) != null) return; // already computed
        if (pipelineBusy) return; // already computing
        if (pipelineStepIndex == nextStep) return; // already submitted

        MacroStep step = currentMacro.getSteps().get(nextStep);
        BlockPos goal = step.getDestination();

        // Use current step's destination as the starting point for next path
        MacroStep currentStep = currentMacro.getSteps().get(currentStepIndex);
        BlockPos from = currentStep.getDestination();

        if (!BlockUtils.isChunkLoaded(world, goal)) return; // can't compute yet

        pipelineBusy = true;
        pipelineStepIndex = nextStep;
        final int stepIdx = nextStep;
        final BlockPos fromFinal = from;
        final BlockPos goalFinal = goal;
        final boolean onlyGround = currentMacro.getConfig().isOnlyGround();
        final int maxNodes = MacroModClient.getConfigManager().getConfig().getMaxPathNodes();

        pathfindingExecutor.submit(() -> {
            try {
                List<BlockPos> path = null;
                PathHandler pathHandler = MacroModClient.getPathHandler();
                if (pathHandler != null) {
                    List<BaseBlockPos> sp = pathHandler.findPath(
                        new BaseBlockPos(fromFinal.getX(), fromFinal.getY(), fromFinal.getZ()),
                        new ExactGoal(new BaseBlockPos(goalFinal.getX(), goalFinal.getY(), goalFinal.getZ())),
                        500L
                    );
                    path = toBlockPosPath(sp);
                }
                if (path == null || path.isEmpty()) {
                    PathFinder bgPathFinder = new PathFinder();
                    bgPathFinder.setOnlyGround(onlyGround);
                    bgPathFinder.setMaxNodes(maxNodes);
                    path = bgPathFinder.findPath(fromFinal, goalFinal, world);
                }
                if (precomputedPaths != null && stepIdx < precomputedPaths.size()) {
                    precomputedPaths.set(stepIdx, path);
                }
                LOGGER.info("[Pipeline] Pre-computed path for step {}: {} nodes", stepIdx,
                        path != null ? path.size() : 0);
            } catch (Exception e) {
                LOGGER.error("[Pipeline] Background path computation failed for step {}", stepIdx, e);
            } finally {
                pipelineBusy = false;
            }
        });
    }

    private void advanceToNextStep() {
        currentStepIndex++;
        radiusScanDone = false;
        aimLocked = false;  // Unlock aim for next step
        aimLockedAtMs = -1L;

        if (currentStepIndex >= currentMacro.getSteps().size()) {
            if (currentMacro.getConfig().isLoop()) {
                // Reset and loop — clear all precomputed paths since player is now
                // at the last step's destination, not the original start position
                currentMacro.reset();
                currentStepIndex = 0;
                if (precomputedPaths != null) {
                    for (int i = 0; i < precomputedPaths.size(); i++) {
                        precomputedPaths.set(i, null);
                    }
                }
                state = MacroState.PATHFINDING;
                LOGGER.info("Macro loop: restarting from step 0");
            } else {
                movementHelper.releaseAllInputs();
                state = MacroState.COMPLETED;
                printStats();
                LOGGER.info("Macro '{}' completed", currentMacro.getName());
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

    private List<BlockPos> toBlockPosPath(List<BaseBlockPos> stevebotPath) {
        if (stevebotPath == null || stevebotPath.isEmpty()) {
            return List.of();
        }
        List<BlockPos> converted = new ArrayList<>(stevebotPath.size());
        for (BaseBlockPos pos : stevebotPath) {
            converted.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }
        return converted;
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
                                            net.minecraft.client.world.ClientWorld world) {
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
            if (!BlockUtils.isAir(world, adjs[i])) continue; // face buried by adjacent solid

            Vec3d[] candidates = buildFaceCandidates(pos, faces[i], adjs[i], inset);
            for (Vec3d candidate : candidates) {
                net.minecraft.world.RaycastContext ctx = new net.minecraft.world.RaycastContext(
                        eye, candidate,
                        net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE,
                        (net.minecraft.entity.Entity) null);
                net.minecraft.util.hit.BlockHitResult hit = world.raycast(ctx);
                // Accept if the ray reached the target block
                if (hit.getBlockPos().equals(pos)) {
                    return candidate;
                }
            }
        }
        // All candidates occluded — best-effort nearest side centre
        return fSide1;
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
