package com.example.macromod.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Utility methods for block-related operations.
 */
@Environment(EnvType.CLIENT)
public final class BlockUtils {

    private BlockUtils() {
    }

    /**
     * Returns the registry ID string for the block at the given position.
     * Example: "minecraft:coal_ore"
     */
    public static String getBlockId(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    /**
     * Checks if the block at the given position matches the expected block ID.
     */
    public static boolean matchesBlockId(ClientWorld world, BlockPos pos, String expectedBlockId) {
        String actualId = getBlockId(world, pos);
        return actualId.equals(expectedBlockId);
    }

    /**
     * Returns true if the block at the position is air.
     */
    public static boolean isAir(ClientWorld world, BlockPos pos) {
        return world.getBlockState(pos).isAir();
    }

    /**
     * Returns true if the block can be walked on (solid top surface).
     */
    public static boolean isSolid(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isSolidBlock(world, pos);
    }

    /**
     * Returns true if the block is passable (air, flowers, grass, etc.).
     * Tree leaves are treated as non-traversable for pathfinding purposes.
     */
    public static boolean isPassable(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        
        // Treat tree leaves as solid blocks (non-passable)
        if (isTreeLeaves(state)) {
            return false;
        }
        
        return !state.isSolidBlock(world, pos) && state.getFluidState().isEmpty();
    }

    /**
     * Returns true if the block is a tree leaves block.
     */
    private static boolean isTreeLeaves(BlockState state) {
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        // Match oak, birch, spruce, jungle, acacia, dark oak, mangrove, cherry, pale oak leaves, etc.
        return blockId.endsWith("leaves");
    }

    /**
     * Returns true if the block is dangerous (lava, fire, cactus, etc.).
     */
    public static boolean isDangerous(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        return blockId.equals("minecraft:lava")
                || blockId.equals("minecraft:fire")
                || blockId.equals("minecraft:soul_fire")
                || blockId.equals("minecraft:cactus")
                || blockId.equals("minecraft:magma_block")
                || blockId.equals("minecraft:sweet_berry_bush");
    }

    /**
     * Returns true if the player can stand at the given position.
     * Requires solid block below and two passable blocks at pos and pos+1.
     */
    public static boolean canStandAt(ClientWorld world, BlockPos pos) {
        return isSolid(world, pos.down())
                && isPassable(world, pos)
                && isPassable(world, pos.up());
    }

    /**
     * Returns true if the chunk containing the position is loaded.
     */
    public static boolean isChunkLoaded(ClientWorld world, BlockPos pos) {
        return world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * Calculates the Direction of the face of a block closest to the given eye position.
     */
    public static Direction getClosestFace(Vec3d eyePos, BlockPos blockPos) {
        Vec3d blockCenter = Vec3d.ofCenter(blockPos);
        Vec3d diff = eyePos.subtract(blockCenter);

        Direction closest = Direction.NORTH;
        double maxDot = Double.NEGATIVE_INFINITY;
        for (Direction dir : Direction.values()) {
            Vec3d dirVec = Vec3d.of(dir.getVector());
            double dot = diff.dotProduct(dirVec);
            if (dot > maxDot) {
                maxDot = dot;
                closest = dir;
            }
        }
        return closest;
    }

    /**
     * Returns the squared distance from a position to a block center.
     */
    public static double distanceSquaredTo(Vec3d from, BlockPos blockPos) {
        Vec3d blockCenter = Vec3d.ofCenter(blockPos);
        return from.squaredDistanceTo(blockCenter);
    }

    /**
     * Checks if a block is within reach distance from the player's eye position.
     */
    public static boolean isWithinReach(Vec3d eyePos, BlockPos blockPos, double reachDistance) {
        return distanceSquaredTo(eyePos, blockPos) <= reachDistance * reachDistance;
    }

    /**
     * Returns true if there is a direct line of sight (no other block in the way)
     * from {@code eyePos} to the center of {@code targetPos}.
     * Simplified: just check if the raycast would hit the target block directly.
     */
    public static boolean hasLineOfSight(ClientWorld world, Vec3d eyePos, BlockPos targetPos) {
        if (world == null || eyePos == null || targetPos == null) {
            return true; // Can't check, assume visible
        }
        try {
            Vec3d targetCenter = Vec3d.ofCenter(targetPos);
            RaycastContext ctx = new RaycastContext(
                    eyePos, targetCenter,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    (net.minecraft.entity.Entity) null
            );
            BlockHitResult result = world.raycast(ctx);
            // Line of sight exists if we hit the target block or nothing at all
            return result.getBlockPos().equals(targetPos) || result.getType() == HitResult.Type.MISS;
        } catch (Exception e) {
            // Fallback on any error during raycast
            return true;
        }
    }

    /**
     * Checks if the target block is blocked by leaves or other solid blocks.
     * Returns true if leaves or solid blocks are between the player's eye and the target.
     * Treats leaves as solid obstacles.
     */
    public static boolean isBlockedByLeavesOrSolid(ClientWorld world, Vec3d eyePos, BlockPos targetPos) {
        if (world == null || eyePos == null || targetPos == null) {
            return false;
        }

        Vec3d targetCenter = Vec3d.ofCenter(targetPos);
        Vec3d direction = targetCenter.subtract(eyePos).normalize();
        double distance = eyePos.distanceTo(targetCenter);
        
        // Step through the path in small increments to check for blocking blocks
        for (double d = 0.1; d < distance; d += 0.5) {
            Vec3d checkPoint = eyePos.add(direction.multiply(d));
            BlockPos checkPos = BlockPos.ofFloored(checkPoint);
            
            // Skip the target block itself
            if (checkPos.equals(targetPos)) {
                continue;
            }
            
            BlockState state = world.getBlockState(checkPos);
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            
            // If we hit leaves, it's blocked
            if (blockId.endsWith("leaves")) {
                return true;
            }
            
            // If we hit a solid block, it's blocked
            if (state.isSolidBlock(world, checkPos)) {
                return true;
            }
        }
        
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // Walkable line-of-sight (ground-level traversability check)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if a player can walk in a straight line from {@code from} to {@code to}
     * without hitting any obstacle. This is NOT an eye-ray check — it verifies that
     * every block along the XZ line is walkable at ground level (passable at feet+head,
     * solid support below). Height changes of more than 1 block break LOS.
     *
     * <p>Uses DDA (2D grid traversal) to step through every block column the line crosses,
     * checking the 1×2 player hitbox at each column.</p>
     *
     * @param world the client world
     * @param from  start block position (feet level)
     * @param to    end block position (feet level)
     * @return true if the player can walk in a straight line between the two positions
     */
    public static boolean hasWalkableLOS(ClientWorld world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) return false;
        if (from.equals(to)) return true;

        // Start/end in XZ continuous space (block centers)
        double x0 = from.getX() + 0.5;
        double z0 = from.getZ() + 0.5;
        double x1 = to.getX() + 0.5;
        double z1 = to.getZ() + 0.5;

        double dx = x1 - x0;
        double dz = z1 - z0;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) return true;

        // DDA setup
        double dirX = dx / dist;
        double dirZ = dz / dist;

        int stepX = dirX >= 0 ? 1 : -1;
        int stepZ = dirZ >= 0 ? 1 : -1;

        // tMaxX/Z: distance along the ray to the next vertical/horizontal grid line
        int gridX = from.getX();
        int gridZ = from.getZ();
        int endGridX = to.getX();
        int endGridZ = to.getZ();

        double tMaxX = (dirX != 0)
                ? ((dirX > 0 ? (gridX + 1) : gridX) - x0) / dirX
                : Double.MAX_VALUE;
        double tMaxZ = (dirZ != 0)
                ? ((dirZ > 0 ? (gridZ + 1) : gridZ) - z0) / dirZ
                : Double.MAX_VALUE;

        double tDeltaX = (dirX != 0) ? Math.abs(1.0 / dirX) : Double.MAX_VALUE;
        double tDeltaZ = (dirZ != 0) ? Math.abs(1.0 / dirZ) : Double.MAX_VALUE;

        int currentY = from.getY();
        int maxSteps = Math.abs(endGridX - gridX) + Math.abs(endGridZ - gridZ) + 2;

        for (int step = 0; step < maxSteps; step++) {
            // Skip the starting block (player is already there)
            if (step > 0) {
                BlockPos feetPos = new BlockPos(gridX, currentY, gridZ);

                // Check if chunk is loaded
                if (!isChunkLoaded(world, feetPos)) return false;

                // Try to find a valid Y level (allow ±1 for step-up / drop-down)
                int validY = findWalkableY(world, gridX, currentY, gridZ);
                if (validY == Integer.MIN_VALUE) return false;

                // Height change > 1 block breaks walkable LOS
                if (Math.abs(validY - currentY) > 1) return false;

                currentY = validY;
            }

            // Reached destination grid cell
            if (gridX == endGridX && gridZ == endGridZ) return true;

            // Advance DDA
            if (tMaxX < tMaxZ) {
                gridX += stepX;
                tMaxX += tDeltaX;
            } else {
                gridZ += stepZ;
                tMaxZ += tDeltaZ;
            }
        }

        return true;
    }

    /**
     * Same as {@link #hasWalkableLOS} but also checks one block to each side
     * of the line to account for the player's hitbox width (~0.6 blocks).
     * The lateral offset is perpendicular to the movement direction.
     */
    public static boolean hasWalkableLOSWide(ClientWorld world, BlockPos from, BlockPos to) {
        if (!hasWalkableLOS(world, from, to)) return false;

        // For very short distances, the narrow check is sufficient
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) + Math.abs(dz) <= 1) return true;

