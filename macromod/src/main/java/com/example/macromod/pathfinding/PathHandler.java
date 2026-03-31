package com.example.macromod.pathfinding;

import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.minecraft.MinecraftAdapter;
import com.example.macromod.misc.StevebotLog;
import com.example.macromod.pathfinding.execution.PathExecutor;
import com.example.macromod.pathfinding.goal.Goal;
import com.example.macromod.rendering.Renderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PathHandler {


    private PathExecutor excecutor = null;

    private final MinecraftAdapter minecraftAdapter;
    private final Renderer renderer;
    private final Pathfinding pathfinding;


    public PathHandler(MinecraftAdapter minecraftAdapter, Renderer renderer) {
        this.minecraftAdapter = minecraftAdapter;
        this.renderer = renderer;
        this.pathfinding = new Pathfinding(minecraftAdapter);
    }


    public void onEventClientTick() {
        if (excecutor != null) {
            excecutor.onClientTick();
        }
    }

    /**
     * Creates a new path and executor from the given start position to the given goal.
     *
     * @param from           the starting position
     * @param goal           the goal of the resulting path
     * @param startFollowing true, to immediately start following the path
     * @param enableFreelook true, to enable freelook when following the path
     */
    public void createPath(BaseBlockPos from, Goal goal, boolean startFollowing, boolean enableFreelook) {
        if (excecutor == null) {
            excecutor = new PathExecutor(minecraftAdapter, from, goal, renderer);
            excecutor.setPathListener(() -> excecutor = null);
            excecutor.start();
            if (startFollowing) {
                excecutor.startFollowing(enableFreelook);
            }
        } else {
            StevebotLog.log("Can not start new path. Another path is already in progress.");
        }
    }


    /**
     * Start following the created path.
     */
    public void startFollowing() {
        if (excecutor != null) {
            excecutor.startFollowing(false);
        }
    }


    /**
     * Stop the current path
     */
    public void cancelPath() {
        if (excecutor != null) {
            excecutor.stop();
        }
    }

    /**
     * Calculates a path using Stevebot-core pathfinding and returns the node positions.
     */
    public List<BaseBlockPos> findPath(BaseBlockPos from, Goal goal, long timeoutInMs) {
        PathfindingResult result = pathfinding.calculatePath(from, goal, timeoutInMs);
        if (result == null || result.finalPath == null || result.finalPath.getNodes().isEmpty()) {
            return Collections.emptyList();
        }

        List<BaseBlockPos> positions = new ArrayList<>();
        result.finalPath.getNodes().forEach(node -> positions.add(new BaseBlockPos(node.getPos())));

        // Drop first node (current player block), keep waypoints only.
        if (positions.size() > 1) {
            positions.remove(0);
        }

        return positions;
    }

}
