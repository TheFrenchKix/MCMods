package com.mwa.n0name.pathfinding;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.*;

public class AStarPathfinder {

    private static final int MAX_ITERATIONS  = 5000;
    private static final int MAX_PATH_LENGTH = 200;
    private static final int MAX_SEARCH_RADIUS = 96;
    private static final int GOAL_TOLERANCE_XZ_SQ = 2; // sqrt(2) blocks
    private static final int GOAL_TOLERANCE_Y = 1;

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
            int byFCost = Double.compare(this.fCost, other.fCost);
            if (byFCost != 0) return byFCost;
            return Double.compare(this.hCost, other.hCost);
        }
    }

    /**
     * Find a path from start to goal using A*.
     * Returns empty list if no path found within iteration limit.
     */
    public static List<PathNode> findPath(BlockView world, BlockPos start, BlockPos goal) {
        return findPath(world, start, goal, true);
    }

    /**
     * Find a path from start to goal using A*.
     * When logResult is false, this method suppresses noisy success/failure logs.
     */
    public static List<PathNode> findPath(BlockView world, BlockPos start, BlockPos goal, boolean logResult) {
        if (start.equals(goal)) return List.of(new PathNode(start));
        if (start.getSquaredDistance(goal) > (double)MAX_SEARCH_RADIUS * MAX_SEARCH_RADIUS) {
            if (logResult) {
                DebugLogger.log("AStar", "Goal too far from start, skipping path search");
            }
            return Collections.emptyList();
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<BlockPos, Double> bestCost = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();

        Node startNode = new Node(start, 0, heuristic(start, goal), null);
        openSet.add(startNode);
        bestCost.put(start, 0.0);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = openSet.poll();

            Double knownBest = bestCost.get(current.pos);
            if (knownBest == null || current.gCost > knownBest + 1e-6) {
                continue;
            }
            if (!closedSet.add(current.pos)) {
                continue;
            }

            // Goal reached
            if (isGoalReached(current.pos, goal)) {
                List<PathNode> rawPath = reconstructPath(current);
                List<PathNode> finalPath = ModConfig.getInstance().isPathSmoothingEnabled()
                    ? smoothPath(world, rawPath)
                    : rawPath;
                if (logResult) {
                    DebugLogger.log("AStar", "Path found: " + rawPath.size() + " nodes"
                        + (finalPath.size() != rawPath.size() ? " -> " + finalPath.size() : "")
                        + " in " + iterations + " iterations");
                }
                return finalPath;
            }

            // Expand neighbors
            for (BlockPos neighbor : getNeighbors(world, current.pos)) {
                if (closedSet.contains(neighbor)) continue;

                double moveCost = calculateMoveCost(current.pos, neighbor);
                double newG = current.gCost + moveCost;

                Double existingCost = bestCost.get(neighbor);
                if (existingCost != null && existingCost <= newG) continue;

                bestCost.put(neighbor, newG);
                Node neighborNode = new Node(neighbor, newG, heuristic(neighbor, goal), current);
                openSet.add(neighborNode);
            }
        }

        if (logResult) {
            DebugLogger.log("AStar", "No path found after " + iterations + " iterations");
        }
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
        int dx = Math.abs(a.getX() - b.getX());
        int dz = Math.abs(a.getZ() - b.getZ());
        int dy = Math.abs(a.getY() - b.getY());

        // Octile distance in XZ with softer vertical penalty reduces unnecessary climbs.
        int min = Math.min(dx, dz);
        int max = Math.max(dx, dz);
        double octileXZ = (max - min) + (1.41421356237 * min);
        return octileXZ + dy * 0.65;
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
            boolean diagonal = Math.abs(dir[0]) + Math.abs(dir[1]) == 2;
            if (diagonal && !canTraverseDiagonal(world, pos, dir[0], dir[1])) {
                continue;
            }

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

    private static boolean isGoalReached(BlockPos current, BlockPos goal) {
        int dx = current.getX() - goal.getX();
        int dz = current.getZ() - goal.getZ();
        int dy = Math.abs(current.getY() - goal.getY());
        return dx * dx + dz * dz <= GOAL_TOLERANCE_XZ_SQ && dy <= GOAL_TOLERANCE_Y;
    }

    private static boolean canTraverseDiagonal(BlockView world, BlockPos pos, int dx, int dz) {
        BlockPos sideA = pos.add(dx, 0, 0);
        BlockPos sideB = pos.add(0, 0, dz);
        // Require at least one side to be traversable to prevent squeezing through corners.
        return WalkabilityChecker.canTraverse(world, pos, sideA)
            || WalkabilityChecker.canTraverse(world, pos, sideB);
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

    private static List<PathNode> smoothPath(BlockView world, List<PathNode> path) {
        if (path.size() < 3) return path;

        List<PathNode> compact = removeCollinear(path);
        if (compact.size() < 3) return compact;

        List<PathNode> smoothed = new ArrayList<>();
        int i = 0;
        smoothed.add(compact.get(0));

        while (i < compact.size() - 1 && smoothed.size() < MAX_PATH_LENGTH) {
            int best = i + 1;
            for (int j = compact.size() - 1; j > i + 1; j--) {
                if (canShortcut(world, compact.get(i), compact.get(j))) {
                    best = j;
                    break;
                }
            }
            smoothed.add(compact.get(best));
            i = best;
        }

        return smoothed;
    }

    private static List<PathNode> removeCollinear(List<PathNode> path) {
        if (path.size() < 3) return path;

        List<PathNode> out = new ArrayList<>();
        out.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            PathNode prev = path.get(i - 1);
            PathNode curr = path.get(i);
            PathNode next = path.get(i + 1);

            int dx1 = Integer.compare(curr.x(), prev.x());
            int dy1 = Integer.compare(curr.y(), prev.y());
            int dz1 = Integer.compare(curr.z(), prev.z());
            int dx2 = Integer.compare(next.x(), curr.x());
            int dy2 = Integer.compare(next.y(), curr.y());
            int dz2 = Integer.compare(next.z(), curr.z());

            if (dx1 == dx2 && dy1 == dy2 && dz1 == dz2) {
                continue;
            }

            out.add(curr);
        }

        out.add(path.get(path.size() - 1));
        return out;
    }

    private static boolean canShortcut(BlockView world, PathNode fromNode, PathNode toNode) {
        BlockPos from = fromNode.toBlockPos();
        BlockPos to = toNode.toBlockPos();

        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) {
            return WalkabilityChecker.canTraverse(world, from, to);
        }

        BlockPos prev = from;
        for (int s = 1; s <= steps; s++) {
            double t = s / (double)steps;
            int x = (int)Math.round(lerp(from.getX(), to.getX(), t));
            int y = (int)Math.round(lerp(from.getY(), to.getY(), t));
            int z = (int)Math.round(lerp(from.getZ(), to.getZ(), t));

            BlockPos next = new BlockPos(x, y, z);
            if (next.equals(prev)) continue;

            if (!WalkabilityChecker.canTraverse(world, prev, next)) {
                return false;
            }
            prev = next;
        }

        return prev.equals(to) || WalkabilityChecker.canTraverse(world, prev, to);
    }

    private static double lerp(int a, int b, double t) {
        return a + (b - a) * t;
    }
}
