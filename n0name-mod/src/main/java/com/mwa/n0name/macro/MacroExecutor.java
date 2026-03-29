package com.mwa.n0name.macro;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.movement.MovementController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;

/**
 * Executes a macro step-by-step.
 * Movement steps use MovementController + pathfinding.
 * Other steps simulate clicks/key presses or schedule waits.
 */
public class MacroExecutor {

    public enum State { IDLE, RUNNING, PAUSED }

    private State state = State.IDLE;
    private Macro currentMacro;
    private int currentStepIndex;
    private int delayCounter;
    private int loopCount;

    // Movement for WALK/SPRINT steps
    private final MovementController movementController = new MovementController();
    private boolean waitingForMovement = false;
    // Direct walk movement (replaces pathfinder-based movement)
    private BlockPos walkTarget = null;
    private int walkTimeoutTicks = 0;
    private static final int WALK_TIMEOUT_TICKS = 100; // 5 seconds max per walk step

    // Mine-wait state: hold attack until target block breaks
    private boolean waitingForMine = false;
    private BlockPos miningTarget = null;
    private int miningTimeoutTicks = 0;
    private static final int MINE_TIMEOUT_TICKS = 400; // 20s max

    // Click/action timing
    private int actionCooldown = 0;
    private static final int ACTION_COOLDOWN_TICKS = 4;

    // Status
    private String statusText = "Idle";

    public State getState() { return state; }
    public boolean isRunning() { return state == State.RUNNING; }
    public boolean isPaused() { return state == State.PAUSED; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public int getLoopCount() { return loopCount; }
    public String getStatusText() { return statusText; }
    public Macro getCurrentMacro() { return currentMacro; }
    public MovementController getMovementController() { return movementController; }

    public void start(Macro macro) {
        if (macro == null || macro.getSteps().isEmpty()) {
            DebugLogger.log("MacroExecutor", "Cannot start: macro is null or empty");
            return;
        }
        currentMacro = macro;
        currentStepIndex = 0;
        delayCounter = 0;
        loopCount = 0;
        waitingForMovement = false;
        actionCooldown = 0;
        state = State.RUNNING;
        statusText = "Running: " + macro.getName();
        DebugLogger.log("MacroExecutor", "Started macro: " + macro.getName() + " (" + macro.stepCount() + " steps)");
    }

    public void stop() {
        waitingForMovement = false;
        walkTarget = null;
        walkTimeoutTicks = 0;
        waitingForMine = false;
        miningTarget = null;
        miningTimeoutTicks = 0;
        // Release movement keys
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.attackKey.setPressed(false);
        }
        state = State.IDLE;
        statusText = "Idle";
        DebugLogger.log("MacroExecutor", "Stopped");
    }

    public void pause() {
        if (state == State.RUNNING) {
            state = State.PAUSED;
            movementController.stop();
            statusText = "Paused at step " + (currentStepIndex + 1);
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            state = State.RUNNING;
            statusText = "Resumed";
        }
    }

    public void frameUpdate() {
        if (state == State.RUNNING && waitingForMovement) {
            movementController.frameUpdate();
        }
    }

    public void tick() {
        if (state != State.RUNNING) return;
        if (currentMacro == null) { stop(); return; }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Handle delay between steps
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        // Handle mine-wait: hold attack until block breaks
        if (waitingForMine) {
            if (miningTarget == null || client.world == null) {
                client.options.attackKey.setPressed(false);
                waitingForMine = false;
                advanceStep();
                return;
            }
            // Block gone → advance
            if (client.world.getBlockState(miningTarget).isAir()) {
                client.options.attackKey.setPressed(false);
                waitingForMine = false;
                miningTarget = null;
                advanceStep();
                return;
            }
            if (--miningTimeoutTicks <= 0) {
                client.options.attackKey.setPressed(false);
                waitingForMine = false;
                miningTarget = null;
                DebugLogger.log("MacroExecutor", "Mine timeout at step " + (currentStepIndex + 1));
                advanceStep();
                return;
            }
            // Keep holding attack and aiming at the block
            aimAtBlock(player, miningTarget);
            client.options.attackKey.setPressed(true);
            return;
        }

        // Handle ongoing movement
        if (waitingForMovement) {
            if (walkTarget == null) {
                waitingForMovement = false;
                advanceStep();
                return;
            }

            GameOptions opt = client.options;
            BlockPos currentPos = player.getBlockPos();

            // Check arrival by block position only (smoother than exact coordinate matching)
            if (currentPos.equals(walkTarget)) {
                opt.forwardKey.setPressed(false);
                opt.jumpKey.setPressed(false);
                walkTarget = null;
                waitingForMovement = false;
                advanceStep();
                return;
            }

            // Timeout check
            if (--walkTimeoutTicks <= 0) {
                opt.forwardKey.setPressed(false);
                opt.jumpKey.setPressed(false);
                DebugLogger.log("MacroExecutor", "Walk timeout at step " + (currentStepIndex + 1));
                walkTarget = null;
                waitingForMovement = false;
                advanceStep();
                return;
            }

            // Aim toward target block center
            double dx = walkTarget.getX() + 0.5 - player.getX();
            double dz = walkTarget.getZ() + 0.5 - player.getZ();
            float yaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
            player.setYaw(yaw);

            // Hold forward key
            opt.forwardKey.setPressed(true);

            // Jump if the target block is higher
            opt.jumpKey.setPressed(walkTarget.getY() > currentPos.getY());

            return;
        }

        // Action cooldown
        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        // Execute current step
        if (currentStepIndex >= currentMacro.stepCount()) {
            handleMacroEnd();
            return;
        }

        MacroStep step = currentMacro.getSteps().get(currentStepIndex);
        statusText = "Step " + (currentStepIndex + 1) + "/" + currentMacro.stepCount() + ": " + step.getType().label;

        executeStep(step, player, client);
    }

