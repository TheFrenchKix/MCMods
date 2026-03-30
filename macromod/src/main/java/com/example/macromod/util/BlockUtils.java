package com.example.macromod.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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
     */
    public static boolean isPassable(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isSolidBlock(world, pos) && !state.isLiquid();
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
}
