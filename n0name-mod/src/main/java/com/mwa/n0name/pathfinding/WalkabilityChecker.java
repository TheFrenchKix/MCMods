package com.mwa.n0name.pathfinding;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public class WalkabilityChecker {

    private static final String LOG_MODULE = "Walkability";

    /**
     * Can the player stand at this position?
     * Ground block (pos.down) must be solid and not dangerous,
     * feet (pos) and head (pos.up) must be passable and safe.
     */
    public static boolean isWalkable(BlockView world, BlockPos pos) {
        boolean groundSolid = isSolid(world, pos.down());
        boolean feetPassable = isPassable(world, pos);
        boolean headPassable = isPassable(world, pos.up());
        boolean groundSafe = !isDangerous(world, pos.down());
        boolean feetSafe = !isDangerous(world, pos);
        boolean headSafe = !isDangerous(world, pos.up());
        boolean result = groundSolid && feetPassable && headPassable && groundSafe && feetSafe && headSafe;
        trace("isWalkable pos=" + formatPos(pos)
            + " groundSolid=" + groundSolid
            + " feetPassable=" + feetPassable
            + " headPassable=" + headPassable
            + " safe=" + (groundSafe && feetSafe && headSafe)
            + " result=" + result);
        return result;
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
            // Drop: target must be walkable, and path down must be clear and safe
            for (int y = 1; y <= -dy; y++) {
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

    private static void trace(String message) {
        PathfindingTrace.log(LOG_MODULE, message);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
