package com.example.macromod.pathfinding;

import com.example.macromod.pathfinding.astar.BinaryHeapOpenSet;
import com.example.macromod.pathfinding.astar.PathNode;
import com.example.macromod.util.BlockUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A* pathfinder with full 8-directional (diagonal) movement, using a custom
 * {@link BinaryHeapOpenSet} with O(log n) decrease-key and intrusive heap
 * positions for fast membership + closed checks.
 *
 * <p>Baritone-inspired techniques:
 * <ul>
 *   <li>Custom binary heap with decrease-key — avoids PriorityQueue re-insert spam</li>
 *   <li>Primitive-int PathNode coords — avoids BlockPos allocation per expansion</li>
 *   <li>Long-packed position hashing — single HashMap lookup per neighbour</li>
 *   <li>Partial path fallback with weighted coefficients — when no full path exists,
 *       returns the best partial path toward the goal</li>
 * </ul>
 */
public class PathFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod-pathfinder");
    private static final double SQRT2 = Math.sqrt(2.0);

    /** Coefficients for partial path scoring (baritone style).
     *  Lower coefficients → prefer nodes with low cost-so-far.
     *  Higher coefficients → prefer nodes close to the goal regardless of cost. */
    private static final double[] COEFFICIENTS = {1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0};

    // Neighbour offsets: 4 straight then 4 diagonal
    // {dx, dz, isDiagonal}
    private static final int[][] OFFSETS = {
            { 1,  0, 0}, {-1,  0, 0}, { 0,  1, 0}, { 0, -1, 0},
            { 1,  1, 1}, { 1, -1, 1}, {-1,  1, 1}, {-1, -1, 1}
    };

    private int maxNodes = 5000;
    private boolean onlyGround = false;
    private boolean debugLogging = true;

    public void setMaxNodes(int maxNodes) {
        this.maxNodes = maxNodes;
    }

    public void setOnlyGround(boolean onlyGround) {
        this.onlyGround = onlyGround;
    }

    public void setDebugLogging(boolean enabled) {
        this.debugLogging = enabled;
    }

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    public List<BlockPos> findPath(BlockPos start, BlockPos goal, ClientWorld world) {
        // Node map: long-packed position → PathNode
        Map<Long, PathNode> map = new HashMap<>(1024);

        BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();

        if (debugLogging) {
            LOGGER.info("PathFinder: Starting path search from {} to {}", start, goal);
        }

        int gx = goal.getX(), gy = goal.getY(), gz = goal.getZ();

        // Seed start node
        PathNode startNode = new PathNode(start.getX(), start.getY(), start.getZ());
        startNode.cost = 0.0;
        startNode.estimatedCostToGoal = heuristic(startNode.x, startNode.y, startNode.z, gx, gy, gz);
        startNode.combinedCost = startNode.estimatedCostToGoal;
        map.put(startNode.posHash(), startNode);
        openSet.insert(startNode);

        // Best partial-path candidates — one per coefficient
        PathNode[] bestPartial = new PathNode[COEFFICIENTS.length];
        double[] bestPartialScore = new double[COEFFICIENTS.length];
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            bestPartialScore[i] = Double.MAX_VALUE;
        }

        int explored = 0;

        while (!openSet.isEmpty() && explored < maxNodes) {
            PathNode current = openSet.removeLowest(); // marks heapPosition = -1 (closed)
            explored++;

            // Goal check
            if (isNear(current.x, current.y, current.z, gx, gy, gz)) {
                List<BlockPos> finalPath = PathSmoother.fullSmooth(reconstructPath(current), world);
                if (debugLogging) {
                    LOGGER.info("PathFinder: Path found! Explored {} nodes, final path length: {}",
                            explored, finalPath.size());
                }
                return finalPath;
            }

            // Update partial-path candidates (baritone technique)
            for (int i = 0; i < COEFFICIENTS.length; i++) {
                double score = current.cost + current.estimatedCostToGoal * COEFFICIENTS[i];
                if (score < bestPartialScore[i]) {
                    bestPartialScore[i] = score;
                    bestPartial[i] = current;
                }
            }

            // Expand neighbours
            expandNeighbours(current, gx, gy, gz, world, map, openSet);
        }

        // No full path found — try partial path fallback
        PathNode bestFallback = selectBestPartial(bestPartial, gx, gy, gz);
        if (bestFallback != null) {
            List<BlockPos> partialPath = PathSmoother.fullSmooth(reconstructPath(bestFallback), world);
            if (debugLogging) {
                LOGGER.info("PathFinder: Partial path (explored {} nodes). Path length: {}, " +
                        "distance to goal: {}", explored, partialPath.size(),
                        String.format("%.1f", Math.sqrt(distSq(bestFallback.x, bestFallback.y,
                                bestFallback.z, gx, gy, gz))));
            }
            return partialPath;
        }

        if (debugLogging) {
            LOGGER.warn("PathFinder: No path found! Explored {} nodes (max: {})", explored, maxNodes);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // Neighbour expansion
    // ═══════════════════════════════════════════════════════════════

    private void expandNeighbours(PathNode current, int gx, int gy, int gz,
                                   ClientWorld world, Map<Long, PathNode> map,
                                   BinaryHeapOpenSet openSet) {
        int cx = current.x, cy = current.y, cz = current.z;
        BlockPos currentPos = new BlockPos(cx, cy, cz);

        for (int[] off : OFFSETS) {
            int dx = off[0], dz = off[1];
            boolean isDiag = off[2] == 1;
            double baseCost = isDiag ? SQRT2 : 1.0;

            // Corner-cutting guard for diagonals
            if (isDiag && !diagonalClear(currentPos, dx, dz, world)) {
                continue;
            }

            // Try flat
            int nx = cx + dx, nz = cz + dz;
            if (canStandAt(nx, cy, nz, world)) {
                tryRelax(current, nx, cy, nz, baseCost, gx, gy, gz, map, openSet);
            }
            // Try step-up (1 block)
            else if (!onlyGround && isSolid(nx, cy, nz, world)) {
                int uy = cy + 1;
                if (canStandAt(nx, uy, nz, world) && isPassable(cx, uy, cz, world)
                        && (!isDiag || diagonalClear(currentPos, dx, dz, world))) {
                    tryRelax(current, nx, uy, nz, baseCost + 0.8, gx, gy, gz, map, openSet);
                }
                // Try 2-block step-up
                else if (!onlyGround && isSolid(nx, uy, nz, world)) {
                    int uy2 = cy + 2;
                    if (canStandAt(nx, uy2, nz, world)
                            && isPassable(cx, uy, cz, world)
                            && isPassable(cx + dx, uy, cz + dz, world)) {
                        tryRelax(current, nx, uy2, nz, baseCost + 1.6, gx, gy, gz, map, openSet);
                    }
                }
            }

            // Try stepping down (1-2 blocks)
            for (int ds = 1; ds <= 2; ds++) {
                int dy = cy - ds;
                if (canStandAt(nx, dy, nz, world)) {
                    tryRelax(current, nx, dy, nz, baseCost + ds * 0.1, gx, gy, gz, map, openSet);
                    break; // found ground
                }
            }
        }
    }

    /**
     * Tries to relax the edge from {@code from} to the neighbour at (nx, ny, nz).
     * If not yet seen, creates and inserts. If open with better cost, updates.
     * If closed (heapPosition == -1), skips.
     */
    private void tryRelax(PathNode from, int nx, int ny, int nz, double edgeCost,
                           int gx, int gy, int gz,
                           Map<Long, PathNode> map, BinaryHeapOpenSet openSet) {
        long hash = PathNode.posHash(nx, ny, nz);
        double tentativeG = from.cost + edgeCost;

        PathNode neighbour = map.get(hash);
        if (neighbour == null) {
            // New node
            neighbour = new PathNode(nx, ny, nz);
            neighbour.cost = tentativeG;
            neighbour.estimatedCostToGoal = heuristic(nx, ny, nz, gx, gy, gz);
            neighbour.combinedCost = tentativeG + neighbour.estimatedCostToGoal;
            neighbour.previous = from;
            map.put(hash, neighbour);
            openSet.insert(neighbour);
        } else if (tentativeG < neighbour.cost) {
            if (neighbour.heapPosition == -1) {
                // Closed — skip (already processed with equal or better cost)
                return;
            }
            // Open — decrease-key
            neighbour.cost = tentativeG;
            neighbour.combinedCost = tentativeG + neighbour.estimatedCostToGoal;
            neighbour.previous = from;
            openSet.update(neighbour);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Heuristic — Octile distance
    // ═══════════════════════════════════════════════════════════════

    private double heuristic(int ax, int ay, int az, int bx, int by, int bz) {
        int dx = Math.abs(ax - bx);
        int dy = Math.abs(ay - by);
        int dz = Math.abs(az - bz);
        double horizontal = Math.max(dx, dz) + (SQRT2 - 1.0) * Math.min(dx, dz);
        return horizontal + dy * 2.0;
    }

    // ═══════════════════════════════════════════════════════════════
    // Walkability helpers (int-coord, zero BlockPos allocation)
    // ═══════════════════════════════════════════════════════════════

    private boolean canStandAt(int x, int y, int z, ClientWorld world) {
        BlockPos pos = new BlockPos(x, y, z);
        return BlockUtils.isSolid(world, pos.down())
                && BlockUtils.isPassable(world, pos)
                && BlockUtils.isPassable(world, pos.up());
    }

    private boolean isSolid(int x, int y, int z, ClientWorld world) {
        return BlockUtils.isSolid(world, new BlockPos(x, y, z));
    }

    private boolean isPassable(int x, int y, int z, ClientWorld world) {
        return BlockUtils.isPassable(world, new BlockPos(x, y, z));
    }

    private boolean diagonalClear(BlockPos from, int dx, int dz, ClientWorld world) {
        BlockPos axisX = from.add(dx, 0, 0);
        BlockPos axisZ = from.add(0, 0, dz);
        return BlockUtils.isPassable(world, axisX) && BlockUtils.isPassable(world, axisX.up())
                && BlockUtils.isPassable(world, axisZ) && BlockUtils.isPassable(world, axisZ.up());
    }

    // ═══════════════════════════════════════════════════════════════
    // Partial path selection (baritone technique)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Selects the best partial path endpoint from the coefficient-weighted candidates.
     * Picks the candidate closest to the goal (by distance), breaking ties on cost.
     */
    private PathNode selectBestPartial(PathNode[] candidates, int gx, int gy, int gz) {
        PathNode best = null;
        double bestDist = Double.MAX_VALUE;

        for (PathNode node : candidates) {
            if (node == null) continue;
            double d = distSq(node.x, node.y, node.z, gx, gy, gz);
            if (d < bestDist || (d == bestDist && best != null && node.cost < best.cost)) {
                bestDist = d;
                best = node;
            }
        }
        return best;
    }

    // ═══════════════════════════════════════════════════════════════
    // Path reconstruction
    // ═══════════════════════════════════════════════════════════════

    private List<BlockPos> reconstructPath(PathNode end) {
        List<BlockPos> path = new ArrayList<>();
        PathNode current = end;
        while (current.previous != null) {
            path.add(current.toBlockPos());
            current = current.previous;
        }
        Collections.reverse(path);
        return path;
    }

    // ═══════════════════════════════════════════════════════════════
    // Misc
    // ═══════════════════════════════════════════════════════════════

    private boolean isNear(int ax, int ay, int az, int bx, int by, int bz) {
        return Math.abs(ax - bx) <= 1 && Math.abs(ay - by) <= 1 && Math.abs(az - bz) <= 1;
    }

    private double distSq(int ax, int ay, int az, int bx, int by, int bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }
}
