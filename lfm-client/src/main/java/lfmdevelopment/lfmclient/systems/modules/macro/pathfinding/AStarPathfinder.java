package lfmdevelopment.lfmclient.systems.modules.macro.pathfinding;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class AStarPathfinder {
    private static final int MAX_ITERATIONS = 5000;
    private static final int MAX_FALL = 3;
    private static final BlockPos[] HORIZONTAL_OFFSETS = {
        new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
        new BlockPos(0, 0, 1), new BlockPos(0, 0, -1),
        new BlockPos(1, 0, 1), new BlockPos(-1, 0, 1),
        new BlockPos(1, 0, -1), new BlockPos(-1, 0, -1)
    };

    private AStarPathfinder() {}

    public static Path findPath(BlockPos start, BlockPos goal) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        World world = mc.world;

        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Map<BlockPos, PathNode> allNodes = new HashMap<>();

        PathNode startNode = new PathNode(start);
        startNode.gCost = 0;
        startNode.hCost = heuristic(start, goal);
        openSet.add(startNode);
        allNodes.put(start, startNode);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            PathNode current = openSet.poll();

            if (current.pos.equals(goal)) {
                return reconstructPath(current);
            }

            for (BlockPos neighborPos : getNeighbors(current.pos, world)) {
                double moveCost = current.pos.getX() != neighborPos.getX() && current.pos.getZ() != neighborPos.getZ()
                    ? 1.414 : 1.0;
                int dy = neighborPos.getY() - current.pos.getY();
                if (dy > 0) moveCost += 0.5;
                if (dy < 0) moveCost += 0.2;

                double tentativeG = current.gCost + moveCost;

                PathNode neighbor = allNodes.computeIfAbsent(neighborPos, PathNode::new);
                if (tentativeG < neighbor.gCost) {
                    neighbor.gCost = tentativeG;
                    neighbor.hCost = heuristic(neighborPos, goal);
                    neighbor.parent = current;
                    openSet.remove(neighbor);
                    openSet.add(neighbor);
                }
            }
        }

        return null; // No path found
    }

    private static List<BlockPos> getNeighbors(BlockPos pos, World world) {
        List<BlockPos> neighbors = new ArrayList<>();

        for (BlockPos offset : HORIZONTAL_OFFSETS) {
            BlockPos candidate = pos.add(offset);

            // Same level walk
            if (isWalkable(candidate, world)) {
                // Diagonal movement requires both adjacent straight blocks to be passable
                if (offset.getX() != 0 && offset.getZ() != 0) {
                    BlockPos adj1 = pos.add(offset.getX(), 0, 0);
                    BlockPos adj2 = pos.add(0, 0, offset.getZ());
                    if (!isPassable(adj1, world) || !isPassable(adj1.up(), world)
                        || !isPassable(adj2, world) || !isPassable(adj2.up(), world)) {
                        continue;
                    }
                }
                neighbors.add(candidate);
                continue;
            }

            // Step up (1 block)
            BlockPos stepUp = candidate.up();
            if (isWalkable(stepUp, world) && isPassable(pos.up().up(), world)) {
                if (offset.getX() != 0 && offset.getZ() != 0) continue; // no diagonal step-up
                neighbors.add(stepUp);
                continue;
            }

            // Fall down (up to MAX_FALL blocks)
            for (int fall = 1; fall <= MAX_FALL; fall++) {
                BlockPos fallPos = candidate.down(fall);
                if (isWalkable(fallPos, world)) {
                    if (offset.getX() != 0 && offset.getZ() != 0 && fall > 1) break;
                    // Check the column is passable
                    boolean columnClear = true;
                    for (int y = 0; y < fall; y++) {
                        if (!isPassable(candidate.down(y), world)) {
                            columnClear = false;
                            break;
                        }
                    }
                    if (columnClear) neighbors.add(fallPos);
                    break;
                }
            }
        }

        return neighbors;
    }

    private static boolean isWalkable(BlockPos pos, World world) {
        // The block below (feet level) must be passable, block below feet must be solid, head must be passable
        BlockPos ground = pos.down();
        return isSolidGround(ground, world)
            && isPassable(pos, world)
            && isPassable(pos.up(), world)
            && !isDangerous(ground, world);
    }

    private static boolean isSolidGround(BlockPos pos, World world) {
        BlockState state = world.getBlockState(pos);
        return state.isSolidBlock(world, pos) || state.getBlock() instanceof SlabBlock || state.getBlock() instanceof StairsBlock;
    }

    private static boolean isPassable(BlockPos pos, World world) {
        BlockState state = world.getBlockState(pos);
        return !state.isSolidBlock(world, pos)
            && !(state.getBlock() instanceof FenceBlock)
            && !(state.getBlock() instanceof FenceGateBlock)
            && !(state.getBlock() instanceof WallBlock);
    }

    private static boolean isDangerous(BlockPos pos, World world) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.SOUL_FIRE
            || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE
            || block == Blocks.MAGMA_BLOCK || block == Blocks.CACTUS
            || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.WITHER_ROSE) {
            return true;
        }
        FluidState fluid = world.getFluidState(pos);
        return fluid.isIn(FluidTags.LAVA);
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = Math.abs(a.getX() - b.getX());
        double dy = Math.abs(a.getY() - b.getY());
        double dz = Math.abs(a.getZ() - b.getZ());
        // Octile distance on XZ, plus vertical
        double horizontal = Math.max(dx, dz) + 0.414 * Math.min(dx, dz);
        return horizontal + dy * 0.5;
    }

    private static Path reconstructPath(PathNode endNode) {
        List<BlockPos> positions = new ArrayList<>();
        PathNode current = endNode;
        while (current != null) {
            positions.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(positions);
        return new Path(positions);
    }
}
