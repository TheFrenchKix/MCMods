package com.example.macromod.manager;

import com.example.macromod.MacroModClient;
import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.model.*;
import com.example.macromod.pathfinding.MovementHelper;
import com.example.macromod.pathfinding.PathHandler;
import com.example.macromod.pathfinding.PathFinder;
import com.example.macromod.pathfinding.SmoothAim;

import java.util.HashSet;
import com.example.macromod.pathfinding.goal.ExactGoal;
import com.example.macromod.util.BlockUtils;
import com.example.macromod.util.PlayerUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
    private long lastMineTime;
    // Anti-cheat: randomised aim point within the target block face
    private static final Random MINE_RAND = new Random();
    private Vec3d   miningAimPoint           = null;
    private BlockPos miningAimTargetPos       = null;
    private long    miningAimRefreshMs        = 0L;
    private long    miningAimRefreshInterval  = 500L;

    // ─── Attack ─────────────────────────────────────────────────
    private long lastAttackMs = 0L;
    private LivingEntity attackTarget = null;
    private long attackChaseStartMs = -1L;
    private static final long ATTACK_COOLDOWN_MS = 640L;
    private static final long ATTACK_CHASE_TIMEOUT_MS = 4000L;
    // Random aim within bounding box (anti-cheat)
    private static final Random ATTACK_RAND = new Random();
    private Vec3d attackAimPoint = null;
    private long attackAimRefreshMs = 0L;
    private long attackAimRefreshInterval = 700L;

    // ─── Chunk loading ──────────────────────────────────────────
    private long chunkWaitStartMs = -1L;
    // ─── Radius scan ───────────────────────────────────────────────
    /** Set once when entering the MINING state for a step; cleared on step advance. */
    private boolean radiusScanDone = false;
    // ─── Statistics ─────────────────────────────────────────────
    private int blocksMinedTotal;
    private int blocksSkippedTotal;
    private long startTime;
    private double totalDistance;
    private Vec3d lastDistCheckPos;

    // ─── State before pause ─────────────────────────────────────
    private MacroState stateBeforePause;

    public MacroExecutor(SmoothAim smoothAim) {
        this.movementHelper = new MovementHelper(smoothAim);
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
     * Stops the macro execution immediately, releasing all inputs.
     */
    public void stop() {
        if (state == MacroState.IDLE) {
            sendMessage("macromod.chat.no_active_macro", Formatting.YELLOW);
            return;
        }

        movementHelper.releaseAllInputs();
        isMiningBlock = false;
        attackTarget = null;
        attackChaseStartMs = -1L;

        // Print stats if we ran for a while
        if (currentMacro != null && startTime > 0) {
            printStats();
        }

        state = MacroState.IDLE;
        currentMacro = null;
        currentPath = null;
        precomputedPaths = null;
        precomputeFromPos = null;
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

        // Track distance
        if (lastDistCheckPos != null) {
            totalDistance += player.getPos().distanceTo(lastDistCheckPos);
        }
        lastDistCheckPos = player.getPos();

        // Safety checks
        if (currentMacro.getConfig().isStopOnDanger() && PlayerUtils.isInDanger(player)) {
            handleDanger(player);
            return;
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
            case NEXT_STEP    -> tickNextStep();
            default -> { }
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
            LOGGER.info("Path pre-computation done ({} steps)", currentMacro.getSteps().size());
            state = MacroState.PATHFINDING;
            return;
        }

        MacroStep step = currentMacro.getSteps().get(precomputeIndex);
        BlockPos goal  = step.getDestination();
        BlockPos from  = precomputeFromPos != null ? precomputeFromPos : player.getBlockPos();

        if (!BlockUtils.isChunkLoaded(world, goal)) {
            // Cannot path to unloaded chunk yet — leave as null, live fallback will handle it
            LOGGER.debug("Pre-compute skip step {} (chunk not loaded)", precomputeIndex);
        } else {
            List<BlockPos> path = null;
            PathHandler pathHandler = MacroModClient.getPathHandler();
            if (pathHandler != null) {
                List<BaseBlockPos> sp = pathHandler.findPath(
                    new BaseBlockPos(from.getX(), from.getY(), from.getZ()),
                    new ExactGoal(new BaseBlockPos(goal.getX(), goal.getY(), goal.getZ())),
                    500L
                );
                path = toBlockPosPath(sp);
            }
            if (path == null || path.isEmpty()) {
                path = fallbackPathFinder.findPath(from, goal, world);
            }
            precomputedPaths.set(precomputeIndex, path);
            LOGGER.debug("Pre-computed path for step {}: {} nodes", precomputeIndex,
                    path != null ? path.size() : 0);
        }

        precomputeFromPos = goal; // next path starts from this step's destination
        precomputeIndex++;

        if (precomputeIndex >= currentMacro.getSteps().size()) {
            LOGGER.info("Path pre-computation done ({} steps)", currentMacro.getSteps().size());
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

        // Already at destination?
        if (PlayerUtils.isArrived(player, goal, currentMacro.getConfig().getArrivalRadius())) {
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
                long timeoutMs = Math.max(1000L, currentMacro.getConfig().getMoveTimeout());
                List<BaseBlockPos> sp = pathHandler.findPath(
                    new BaseBlockPos(player.getBlockPos().getX(), player.getBlockPos().getY(), player.getBlockPos().getZ()),
                    new ExactGoal(new BaseBlockPos(goal.getX(), goal.getY(), goal.getZ())),
                    timeoutMs
                );
                path = toBlockPosPath(sp);
            }
            if (path == null || path.isEmpty()) {
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
        lastPosition = player.getPos();
        movementHelper.resetJumpState();
        state = MacroState.MOVING;
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
            movementHelper.releaseAllInputs();
            state = MacroState.MINING;
            currentBlockTargetIndex = 0;
            isMiningBlock = false;
            return;
        }

        // Timeout check per tick for movement only (not mining) to prevent getting stuck indefinitely
        if (System.currentTimeMillis() - moveStartMs > 60000) {
            LOGGER.warn("Navigation timeout at step {}", currentStepIndex);
            sendMessage("macromod.chat.timeout", Formatting.RED);
            movementHelper.releaseAllInputs();
            state = MacroState.ERROR;
            return;
        }

        // Stuck detection (no movement in 3 seconds)
        if (lastPosition != null) {
            double moved = PlayerUtils.horizontalDistanceTo(player, lastPosition);
            if (moved < 0.5) {
                if (stuckSince < 0) stuckSince = System.currentTimeMillis();
            } else {
                stuckSince = -1L;
                lastPosition = player.getPos();
            }
        }

        if (stuckSince >= 0 && System.currentTimeMillis() - stuckSince > 3000) {
            LOGGER.warn("Player stuck, recalculating path");
            sendMessage("macromod.chat.stuck", Formatting.YELLOW);
            stuckSince = -1L;
            // Clear cached path so it's re-computed from the current (actual) position
            if (precomputedPaths != null && currentStepIndex < precomputedPaths.size()) {
                precomputedPaths.set(currentStepIndex, null);
            }
            state = MacroState.PATHFINDING;
            movementHelper.releaseAllInputs();
            return;
        }

        // Follow path — keep forward key held through all waypoints for smooth momentum
        if (currentPath != null && currentPathIndex < currentPath.size()) {
            BlockPos nextWaypoint = currentPath.get(currentPathIndex);

            // Advance to next waypoint when player reaches the center of the current one
            if (movementHelper.hasReachedWaypoint(player, nextWaypoint)) {
                currentPathIndex++;
                if (currentPathIndex >= currentPath.size()) {
                    // Reached the end of the path
                    movementHelper.releaseAllInputs();
                    state = MacroState.MINING;
                    currentBlockTargetIndex = 0;
                    isMiningBlock = false;
                    return;
                }
                nextWaypoint = currentPath.get(currentPathIndex);
            }

            // Look-ahead skip: if the player has overshot the current waypoint and is
            // already past it toward the next one, advance the index without backtracking.
            // Uses dot product: if projection of player-position onto curr→next exceeds
            // the segment length, the player is already beyond the current node.
            while (currentPathIndex < currentPath.size() - 1) {
                Vec3d currCenter = net.minecraft.util.math.Vec3d.ofCenter(currentPath.get(currentPathIndex));
                Vec3d nextCenter = net.minecraft.util.math.Vec3d.ofCenter(currentPath.get(currentPathIndex + 1));
                Vec3d seg        = nextCenter.subtract(currCenter);
                Vec3d toPlayer   = player.getPos().subtract(currCenter);
                double dot       = seg.dotProduct(toPlayer);
                double segLenSq  = seg.dotProduct(seg);
                if (dot > segLenSq * 0.85) {   // 85% through segment = skip
                    currentPathIndex++;
                    nextWaypoint = currentPath.get(currentPathIndex);
                } else {
                    break;
                }
            }

            // Jump over 1-block obstacles and handle horizontal stalls
            movementHelper.handleJump(player, nextWaypoint);
            // Smoothly steer and hold forward — no key release between waypoints
            movementHelper.moveTowards(player, nextWaypoint);
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

        // ── Radius scan: on first entry for this step, find matching blocks nearby ───
        if (!radiusScanDone) {
            radiusScanDone = true;
            if (!step.getTargets().isEmpty()) {
                scanRadiusTargets(step, world);
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

        // Find next unprocessed target
        while (currentBlockTargetIndex < step.getTargets().size()) {
            BlockTarget target = step.getTargets().get(currentBlockTargetIndex);

            if (target.isProcessed()) {
                currentBlockTargetIndex++;
                continue;
            }

            // Block verification
            String actualBlockId = BlockUtils.getBlockId(world, target.getPos());

            // If block is already air, it's already mined
            if (BlockUtils.isAir(world, target.getPos())) {
                target.setMined(true);
                blocksMinedTotal++;
                currentBlockTargetIndex++;
                isMiningBlock = false;
                miningDelayEndMs = System.currentTimeMillis() + currentMacro.getConfig().getMiningDelay();
                continue;
            }

            // Check block type match
            if (!actualBlockId.equals(target.getBlockId())) {
                if (currentMacro.getConfig().isSkipMismatch()) {
                    target.setSkipped(true);
                    blocksSkippedTotal++;
                    currentBlockTargetIndex++;
                    isMiningBlock = false;
                    continue;
                }
            }

            // Check reach
            Vec3d eyePos = player.getEyePos();
            double reachDist = player.getBlockInteractionRange();
            if (!BlockUtils.isWithinReach(eyePos, target.getPos(), reachDist)) {
                // Too far — skip for now
                movementHelper.forwardToBlock(player, target.getPos());
                target.setSkipped(true);
                blocksSkippedTotal++;
                currentBlockTargetIndex++;
                isMiningBlock = false;
                continue;
            }

            // LOS check: skip blocks hidden behind other blocks
            if (!BlockUtils.hasLineOfSight(world, player.getEyePos(), target.getPos())) {
                target.setSkipped(true);
                blocksSkippedTotal++;
                currentBlockTargetIndex++;
                isMiningBlock = false;
                continue;
            }

            // ── Anti-cheat: randomise aim point within the closest block face ──
            boolean newTarget = !target.getPos().equals(miningAimTargetPos);
            long now = System.currentTimeMillis();
            if (newTarget || miningAimPoint == null || now - miningAimRefreshMs > miningAimRefreshInterval) {
                miningAimTargetPos       = target.getPos();
                miningAimPoint           = randomPointOnFace(player.getEyePos(), target.getPos());
                miningAimRefreshMs       = now;
                miningAimRefreshInterval = 400L + MINE_RAND.nextInt(500); // 400–900 ms
            }

            // Smoothly look at the randomised aim point
            movementHelper.lookAt(player, miningAimPoint, 0.10f);

            // Wait until roughly aligned to avoid starting mining at wrong face angles.
            if (!movementHelper.isLookingAt(player, miningAimPoint, 10.0f)) {
                return;
            }

            // Wait for the game's crosshair to actually be on the target block.
            // If after aligning the block still isn't targeted, it’s obstructed — skip it.
            HitResult crosshair = client.crosshairTarget;
            if (!(crosshair instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(target.getPos())) {
                // Give one tick of grace; if still wrong next tick we return again until aligned.
                return;
            }

            // Start or continue mining
            Direction face = BlockUtils.getClosestFace(eyePos, target.getPos());
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (interactionManager == null) return;

            if (!isMiningBlock) {
                // Start mining
                interactionManager.attackBlock(target.getPos(), face);
                isMiningBlock = true;
                lastMineTime = System.currentTimeMillis();
            } else {
                // Continue mining
                interactionManager.updateBlockBreakingProgress(target.getPos(), face);

                // Check if block is now air (mined successfully)
                if (BlockUtils.isAir(world, target.getPos())) {
                    target.setMined(true);
                    blocksMinedTotal++;
                    currentBlockTargetIndex++;
                    isMiningBlock = false;
                    miningAimPoint = null; // force re-randomise on next block
                    miningDelayEndMs = System.currentTimeMillis() + currentMacro.getConfig().getMiningDelay();
                }

                // Stuck mining timeout (5 seconds)
                if (System.currentTimeMillis() - lastMineTime > 5000) {
                    LOGGER.warn("Stuck mining block at {}, skipping", target.getPos());
                    target.setSkipped(true);
                    blocksSkippedTotal++;
                    currentBlockTargetIndex++;
                    isMiningBlock = false;
                }
            }
            return; // Only process one block per tick
        }

        // All targets processed
        advanceToNextStep();
    }

    private void tickNextStep() {
        advanceToNextStep();
    }

    /**
     * Scans nearby entities and attacks/chases the nearest valid target.
     * Only chases (overrides movement) while in MINING state to avoid disrupting pathfinding.
     */
    private void tickAttack(ClientPlayerEntity player, ClientWorld world, MinecraftClient client) {
        MacroConfig cfg = currentMacro.getConfig();
        float range = cfg.getAttackRange();

        // Clear dead / out-of-expanded-range target
        if (attackTarget != null && (attackTarget.isDead() || attackTarget.getHealth() <= 0
                || player.squaredDistanceTo(attackTarget) > (range * 2) * (range * 2))) {
            attackTarget = null;
            attackChaseStartMs = -1L;
        }
        // Chase timeout
        if (attackTarget != null && attackChaseStartMs > 0
                && System.currentTimeMillis() - attackChaseStartMs > ATTACK_CHASE_TIMEOUT_MS) {
            attackTarget = null;
            attackChaseStartMs = -1L;
        }

        // Find new target if needed
        if (attackTarget == null) {
            Box box = player.getBoundingBox().expand(range);
            List<LivingEntity> candidates = world.getEntitiesByClass(
                    LivingEntity.class, box, e -> {
                        if (e == player || e.isDead() || e.getHealth() <= 0) return false;
                        if (cfg.isAttackWhitelistOnly()) {
                            String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
                            return cfg.getAttackWhitelist().contains(id);
                        }
                        // "All" mode: attack any mob but not other players
                        return !(e instanceof net.minecraft.entity.player.PlayerEntity);
                    }
            );
            if (candidates.isEmpty()) return;

            LivingEntity nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (LivingEntity e : candidates) {
                double d = player.squaredDistanceTo(e);
                if (d < nearestDist) { nearestDist = d; nearest = e; }
            }
            attackTarget = nearest;
            if (attackTarget != null) {
                attackChaseStartMs = System.currentTimeMillis();
                attackAimPoint = randomPointInBox(attackTarget.getBoundingBox());
                attackAimRefreshInterval = 600L + ATTACK_RAND.nextInt(600);
                attackAimRefreshMs = System.currentTimeMillis();
            }
        }

        if (attackTarget == null) return;

        // Periodically re-randomize the aim point within the bounding box
        long now = System.currentTimeMillis();
        if (attackAimPoint == null || now - attackAimRefreshMs > attackAimRefreshInterval) {
            attackAimPoint = randomPointInBox(attackTarget.getBoundingBox());
            attackAimRefreshInterval = 600L + ATTACK_RAND.nextInt(600);
            attackAimRefreshMs = now;
        }

        movementHelper.lookAt(player, attackAimPoint, 1.0f);

        double dist = Math.sqrt(player.squaredDistanceTo(attackTarget));
        double reach = player.getEntityInteractionRange();

        if (dist <= reach) {
            // Attack when cooldown is up and roughly aligned
            if (now - lastAttackMs >= ATTACK_COOLDOWN_MS
                    && movementHelper.isLookingAt(player, attackAimPoint, 35.0f)
                    && client.interactionManager != null) {
                client.interactionManager.attackEntity(player, attackTarget);
                player.swingHand(Hand.MAIN_HAND);
                lastAttackMs = now;
                // Re-randomize after each hit
                attackAimPoint = randomPointInBox(attackTarget.getBoundingBox());
                attackAimRefreshMs = now;
            }
        } else if (state == MacroState.MINING) {
            // Chase during mining phase only (don't disrupt pathfinding during MOVING)
            movementHelper.moveTowards(player, attackTarget.getBlockPos());
        }
    }

    /**
     * Scans a cube of radius {@code step.getRadius()} (default 5) around the step
     * destination and appends any blocks whose ID matches one of the step's already-
     * registered targets — as long as the position isn't already listed.
     *
     * <p>Blocks that aren't in reach when the player arrives are still added;
     * {@code tickMining} will call {@code forwardToBlock} to close the gap.</p>
     */
    private void scanRadiusTargets(MacroStep step, ClientWorld world) {
        int radius = step.getRadius();
        if (radius <= 0) return;

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

        BlockPos center = step.getDestination();
        int added = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = center.add(dx, dy, dz);
                    if (knownPositions.contains(candidate)) continue;
                    if (BlockUtils.isAir(world, candidate)) continue;
                    String id = BlockUtils.getBlockId(world, candidate);
                    if (wantedIds.contains(id)) {
                        step.addTarget(new BlockTarget(candidate, id));
                        knownPositions.add(candidate);
                        added++;
                    }
                }
            }
        }
        if (added > 0) {
            LOGGER.debug("Radius scan found {} extra blocks for step {}", added, currentStepIndex);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════

    private void advanceToNextStep() {
        currentStepIndex++;
        radiusScanDone = false;

        if (currentStepIndex >= currentMacro.getSteps().size()) {
            if (currentMacro.getConfig().isLoop()) {
                // Reset and loop
                currentMacro.reset();
                currentStepIndex = 0;
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
     * Returns a random point on the face of {@code blockPos} that is closest to {@code eyePos}.
     * The point is inset 0.05 from each edge to avoid face boundaries, and Y is
     * constrained to the 15%–85% range of the face height — matching natural human aim.
     */
    private Vec3d randomPointOnFace(Vec3d eyePos, BlockPos blockPos) {
        double bx = blockPos.getX();
        double by = blockPos.getY();
        double bz = blockPos.getZ();
        final double INSET = 0.05;

        // Determine the closest face axis to the eye
        double cx = bx + 0.5, cy = by + 0.5, cz = bz + 0.5;
        double dx = Math.abs(eyePos.x - cx);
        double dy = Math.abs(eyePos.y - cy);
        double dz = Math.abs(eyePos.z - cz);

        if (dx >= dy && dx >= dz) {
            // X face (east or west)
            double faceX = eyePos.x > cx ? bx + 1.0 - INSET : bx + INSET;
            double randY = by + 0.15 + MINE_RAND.nextDouble() * 0.70; // 15%–85% height
            double randZ = bz + INSET + MINE_RAND.nextDouble() * (1.0 - 2 * INSET);
            return new Vec3d(faceX, randY, randZ);
        } else if (dz >= dy) {
            // Z face (north or south)
            double faceZ = eyePos.z > cz ? bz + 1.0 - INSET : bz + INSET;
            double randX = bx + INSET + MINE_RAND.nextDouble() * (1.0 - 2 * INSET);
            double randY = by + 0.15 + MINE_RAND.nextDouble() * 0.70;
            return new Vec3d(randX, randY, faceZ);
        } else {
            // Y face (top or bottom)
            double faceY = eyePos.y > cy ? by + 1.0 - INSET : by + INSET;
            double randX = bx + INSET + MINE_RAND.nextDouble() * (1.0 - 2 * INSET);
            double randZ = bz + INSET + MINE_RAND.nextDouble() * (1.0 - 2 * INSET);
            return new Vec3d(randX, faceY, randZ);
        }
    }
}
