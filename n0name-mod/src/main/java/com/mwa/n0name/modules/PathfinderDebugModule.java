package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.movement.MovementController;
import com.mwa.n0name.pathfinding.BlockPosPathfinder;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.pathfinding.PathfindingService;
import com.mwa.n0name.pathfinding.WalkabilityChecker;
import com.mwa.n0name.render.N0nameRenderLayers;
import com.mwa.n0name.render.PathRenderer;
import com.mwa.n0name.render.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manual pathfinder debugger workflow:
 * set start, set stop, go to start, then compute+walk to stop with detailed logs.
 */
public class PathfinderDebugModule {

    private enum State {
        IDLE,
        GOING_TO_START,
        GOING_TO_STOP
    }

    private enum Verbosity {
        SUMMARY,
        FULL
    }

    private final MovementController movementController = new MovementController();
    private State state = State.IDLE;

    private BlockPos startPos;
    private BlockPos stopPos;
        private BlockPos coordsGoal;
    private List<PathNode> currentPath = Collections.emptyList();
    private List<PathNode> lastPathToStart = Collections.emptyList();
    private List<PathNode> lastPathToStop = Collections.emptyList();
    private int logTickCooldown = 0;
    private Verbosity verbosity = Verbosity.SUMMARY;
    private boolean snapToNearestWalkable = true;

    private static final float[] START_LINE_C = {1.0f, 0.65f, 0.2f, 0.9f};
    private static final float[] START_NODE_C = {1.0f, 0.5f, 0.2f, 0.7f};
    private static final float[] START_CUR_C = {1.0f, 1.0f, 0.25f, 0.9f};
    private static final float[] START_TGT_C = {1.0f, 0.4f, 0.2f, 0.9f};

    private static final float[] STOP_LINE_C = {0.2f, 0.85f, 1.0f, 0.9f};
    private static final float[] STOP_NODE_C = {0.25f, 0.65f, 1.0f, 0.7f};
    private static final float[] STOP_CUR_C = {1.0f, 1.0f, 0.25f, 0.9f};
    private static final float[] STOP_TGT_C = {0.3f, 1.0f, 0.45f, 0.9f};

