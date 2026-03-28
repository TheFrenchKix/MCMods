package com.mwa.n0name.pathfinding;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.*;

/**
 * A* pathfinder inspired by Stevebot's architecture:
 * - Weighted heuristic (1.3x) for greedier/faster search
 * - Empirically-calibrated movement costs (step-up ~3x walk, step-down ~1.2x)
 * - Partial path fallback via best-node tracking
 * - Early termination: consecutive worse-than-best counter
 * - Timeout-based termination to prevent blocking the game thread
 * - Danger-aware neighbor generation (lava, fire, cactus, magma...)
 * - Diagonal touch avoidance for harmful blocks
 * - Node caching to avoid duplicate allocations
 */
public class AStarPathfinder {

    private static final String LOG_MODULE = "AStar";
    private static final int TRACE_BUDGET_LINES = 500;

    // --- Search limits ---
    private static final int MAX_ITERATIONS  = 8000;
    private static final int MAX_PATH_LENGTH = 200;
    private static final int MAX_SEARCH_RADIUS = 96;
    private static final long DEFAULT_TIMEOUT_MS = 80; // ~4 game ticks at 20 TPS

    // --- Goal tolerance ---
    private static final int GOAL_TOLERANCE_XZ_SQ = 2; // sqrt(2) blocks
    private static final int GOAL_TOLERANCE_Y = 1;

    // --- Movement costs (ratios from Stevebot's empirically-measured tick costs) ---
    private static final double COST_WALK_STRAIGHT   = 1.0;
    private static final double COST_WALK_DIAGONAL   = 1.414;
    private static final double COST_WALK_DIAGONAL_TOUCHES = 2.5; // touching blocks when cutting diagonal
    private static final double COST_STEP_UP_STRAIGHT    = 2.9;  // ~23/7.9 from Stevebot
    private static final double COST_STEP_UP_DIAGONAL    = 3.4;  // ~26.7/7.9
    private static final double COST_STEP_DOWN_STRAIGHT  = 1.18; // ~9.3/7.9
    private static final double COST_STEP_DOWN_DIAGONAL  = 1.7;  // ~13.3/7.9
    private static final double COST_DROP_BASE       = 1.5;      // base drop cost
    private static final double COST_DROP_PER_BLOCK  = 0.8;      // add per extra block height

    // --- Search tuning (from Stevebot + mineflayer-pathfinder) ---
    private static final double H_COST_WEIGHT = 1.3;     // weighted A*: greedier but faster
    private static final int MAX_WORSE_NODES  = 500;     // consecutive nodes worse than best → stop
    private static final int BEST_NODES_CAPACITY = 20;   // partial path fallback candidates
    /** Max extra cost beyond heuristic(start) before pruning a node (mineflayer's searchRadius). */
    private static final double SEARCH_BUBBLE_COST = 32.0;

