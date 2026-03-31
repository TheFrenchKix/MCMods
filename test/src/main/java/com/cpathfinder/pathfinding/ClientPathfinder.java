package com.cpathfinder.pathfinding;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Async A* pathfinder using CompletableFuture.
 *
 * Walkability rules:
 *  - The block directly below the feet position must have non-empty collision (solid support).
 *  - The feet block and the head block (1 above) must have empty collision (passable).
 *
 * Supported moves:  8 horizontal directions (cardinal + diagonal),
 *                   jump 1 block up, fall 1–3 blocks down.
 *
 * NOTE: ClientLevel is accessed from the ForkJoinPool thread.
 * Block-state reads are effectively read-only and work safely in practice,
 * but this mod is intended for single-player / integrated-server use.
 */
public final class ClientPathfinder {

    private static final int MAX_NODES = 50_000;

    // 8 directions: 4 cardinal + 4 diagonal
    private static final int[][] DIRECTIONS = {
        { 1,  0}, {-1,  0}, { 0,  1}, { 0, -1},   // cardinal
        { 1,  1}, { 1, -1}, {-1,  1}, {-1, -1}    // diagonal
    };

    private ClientPathfinder() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Launches an async A* search and returns a CompletableFuture.
     * The caller should post the result to PathState via thenAccept(PathState::postPath).
     */
    public static CompletableFuture<List<BlockPos>> findPathAsync(
            ClientLevel world, BlockPos start, BlockPos goal) {
        return CompletableFuture.supplyAsync(() -> findPath(world, start, goal));
    }

    // ── A* implementation ─────────────────────────────────────────────────────

    private record SearchNode(BlockPos pos, double g, double f)
            implements Comparable<SearchNode> {
        @Override public int compareTo(SearchNode o) { return Double.compare(f, o.f); }
    }

    private record Neighbor(BlockPos pos, double cost) {}

    private static List<BlockPos> findPath(ClientLevel world, BlockPos start, BlockPos goal) {
        PriorityQueue<SearchNode> open = new PriorityQueue<>();
        Map<Long, Double>         gScore   = new HashMap<>();
        Map<Long, Long>           cameFrom = new HashMap<>();

        long startKey = start.asLong();
        gScore.put(startKey, 0.0);
        open.add(new SearchNode(start, 0.0, heuristic(start, goal)));

        int iter = 0;
        while (!open.isEmpty() && iter++ < MAX_NODES) {
            SearchNode cur = open.poll();
            long curKey = cur.pos().asLong();

            // Skip stale entries
            if (cur.g() > gScore.getOrDefault(curKey, Double.MAX_VALUE)) continue;

            // Goal reached (exact or 1-block Manhattan proximity)
            if (cur.pos().equals(goal) || cur.pos().distManhattan(goal) <= 1) {
                if (!cur.pos().equals(goal)) {
                    cameFrom.put(goal.asLong(), curKey);
                }
                return buildPath(cameFrom, goal);
            }

            for (Neighbor nb : getNeighbors(world, cur.pos())) {
                double newG   = cur.g() + nb.cost();
                long   nbKey  = nb.pos().asLong();
                if (newG < gScore.getOrDefault(nbKey, Double.MAX_VALUE)) {
                    gScore.put(nbKey, newG);
                    cameFrom.put(nbKey, curKey);
                    open.add(new SearchNode(nb.pos(), newG, newG + heuristic(nb.pos(), goal)));
                }
            }
        }

        return List.of(); // no path found
    }

    // ── Neighbours ────────────────────────────────────────────────────────────

    private static List<Neighbor> getNeighbors(ClientLevel world, BlockPos cur) {
        List<Neighbor> result = new ArrayList<>(16);
        int x = cur.getX(), y = cur.getY(), z = cur.getZ();

        for (int[] dir : DIRECTIONS) {
            int nx = x + dir[0];
            int nz = z + dir[1];
            double moveCost = (dir[0] != 0 && dir[1] != 0) ? 1.414 : 1.0;

            BlockPos sameLvl = new BlockPos(nx, y, nz);

            if (isWalkable(world, sameLvl)) {
                result.add(new Neighbor(sameLvl, moveCost));
                continue;
            }

            // Jump 1 block up (only if there is headroom above current position)
            if (canPassThrough(world, new BlockPos(x, y + 1, z))) {
                BlockPos jumpPos = new BlockPos(nx, y + 1, nz);
                if (isWalkable(world, jumpPos)) {
                    result.add(new Neighbor(jumpPos, moveCost + 0.5));
                    continue;
                }
            }

            // Fall 1–3 blocks down
            for (int drop = 1; drop <= 3; drop++) {
                BlockPos fallPos = new BlockPos(nx, y - drop, nz);
                if (!canPassThrough(world, new BlockPos(nx, y - drop + 1, nz))) break;
                if (isWalkable(world, fallPos)) {
                    result.add(new Neighbor(fallPos, moveCost + drop * 0.5));
                    break;
                }
            }
        }
        return result;
    }

    // ── Walkability helpers ───────────────────────────────────────────────────

    /**
     * A position is walkable if:
     *  - the support below has solid collision,
     *  - the feet block is passable,
     *  - the head block (1 above) is passable.
     */
    private static boolean isWalkable(ClientLevel world, BlockPos feet) {
        if (!world.isLoaded(feet)) return false;
        BlockPos below = feet.below();
        if (!world.isLoaded(below)) return false;

        // Support must be solid
        if (world.getBlockState(below).getCollisionShape(world, below).isEmpty()) return false;

        // 2-block clearance
        return canPassThrough(world, feet) && canPassThrough(world, feet.above());
    }

    private static boolean canPassThrough(ClientLevel world, BlockPos pos) {
        if (!world.isLoaded(pos)) return false;
        BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos).isEmpty();
    }

    // ── Heuristic / path reconstruction ──────────────────────────────────────

    private static double heuristic(BlockPos a, BlockPos b) {
        // Euclidean distance as admissible heuristic
        return Math.sqrt(a.distSqr(b));
    }

    private static List<BlockPos> buildPath(Map<Long, Long> cameFrom, BlockPos end) {
        LinkedList<BlockPos> path = new LinkedList<>();
        BlockPos current = end;
        path.addFirst(current);
        while (cameFrom.containsKey(current.asLong())) {
            current = BlockPos.of(cameFrom.get(current.asLong()));
            path.addFirst(current);
        }
        return new ArrayList<>(path);
    }
}