    public void setStartFromPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        startPos = player.getBlockPos().toImmutable();
        DebugLogger.info("[PathfinderDebug] Start set to " + startPos.toShortString());
    }

    public void setStopFromPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        stopPos = player.getBlockPos().toImmutable();
        DebugLogger.info("[PathfinderDebug] Stop set to " + stopPos.toShortString());
    }

    public void goToCoords(int x, int y, int z) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        coordsGoal = new BlockPos(x, y, z);
        BlockPos from = sanitizeAnchor(client, player.getBlockPos(), "player");
        if (from == null) {
            DebugLogger.info("PathfinderDebug: Player position is not walkable");
            return;
        }
        BlockPos saneGoal = sanitizeAnchor(client, coordsGoal, "coords-goal");
        if (saneGoal == null) {
            DebugLogger.info("PathfinderDebug: Coords goal is not walkable, no fallback found");
            return;
        }
        coordsGoal = saneGoal;

        // Build a local walkable grid and run BlockPos A* (simple 3D grid pathfinder).
        List<BlockPos> grid = scanWalkableGrid(client, from, coordsGoal, 6, 4);
        List<BlockPos> blockPath = BlockPosPathfinder.findPath(client.world, grid, from, coordsGoal);
        if (!blockPath.isEmpty() && blockPath.size() >= 2) {
            List<PathNode> pathNodes = PathfindingService.toPathNodes(blockPath);
            logPath("coords-grid", pathNodes, from, coordsGoal);
            lastPathToStop = pathNodes;
            lastPathToStart = Collections.emptyList();
            currentPath = pathNodes;
            movementController.startBlockPath(blockPath, grid);
            state = State.GOING_TO_STOP;
            logTickCooldown = 0;
            return;
        }

        DebugLogger.info("PathfinderDebug: grid path failed, fallback to global A*");
        List<PathNode> path = findPathWithFallback(client, from, coordsGoal, "coords");
        logPath("coords", path, from, coordsGoal);
        if (path.isEmpty() || path.size() < 2) {
            DebugLogger.info("PathfinderDebug: No path found to coords " + coordsGoal.toShortString());
            stop();
            return;
        }
        lastPathToStop = path;
        lastPathToStart = Collections.emptyList();
        currentPath = path;
        movementController.startPath(path);
        state = State.GOING_TO_STOP;
        logTickCooldown = 0;
    }

    public String getCoordsGoalText() {
        return coordsGoal == null ? "Coords: not set" : "Coords: " + coordsGoal.toShortString();
    }

    public void go() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        if (startPos == null || stopPos == null) {
            DebugLogger.info("[PathfinderDebug] Missing start/stop position. Set both before GO.");
            return;
        }

        DebugLogger.info("[PathfinderDebug] GO pressed");
        DebugLogger.info("[PathfinderDebug] Start=" + startPos.toShortString() + " Stop=" + stopPos.toShortString());

        // Reset prior traces for a fresh debug session.
        lastPathToStart = Collections.emptyList();
        lastPathToStop = Collections.emptyList();

        BlockPos saneStart = sanitizeAnchor(client, startPos, "start");
        BlockPos saneStop = sanitizeAnchor(client, stopPos, "stop");
        if (saneStart == null || saneStop == null) {
            DebugLogger.info("[PathfinderDebug] Sanity checks failed. Cannot start debug run.");
            return;
        }
        startPos = saneStart;
        stopPos = saneStop;

        if (isAt(player.getBlockPos(), startPos, 2.0)) {
            startPathToStop(client, player);
            return;
        }

        List<PathNode> toStart = findPathWithFallback(client, player.getBlockPos(), startPos, "to start");
        lastPathToStart = toStart;
        logPath("to start", toStart, player.getBlockPos(), startPos);
        if (toStart.isEmpty() || toStart.size() < 2) {
            DebugLogger.info("[PathfinderDebug] Failed to find path to start position");
            stop();
            return;
        }

        currentPath = toStart;
        movementController.startPath(toStart);
        state = State.GOING_TO_START;
        logTickCooldown = 0;
    }

    public void stop() {
        movementController.stop();
        currentPath = Collections.emptyList();
        state = State.IDLE;
        DebugLogger.info("[PathfinderDebug] Stopped");
    }

    public void clearPoints() {
        stop();
        startPos = null;
        stopPos = null;
        lastPathToStart = Collections.emptyList();
        lastPathToStop = Collections.emptyList();
        DebugLogger.info("[PathfinderDebug] Cleared start/stop and cached paths");
    }

    public void tick() {
        if (state == State.IDLE) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            stop();
            return;
        }

        movementController.tick();

        if (--logTickCooldown <= 0) {
            logTickCooldown = 20;
            int idx = movementController.getCurrentNodeIndex();
            int total = currentPath == null ? 0 : currentPath.size();
            if (verbosity == Verbosity.FULL && currentPath != null && idx >= 0 && idx < currentPath.size()) {
                PathNode next = currentPath.get(idx);
                DebugLogger.info("[PathfinderDebug] State=" + state.name() + " node=" + idx + "/" + total
                    + " next=" + next.x() + "," + next.y() + "," + next.z()
                    + " player=" + player.getBlockPos().toShortString());
            } else {
                DebugLogger.info("[PathfinderDebug] State=" + state.name() + " node=" + idx + "/" + total
                    + " player=" + player.getBlockPos().toShortString());
            }
        }

        MovementController.WalkState walkState = movementController.getState();
        if (walkState == MovementController.WalkState.STUCK) {
            DebugLogger.info("[PathfinderDebug] Movement reported STUCK in state " + state.name());
            stop();
            return;
        }

        if (walkState != MovementController.WalkState.ARRIVED) return;

        if (state == State.GOING_TO_START) {
            DebugLogger.info("[PathfinderDebug] Arrived at start, computing path to stop");
            startPathToStop(client, player);
            return;
        }

        if (state == State.GOING_TO_STOP) {
            DebugLogger.info("[PathfinderDebug] Arrived at stop position");
            stop();
        }
    }

    public void frameUpdate() {
        movementController.frameUpdate();
    }

    public void render(WorldRenderContext context) {
        renderMarkers(context);

        if (state == State.GOING_TO_START && currentPath != null && !currentPath.isEmpty()) {
            PathRenderer.renderPath(context, currentPath, movementController.getCurrentNodeIndex(),
                START_LINE_C, START_NODE_C, START_CUR_C, START_TGT_C);
            return;
        }

        if (state == State.GOING_TO_STOP && currentPath != null && !currentPath.isEmpty()) {
            PathRenderer.renderPath(context, currentPath, movementController.getCurrentNodeIndex(),
                STOP_LINE_C, STOP_NODE_C, STOP_CUR_C, STOP_TGT_C);
            return;
        }

        if (!lastPathToStart.isEmpty()) {
            PathRenderer.renderPath(context, lastPathToStart, -1,
                START_LINE_C, START_NODE_C, START_CUR_C, START_TGT_C);
        }
        if (!lastPathToStop.isEmpty()) {
            PathRenderer.renderPath(context, lastPathToStop, -1,
                STOP_LINE_C, STOP_NODE_C, STOP_CUR_C, STOP_TGT_C);
        }
    }

    public String getStatusText() {
        return "State: " + state.name();
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    public String getStartText() {
        return startPos == null ? "Start: not set" : "Start: " + startPos.toShortString();
    }

    public String getStopText() {
        return stopPos == null ? "Stop: not set" : "Stop: " + stopPos.toShortString();
    }

    public String getVerbosityText() {
        return "Verbosity: " + verbosity.name();
    }

    public void cycleVerbosity(int direction) {
        Verbosity[] values = Verbosity.values();
        int dir = direction == 0 ? 1 : direction;
        int next = (verbosity.ordinal() + dir + values.length) % values.length;
        verbosity = values[next];
        DebugLogger.info("[PathfinderDebug] Verbosity set to " + verbosity.name()
            + (verbosity == Verbosity.FULL ? " (deep trace ON)" : " (deep trace OFF)"));
    }

    public boolean isSnapToNearestWalkable() {
        return snapToNearestWalkable;
    }

    public void toggleSnapToNearestWalkable() {
        snapToNearestWalkable = !snapToNearestWalkable;
        DebugLogger.info("[PathfinderDebug] Snap To Walkable: " + (snapToNearestWalkable ? "ON" : "OFF"));
    }

    public void renderHud(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (state == State.IDLE && startPos == null && stopPos == null) return;

        int x = 8;
        int y = 8;
        int w = 240;
        int h = 66;
        ctx.fill(x, y, x + w, y + h, 0x8C10141A);
        ctx.fill(x, y, x + w, y + 1, 0xFF66FF44);

        int lineY = y + 6;
        ctx.drawTextWithShadow(client.textRenderer, "Pathfinder Debug", x + 6, lineY, 0xFFE6F2FF);
        lineY += 12;
        ctx.drawTextWithShadow(client.textRenderer, getStatusText(), x + 6, lineY, 0xFFBBD4FF);
        lineY += 10;
        ctx.drawTextWithShadow(client.textRenderer, getVerbosityText() + " | Snap: " + (snapToNearestWalkable ? "ON" : "OFF"),
            x + 6, lineY, 0xFFAACCB8);
        lineY += 10;
        ctx.drawTextWithShadow(client.textRenderer,
            (startPos == null ? "S:-" : "S:" + startPos.toShortString()) + "  "
                + (stopPos == null ? "T:-" : "T:" + stopPos.toShortString()),
            x + 6, lineY, 0xFFCED8E6);
    }

    private void startPathToStop(MinecraftClient client, ClientPlayerEntity player) {
        if (stopPos == null) {
            DebugLogger.info("[PathfinderDebug] Stop position is not set");
            stop();
            return;
        }

        BlockPos saneFrom = sanitizeAnchor(client, player.getBlockPos(), "player");
        if (saneFrom == null) {
            DebugLogger.info("[PathfinderDebug] Player anchor is not walkable and no fallback was found");
            stop();
            return;
        }

        List<PathNode> toStop = findPathWithFallback(client, saneFrom, stopPos, "to stop");
        lastPathToStop = toStop;
        logPath("to stop", toStop, saneFrom, stopPos);
        if (toStop.isEmpty() || toStop.size() < 2) {
            DebugLogger.info("[PathfinderDebug] Failed to find path to stop position");
            stop();
            return;
        }

        currentPath = toStop;
        movementController.startPath(toStop);
        state = State.GOING_TO_STOP;
        logTickCooldown = 0;
    }

    private void logPath(String label, List<PathNode> path, BlockPos from, BlockPos to) {
        double straight = Math.sqrt(from.getSquaredDistance(to));
        DebugLogger.info("[PathfinderDebug] Path " + label + " from " + from.toShortString() + " to " + to.toShortString()
            + " nodes=" + path.size() + " straightDist=" + String.format(java.util.Locale.ROOT, "%.2f", straight));
        if (verbosity == Verbosity.FULL) {
            for (int i = 0; i < path.size(); i++) {
                PathNode node = path.get(i);
                DebugLogger.info("[PathfinderDebug] " + label + " node[" + i + "]=" + node.x() + "," + node.y() + "," + node.z());
            }
        } else if (!path.isEmpty()) {
            PathNode first = path.get(0);
            PathNode last = path.get(path.size() - 1);
            DebugLogger.info("[PathfinderDebug] " + label + " endpoints first=" + first.x() + "," + first.y() + "," + first.z()
                + " last=" + last.x() + "," + last.y() + "," + last.z());
        }
    }

    private boolean isAt(BlockPos a, BlockPos b, double toleranceSq) {
        return a.getSquaredDistance(b) <= toleranceSq;
    }

    private BlockPos sanitizeAnchor(MinecraftClient client, BlockPos anchor, String label) {
        if (anchor == null || client.world == null) return null;
        if (WalkabilityChecker.isWalkable(client.world, anchor)) {
            return anchor;
        }

        DebugLogger.info("[PathfinderDebug] " + label + " is not walkable at " + anchor.toShortString());
        if (!snapToNearestWalkable) {
            return null;
        }

        BlockPos nearest = findNearestWalkable(client, anchor, 6);
        if (nearest == null) {
            DebugLogger.info("[PathfinderDebug] No walkable fallback found near " + label + " anchor");
            return null;
        }

        DebugLogger.info("[PathfinderDebug] " + label + " snapped to nearest walkable " + nearest.toShortString());
        return nearest;
    }

    private List<PathNode> findPathWithFallback(MinecraftClient client, BlockPos from, BlockPos to, String label) {
        if (client.world == null) return Collections.emptyList();
        boolean verboseTrace = verbosity == Verbosity.FULL;

        List<PathNode> direct = PathfindingService.findPath(client.world, from, to, verboseTrace);
        if (!direct.isEmpty()) {
            return direct;
        }

        if (!snapToNearestWalkable) {
            return direct;
        }

        DebugLogger.info("[PathfinderDebug] Direct path " + label + " failed, trying nearby walkable fallbacks");
        List<BlockPos> candidates = collectWalkableCandidates(client, to, 4);
        for (BlockPos candidate : candidates) {
            List<PathNode> alt = PathfindingService.findPath(client.world, from, candidate, verboseTrace);
            if (!alt.isEmpty()) {
                DebugLogger.info("[PathfinderDebug] Fallback path " + label + " succeeded via " + candidate.toShortString());
                return alt;
            }
        }

        return direct;
    }

    private BlockPos findNearestWalkable(MinecraftClient client, BlockPos around, int radius) {
        List<BlockPos> candidates = collectWalkableCandidates(client, around, radius);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<BlockPos> collectWalkableCandidates(MinecraftClient client, BlockPos around, int radius) {
        if (client.world == null) return Collections.emptyList();

        List<BlockPos> out = new ArrayList<>();
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos candidate = around.add(dx, dy, dz);
                        if (!WalkabilityChecker.isWalkable(client.world, candidate)) continue;
                        out.add(candidate.toImmutable());
                    }
                }
            }
            if (!out.isEmpty()) break;
        }

        out.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(around)));
        return out;
    }

    private List<BlockPos> scanWalkableGrid(MinecraftClient client, BlockPos from, BlockPos to, int padding, int verticalRange) {
        if (client.world == null) return Collections.emptyList();

        int minX = Math.min(from.getX(), to.getX()) - padding;
        int maxX = Math.max(from.getX(), to.getX()) + padding;
        int minY = Math.min(from.getY(), to.getY()) - verticalRange;
        int maxY = Math.max(from.getY(), to.getY()) + verticalRange;
        int minZ = Math.min(from.getZ(), to.getZ()) - padding;
        int maxZ = Math.max(from.getZ(), to.getZ()) + padding;

        List<BlockPos> walkable = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (WalkabilityChecker.isWalkable(client.world, pos)) {
                        walkable.add(pos.toImmutable());
                    }
                }
            }
        }

        return walkable;
    }

    private void renderMarkers(WorldRenderContext context) {
        if (startPos == null && stopPos == null) return;

        MatrixStack matrices = context.matrices();
        var consumers = context.consumers();
        if (consumers == null) return;
        Vec3d cam = RenderUtils.getCameraPos();

        if (startPos != null) {
            var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
            RenderUtils.renderEspOutlines(matrices, lineConsumer,
                List.of(new Box(startPos)), 0xFFFFAA44, 0.9f, cam);
            RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
        }

        if (stopPos != null) {
            var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
            RenderUtils.renderEspOutlines(matrices, lineConsumer,
                List.of(new Box(stopPos)), 0xFF44FF88, 0.9f, cam);
            RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
        }
    }
}