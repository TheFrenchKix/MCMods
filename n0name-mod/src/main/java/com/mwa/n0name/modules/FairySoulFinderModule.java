package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.render.N0nameRenderLayers;
import com.mwa.n0name.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds likely fairy soul heads in the current lobby by scanning for player skulls with custom owners.
 * Note: client-side cannot enumerate all lobbies automatically without explicit lobby switching.
 */
public class FairySoulFinderModule {

    private final List<BlockPos> foundSkulls = new ArrayList<>();
    private int scanCooldown = 0;

    public List<BlockPos> getFoundSkulls() {
        return foundSkulls;
    }

    public void tick() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.isFairySoulFinderEnabled()) {
            foundSkulls.clear();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        if (--scanCooldown > 0) return;
        scanCooldown = 40;

        foundSkulls.clear();
        int range = cfg.getFairySoulScanRange();
        BlockPos center = client.player.getBlockPos();

        for (int x = -range; x <= range; x++) {
            for (int y = -range / 2; y <= range / 2; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Block block = client.world.getBlockState(pos).getBlock();
                    if (block != Blocks.PLAYER_HEAD && block != Blocks.PLAYER_WALL_HEAD) continue;

                    BlockEntity be = client.world.getBlockEntity(pos);
                    if (!(be instanceof SkullBlockEntity skull)) continue;

                    // Use owner/profile presence as custom skin hint.
                    if (skull.getOwner() == null) continue;
                    foundSkulls.add(pos.toImmutable());
                }
            }
        }

        if (!foundSkulls.isEmpty()) {
            DebugLogger.log("FairySoulFinder", "Found skull candidates: " + foundSkulls.size());
        }
    }

    public void render(WorldRenderContext context) {
        if (!ModConfig.getInstance().isFairySoulFinderEnabled()) return;
        if (foundSkulls.isEmpty()) return;

        MatrixStack matrices = context.matrices();
        Vec3d cam = RenderUtils.getCameraPos();
        var consumers = context.consumers();
        if (consumers == null) return;

        List<Box> boxes = new ArrayList<>();
        for (BlockPos p : foundSkulls) {
            boxes.add(new Box(p));
        }

        var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
        RenderUtils.renderEspOutlines(matrices, lineConsumer, boxes, 0xFFFF66FF, 0.85f, cam);
        RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
    }
}