        // Check one block offset perpendicular to the line on each side
        // Perpendicular direction: (-dz, dx) normalized to ±1
        int perpX, perpZ;
        if (Math.abs(dx) >= Math.abs(dz)) {
            perpX = 0;
            perpZ = 1;
        } else {
            perpX = 1;
            perpZ = 0;
        }

        // Only check the perpendicular columns at the midpoint and endpoints
        // to avoid excessive checks on long paths
        BlockPos mid = new BlockPos(
                (from.getX() + to.getX()) / 2,
                (from.getY() + to.getY()) / 2,
                (from.getZ() + to.getZ()) / 2);

        for (BlockPos check : new BlockPos[]{from, mid, to}) {
            BlockPos side1 = check.add(perpX, 0, perpZ);
            BlockPos side2 = check.add(-perpX, 0, -perpZ);
            // If both sides are blocked, the player can't fit through
            if (!isPassable(world, side1) && !isPassable(world, side2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Finds a walkable Y level at (x, z) within ±1 of {@code baseY}.
     * Returns the valid Y, or {@link Integer#MIN_VALUE} if none found.
     */
    private static int findWalkableY(ClientWorld world, int x, int baseY, int z) {
        // Check same level first (most common)
        if (canStandAt(world, new BlockPos(x, baseY, z))) return baseY;
        // Step up 1
        if (canStandAt(world, new BlockPos(x, baseY + 1, z))) return baseY + 1;
        // Drop down 1
        if (canStandAt(world, new BlockPos(x, baseY - 1, z))) return baseY - 1;
        return Integer.MIN_VALUE;
    }
}
