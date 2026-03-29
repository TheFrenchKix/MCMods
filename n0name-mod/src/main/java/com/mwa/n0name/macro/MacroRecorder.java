package com.mwa.n0name.macro;

import com.mwa.n0name.DebugLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * Records player actions into a Macro.
 *
 * Recording rules:
 *   SHIFT (sneaking) → WALK step with current position/rotation
 *   Left-click while looking at block → MINE step (block pos, block type, held item)
 *   Right-click → TELEPORT step (position before and after, held item)
 */
public class MacroRecorder {

    private enum State { IDLE, RECORDING }

    private State state = State.IDLE;
    private Macro currentMacro;
    private MacroType recordingType = MacroType.CUSTOM;

    // Recording state
    private boolean wasSneaking = false;
    private BlockPos lastRecordedPos = null;
    private int recordCooldown = 0;
    private static final int WALK_RECORD_INTERVAL = 8;   // min ticks between walk nodes
    private static final double MIN_WALK_DIST_SQ = 0.64; // 0.8 blocks squared

    // Click tracking
    private boolean leftClickPending = false;
    private boolean rightClickPending = false;
    private double preClickX, preClickY, preClickZ;

    public boolean isRecording() { return state == State.RECORDING; }
    public Macro getCurrentMacro() { return currentMacro; }
    public MacroType getRecordingType() { return recordingType; }
    public void setRecordingType(MacroType type) { this.recordingType = type; }

    public void startRecording(String name) {
        currentMacro = new Macro(name, recordingType);
        state = State.RECORDING;
        wasSneaking = false;
        lastRecordedPos = null;
        recordCooldown = 0;
        leftClickPending = false;
        rightClickPending = false;
        DebugLogger.log("MacroRecorder", "Started recording: " + name);
    }

    public Macro stopRecording() {
        if (state != State.RECORDING) return null;
        state = State.IDLE;
        Macro result = currentMacro;
        currentMacro = null;
        DebugLogger.log("MacroRecorder", "Stopped recording: " + (result != null ? result.stepCount() + " steps" : "null"));
        return result;
    }

    /**
     * Called externally when the player left-clicks (attack/mine).
     */
    public void onLeftClick() {
        if (state == State.RECORDING) {
            leftClickPending = true;
            cachePlayerPosition();
        }
    }

    /**
     * Called externally when the player right-clicks (use/interact).
     */
    public void onRightClick() {
        if (state == State.RECORDING) {
            rightClickPending = true;
            cachePlayerPosition();
        }
    }

    private void cachePlayerPosition() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            preClickX = player.getX();
            preClickY = player.getY();
            preClickZ = player.getZ();
        }
    }

    public void tick() {
        if (state != State.RECORDING) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Handle left-click → MINE step
        if (leftClickPending) {
            leftClickPending = false;
            BlockPos targetBlock = null;
            if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bhr) {
                targetBlock = bhr.getBlockPos();
            }
            if (targetBlock != null) {
                String blockType = client.world != null ?
                    client.world.getBlockState(targetBlock).getBlock().getTranslationKey() : "";
                ItemStack heldItem = player.getMainHandStack();
                String itemName = heldItem.isEmpty() ? "" : heldItem.getItem().getTranslationKey();
                MacroStep step = new MacroStep(StepType.MINE,
                    targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(),
                    player.getYaw(), player.getPitch(),
                    blockType + "|" + itemName, 0);
                currentMacro.addStep(step);
                DebugLogger.log("MacroRecorder", "MINE step at " + targetBlock);
            }
        }

        // Handle right-click → TELEPORT step (records pre-click position + current item)
        if (rightClickPending) {
            rightClickPending = false;
            ItemStack heldItem = player.getMainHandStack();
            String itemName = heldItem.isEmpty() ? "" : heldItem.getItem().getTranslationKey();
            MacroStep step = new MacroStep(StepType.TELEPORT,
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(),
                itemName, 0);
            currentMacro.addStep(step);
            DebugLogger.log("MacroRecorder", "TELEPORT step at " +
                String.format("%.1f %.1f %.1f", player.getX(), player.getY(), player.getZ()));
        }

        // Handle sneak → WALK step
        // Record WALK step when sneaking and player moved to a new block (sneak = waypoint marker)
        boolean sneaking = player.isSneaking();
        if (sneaking && !wasSneaking) {
            // Rising edge of sneak: record the block the player is standing on
            BlockPos standingOn = player.getBlockPos();
            double distSq = lastRecordedPos == null ? MIN_WALK_DIST_SQ + 1
                : lastRecordedPos.getSquaredDistance(standingOn);
            if (distSq >= MIN_WALK_DIST_SQ && --recordCooldown <= 0) {
                recordCooldown = WALK_RECORD_INTERVAL;
                lastRecordedPos = standingOn;
                MacroStep step = new MacroStep(StepType.WALK,
                    standingOn.getX(), standingOn.getY(), standingOn.getZ(),
                    player.getYaw(), player.getPitch());
                currentMacro.addStep(step);
                DebugLogger.log("MacroRecorder", "WALK step at " + standingOn);
            }
        }
        wasSneaking = sneaking;
    }
}
