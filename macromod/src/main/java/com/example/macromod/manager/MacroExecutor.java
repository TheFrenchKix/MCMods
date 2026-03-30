package com.example.macromod.manager;

import com.example.macromod.MacroModClient;
import com.example.macromod.model.*;
import com.example.macromod.pathfinding.MovementHelper;
import com.example.macromod.pathfinding.PathFinder;
import com.example.macromod.util.BlockUtils;
import com.example.macromod.util.PlayerUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

    private final PathFinder pathFinder = new PathFinder();
    private final MovementHelper movementHelper = new MovementHelper();

    // ─── Execution state ────────────────────────────────────────
    private MacroState state = MacroState.IDLE;
    private Macro currentMacro;
    private int currentStepIndex;
    private int currentBlockTargetIndex;

    // ─── Path & movement ────────────────────────────────────────
    private List<BlockPos> currentPath;
    private int currentPathIndex;
    private int moveTicks;
    private Vec3d lastPosition;
    private int stuckTicks;

    // ─── Mining ─────────────────────────────────────────────────
    private boolean isMiningBlock;
    private int miningDelayTicks;
    private long lastMineTime;

    // ─── Chunk loading ──────────────────────────────────────────
    private int chunkWaitTicks;

    // ─── Statistics ─────────────────────────────────────────────
    private int blocksMinedTotal;
    private int blocksSkippedTotal;
    private long startTime;
    private double totalDistance;
    private Vec3d lastDistCheckPos;

    // ─── State before pause ─────────────────────────────────────
    private MacroState stateBeforePause;

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

        pathFinder.setMaxNodes(MacroModClient.getConfigManager().getConfig().getMaxPathNodes());

        // Reset stats
        blocksMinedTotal = 0;
        blocksSkippedTotal = 0;
        startTime = System.currentTimeMillis();
        totalDistance = 0;
        lastDistCheckPos = null;

        state = MacroState.PATHFINDING;
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

        // Print stats if we ran for a while
        if (currentMacro != null && startTime > 0) {
            printStats();
        }

        state = MacroState.IDLE;
        currentMacro = null;
        currentPath = null;
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

        switch (state) {
            case PATHFINDING -> tickPathfinding(player, world);
            case MOVING -> tickMoving(player, world);
            case MINING -> tickMining(player, world, client);
            case NEXT_STEP -> tickNextStep();
            default -> { }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // State tick handlers
    // ═══════════════════════════════════════════════════════════════

    private void tickPathfinding(ClientPlayerEntity player, ClientWorld world) {
        MacroStep step = getCurrentStep();
        if (step == null) {
            state = MacroState.COMPLETED;
            return;
        }

        BlockPos goal = step.getDestination();

        // Check if chunk is loaded
        if (!BlockUtils.isChunkLoaded(world, goal)) {
            chunkWaitTicks++;
            if (chunkWaitTicks > 100) {
                LOGGER.warn("Chunk not loaded after 100 ticks, skipping step");
                sendMessage("macromod.chat.path_not_found", Formatting.RED);
                advanceToNextStep();
                return;
            }
            if (chunkWaitTicks == 1) {
                sendMessage("macromod.chat.chunk_not_loaded", Formatting.YELLOW);
            }
            return;
        }
        chunkWaitTicks = 0;

        // Already at destination?
        if (PlayerUtils.isArrived(player, goal, currentMacro.getConfig().getArrivalRadius())) {
            state = MacroState.MINING;
            currentBlockTargetIndex = 0;
            isMiningBlock = false;
            return;
        }

        // Find path
        currentPath = pathFinder.findPath(player.getBlockPos(), goal, world);
        if (currentPath == null || currentPath.isEmpty()) {
            LOGGER.warn("No path to step {} destination {}", currentStepIndex, goal);
            sendMessage("macromod.chat.path_not_found", Formatting.RED);
            advanceToNextStep();
            return;
        }

        currentPathIndex = 0;
        moveTicks = 0;
        stuckTicks = 0;
        lastPosition = player.getPos();
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

        // Timeout check
        moveTicks++;
        if (moveTicks > currentMacro.getConfig().getMoveTimeout()) {
            LOGGER.warn("Navigation timeout at step {}", currentStepIndex);
            sendMessage("macromod.chat.timeout", Formatting.RED);
            movementHelper.releaseAllInputs();
            state = MacroState.ERROR;
            return;
        }

        // Stuck detection (no movement in 60 ticks = 3 seconds)
        if (lastPosition != null) {
            double moved = PlayerUtils.horizontalDistanceTo(player, lastPosition);
            if (moved < 0.5) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
                lastPosition = player.getPos();
            }
        }

        if (stuckTicks > 60) {
            LOGGER.warn("Player stuck, recalculating path");
            sendMessage("macromod.chat.stuck", Formatting.YELLOW);
            stuckTicks = 0;
            state = MacroState.PATHFINDING;
            movementHelper.releaseAllInputs();
            return;
        }

        // Anti-fall safety: check if block below is void or dangerous
        BlockPos belowPlayer = player.getBlockPos().down();
        if (world.getBlockState(belowPlayer).isAir() || BlockUtils.isDangerous(world, belowPlayer)) {
            // Check two blocks below too
            BlockPos twoBelowPlayer = belowPlayer.down();
            if (world.getBlockState(twoBelowPlayer).isAir()) {
                LOGGER.warn("Void/danger detected below player, pausing");
                pause();
                return;
            }
        }

        // Follow path
        if (currentPath != null && currentPathIndex < currentPath.size()) {
            BlockPos nextWaypoint = currentPath.get(currentPathIndex);

            if (PlayerUtils.isArrived(player, nextWaypoint, 1.0f)) {
                currentPathIndex++;
                if (currentPathIndex >= currentPath.size()) {
                    // Arrived at end of path
                    movementHelper.releaseAllInputs();
                    state = MacroState.MINING;
                    currentBlockTargetIndex = 0;
                    isMiningBlock = false;
                    return;
                }
                nextWaypoint = currentPath.get(currentPathIndex);
            }

            movementHelper.moveTowards(player, nextWaypoint);
        } else {
            // Path ended but not at destination — recalculate
            state = MacroState.PATHFINDING;
            movementHelper.releaseAllInputs();
        }
    }

    private void tickMining(ClientPlayerEntity player, ClientWorld world, MinecraftClient client) {
        MacroStep step = getCurrentStep();
        if (step == null || step.getTargets().isEmpty() || step.isComplete()) {
            advanceToNextStep();
            return;
        }

        // Check mining delay
        if (miningDelayTicks > 0) {
            miningDelayTicks--;
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
                miningDelayTicks = currentMacro.getConfig().getMiningDelay() / 50; // convert ms to ticks
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
                // Too far — need to move closer; mark as skipped for now
                target.setSkipped(true);
                blocksSkippedTotal++;
                currentBlockTargetIndex++;
                isMiningBlock = false;
                continue;
            }

            // Smoothly look at the block
            movementHelper.lookAt(player, target.getPos(), 0.5f);

            // Wait until we're looking roughly at the target
            if (!movementHelper.isLookingAt(player, target.getPos(), 10.0f)) {
                return; // Keep looking, try again next tick
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
                    miningDelayTicks = currentMacro.getConfig().getMiningDelay() / 50;
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

    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════

    private void advanceToNextStep() {
        currentStepIndex++;

        if (currentStepIndex >= currentMacro.getSteps().size()) {
            if (currentMacro.getConfig().isLoop()) {
                // Reset and loop
                currentMacro.reset();
                currentStepIndex = 0;
                state = MacroState.PATHFINDING;
                LOGGER.info("Macro loop: restarting from step 0");
            } else {
                state = MacroState.COMPLETED;
                movementHelper.releaseAllInputs();
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
     * Returns the current path (for rendering preview).
     */
    public List<BlockPos> getCurrentPath() {
        return currentPath;
    }

    /**
     * Returns true if a macro is actively running (not idle/completed/error).
     */
    public boolean isRunning() {
        return state != MacroState.IDLE && state != MacroState.COMPLETED && state != MacroState.ERROR;
    }
}
