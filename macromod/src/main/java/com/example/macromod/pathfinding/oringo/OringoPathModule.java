package com.example.macromod.pathfinding.oringo;

import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.pathfinding.PathFinder;
import com.example.macromod.pathfinding.PathHandler;
import com.example.macromod.pathfinding.goal.ExactGoal;
import com.example.macromod.util.BlockUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Oringo-style path routing module facade.
 *
 * <p>Oringo itself does not provide A* navigation, so this module wraps
 * macromod's Stevebot + fallback A* chain behind a single service API.</p>
 */
public class OringoPathModule {

    public List<BlockPos> findPath(
            BlockPos from,
            BlockPos goal,
            ClientWorld world,
            boolean onlyGround,
            int maxNodes,
            long timeoutMs,
            PathHandler pathHandler,
            PathFinder fallbackPathFinder
    ) {
        List<BlockPos> path = Collections.emptyList();

        if (pathHandler != null) {
            List<BaseBlockPos> stevebotPath = pathHandler.findPath(
                new BaseBlockPos(from.getX(), from.getY(), from.getZ()),
                new ExactGoal(new BaseBlockPos(goal.getX(), goal.getY(), goal.getZ())),
                timeoutMs
            );
            path = toBlockPosPath(stevebotPath);
            // Apply LOS-based simplification to Stevebot paths too
            if (path != null && path.size() > 2) {
                path = simplifyPathLOS(path, world);
            }
        }

        if (path == null || path.isEmpty()) {
            fallbackPathFinder.setOnlyGround(onlyGround);
            fallbackPathFinder.setMaxNodes(maxNodes);
            path = fallbackPathFinder.findPath(from, goal, world);
        }

        return path != null ? path : Collections.emptyList();
    }

    private List<BlockPos> toBlockPosPath(List<BaseBlockPos> stevebotPath) {
        if (stevebotPath == null || stevebotPath.isEmpty()) {
            return Collections.emptyList();
        }

        List<BlockPos> converted = new ArrayList<>(stevebotPath.size());
        for (BaseBlockPos pos : stevebotPath) {
            converted.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }
        return converted;
    }

    /**
     * LOS-based path simplification: for each waypoint, skip ahead to the
     * farthest subsequent waypoint reachable by straight-line walking.
     * Preserves nodes where Y changes occur (jump/fall points).
     */
    private List<BlockPos> simplifyPathLOS(List<BlockPos> path, ClientWorld world) {
        if (path.size() <= 2) return path;
        List<BlockPos> result = new ArrayList<>();
        result.add(path.get(0));

        int i = 0;
        while (i < path.size() - 1) {
            int farthest = i + 1;
            for (int j = path.size() - 1; j > i + 1; j--) {
                // Preserve Y-change nodes
                boolean hasYChange = false;
                for (int k = i + 1; k <= j; k++) {
                    if (Math.abs(path.get(k).getY() - path.get(k - 1).getY()) > 0) {
                        hasYChange = true;
                        break;
                    }
                }
                if (hasYChange) continue;

                if (BlockUtils.hasWalkableLOS(world, path.get(i), path.get(j))) {
                    farthest = j;
                    break;
                }
            }
            result.add(path.get(farthest));
            i = farthest;
        }
        return result;
    }
}
