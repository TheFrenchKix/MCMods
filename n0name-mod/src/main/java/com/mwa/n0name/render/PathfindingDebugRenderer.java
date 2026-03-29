package com.mwa.n0name.render;

import com.mwa.n0name.ModConfig;
import com.mwa.n0name.pathfinding.WalkabilityChecker;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Debug renderer for pathfinding visualization.
 * Green boxes = walkable positions
 * Red boxes = dangerous/non-walkable blocks
 * Yellow boxes = partial blocks (slabs, carpets)
 * Blue boxes = solid ground
 */
public class PathfindingDebugRenderer {

    private static final int RENDER_RADIUS = 12;  // Blocks to render around player
    private static final float BOX_ALPHA = 0.25f;
    
    // Colors (ARGB)
    private static final float[] GREEN  = {0.1f, 0.7f, 0.1f, BOX_ALPHA};   // Walkable
    private static final float[] RED    = {0.7f, 0.1f, 0.1f, BOX_ALPHA};   // Non-walkable
    private static final float[] YELLOW = {0.7f, 0.7f, 0.1f, BOX_ALPHA};   // Partial blocks
    private static final float[] BLUE   = {0.1f, 0.1f, 0.7f, BOX_ALPHA};   // Ground blocks

    public static void render(WorldRenderContext context) {
        if (!ModConfig.getInstance().isPathfindingDebugEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        World world = client.world;
        
        if (player == null || world == null) return;

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        BlockPos playerPos = player.getBlockPos();
        Vec3d cam = RenderUtils.getCameraPos();
        
        matrices.push();
        
        // Render scan results
        renderScanArea(world, playerPos, matrices, consumers, cam);
        
        matrices.pop();
    }

    private static void renderScanArea(World world, BlockPos center, 
                                        MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam) {
        
        int startX = center.getX() - RENDER_RADIUS;
        int startY = center.getY() - RENDER_RADIUS / 2;
        int startZ = center.getZ() - RENDER_RADIUS;
        
        int endX = center.getX() + RENDER_RADIUS;
        int endY = center.getY() + RENDER_RADIUS / 2;
        int endZ = center.getZ() + RENDER_RADIUS;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    
                    if (state.isAir()) {
                        // Air block - check if it's walkable as a destination
                        boolean isWalkable = WalkabilityChecker.isWalkable(world, pos);
                        if (isWalkable) {
                            // Green = walkable position
                            renderBlockOutline(pos, GREEN, matrices, consumers, cam);
                        }
                    } else {
                        // Solid block - check its category
                        boolean isDangerous = WalkabilityChecker.isDangerous(world, pos);
                        
                        if (isDangerous) {
                            // Red = dangerous block
                            renderBlockOutline(pos, RED, matrices, consumers, cam);
                        } else if (isPartialBlock(state)) {
                            // Yellow = partial block (player can stand on top)
                            renderBlockOutline(pos, YELLOW, matrices, consumers, cam);
                        } else if (isSolidGround(state)) {
                            // Blue = regular solid ground
                            renderBlockOutline(pos, BLUE, matrices, consumers, cam);
                        }
                    }
                }
            }
        }
    }

    private static void renderBlockOutline(BlockPos pos, float[] color,
                                           MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam) {
        double x = pos.getX() - cam.x;
        double y = pos.getY() - cam.y;
        double z = pos.getZ() - cam.z;

        var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
        RenderUtils.drawWireframeBox(matrices, lineConsumer,
            x, y, z, 1.0, 1.0, 1.0, color[0], color[1], color[2], color[3], Vec3d.ZERO);
    }

    /**
     * Check if a block is considered "partial" (slabs, carpets, etc.)
     */
    private static boolean isPartialBlock(BlockState state) {
        if (state.isAir()) return false;
        
        // Check collision height
        var shape = state.getCollisionShape(null, null);
        if (shape.isEmpty()) return false;
        
        float max = (float) shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
        return max > 0 && max <= 0.6f;
    }

    /**
     * Check if a block is solid ground
     */
    private static boolean isSolidGround(BlockState state) {
        if (state.isAir()) return false;
        
        // Check if it has a solid top surface
        var shape = state.getCollisionShape(null, null);
        if (shape.isEmpty()) return false;
        
        float max = (float) shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
        return max > 0.6f;
    }
}

