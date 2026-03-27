package com.mwa.n0name.pathfinding;

import com.mwa.n0name.DebugLogger;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.*;

public class AStarPathfinder {

    private static final int MAX_ITERATIONS  = 5000;
    private static final int MAX_PATH_LENGTH = 200;

    private static final int[][] NEIGHBORS_XZ = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},       // cardinal
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}       // diagonal
    };

    private static class Node implements Comparable<Node> {
        final BlockPos pos;
        double gCost;
        double hCost;
        double fCost;
        Node parent;

        Node(BlockPos pos, double gCost, double hCost, Node parent) {
            this.pos = pos;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fCost, other.fCost);
        }
    }

    /**
     * Find a path from start to goal using A*.
     * Returns empty list if no path found within iteration limit.
     */
    public static List<PathNode> findPath(BlockView world, BlockPos start, BlockPos goal) {
        if (start.equals(goal)) return List.of(new PathNode(start));

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<BlockPos, Double> bestCost = new HashMap<>();

        Node startNode = new Node(start, 0, heuristic(start, goal), null);
        openSet.add(startNode);
        bestCost.put(start, 0.0);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = openSet.poll();

            // Goal reached
            if (current.pos.equals(goal)) {
                List<PathNode> path = reconstructPath(current);
                DebugLogger.log("AStar", "Path found: " + path.size() + " nodes in " + iterations + " iterations");
                return path;
            }

            // Expand neighbors
            for (BlockPos neighbor : getNeighbors(world, current.pos)) {
                double moveCost = calculateMoveCost(current.pos, neighbor);
                double newG = current.gCost + moveCost;

                Double existingCost = bestCost.get(neighbor);
                if (existingCost != null && existingCost <= newG) continue;

                bestCost.put(neighbor, newG);
                Node neighborNode = new Node(neighbor, newG, heuristic(neighbor, goal), current);
                openSet.add(neighborNode);
            }
        }

        DebugLogger.log("AStar", "No path found after " + iterations + " iterations");
        return Collections.emptyList();
    }

    /**
     * Find a random walkable position within radius blocks of origin.
     */
    public static BlockPos findRandomWalkableTarget(BlockView world, BlockPos origin, int radius, Random random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx == 0 && dz == 0) continue;

            // Search vertically for a walkable position
            for (int dy = -3; dy <= 3; dy++) {
                BlockPos candidate = origin.add(dx, dy, dz);
                if (WalkabilityChecker.isWalkable(world, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double calculateMoveCost(BlockPos from, BlockPos to) {
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();

        double baseCost = (dx + dz == 2) ? 1.414 : 1.0; // diagonal vs cardinal
        if (dy > 0) baseCost += 0.5 * dy;   // jumping costs more
        if (dy < 0) baseCost += 0.2 * -dy;  // dropping is slightly costly
        return baseCost;
    }

    private static List<BlockPos> getNeighbors(BlockView world, BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();

        for (int[] dir : NEIGHBORS_XZ) {
            // Try flat movement
            BlockPos flat = pos.add(dir[0], 0, dir[1]);
            if (WalkabilityChecker.canTraverse(world, pos, flat)) {
                neighbors.add(flat);
                continue;
            }

            // Try step up (jump)
            BlockPos up = pos.add(dir[0], 1, dir[1]);
            if (WalkabilityChecker.canTraverse(world, pos, up)) {
                neighbors.add(up);
                continue;
            }

            // Try drops (1-3 blocks)
            for (int drop = 1; drop <= 3; drop++) {
                BlockPos down = pos.add(dir[0], -drop, dir[1]);
                if (WalkabilityChecker.canTraverse(world, pos, down)) {
                    neighbors.add(down);
                    break;
                }
            }
        }

        return neighbors;
    }

    private static List<PathNode> reconstructPath(Node endNode) {
        List<PathNode> path = new ArrayList<>();
        Node current = endNode;
        while (current != null && path.size() < MAX_PATH_LENGTH) {
            path.add(new PathNode(current.pos));
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }
}