    private static final int[][] NEIGHBORS_XZ = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},       // cardinal
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}       // diagonal
    };

    // --- Node representation ---
    private static class Node implements Comparable<Node> {
        final BlockPos pos;
        double gCost;
        double hCost;
        double fCost;
        Node parent;
        boolean closed;

        Node(BlockPos pos) {
            this.pos = pos;
            this.gCost = Double.MAX_VALUE;
            this.hCost = 0;
            this.fCost = Double.MAX_VALUE;
            this.parent = null;
            this.closed = false;
        }

        void update(double gCost, double hCost, Node parent) {
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node other) {
            int byFCost = Double.compare(this.fCost, other.fCost);
            if (byFCost != 0) return byFCost;
            return Double.compare(this.hCost, other.hCost); // tiebreak by heuristic (prefer closer-to-goal)
        }
    }

    /**
     * Best-nodes container: tracks N closest nodes to goal for partial path fallback.
     * Inspired by Stevebot's BestNodesContainer.
     */
    private static class BestNodesTracker {
        private final Node[] nodes;
        private int count;

        BestNodesTracker(int capacity) {
            this.nodes = new Node[capacity];
            this.count = 0;
        }

        void update(Node node) {
            // Insert if better (lower hCost) than worst tracked node
            if (count < nodes.length) {
                nodes[count++] = node;
                bubbleUp();
            } else if (node.hCost < nodes[count - 1].hCost) {
                nodes[count - 1] = node;
                bubbleUp();
            }
        }

        private void bubbleUp() {
            // Keep sorted by hCost ascending (best first)
            Arrays.sort(nodes, 0, count, Comparator.comparingDouble(n -> n.hCost));
        }

        Node getBest() {
            return count > 0 ? nodes[0] : null;
        }
    }

    // ===================== PUBLIC API =====================

    public static List<PathNode> findPath(BlockView world, BlockPos start, BlockPos goal) {
        return findPath(world, start, goal, true, false);
    }

    public static List<PathNode> findPath(BlockView world, BlockPos start, BlockPos goal, boolean logResult) {
        return findPath(world, start, goal, logResult, false);
    }

    public static List<PathNode> findPath(BlockView world, BlockPos start, BlockPos goal, boolean logResult, boolean verboseTrace) {
        PathfindingTrace.begin(verboseTrace, TRACE_BUDGET_LINES);
        try {
            return doFindPath(world, start, goal, logResult);
        } finally {
            PathfindingTrace.end();
        }
    }

    // ===================== CORE A* =====================

    private static List<PathNode> doFindPath(BlockView world, BlockPos start, BlockPos goal, boolean logResult) {
        trace("findPath start=" + formatPos(start) + " goal=" + formatPos(goal));

        if (start.equals(goal)) {
            trace("Start equals goal");
            return List.of(new PathNode(start));
        }
        if (start.getSquaredDistance(goal) > (double) MAX_SEARCH_RADIUS * MAX_SEARCH_RADIUS) {
            trace("Goal outside max search radius");
            if (logResult) DebugLogger.log(LOG_MODULE, "Goal too far, skipping");
            return Collections.emptyList();
        }

        // Node cache: one Node per BlockPos (like Stevebot's NodeCache)
        Map<BlockPos, Node> nodeCache = new HashMap<>();
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        BestNodesTracker bestNodes = new BestNodesTracker(BEST_NODES_CAPACITY);

        Node startNode = getOrCreateNode(nodeCache, start);
        double startH = heuristic(start, goal);
        startNode.update(0, startH, null);
        openSet.add(startNode);
        double maxSearchCost = startH + SEARCH_BUBBLE_COST;

        int iterations = 0;
        int worseNodeStreak = 0;
        double bestGCost = Double.MAX_VALUE; // best complete path cost found
        Node bestGoalNode = null;
        long timeStart = System.nanoTime();
        long timeoutNs = DEFAULT_TIMEOUT_MS * 1_000_000L;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;

            // Timeout check (every 64 iterations to avoid syscall overhead)
            if ((iterations & 63) == 0 && (System.nanoTime() - timeStart) > timeoutNs) {
                trace("Timeout after " + iterations + " iterations");
                if (logResult) DebugLogger.log(LOG_MODULE, "Timeout after " + iterations + " iters");
                break;
            }

            Node current = openSet.poll();

            // Skip if already closed (stale entry in PQ)
            if (current.closed) {
                trace("Skipping closed node " + formatPos(current.pos));
                continue;
            }
            current.closed = true;

            // Goal reached
            if (isGoalReached(current.pos, goal)) {
                if (bestGoalNode == null || current.gCost < bestGCost) {
                    bestGoalNode = current;
                    bestGCost = current.gCost;
                    trace("Goal reached at " + formatPos(current.pos) + " cost=" + formatDouble(current.gCost));
                }
                // Keep searching briefly for a better path (like Stevebot's MAX_BETTER_PATHS)
                // but for our use case, first hit is good enough with weighted heuristic
                break;
            }

            // Skip if already worse than a found path
            if (bestGoalNode != null && current.gCost >= bestGCost) {
                continue;
            }

            // Track best node for partial path fallback
            bestNodes.update(current);

            // Consecutive worse-than-best detection (from Stevebot)
            Node bestTracked = bestNodes.getBest();
            if (bestTracked != null && current.gCost > bestTracked.gCost) {
                worseNodeStreak++;
                if (worseNodeStreak > MAX_WORSE_NODES) {
                    trace("Terminated: " + MAX_WORSE_NODES + " consecutive worse nodes");
                    if (logResult) DebugLogger.log(LOG_MODULE, "Goal likely unreachable, " + iterations + " iters");
                    break;
                }
            } else {
                worseNodeStreak = 0;
            }

            // Expand neighbors
            expandNeighbors(world, current, goal, nodeCache, openSet, maxSearchCost);
        }

        // Build result path
        if (bestGoalNode != null) {
            List<PathNode> rawPath = reconstructPath(bestGoalNode);
            List<PathNode> finalPath = ModConfig.getInstance().isPathSmoothingEnabled()
                ? smoothPath(world, rawPath)
                : rawPath;
            trace("Complete path: " + rawPath.size() + " nodes -> " + finalPath.size());
            if (logResult) {
                DebugLogger.log(LOG_MODULE, "Path found: " + finalPath.size() + " nodes in " + iterations + " iters");
            }
            return finalPath;
        }

        // Partial path fallback (from Stevebot's BestNodesContainer)
        Node partialTarget = bestNodes.getBest();
        if (partialTarget != null && partialTarget.parent != null) {
            List<PathNode> rawPath = reconstructPath(partialTarget);
            List<PathNode> finalPath = ModConfig.getInstance().isPathSmoothingEnabled()
                ? smoothPath(world, rawPath)
                : rawPath;
            trace("Partial path: " + rawPath.size() + " nodes -> " + finalPath.size());
            if (logResult) {
                DebugLogger.log(LOG_MODULE, "Partial path: " + finalPath.size() + " nodes in " + iterations + " iters (goal not reached)");
            }
            return finalPath;
        }

        trace("No path found after " + iterations + " iterations");
        if (logResult) DebugLogger.log(LOG_MODULE, "No path found after " + iterations + " iters");
        return Collections.emptyList();
    }

    // ===================== NEIGHBOR EXPANSION =====================

    private static void expandNeighbors(BlockView world, Node current, BlockPos goal,
                                         Map<BlockPos, Node> nodeCache, PriorityQueue<Node> openSet,
                                         double maxSearchCost) {
        trace("expand " + formatPos(current.pos));

        for (int[] dir : NEIGHBORS_XZ) {
            boolean diagonal = Math.abs(dir[0]) + Math.abs(dir[1]) == 2;

            if (diagonal && !canTraverseDiagonal(world, current.pos, dir[0], dir[1])) {
                continue;
            }

            // Try flat movement first
            BlockPos flat = current.pos.add(dir[0], 0, dir[1]);
            if (WalkabilityChecker.canTraverse(world, current.pos, flat)) {
                double cost = diagonal
                    ? (touchesDangerous(world, current.pos, dir[0], dir[1]) ? COST_WALK_DIAGONAL_TOUCHES : COST_WALK_DIAGONAL)
                    : COST_WALK_STRAIGHT;
                tryAddNeighbor(current, flat, cost, goal, nodeCache, openSet, maxSearchCost);
                continue;
            }

            // Try step up (+1)
            BlockPos up = current.pos.add(dir[0], 1, dir[1]);
            if (WalkabilityChecker.canTraverse(world, current.pos, up)) {
                double cost = diagonal ? COST_STEP_UP_DIAGONAL : COST_STEP_UP_STRAIGHT;
                tryAddNeighbor(current, up, cost, goal, nodeCache, openSet, maxSearchCost);
                continue;
            }

            // Try drops (-1 to -3)
            for (int drop = 1; drop <= 3; drop++) {
                BlockPos down = current.pos.add(dir[0], -drop, dir[1]);
                if (WalkabilityChecker.canTraverse(world, current.pos, down)) {
                    double cost = (diagonal ? COST_STEP_DOWN_DIAGONAL : COST_STEP_DOWN_STRAIGHT)
                        + (drop > 1 ? COST_DROP_BASE + COST_DROP_PER_BLOCK * (drop - 1) : 0);
                    tryAddNeighbor(current, down, cost, goal, nodeCache, openSet, maxSearchCost);
                    break;
                }
            }
        }
    }

    private static void tryAddNeighbor(Node current, BlockPos neighborPos, double moveCost,
                                        BlockPos goal, Map<BlockPos, Node> nodeCache,
                                        PriorityQueue<Node> openSet, double maxSearchCost) {
        Node neighbor = getOrCreateNode(nodeCache, neighborPos);
        if (neighbor.closed) return;

        double newG = current.gCost + moveCost;
        double h = heuristic(neighborPos, goal);

        // Search radius: prune if g+h exceeds startH + SEARCH_BUBBLE_COST (mineflayer technique)
        if (newG + h > maxSearchCost) return;

        // Only reopen if improvement is significant (>1.0, from Stevebot)
        if (newG >= neighbor.gCost) return;
        if (neighbor.gCost < Double.MAX_VALUE - 10 && (neighbor.gCost - newG) < 1.0) return;

        neighbor.update(newG, h, current);
        openSet.add(neighbor); // add new entry; stale entries filtered by 'closed' flag
        trace("  + " + formatPos(neighborPos) + " g=" + formatDouble(newG) + " f=" + formatDouble(neighbor.fCost));
    }

    // ===================== NODE CACHE =====================

    private static Node getOrCreateNode(Map<BlockPos, Node> cache, BlockPos pos) {
        return cache.computeIfAbsent(pos, Node::new);
    }

    // ===================== HEURISTIC =====================

    /**
     * Weighted octile heuristic with realistic vertical costs.
     * Uses Stevebot's formula: diagonal cost ratio + asymmetric vertical penalty.
     */
    private static double heuristic(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dz = Math.abs(a.getZ() - b.getZ());
        int dy = a.getY() - b.getY(); // positive = a is above b

        int dMin = Math.min(dx, dz);
        int dMax = Math.max(dx, dz);

        // Octile distance using actual cost ratios
        double octileXZ = dMin * COST_WALK_DIAGONAL + (dMax - dMin) * COST_WALK_STRAIGHT;

        // Asymmetric vertical: going up (step-up) is expensive, going down (step-down) is cheap
        double verticalCost = dy < 0
            ? (-dy) * COST_STEP_UP_STRAIGHT    // need to climb
            : dy * COST_STEP_DOWN_STRAIGHT;    // dropping down

        return (octileXZ + verticalCost) * H_COST_WEIGHT;
    }

    // ===================== DIAGONAL CHECKS =====================

    private static boolean canTraverseDiagonal(BlockView world, BlockPos pos, int dx, int dz) {
        BlockPos sideA = pos.add(dx, 0, 0);
        BlockPos sideB = pos.add(0, 0, dz);
        boolean canA = WalkabilityChecker.canTraverse(world, pos, sideA);
        boolean canB = WalkabilityChecker.canTraverse(world, pos, sideB);

        // Need at least one side passable
        if (!canA && !canB) return false;

        // Avoid touching dangerous blocks (from Stevebot's avoidTouching)
        boolean avoidA = WalkabilityChecker.avoidTouching(world, sideA)
            || WalkabilityChecker.avoidTouching(world, sideA.up());
        boolean avoidB = WalkabilityChecker.avoidTouching(world, sideB)
            || WalkabilityChecker.avoidTouching(world, sideB.up());

        // If the only traversable side's other side is dangerous, block the diagonal
        if (canA && !canB && avoidB) return false;
        if (canB && !canA && avoidA) return false;
        if (avoidA && avoidB) return false;

        return true;
    }

    /**
     * Check if a diagonal move would touch a non-traversable block (costs more).
     */
    private static boolean touchesDangerous(BlockView world, BlockPos pos, int dx, int dz) {
        BlockPos sideA = pos.add(dx, 0, 0);
        BlockPos sideB = pos.add(0, 0, dz);
        boolean canA = WalkabilityChecker.isPassable(world, sideA) && WalkabilityChecker.isPassable(world, sideA.up());
        boolean canB = WalkabilityChecker.isPassable(world, sideB) && WalkabilityChecker.isPassable(world, sideB.up());
        return !canA || !canB;
    }

    // ===================== GOAL CHECK =====================

    private static boolean isGoalReached(BlockPos current, BlockPos goal) {
        int dx = current.getX() - goal.getX();
        int dz = current.getZ() - goal.getZ();
        int dy = Math.abs(current.getY() - goal.getY());
        return dx * dx + dz * dz <= GOAL_TOLERANCE_XZ_SQ && dy <= GOAL_TOLERANCE_Y;
    }

    // ===================== PATH BUILDING =====================

    private static List<PathNode> reconstructPath(Node endNode) {
        List<PathNode> path = new ArrayList<>();
        Node current = endNode;
        while (current != null && path.size() < MAX_PATH_LENGTH) {
            path.add(new PathNode(current.pos));
            current = current.parent;
        }
        Collections.reverse(path);
        trace("reconstructPath size=" + path.size());
        return path;
    }

    // ===================== PATH SMOOTHING =====================

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

            if (dx1 != dx2 || dy1 != dy2 || dz1 != dz2) {
                out.add(curr);
            }
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
        if (steps == 0) return WalkabilityChecker.canTraverse(world, from, to);

        BlockPos prev = from;
        for (int s = 1; s <= steps; s++) {
            double t = s / (double) steps;
            int x = (int) Math.round(lerp(from.getX(), to.getX(), t));
            int y = (int) Math.round(lerp(from.getY(), to.getY(), t));
            int z = (int) Math.round(lerp(from.getZ(), to.getZ(), t));

            BlockPos next = new BlockPos(x, y, z);
            if (next.equals(prev)) continue;

            if (!WalkabilityChecker.canTraverse(world, prev, next)) return false;
            prev = next;
        }

        return prev.equals(to) || WalkabilityChecker.canTraverse(world, prev, to);
    }

    // ===================== RANDOM WALKABLE TARGET =====================

    public static BlockPos findRandomWalkableTarget(BlockView world, BlockPos origin, int radius, Random random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx == 0 && dz == 0) continue;

            for (int dy = -3; dy <= 3; dy++) {
                BlockPos candidate = origin.add(dx, dy, dz);
                if (WalkabilityChecker.isWalkable(world, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    // ===================== UTILITY =====================

    private static double lerp(int a, int b, double t) {
        return a + (b - a) * t;
    }

    private static void trace(String message) {
        PathfindingTrace.log(LOG_MODULE, message);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
