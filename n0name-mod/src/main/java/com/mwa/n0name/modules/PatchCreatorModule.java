package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.movement.MovementController;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.render.PathRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PatchCreator module: record, save, load, and execute player-defined routes.
 */
public class PatchCreatorModule {

    private enum State { IDLE, RECORDING, EXECUTING }

    private final MovementController movementController = new MovementController();

    private State state = State.IDLE;
    private final List<PathNode> recordedNodes = new ArrayList<>();
    private List<PathNode> executingPath = Collections.emptyList();
    private int executingIndex = 0;
    private BlockPos lastRecordedPos = null;
    private String loopingRouteName = "";

    private static final int RECORD_INTERVAL = 10;  // record every 0.5s
    private static final double MIN_MOVE_DIST = 0.5; // min blocks to record a new node
    private int recordCooldown = 0;

    // Rendering colors for recording
    private static final float[] REC_LINE_C   = {1.0f, 0.5f, 0.0f, 0.8f};
    private static final float[] REC_NODE_C   = {1.0f, 0.4f, 0.0f, 0.6f};
    private static final float[] REC_CUR_C    = {1.0f, 1.0f, 0.0f, 0.9f};
    private static final float[] REC_TARGET_C = {1.0f, 0.0f, 0.0f, 0.9f};

    // Rendering colors for executing
    private static final float[] EXE_LINE_C   = {0.0f, 0.8f, 1.0f, 0.8f};
    private static final float[] EXE_NODE_C   = {0.0f, 0.6f, 1.0f, 0.6f};
    private static final float[] EXE_CUR_C    = {1.0f, 1.0f, 0.2f, 0.9f};
    private static final float[] EXE_TARGET_C = {1.0f, 0.3f, 0.3f, 0.9f};

    public State getState() { return state; }
    public List<PathNode> getRecordedNodes() { return recordedNodes; }
    public boolean isRecording() { return state == State.RECORDING; }
    public boolean isExecuting() { return state == State.EXECUTING; }

    public void startRecording() {
        recordedNodes.clear();
        lastRecordedPos = null;
        recordCooldown = 0;
        state = State.RECORDING;
        DebugLogger.log("PatchCreator", "Recording started");
    }

    public void stopRecording() {
        state = State.IDLE;
        DebugLogger.log("PatchCreator", "Recording stopped, " + recordedNodes.size() + " nodes");
    }

    public void saveRoute(String name) {
        if (recordedNodes.isEmpty()) {
            DebugLogger.log("PatchCreator", "Nothing to save");
            return;
        }
        ModConfig.getInstance().saveRoute(name, recordedNodes);
        DebugLogger.log("PatchCreator", "Saved route '" + name + "' with " + recordedNodes.size() + " nodes");
    }

    public void executeRoute(String name) {
        List<PathNode> route = ModConfig.getInstance().getRoute(name);
        if (route.isEmpty()) {
            DebugLogger.log("PatchCreator", "Route '" + name + "' not found or empty");
            return;
        }

        executingPath = new ArrayList<>(route);
        executingIndex = 0;
        movementController.startPath(executingPath);
        state = State.EXECUTING;
        DebugLogger.log("PatchCreator", "Executing route '" + name + "' with " + route.size() + " nodes");
    }

    public void setLoopRoute(String name, boolean enabled) {
        loopingRouteName = enabled ? (name == null ? "" : name.trim()) : "";
        ModConfig cfg = ModConfig.getInstance();
        cfg.setGardenLaneRouteName(loopingRouteName);
        cfg.setGardenLaneLoopEnabled(enabled && !loopingRouteName.isEmpty());
    }

    public void stopExecution() {
        movementController.stop();
        executingPath = Collections.emptyList();
        state = State.IDLE;
        DebugLogger.log("PatchCreator", "Execution stopped");
    }

    public void tick() {
        switch (state) {
            case RECORDING -> tickRecording();
            case EXECUTING -> tickExecuting();
        }
    }

    private void tickRecording() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        if (--recordCooldown > 0) return;
        recordCooldown = RECORD_INTERVAL;

        BlockPos currentPos = player.getBlockPos();

        if (lastRecordedPos == null || !currentPos.equals(lastRecordedPos)) {
            double movedDist = lastRecordedPos == null ? MIN_MOVE_DIST + 1 :
                Math.sqrt(currentPos.getSquaredDistance(lastRecordedPos));

            if (movedDist >= MIN_MOVE_DIST) {
                recordedNodes.add(new PathNode(currentPos));
                lastRecordedPos = currentPos;
                DebugLogger.log("PatchCreator", "Recorded node " + recordedNodes.size() +
                    " at " + currentPos);
            }
        }
    }

    private void tickExecuting() {
        movementController.tick();

        MovementController.WalkState walkState = movementController.getState();
        switch (walkState) {
            case ARRIVED -> {
                ModConfig cfg = ModConfig.getInstance();
                if (cfg.isGardenLaneLoopEnabled() && !cfg.getGardenLaneRouteName().isEmpty()) {
                    executeRoute(cfg.getGardenLaneRouteName());
                    return;
                }
                DebugLogger.log("PatchCreator", "Route execution complete");
                executingPath = Collections.emptyList();
                state = State.IDLE;
            }
            case STUCK -> {
                DebugLogger.log("PatchCreator", "Stuck during route execution, stopping");
                movementController.stop();
                executingPath = Collections.emptyList();
                state = State.IDLE;
            }
        }
    }

    public void render(WorldRenderContext context) {
        if (state == State.RECORDING && !recordedNodes.isEmpty()) {
            PathRenderer.renderPath(context, recordedNodes, recordedNodes.size() - 1,
                REC_LINE_C, REC_NODE_C, REC_CUR_C, REC_TARGET_C);
        }

        if (state == State.EXECUTING && !executingPath.isEmpty()) {
            PathRenderer.renderPath(context, executingPath,
                movementController.getCurrentNodeIndex(),
                EXE_LINE_C, EXE_NODE_C, EXE_CUR_C, EXE_TARGET_C);
        }
    }
}
