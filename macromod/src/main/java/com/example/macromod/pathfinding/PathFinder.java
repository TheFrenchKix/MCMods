package com.example.macromod.pathfinding;

import com.example.macromod.util.BlockUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Client-side A* pathfinding implementation for navigating through the Minecraft world.
 * Supports walking, jumping (1 block up), and descending (1-2 blocks down).
 * Avoids dangerous blocks like lava, fire, and cactus.
 */
@Environment(EnvType.CLIENT)
public class PathFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");

    /** Maximum number of nodes to expand before giving up. */
    private int maxNodes = 2000;

    /** Movement offsets: 4 cardinal + 4 diagonal directions. */
    private static final int[][] HORIZONTAL_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},     // cardinal
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}     // diagonal
    };

    public PathFinder() {
    }

    public PathFinder(int maxNodes) {
        this.maxNodes = maxNodes;
    }

    /**
     * Finds a path from start to goal using A* algorithm.
     *
     * @param start the starting position (player feet position)
     * @param goal  the target position
     * @param world the client world
     * @return ordered list of positions from start to goal, or null if no path found
     */
    public List<BlockPos> findPath(BlockPos start, BlockPos goal, ClientWorld world) {
        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Map<BlockPos, PathNode> allNodes = new HashMap<>();

        PathNode startNode = new PathNode(start);
        startNode.setG(0);
        startNode.setH(heuristic(start, goal));
        openSet.add(startNode);
        allNodes.put(start, startNode);

        int nodesExpanded = 0;

        while (!openSet.isEmpty() && nodesExpanded < maxNodes) {
            PathNode current = openSet.poll();
            nodesExpanded++;

            // Goal reached
            if (current.getPos().equals(goal)) {
                return reconstructPath(current);
            }

            // Also accept being close to goal (horizontally within 1 block)
            if (isCloseEnough(current.getPos(), goal)) {
                return reconstructPath(current);
            }

            // Expand neighbors
            for (BlockPos neighborPos : getNeighbors(current.getPos(), world)) {
                double moveCost = getMoveCost(current.getPos(), neighborPos);
                double tentativeG = current.getG() + moveCost;

                PathNode neighborNode = allNodes.get(neighborPos);
                if (neighborNode == null) {
                    neighborNode = new PathNode(neighborPos);
                    allNodes.put(neighborPos, neighborNode);
                }

                if (tentativeG < neighborNode.getG()) {
                    neighborNode.setParent(current);
                    neighborNode.setG(tentativeG);
                    neighborNode.setH(heuristic(neighborPos, goal));
                    // Re-add to open set (priority queue handles ordering)
                    openSet.remove(neighborNode);
                    openSet.add(neighborNode);
                }
            }
        }

        LOGGER.warn("No path found from {} to {} after expanding {} nodes", start, goal, nodesExpanded);
        return null;
    }

    /**
     * Euclidean distance heuristic.
     */
    private double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Returns true if the position is close enough to the goal to consider it reached.
     */
    private boolean isCloseEnough(BlockPos pos, BlockPos goal) {
        int dx = Math.abs(pos.getX() - goal.getX());
        int dy = Math.abs(pos.getY() - goal.getY());
        int dz = Math.abs(pos.getZ() - goal.getZ());
        return dx <= 1 && dy <= 1 && dz <= 1;
    }

    /**
     * Returns movement cost between two adjacent positions.
     */
    private double getMoveCost(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dz = Math.abs(from.getZ() - to.getZ());
        int dy = to.getY() - from.getY();

        double baseCost = (dx + dz == 2) ? 1.414 : 1.0; // diagonal vs cardinal
        if (dy > 0) baseCost += 0.5; // jumping costs more
        if (dy < 0) baseCost += 0.3; // falling is slightly costly
        return baseCost;
    }

    /**
     * Returns all valid neighbor positions from the given position.
     * Checks walkability, jump/fall possibilities, and danger.
     */
    private List<BlockPos> getNeighbors(BlockPos pos, ClientWorld world) {
        List<BlockPos> neighbors = new ArrayList<>();

        for (int[] offset : HORIZONTAL_OFFSETS) {
            int dx = offset[0];
            int dz = offset[1];

            // Same level: walk
            BlockPos sameLevel = pos.add(dx, 0, dz);
            if (canWalkTo(world, pos, sameLevel)) {
                neighbors.add(sameLevel);
                continue; // don't check jump/fall if we can walk
            }

            // One block up: jump
            BlockPos oneUp = pos.add(dx, 1, dz);
            if (canJumpTo(world, pos, oneUp)) {
                neighbors.add(oneUp);
            }

            // One block down: step down
            BlockPos oneDown = pos.add(dx, -1, dz);
            if (canFallTo(world, pos, oneDown)) {
                neighbors.add(oneDown);
            }

            // Two blocks down: fall
            BlockPos twoDown = pos.add(dx, -2, dz);
            if (canFallTo(world, pos, twoDown)) {
                neighbors.add(twoDown);
            }
        }

        return neighbors;
    }

    /**
     * Checks if the player can walk from 'from' to 'to' at the same level.
     * Requires: solid ground below 'to', passable at 'to' and 'to.up()', no danger.
     */
    private boolean canWalkTo(ClientWorld world, BlockPos from, BlockPos to) {
        if (!BlockUtils.isChunkLoaded(world, to)) return false;
        if (BlockUtils.isDangerous(world, to) || BlockUtils.isDangerous(world, to.down())) return false;

        // Must have solid ground below and two passable blocks for the player body
        if (!BlockUtils.isSolid(world, to.down())) return false;
        if (!BlockUtils.isPassable(world, to)) return false;
        if (!BlockUtils.isPassable(world, to.up())) return false;

        // For diagonal movement, check that both cardinal blocks are passable
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx != 0 && dz != 0) {
            BlockPos mid1 = from.add(dx, 0, 0);
            BlockPos mid2 = from.add(0, 0, dz);
            if (!BlockUtils.isPassable(world, mid1) || !BlockUtils.isPassable(world, mid1.up())) return false;
            if (!BlockUtils.isPassable(world, mid2) || !BlockUtils.isPassable(world, mid2.up())) return false;
        }

        return true;
    }

    /**
     * Checks if the player can jump from 'from' to 'to' (1 block up).
     * Requires: room to jump (3 blocks of head clearance at from),
     * solid block below 'to', passable at 'to' and 'to.up()'.
     */
    private boolean canJumpTo(ClientWorld world, BlockPos from, BlockPos to) {
        if (!BlockUtils.isChunkLoaded(world, to)) return false;
        if (BlockUtils.isDangerous(world, to) || BlockUtils.isDangerous(world, to.down())) return false;

        // Player needs headroom at starting position (3 blocks: feet, body, jump clearance)
        if (!BlockUtils.isPassable(world, from.up(2))) return false;

        // Destination checks
        if (!BlockUtils.isSolid(world, to.down())) return false;
        if (!BlockUtils.isPassable(world, to)) return false;
        if (!BlockUtils.isPassable(world, to.up())) return false;

        return true;
    }

    /**
     * Checks if the player can fall from 'from' to 'to' (1-2 blocks down).
     * Requires: passable in the horizontal movement gap, solid ground at 'to'.
     */
    private boolean canFallTo(ClientWorld world, BlockPos from, BlockPos to) {
        if (!BlockUtils.isChunkLoaded(world, to)) return false;
        if (BlockUtils.isDangerous(world, to) || BlockUtils.isDangerous(world, to.down())) return false;

        // Destination must have solid ground and passable body space
        if (!BlockUtils.isSolid(world, to.down())) return false;
        if (!BlockUtils.isPassable(world, to)) return false;
        if (!BlockUtils.isPassable(world, to.up())) return false;

        // Check the intermediate horizontal position is passable at from's level
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        BlockPos horizontalStep = from.add(dx, 0, dz);
        if (!BlockUtils.isPassable(world, horizontalStep)) return false;
        if (!BlockUtils.isPassable(world, horizontalStep.up())) return false;

        return true;
    }

    /**
     * Reconstructs the path from goal node back to start by following parent pointers.
     */
    private List<BlockPos> reconstructPath(PathNode goalNode) {
        List<BlockPos> path = new ArrayList<>();
        PathNode current = goalNode;
        while (current != null) {
            path.add(current.getPos());
            current = current.getParent();
        }
        Collections.reverse(path);
        return path;
    }

    public void setMaxNodes(int maxNodes) {
        this.maxNodes = maxNodes;
    }
}
