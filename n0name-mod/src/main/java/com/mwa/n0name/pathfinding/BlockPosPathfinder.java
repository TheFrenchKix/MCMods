package com.mwa.n0name.pathfinding;

import com.mwa.n0name.DebugLogger;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Simple 3D A* on a BlockPos grid.
 * Input: scanned walkable blocks + destination.
 * Output: block-by-block path (List<BlockPos>).
 */
public final class BlockPosPathfinder {

    private static final String LOG_MODULE = "BlockPosPathfinder";
    private static final int MAX_ITERATIONS = 10_000;

    private static final int[][] OFFSETS_XZ = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private BlockPosPathfinder() {
    }

    public static List<BlockPos> findPath(Collection<BlockPos> scannedBlocks, BlockPos start, BlockPos destination) {
        return findPath(null, scannedBlocks, start, destination);
    }

    public static List<BlockPos> findPath(BlockView world, Collection<BlockPos> scannedBlocks, BlockPos start, BlockPos destination) {
        if (start == null || destination == null || scannedBlocks == null || scannedBlocks.isEmpty()) {
            return Collections.emptyList();
        }

        Set<BlockPos> grid = new HashSet<>(scannedBlocks.size() + 2);
        for (BlockPos pos : scannedBlocks) {
            grid.add(pos.toImmutable());
        }
        grid.add(start.toImmutable());
        grid.add(destination.toImmutable());

        if (start.equals(destination)) {
            return List.of(start.toImmutable());
        }

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.fCost, b.fCost));
        Map<BlockPos, Node> nodes = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = nodeFor(nodes, start);
        startNode.gCost = 0.0;
        startNode.hCost = heuristic(start, destination);
        startNode.fCost = startNode.hCost;
        open.add(startNode);

        Node best = startNode;
        int iterations = 0;

        while (!open.isEmpty() && iterations++ < MAX_ITERATIONS) {
            Node current = open.poll();
            if (!closed.add(current.pos)) {
                continue;
            }

            if (current.hCost < best.hCost) {
                best = current;
            }

            if (current.pos.equals(destination)) {
                List<BlockPos> path = rebuildPath(current);
                DebugLogger.log(LOG_MODULE, "Path found with " + path.size() + " nodes");
                return path;
            }

            for (BlockPos neighborPos : neighbors(current.pos, grid)) {
                if (closed.contains(neighborPos)) {
                    continue;
                }

                if (world != null) {
                    if (!WalkabilityChecker.isWalkable(world, neighborPos)) {
                        continue;
                    }
                    if (!WalkabilityChecker.canTraverse(world, current.pos, neighborPos)) {
                        continue;
                    }
                }

                Node neighbor = nodeFor(nodes, neighborPos);
                double newG = current.gCost + movementCost(current.pos, neighborPos);
                if (newG >= neighbor.gCost) {
                    continue;
                }

                neighbor.parent = current;
                neighbor.gCost = newG;
                neighbor.hCost = heuristic(neighborPos, destination);
                neighbor.fCost = neighbor.gCost + neighbor.hCost;
                open.add(neighbor);
            }
        }

        // Partial fallback: return best path found toward destination.
        if (best != null && best != startNode) {
            List<BlockPos> partial = rebuildPath(best);
            DebugLogger.log(LOG_MODULE, "Partial path returned (" + partial.size() + " nodes)");
            return partial;
        }

        DebugLogger.log(LOG_MODULE, "No path found");
        return Collections.emptyList();
    }

    private static List<BlockPos> neighbors(BlockPos pos, Set<BlockPos> grid) {
        List<BlockPos> out = new ArrayList<>(24);

        for (int[] d : OFFSETS_XZ) {
            int dx = d[0];
            int dz = d[1];

            for (int dy = -1; dy <= 1; dy++) {
                BlockPos n = pos.add(dx, dy, dz).toImmutable();
                if (grid.contains(n)) {
                    out.add(n);
                }
            }
        }

        return out;
    }

    private static double movementCost(BlockPos from, BlockPos to) {
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();

        double base = 1.0;
        if (dy > 0) {
            return base + 0.9; // jump/step-up penalty
        }
        if (dy < 0) {
            return base + 0.25; // controlled drop penalty
        }
        return base;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Node nodeFor(Map<BlockPos, Node> nodes, BlockPos pos) {
        return nodes.computeIfAbsent(pos.toImmutable(), Node::new);
    }

    private static List<BlockPos> rebuildPath(Node endNode) {
        List<BlockPos> path = new ArrayList<>();
        Node cursor = endNode;
        while (cursor != null) {
            path.add(cursor.pos);
            cursor = cursor.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private static final class Node {
        private final BlockPos pos;
        private Node parent;
        private double gCost = Double.POSITIVE_INFINITY;
        private double hCost = Double.POSITIVE_INFINITY;
        private double fCost = Double.POSITIVE_INFINITY;

        private Node(BlockPos pos) {
            this.pos = pos;
        }
    }
}
