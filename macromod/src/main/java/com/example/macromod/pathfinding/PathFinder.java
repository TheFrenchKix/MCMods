package com.example.macromod.pathfinding;

import com.example.macromod.util.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Fallback A* pathfinder for voxel navigation.
 *
 * Uses 8-direction movement, strict queue ordering, conservative collision
 * checks for a 1x2 player, 1-block step-up max, and bounded controlled drops.
 */
public class PathFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod-pathfinder");
    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double EPS = 1.0e-9;
    private static final int MAX_FALL_BLOCKS = 6;
    private static final double STEP_UP_PENALTY = 0.8;
    private static final double FALL_PENALTY_PER_BLOCK = 0.12;

    // ── Neighbour offsets: 4 straight then 4 diagonal ─────────────
    // Each entry: {dx, dz, isDiagonal}
    private static final int[][] OFFSETS = {
            // straight
            { 1,  0, 0}, {-1,  0, 0}, { 0,  1, 0}, { 0, -1, 0},
            // diagonal
            { 1,  1, 1}, { 1, -1, 1}, {-1,  1, 1}, {-1, -1, 1}
    };

    private int maxNodes = 5000;
    private boolean onlyGround = false;
    private boolean debugLogging = true;
    private ClientWorld lastUsedWorld = null;

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
        if (start == null || goal == null || world == null) {
            return null;
        }
        this.lastUsedWorld = world;
        if (start.equals(goal)) {
            return Collections.emptyList();
        }

        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        PriorityQueue<NodeRecord> openSet = new PriorityQueue<>((a, b) -> {
            int byF = Double.compare(a.f, b.f);
            if (byF != 0) return byF;
            int byH = Double.compare(a.h, b.h);
            if (byH != 0) return byH;
            return Double.compare(a.g, b.g);
        });

        double hStart = heuristic(start, goal);
        gScore.put(start, 0.0);
        openSet.add(new NodeRecord(start, 0.0, hStart));

        if (debugLogging) {
            LOGGER.info("PathFinder: starting from {} to {}", start, goal);
        }

        int explored = 0;
        while (!openSet.isEmpty() && explored < maxNodes) {
            NodeRecord currentRecord = openSet.poll();
            BlockPos current = currentRecord.pos;

            double bestKnown = gScore.getOrDefault(current, Double.MAX_VALUE);
            if (currentRecord.g > bestKnown + EPS) {
                continue;
            }
            if (closed.contains(current)) {
                continue;
            }

            explored++;
            if (current.equals(goal)) {
                List<BlockPos> finalPath = simplifyPath(reconstructPath(cameFrom, current));
                if (debugLogging) {
                    LOGGER.info("PathFinder: path found (explored={}, nodes={})", explored, finalPath.size());
                }
                return finalPath;
            }

            closed.add(current);

            for (Move move : getNeighbors(current, world)) {
                if (closed.contains(move.pos)) {
                    continue;
                }
                if (!BlockUtils.isChunkLoaded(world, move.pos)) {
                    continue;
                }

                double tentativeG = currentRecord.g + move.cost;
                double oldG = gScore.getOrDefault(move.pos, Double.MAX_VALUE);
                if (tentativeG + EPS < oldG) {
                    cameFrom.put(move.pos, current);
                    gScore.put(move.pos, tentativeG);
                    double h = heuristic(move.pos, goal);
                    openSet.add(new NodeRecord(move.pos, tentativeG, h));
                }
            }
        }

        if (debugLogging) {
            LOGGER.warn("PathFinder: no path found (explored={}, maxNodes={})", explored, maxNodes);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // Heuristic — Octile distance
    // ═══════════════════════════════════════════════════════════════

    /**
     * Admissible octile lower bound in XZ plane.
     *
     * Y is intentionally omitted here to avoid overestimating mixed mechanics
     * (step-up, step-down, fall) in voxel worlds.
     */
    private double heuristic(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dz = Math.abs(a.getZ() - b.getZ());
        return Math.max(dx, dz) + (SQRT2 - 1.0) * Math.min(dx, dz);
    }

    // ═══════════════════════════════════════════════════════════════
    // Neighbour generation
    // ═══════════════════════════════════════════════════════════════

    private static final class NodeRecord {
        final BlockPos pos;
        final double g;
        final double h;
        final double f;

        NodeRecord(BlockPos pos, double g, double h) {
            this.pos = pos;
            this.g = g;
            this.h = h;
            this.f = g + h;
        }
    }

    private static final class Move {
        final BlockPos pos;
        final double   cost;
        Move(BlockPos pos, double cost) { this.pos = pos; this.cost = cost; }
    }

    /**
     * Generates physically valid neighbors for a 1x2 player.
     */
    private List<Move> getNeighbors(BlockPos pos, ClientWorld world) {
        List<Move> moves = new ArrayList<>(16);

        for (int[] off : OFFSETS) {
            int dx = off[0];
            int dz = off[1];
            boolean isDiag = off[2] == 1;
            double baseCost = isDiag ? SQRT2 : 1.0;

            if (isDiag && !diagonalClear(pos, dx, dz, world)) {
                continue;
            }

            BlockPos flat = pos.add(dx, 0, dz);
            if (!BlockUtils.isChunkLoaded(world, flat)) {
                continue;
            }

            boolean hasFlat = canStandAt(flat, world);
            if (hasFlat) {
                moves.add(new Move(flat, baseCost));
            }

            if (!hasFlat) {
                if (!onlyGround && canStepUp(pos, dx, dz, isDiag, world)) {
                    BlockPos up = pos.add(dx, 1, dz);
                    moves.add(new Move(up, baseCost + STEP_UP_PENALTY));
                }

                Move drop = findDropMove(pos, dx, dz, baseCost, world);
                if (drop != null) {
                    moves.add(drop);
                }
            }
        }

        return moves;
    }

    private Move findDropMove(BlockPos pos, int dx, int dz, double baseCost, ClientWorld world) {
        BlockPos edge = pos.add(dx, 0, dz);
        if (!BlockUtils.isChunkLoaded(world, edge)) {
            return null;
        }
        if (!BlockUtils.isPassable(world, edge) || !BlockUtils.isPassable(world, edge.up())) {
            return null;
        }
        if (BlockUtils.isDangerous(world, edge) || BlockUtils.isDangerous(world, edge.up())) {
            return null;
        }

        for (int drop = 1; drop <= MAX_FALL_BLOCKS; drop++) {
            BlockPos candidate = pos.add(dx, -drop, dz);
            if (!BlockUtils.isChunkLoaded(world, candidate)) {
                break;
            }

            if (canStandAt(candidate, world)) {
                return new Move(candidate, baseCost + drop * FALL_PENALTY_PER_BLOCK);
            }

            if (!BlockUtils.isPassable(world, candidate) || !BlockUtils.isPassable(world, candidate.up())) {
                break;
            }
            if (BlockUtils.isDangerous(world, candidate) || BlockUtils.isDangerous(world, candidate.up())) {
                break;
            }
        }

        return null;
    }

    private boolean canStepUp(BlockPos pos, int dx, int dz, boolean isDiag, ClientWorld world) {
        BlockPos obstacle = pos.add(dx, 0, dz);
        BlockPos landing = obstacle.up();

        if (!BlockUtils.isChunkLoaded(world, landing)) {
            return false;
        }
        if (!BlockUtils.isSolid(world, obstacle)) {
            return false;
        }
        if (!canStandAt(landing, world)) {
            return false;
        }

        if (!isDiag) {
            return true;
        }

        // Step-up diagonal must clear both body and shoulder-level corner clips.
        return diagonalClear(pos, dx, dz, world) && diagonalClear(pos.up(), dx, dz, world);
    }

    private boolean diagonalClear(BlockPos from, int dx, int dz, ClientWorld world) {
        BlockPos axisX = from.add(dx, 0, 0);
        BlockPos axisZ = from.add(0, 0, dz);

        boolean axisXPass = BlockUtils.isPassable(world, axisX)
                && BlockUtils.isPassable(world, axisX.up())
                && !BlockUtils.isDangerous(world, axisX)
                && !BlockUtils.isDangerous(world, axisX.up());
        boolean axisZPass = BlockUtils.isPassable(world, axisZ)
                && BlockUtils.isPassable(world, axisZ.up())
                && !BlockUtils.isDangerous(world, axisZ)
                && !BlockUtils.isDangerous(world, axisZ.up());

        return axisXPass && axisZPass;
    }

    // ═══════════════════════════════════════════════════════════════
    // Walkability
    // ═══════════════════════════════════════════════════════════════

    /** A standable position for a 1x2 player with hazard checks. */
    private boolean canStandAt(BlockPos pos, ClientWorld world) {
        BlockPos ground = pos.down();
        boolean hasSolidGround = hasSupport(world, ground);
        boolean feetPassable = BlockUtils.isPassable(world, pos);
        boolean headPassable = BlockUtils.isPassable(world, pos.up());

        if (!hasSolidGround || !feetPassable || !headPassable) {
            return false;
        }

        if (BlockUtils.isDangerous(world, ground)
                || BlockUtils.isDangerous(world, pos)
                || BlockUtils.isDangerous(world, pos.up())) {
            return false;
        }

        return true;
    }

    /**
     * Support check for the block under player feet.
     * Accepts full blocks and blocks with a meaningful top collision surface.
     */
    private boolean hasSupport(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        if (state.isSideSolidFullSquare(world, pos, Direction.UP)) {
            return true;
        }

        if (state.getCollisionShape(world, pos).isEmpty()) {
            return false;
        }

        return state.getCollisionShape(world, pos).getMax(Direction.Axis.Y) >= 0.5;
    }

    // ═══════════════════════════════════════════════════════════════
    // Path reconstruction
    // ═══════════════════════════════════════════════════════════════

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = end;
        while (cameFrom.containsKey(current)) {
            path.add(current);
            current = cameFrom.get(current);
        }
        Collections.reverse(path);
        return path;
    }

    // ═══════════════════════════════════════════════════════════════
    // Path simplification
    // ═══════════════════════════════════════════════════════════════

    /**
     * Aggressively simplifies a path using walkable line-of-sight checks.
     * For each waypoint, finds the FARTHEST subsequent waypoint reachable
     * via straight-line walking, then skips everything in between.
     * Falls back to direction-based simplification if LOS checks fail.
     * Also enforces a minimum distance of 1 block between consecutive nodes.
     */
    private List<BlockPos> simplifyPath(List<BlockPos> path) {
        if (path.size() <= 2) return path;

        // First pass: LOS-based simplification (skip to farthest visible)
        List<BlockPos> losSimplified = new ArrayList<>();
        losSimplified.add(path.get(0));

        int i = 0;
        while (i < path.size() - 1) {
            // Find the farthest node reachable from path[i] by walking straight
            int farthest = i + 1;
            for (int j = path.size() - 1; j > i + 1; j--) {
                // Preserve nodes with significant Y changes (jump/fall required)
                boolean hasYChange = false;
                for (int k = i + 1; k <= j; k++) {
                    if (Math.abs(path.get(k).getY() - path.get(k - 1).getY()) > 0) {
                        hasYChange = true;
                        break;
                    }
                }
                if (hasYChange) continue;

                if (BlockUtils.hasWalkableLOS(lastUsedWorld, path.get(i), path.get(j))) {
                    farthest = j;
                    break;
                }
            }
            losSimplified.add(path.get(farthest));
            i = farthest;
        }

        // Second pass: remove nodes that are too close together (< 1.0 block apart)
        // Keep first and last always
        if (losSimplified.size() <= 2) return losSimplified;

        List<BlockPos> result = new ArrayList<>();
        result.add(losSimplified.get(0));
        for (int k = 1; k < losSimplified.size() - 1; k++) {
            BlockPos prev = result.get(result.size() - 1);
            BlockPos curr = losSimplified.get(k);
            double distSq = prev.getSquaredDistance(curr);
            if (distSq >= 1.0) {  // Keep only if >= 1 block apart
                result.add(curr);
            }
        }
        result.add(losSimplified.get(losSimplified.size() - 1));
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Misc
    // ═══════════════════════════════════════════════════════════════

}