    private void executeStep(MacroStep step, ClientPlayerEntity player, MinecraftClient client) {
        switch (step.getType()) {
            case WALK -> executeMovement(step, player);
            case TELEPORT -> {
                // Teleport is recording-only, on playback just walk there
                executeMovement(step, player);
            }
            case MINE -> {
                // Look at block and keep attacking until it breaks
                BlockPos target = new BlockPos(step.getBlockX(), step.getBlockY(), step.getBlockZ());
                if (client.world != null && client.world.getBlockState(target).isAir()) {
                    // Already broken, skip
                    advanceStep();
                } else {
                    aimAtBlock(player, target);
                    client.options.attackKey.setPressed(true);
                    miningTarget = target;
                    miningTimeoutTicks = MINE_TIMEOUT_TICKS;
                    waitingForMine = true;
                }
            }
            case INTERACT, CLICK, USE_ITEM -> {
                GameOptions opt = client.options;
                opt.useKey.setPressed(true);
                actionCooldown = ACTION_COOLDOWN_TICKS;
                advanceStep();
            }
            case SELECT_SLOT -> {
                String param = step.getParam();
                try {
                    int slot = Integer.parseInt(param);
                    if (slot >= 0 && slot <= 8) {
                        player.getInventory().setSelectedSlot(slot);
                    }
                } catch (NumberFormatException ignored) {}
                advanceStep();
            }
            case ATTACK -> {
                GameOptions opt = client.options;
                opt.attackKey.setPressed(true);
                actionCooldown = ACTION_COOLDOWN_TICKS;
                advanceStep();
            }
            case WAIT -> {
                delayCounter = Math.max(step.getDelayTicks(), 1);
                advanceStepAfterDelay();
            }
            case LOOP -> {
                // Jump back to step 0
                currentStepIndex = 0;
                loopCount++;
                DebugLogger.log("MacroExecutor", "Loop iteration " + loopCount);
            }
            case STOP -> {
                stop();
            }
            default -> {
                // Unhandled step types: just advance
                DebugLogger.log("MacroExecutor", "Skipping unhandled step type: " + step.getType());
                advanceStep();
            }
        }
    }

    private void executeMovement(MacroStep step, ClientPlayerEntity player) {
        BlockPos target = new BlockPos(step.getBlockX(), step.getBlockY(), step.getBlockZ());
        BlockPos from = player.getBlockPos();

        if (from.equals(target)) {
            advanceStep();
            return;
        }

        walkTarget = target;
        walkTimeoutTicks = WALK_TIMEOUT_TICKS;
        waitingForMovement = true;
    }

    private void executeTeleport(MacroStep step, ClientPlayerEntity player, BlockPos target) {
        // Swap to a specific item, aim at the block and right click to teleport to it

        GameOptions opt = MinecraftClient.getInstance().options;
        opt.useKey.setPressed(true);
        actionCooldown = ACTION_COOLDOWN_TICKS;
        delayCounter = Math.max(step.getDelayTicks(), 10);
        advanceStepAfterDelay();
    }

    private void aimAtBlock(ClientPlayerEntity player, BlockPos target) {
        double dx = target.getX() + 0.5 - player.getX();
        double dy = target.getY() + 0.5 - player.getEyeY();
        double dz = target.getZ() + 0.5 - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float)(Math.toDegrees(Math.atan2(-dy, dist)));
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private void advanceStep() {
        currentStepIndex++;
        actionCooldown = 0;
        releaseActionKeys();
    }

    private void advanceStepAfterDelay() {
        // Step will advance after delayCounter reaches 0 on next tick
        // We schedule the advance for next iteration
        currentStepIndex++;
    }

    private void handleMacroEnd() {
        if (currentMacro.isRepeat()) {
            currentStepIndex = 0;
            loopCount++;
            statusText = "Loop " + loopCount;
            DebugLogger.log("MacroExecutor", "Repeating macro (loop " + loopCount + ")");
        } else {
            statusText = "Completed";
            DebugLogger.log("MacroExecutor", "Macro completed");
            stop();
        }
    }

    private void releaseActionKeys() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            client.options.attackKey.setPressed(false);
            client.options.useKey.setPressed(false);
            client.options.sneakKey.setPressed(false);
        }
    }
}
