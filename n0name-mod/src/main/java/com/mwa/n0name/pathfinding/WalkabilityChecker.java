package com.mwa.n0name.pathfinding;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.StairsBlock;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public class WalkabilityChecker {

    private static final String LOG_MODULE = "Walkability";

    /** Max collision height that counts as a partial/step-over block (slabs, carpets, snow). */
    private static final double PARTIAL_MAX_HEIGHT = 0.6;

    /**
     * Can the player stand at this position?
     * Handles full blocks (ground below), partial blocks (slabs/carpet at feet level), and stairs.
     */
    public static boolean isWalkable(BlockView world, BlockPos pos) {
        BlockState feetState = world.getBlockState(pos);

        // Case 1: Feet block is fully passable (air, plants, etc.) → standard ground check
        if (isPassableState(feetState, world, pos)) {
            boolean groundSolid = isSolid(world, pos.down());
            // Reject if block below extends above Y=1.0 (fences, walls = 1.5 tall)
            if (groundSolid && blockExtendsAbove(world, pos.down())) {
                trace("isWalkable pos=" + formatPos(pos) + " => false (block below extends up, e.g. fence/wall)");
                return false;
            }
            boolean headPassable = isPassable(world, pos.up());
            boolean safe = !isDangerous(world, pos) && !isDangerous(world, pos.down()) && !isDangerous(world, pos.up());
            boolean result = groundSolid && headPassable && safe;
            trace("isWalkable(std) pos=" + formatPos(pos)
                + " ground=" + groundSolid + " head=" + headPassable + " safe=" + safe + " => " + result);
            return result;
        }

        // Case 2: Feet block is a partial block (bottom slab, carpet, snow layer, etc.)
        // Player stands on top of the partial collision surface
        if (isPartialBlock(feetState, world, pos)) {
            boolean headClear = isPassable(world, pos.up());
            boolean aboveHeadClear = isPassable(world, pos.up(2));
            boolean safe = !isDangerous(world, pos) && !isDangerous(world, pos.up()) && !isDangerous(world, pos.up(2));
            boolean result = headClear && aboveHeadClear && safe;
            trace("isWalkable(partial) pos=" + formatPos(pos)
                + " head=" + headClear + " aboveHead=" + aboveHeadClear + " safe=" + safe + " => " + result);
            return result;
        }

        // Case 3: Feet block is a full solid block → can't stand here
        trace("isWalkable pos=" + formatPos(pos) + " => false (solid feet)");
        return false;
    }

    /**
     * Can the player walk/jump from 'from' to 'to'?
     * Handles flat movement, 1-block step-up, and safe drops (max 3).
     */
    public static boolean canTraverse(BlockView world, BlockPos from, BlockPos to) {
        int dy = to.getY() - from.getY();
        trace("canTraverse from=" + formatPos(from) + " to=" + formatPos(to) + " dy=" + dy);

        if (dy == 0) {
            boolean result = isWalkable(world, to);
            trace("canTraverse flat result=" + result + " to=" + formatPos(to));
            return result;
        } else if (dy == 1) {
            // Step up: need 3-block clearance at 'from' (feet, head, above head)
            boolean headClear = isPassable(world, from.up(2));
            boolean targetWalkable = isWalkable(world, to);
            boolean result = headClear && targetWalkable;
            trace("canTraverse step-up result=" + result
                + " headClear=" + headClear
                + " targetWalkable=" + targetWalkable);
            return result;
        } else if (dy >= -3 && dy < 0) {
            // Drop: check intermediate air blocks (NOT the destination itself)
            for (int y = 1; y < -dy; y++) {
                BlockPos checkPos = from.down(y);
                boolean passable = isPassable(world, checkPos);
                boolean safe = !isDangerous(world, checkPos);
                trace("canTraverse drop clearance pos=" + formatPos(checkPos) + " passable=" + passable + " safe=" + safe);
                if (!passable || !safe) {
                    trace("canTraverse drop rejected");
                    return false;
                }
            }
            boolean result = isWalkable(world, to);
            trace("canTraverse drop result=" + result + " to=" + formatPos(to));
            return result;
        }
        trace("canTraverse rejected because dy is outside supported range");
        return false;
    }

    /**
     * Is there solid ground within maxFall blocks below pos?
     */
    public static boolean hasGroundBelow(BlockView world, BlockPos pos, int maxFall) {
        for (int dy = 0; dy <= maxFall; dy++) {
            BlockPos check = pos.down(dy + 1);
            boolean solid = isSolid(world, check);
            trace("hasGroundBelow checking=" + formatPos(check) + " solid=" + solid);
            if (solid) {
                trace("hasGroundBelow success for pos=" + formatPos(pos) + " at depth=" + (dy + 1));
                return true;
            }
        }
        trace("hasGroundBelow failed for pos=" + formatPos(pos) + " maxFall=" + maxFall);
        return false;
    }

    /**
     * Is this block dangerous to stand on or walk through?
     * (lava, fire, cactus, magma, berry bush, campfire, wither rose, powder snow)
     */
    public static boolean isDangerous(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        return block == Blocks.LAVA
            || block == Blocks.FIRE
            || block == Blocks.SOUL_FIRE
            || block == Blocks.CACTUS
            || block == Blocks.SWEET_BERRY_BUSH
            || block == Blocks.MAGMA_BLOCK
            || block == Blocks.CAMPFIRE
            || block == Blocks.SOUL_CAMPFIRE
            || block == Blocks.WITHER_ROSE
            || block == Blocks.POWDER_SNOW;
    }

    /**
     * Should the player avoid touching this block while walking diagonally?
     */
    public static boolean avoidTouching(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        return block == Blocks.CACTUS
            || block == Blocks.SWEET_BERRY_BUSH
            || block == Blocks.FIRE
            || block == Blocks.SOUL_FIRE
            || block == Blocks.LAVA;
    }

    /**
     * Is this block water?
     */
    public static boolean isWater(BlockView world, BlockPos pos) {
        return world.getBlockState(pos).getFluidState().isIn(FluidTags.WATER);
    }

    /**
     * Is this block lava?
     */
    public static boolean isLava(BlockView world, BlockPos pos) {
        return world.getBlockState(pos).getFluidState().isIn(FluidTags.LAVA);
    }

    /**
     * Does this block have a collision shape?
     */
    public static boolean isSolid(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        boolean result = !state.isAir() && !state.getCollisionShape(world, pos, ShapeContext.absent()).isEmpty();
        trace("isSolid pos=" + formatPos(pos) + " block=" + state.getBlock() + " result=" + result);
        return result;
    }

    /**
     * Is this block passable (no collision)?
     */
    public static boolean isPassable(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        boolean result = state.isAir() || state.getCollisionShape(world, pos, ShapeContext.absent()).isEmpty();
        trace("isPassable pos=" + formatPos(pos) + " block=" + state.getBlock() + " result=" + result);
        return result;
    }

    /**
     * Is this block a partial block (collision height ≤ PARTIAL_MAX_HEIGHT)?
     * Covers bottom slabs, carpets, snow layers, daylight sensors, etc.
     */
    private static boolean isPartialBlock(BlockState state, BlockView world, BlockPos pos) {
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.absent());
        if (shape.isEmpty()) return false;
        double maxY = shape.getMax(Direction.Axis.Y);
        return maxY > 0 && maxY <= PARTIAL_MAX_HEIGHT;
    }

    /**
     * Is this block passable? Pre-fetched state version to avoid double world lookup.
     */
    private static boolean isPassableState(BlockState state, BlockView world, BlockPos pos) {
        return state.isAir() || state.getCollisionShape(world, pos, ShapeContext.absent()).isEmpty();
    }

    /**
     * Is this block a stair? Used by pathfinder for reduced step-up cost (smooth ascent).
     */
    public static boolean isStairLike(BlockView world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof StairsBlock;
    }

    /**
     * Does this block's collision shape extend above Y=1.0?
     * True for fences (1.5), walls (1.5), fence gates (closed), etc.
     * Positions directly above such blocks are not standable.
     */
    private static boolean blockExtendsAbove(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.absent());
        if (shape.isEmpty()) return false;
        return shape.getMax(Direction.Axis.Y) > 1.0;
    }

    private static void trace(String message) {
        PathfindingTrace.log(LOG_MODULE, message);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
