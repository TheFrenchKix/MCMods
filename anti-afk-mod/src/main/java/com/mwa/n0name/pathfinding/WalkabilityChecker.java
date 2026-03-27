package com.mwa.n0name.pathfinding;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public class WalkabilityChecker {

    /**
     * Can the player stand at this position?
     * Ground block (pos.down) must be solid, feet (pos) and head (pos.up) must be passable.
     */
    public static boolean isWalkable(BlockView world, BlockPos pos) {
        return isSolid(world, pos.down()) && isPassable(world, pos) && isPassable(world, pos.up());
    }

    /**
     * Can the player walk/jump from 'from' to 'to'?
     * Handles flat movement, 1-block step-up, and safe drops (max 3).
     */
    public static boolean canTraverse(BlockView world, BlockPos from, BlockPos to) {
        int dy = to.getY() - from.getY();

        if (dy == 0) {
            // Flat movement: target must be walkable
            return isWalkable(world, to);
        } else if (dy == 1) {
            // Step up (jump): need clearance above 'from' (2 blocks above head)
            // and target must be walkable
            return isPassable(world, from.up(2)) && isWalkable(world, to);
        } else if (dy >= -3 && dy < 0) {
            // Drop: target must be walkable, and path down must be clear
            for (int y = 1; y <= -dy; y++) {
                if (!isPassable(world, from.down(y))) return false;
            }
            return isWalkable(world, to);
        }
        return false;
    }

    /**
     * Is there solid ground within maxFall blocks below pos?
     */
    public static boolean hasGroundBelow(BlockView world, BlockPos pos, int maxFall) {
        for (int dy = 0; dy <= maxFall; dy++) {
            BlockPos check = pos.down(dy + 1);
            if (isSolid(world, check)) return true;
        }
        return false;
    }

    /**
     * Does this block have a collision shape?
     */
    public static boolean isSolid(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(world, pos, ShapeContext.absent()).isEmpty();
    }

    /**
     * Is this block passable (no collision)?
     */
    public static boolean isPassable(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir() || state.getCollisionShape(world, pos, ShapeContext.absent()).isEmpty();
    }
}
