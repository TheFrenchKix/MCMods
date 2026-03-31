package com.example.macromod.recording;

import com.example.macromod.MacroModClient;
import com.example.macromod.model.BlockTarget;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroStep;
import com.example.macromod.util.BlockUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the macro recording workflow: capturing waypoints and block targets
 * based on the player's current position and crosshair target.
 */
@Environment(EnvType.CLIENT)
public class MacroRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");

    private RecordingState state = RecordingState.IDLE;
    private Macro currentMacro;
    private int stepCounter;
    private String lastAddedInfo = "";

    /**
     * Starts recording a new macro with the given name.
     */
    public void startRecording(String macroName) {
        if (state != RecordingState.IDLE) {
            sendMessage("macromod.chat.already_recording", Formatting.RED);
            return;
        }
        currentMacro = new Macro(macroName);
        stepCounter = 0;
        state = RecordingState.RECORDING;
        lastAddedInfo = "";
        sendMessage("macromod.chat.recording_started", Formatting.GREEN, macroName);
        LOGGER.info("Started recording macro '{}'", macroName);
    }

    /**
     * Adds a waypoint at the player's current position.
     * Creates a new MacroStep with an auto-generated label.
     *
     * @param label optional label; if null, an auto label is generated
     */
    public void addWaypoint(String label) {
        if (state != RecordingState.RECORDING) {
            sendMessage("macromod.chat.not_recording", Formatting.RED);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        stepCounter++;
        BlockPos pos = player.getBlockPos();
        String stepLabel = label != null ? label : "Step " + stepCounter;

        MacroStep step = new MacroStep(stepLabel, pos);
        currentMacro.addStep(step);
        lastAddedInfo = "Waypoint: " + stepLabel;

        sendMessage("macromod.chat.waypoint_added", Formatting.YELLOW, pos.getX(), pos.getY(), pos.getZ());
        LOGGER.debug("Added waypoint '{}' at {}", stepLabel, pos);
    }

    /**
     * Adds the block the player is currently looking at as a target in the current (last) step.
     */
    public void addBlockTarget() {
        if (state != RecordingState.RECORDING) {
            sendMessage("macromod.chat.not_recording", Formatting.RED);
            return;
        }

        if (currentMacro.getSteps().isEmpty()) {
            sendMessage("macromod.chat.no_waypoint", Formatting.RED);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) return;

        HitResult hitResult = client.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            sendMessage("macromod.chat.no_block_targeted", Formatting.RED);
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        String blockId = BlockUtils.getBlockId(world, pos);

        BlockTarget target = new BlockTarget(pos, blockId);
        MacroStep currentStep = currentMacro.getSteps().get(currentMacro.getSteps().size() - 1);
        currentStep.addTarget(target);
        lastAddedInfo = blockId;

        sendMessage("macromod.chat.block_added", Formatting.AQUA, blockId, pos.getX(), pos.getY(), pos.getZ());
        LOGGER.debug("Added block target {} at {}", blockId, pos);
    }

    /**
     * Pauses the current recording.
     */
    public void pauseRecording() {
        if (state == RecordingState.RECORDING) {
            state = RecordingState.PAUSED;
            LOGGER.info("Recording paused");
        }
    }

    /**
     * Resumes a paused recording.
     */
    public void resumeRecording() {
        if (state == RecordingState.PAUSED) {
            state = RecordingState.RECORDING;
            LOGGER.info("Recording resumed");
        }
    }

    /**
     * Stops recording and saves the macro.
     */
    public void stopRecording() {
        if (state == RecordingState.IDLE) {
            sendMessage("macromod.chat.not_recording", Formatting.RED);
            return;
        }

        MacroModClient.getManager().save(currentMacro);
        String name = currentMacro.getName();
        int steps = currentMacro.getSteps().size();

        sendMessage("macromod.chat.recording_stopped", Formatting.GREEN, name, steps);
        LOGGER.info("Stopped recording macro '{}' with {} steps", name, steps);

        resetState();
    }

    /**
     * Cancels the current recording without saving.
     */
    public void cancelRecording() {
        if (state == RecordingState.IDLE) {
            sendMessage("macromod.chat.not_recording", Formatting.RED);
            return;
        }

        sendMessage("macromod.chat.recording_cancelled", Formatting.YELLOW);
        LOGGER.info("Recording cancelled");
        resetState();
    }

    /**
     * Returns the current recording state.
     */
    public RecordingState getState() {
        return state;
    }

    /**
     * Returns the macro currently being recorded, or null.
     */
    public Macro getCurrentMacro() {
        return currentMacro;
    }

    /**
     * Returns the last added info string (for HUD display).
     */
    public String getLastAddedInfo() {
        return lastAddedInfo;
    }

    /**
     * Returns the current step being recorded (last step), or null.
     */
    public MacroStep getCurrentStep() {
        if (currentMacro == null || currentMacro.getSteps().isEmpty()) {
            return null;
        }
        return currentMacro.getSteps().get(currentMacro.getSteps().size() - 1);
    }

    private void resetState() {
        state = RecordingState.IDLE;
        currentMacro = null;
        stepCounter = 0;
        lastAddedInfo = "";
    }

    private void sendMessage(String key, Formatting color, Object... args) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.translatable(key, args).formatted(color),
                    false
            );
        }
    }
}
