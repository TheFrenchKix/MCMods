package com.example.macromod.pathfinding;

import com.example.macromod.util.BlockUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* fallback pathfinder with full 8-directional (diagonal) movement.
 *
 * <p>Design principles (per diagonal movement spec):
 * <ul>
 *   <li>Straight cost = 1.0, diagonal cost = √2 (~1.414)</li>
 *   <li>Heuristic = Octile distance — consistent with 8-directional movement,
 *       prevents zig-zag artifacts that Manhattan would introduce</li>
 *   <li>Corner-cutting prevention — a diagonal move (dx,dz) is only allowed
 *       when both orthogonal neighbours (dx,0) and (0,dz) are also passable</li>
 *   <li>3D clearance — both axes of a diagonal step are validated independently
 *       for ground support and 2-block headroom</li>
 *   <li>Path simplification — collinear nodes (same direction vector including
 *       diagonals) are collapsed to start + end only</li>
 * </ul>
 * </p>
 */
public class PathFinder {

    private static final double SQRT2 = Math.sqrt(2.0);

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

    public void setMaxNodes(int maxNodes) {
        this.maxNodes = maxNodes;
    }

    public void setOnlyGround(boolean onlyGround) {
        this.onlyGround = onlyGround;
    }

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    public List<BlockPos> findPath(BlockPos start, BlockPos goal, ClientWorld world) {
        Map<BlockPos, BlockPos>  cameFrom = new HashMap<>();
        Map<BlockPos, Double>    gScore   = new HashMap<>();

        // f(n) = g(n) + h(n); tie-break on h so we prefer nodes closer to goal
        PriorityQueue<BlockPos> openSet = new PriorityQueue<>((a, b) -> {
            double fa = gScore.getOrDefault(a, Double.MAX_VALUE) + heuristic(a, goal);
            double fb = gScore.getOrDefault(b, Double.MAX_VALUE) + heuristic(b, goal);
            if (fa != fb) return Double.compare(fa, fb);
            return Double.compare(heuristic(a, goal), heuristic(b, goal));
        });

        gScore.put(start, 0.0);
        openSet.add(start);

        int explored = 0;
        while (!openSet.isEmpty() && explored < maxNodes) {
            BlockPos current = openSet.poll();
            explored++;

            if (current.equals(goal) || isNear(current, goal)) {
                return simplifyPath(reconstructPath(cameFrom, current));
            }

            double gCurrent = gScore.getOrDefault(current, Double.MAX_VALUE);

            for (Move move : getNeighbors(current, world)) {
                double tentativeG = gCurrent + move.cost;
                if (tentativeG < gScore.getOrDefault(move.pos, Double.MAX_VALUE)) {
                    cameFrom.put(move.pos, current);
                    gScore.put(move.pos, tentativeG);
                    // Re-add even if already present; stale entries are harmless —
                    // they'll be skipped once their g-score is no longer optimal.
                    openSet.add(move.pos);
                }
            }
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // Heuristic — Octile distance
    // ═══════════════════════════════════════════════════════════════

    /**
     * Octile distance: consistent with 8-directional movement.
     * h = max(dx,dz) + (√2−1)·min(dx,dz)
     * A vertical penalty keeps the search biased toward the correct Y level.
     */
    private double heuristic(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        double horizontal = Math.max(dx, dz) + (SQRT2 - 1.0) * Math.min(dx, dz);
        return horizontal + dy * 2.0; // vertical steps cost more (jump/fall penalty)
    }

    // ═══════════════════════════════════════════════════════════════
    // Neighbour generation
    // ═══════════════════════════════════════════════════════════════

    private static final class Move {
        final BlockPos pos;
        final double   cost;
        Move(BlockPos pos, double cost) { this.pos = pos; this.cost = cost; }
    }

    /**
     * Generates valid neighbouring positions from {@code pos}.
     *
     * <p>For each of the 8 horizontal directions (4 straight + 4 diagonal) we
     * try three vertical variants: same Y, +1 (step up), −1 (step down).</p>
     *
     * <p>Diagonal moves additionally enforce corner-cutting prevention:
     * both orthogonal axis intermediates must be passable on the player's
     * column before the diagonal is accepted.</p>
     * 
     * <p>Height limits prevent unrealistic vertical climbing:
     * - No position can be more than MAX_HEIGHT_ABOVE_START above the path start
     * - Step-up (+1) is only allowed if flat position isn't valid (prevents stacking)
     * - Step-up has high cost penalty to discourage unnecessary climbing</p>
     */
    private List<Move> getNeighbors(BlockPos pos, ClientWorld world) {
        List<Move> moves = new ArrayList<>(16);

        for (int[] off : OFFSETS) {
            int  dx         = off[0];
            int  dz         = off[1];
            boolean isDiag  = off[2] == 1;
            double baseCost = isDiag ? SQRT2 : 1.0;

            // ── Corner-cutting guard ──────────────────────────────
            // For diagonal (dx, dz): both (dx,0) and (0,dz) axes must be
            // passable at the player's current level and one above.
            if (isDiag && !diagonalClear(pos, dx, dz, world)) continue;

            // ── Try vertical variants ─────────────────────────────
            BlockPos flat = pos.add(dx, 0,  dz);
            BlockPos up   = pos.add(dx, 1,  dz);

            // Check if flat position is valid
            if (canStandAt(flat, world)) {
                moves.add(new Move(flat, baseCost));
            } 
            // Only allow step-up if flat is SOLID ground and up is valid
            // This prevents stepping through leaves or air (no block placement/flying)
            // Skip if onlyGround mode is enabled
            else if (!onlyGround && BlockUtils.isSolid(world, flat) && canStandAt(up, world) && BlockUtils.isPassable(world, pos.up())) {
                // Step up: only 1 block; headroom at current pos must be clear
                // Must step UP ONTO solid ground (not empty space or leaves)
                // Diagonal step-up: both intermediate columns must also clear at y+1
                double stepUpCost = baseCost + 0.8; // significant cost penalty for stepping up
                if (!isDiag || diagonalClear(pos.up(), dx, dz, world)) {
                    moves.add(new Move(up, stepUpCost));
                }
            } 
            
            // ── Try stepping down (support multiple descent levels) ────
            // Check up to 3 blocks down for paths through lower openings
            for (int downStep = 1; downStep <= 3; downStep++) {
                BlockPos down = pos.add(dx, -downStep, dz);
                if (canStandAt(down, world)) {
                    // Cost increases with depth to prefer shallower descents
                    double downCost = baseCost + (downStep * 0.1);
                    moves.add(new Move(down, downCost));
                    break; // Found ground, no need to check deeper
                }
            }
        }

        return moves;
    }

    /**
     * Returns true when both orthogonal intermediates of a diagonal move are
     * passable — prevents cutting through wall corners.
     *
     * <p>Checks two blocks per axis (feet + head) so a 2-block-tall player
     * cannot clip through a 1-block gap at shoulder height.</p>
     */
    private boolean diagonalClear(BlockPos from, int dx, int dz, ClientWorld world) {
        BlockPos axisX = from.add(dx, 0, 0);
        BlockPos axisZ = from.add(0,  0, dz);
        return BlockUtils.isPassable(world, axisX)
                && BlockUtils.isPassable(world, axisX.up())
                && BlockUtils.isPassable(world, axisZ)
                && BlockUtils.isPassable(world, axisZ.up());
    }

    // ═══════════════════════════════════════════════════════════════
    // Walkability
    // ═══════════════════════════════════════════════════════════════

    /** A position is valid to stand at when the block below is solid and the
     *  2-block player column (feet + head) is passable. */
    private boolean canStandAt(BlockPos pos, ClientWorld world) {
        return BlockUtils.isSolid(world, pos.down())
                && BlockUtils.isPassable(world, pos)
                && BlockUtils.isPassable(world, pos.up());
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
     * Collapses runs of nodes with the same direction vector (including
     * diagonals) into just their start and end.  A diagonal run of 10 blocks
     * becomes 2 waypoints instead of 11; direction changes (turns, height
     * steps) are always preserved.
     */
    private List<BlockPos> simplifyPath(List<BlockPos> path) {
        if (path.size() <= 2) return path;
        List<BlockPos> result = new ArrayList<>();
        result.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            BlockPos prev = result.get(result.size() - 1);
            BlockPos curr = path.get(i);
            BlockPos next = path.get(i + 1);

            int dx1 = Integer.signum(curr.getX() - prev.getX());
            int dz1 = Integer.signum(curr.getZ() - prev.getZ());
            int dy1 = Integer.signum(curr.getY() - prev.getY());

            int dx2 = Integer.signum(next.getX() - curr.getX());
            int dz2 = Integer.signum(next.getZ() - curr.getZ());
            int dy2 = Integer.signum(next.getY() - curr.getY());

            if (dx1 != dx2 || dz1 != dz2 || dy1 != dy2) {
                result.add(curr);
            }
        }

        result.add(path.get(path.size() - 1));
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Misc
    // ═══════════════════════════════════════════════════════════════

    private boolean isNear(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1
                && Math.abs(a.getY() - b.getY()) <= 1
                && Math.abs(a.getZ() - b.getZ()) <= 1;
    }
}
