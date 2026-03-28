package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.render.N0nameRenderLayers;
import com.mwa.n0name.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BlockNuker: breaks nearby blocks using whitelist/range without requiring camera movement.
 */
public class BlockNukerModule {

    private static final int SCAN_INTERVAL = 2;

    private boolean wasActive = false;
    private int scanCooldown = 0;
    private BlockPos currentTarget = null;

    public void tick() {
        ModConfig cfg = ModConfig.getInstance();
        boolean active = cfg.isBlockNukerEnabled();

        if (wasActive && !active) {
            reset();
            DebugLogger.log("Nuker", "Disabled");
        }
        wasActive = active;
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) return;

        List<BlockPos> candidates = findTargets(client, player, cfg);
        if (candidates.isEmpty()) {
            currentTarget = null;
            return;
        }

        int perTick = cfg.getBlockNukerBlocksPerTick();
        Direction face = Direction.UP;

        for (int i = 0; i < candidates.size() && i < perTick; i++) {
            BlockPos pos = candidates.get(i);
            currentTarget = pos;

            // Start and continue block breaking packet flow (silent for blocks).
            client.interactionManager.attackBlock(pos, face);
            client.interactionManager.updateBlockBreakingProgress(pos, face);

            if ((client.player.age & 1) == 0) {
                player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }
        }
    }

    private List<BlockPos> findTargets(MinecraftClient client, ClientPlayerEntity player, ModConfig cfg) {
        if (--scanCooldown > 0 && currentTarget != null && isValidTarget(client, player, currentTarget, cfg)) {
            return List.of(currentTarget);
        }
        scanCooldown = SCAN_INTERVAL;

        int range = (int) Math.round(cfg.getBlockNukerRange());
        BlockPos base = player.getBlockPos();
        double maxDistSq = (cfg.getBlockNukerRange() + 0.5) * (cfg.getBlockNukerRange() + 0.5);

        List<BlockPos> found = new ArrayList<>();

        for (int x = base.getX() - range; x <= base.getX() + range; x++) {
            for (int z = base.getZ() - range; z <= base.getZ() + range; z++) {
                for (int y = base.getY() - range; y <= base.getY() + range; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isValidTarget(client, player, pos, cfg)) continue;
                    if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistSq) continue;
                    found.add(pos);
                }
            }
        }

        found.sort(Comparator.comparingDouble(p ->
            player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)
        ));

        return found;
    }

    private boolean isValidTarget(MinecraftClient client, ClientPlayerEntity player, BlockPos pos, ModConfig cfg) {
        BlockState state = client.world.getBlockState(pos);
        if (state.isAir()) return false;

        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        if (!cfg.isBlockNukerWhitelisted(blockId)) return false;

        float hardness = state.getHardness(client.world, pos);
        if (hardness < 0) return false; // unbreakable
        if (hardness > cfg.getBlockNukerMaxHardness()) return false;

        if (cfg.isBlockNukerSkipBlockEntities() && client.world.getBlockEntity(pos) != null) return false;

        // Do not mine directly under player feet to reduce accidental falls.
        BlockPos playerPos = player.getBlockPos();
        if (pos.getX() == playerPos.getX() && pos.getZ() == playerPos.getZ() && pos.getY() <= playerPos.getY()) {
            return false;
        }

        if (cfg.isBlockNukerRequireLineOfSight() && !canSee(player, pos)) return false;

        return true;
    }

    private boolean canSee(ClientPlayerEntity player, BlockPos pos) {
        Vec3d from = player.getEyePos();
        Vec3d to = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;

        BlockHitResult hit = client.world.raycast(new RaycastContext(
            from,
            to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            player
        ));

        return hit.getType() == HitResult.Type.MISS || pos.equals(hit.getBlockPos());
    }

    private void reset() {
        currentTarget = null;
    }

    public void render(WorldRenderContext context) {
        if (!ModConfig.getInstance().isBlockNukerEnabled()) return;
        if (currentTarget == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        MatrixStack matrices = context.matrices();
        Vec3d cam = RenderUtils.getCameraPos();
        var consumers = context.consumers();
        if (consumers == null) return;

        Box box = new Box(
            currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(),
            currentTarget.getX() + 1.0, currentTarget.getY() + 1.0, currentTarget.getZ() + 1.0
        );

        var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
        RenderUtils.renderEspOutlines(matrices, lineConsumer, List.of(box), 0xFFFF8844, 0.9f, cam);
        RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
    }
}
