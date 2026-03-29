package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.movement.AimController;
import com.mwa.n0name.movement.MovementController;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.pathfinding.PathfindingService;
import com.mwa.n0name.pathfinding.WalkabilityChecker;
import com.mwa.n0name.render.N0nameRenderLayers;
import com.mwa.n0name.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AutoMineModule {

    private static final double BREAK_RANGE = 4.5;
    private static final int SCAN_INTERVAL = 3;
    private static final int REPATH_INTERVAL = 30;
    private static final int TARGET_BOX_COLOR = 0xFF44DD88;
    private static final int PATH_LINE_COLOR = 0xFF55AA88;
    private static final int PATH_NODE_COLOR = 0xFF88CCAA;
    private static final int PATH_CURRENT_COLOR = 0xFFFFFF66;
    private static final int PATH_TARGET_COLOR = 0xFF44DD88;

    private enum State { IDLE, PATHFINDING, WALKING, MINING }

    private final AimController aimController = new AimController();
    private final MovementController movementController = new MovementController();

    private State state = State.IDLE;
    private BlockPos currentTarget = null;
    private List<PathNode> currentPath = Collections.emptyList();
    private boolean wasActive = false;
    private int scanCooldown = 0;
    private int repathCooldown = 0;

    public void frameUpdate() {
        if (aimController.isActive()) {
            aimController.tick();
        }
        movementController.frameUpdate();
    }

    public void tick() {
        ModConfig cfg = ModConfig.getInstance();
        boolean active = cfg.isAutoMineEnabled();

        if (wasActive && !active) {
            reset();
            DebugLogger.log("AutoMine", "Disabled");
        }
        wasActive = active;
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) return;

        if (!hasEnabledWhitelist(cfg)) {
            if (currentTarget != null || state != State.IDLE) reset();
            return;
        }

        double range = cfg.getAutoMineRange();
        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            findTarget(client, player, cfg, range);
        }

        if (currentTarget != null && !isValidTarget(client, currentTarget, cfg, range)) {
            DebugLogger.log("AutoMine", "Target lost or out of range");
            reset();
            scanCooldown = 0;
            return;
        }

        if (currentTarget == null) {
            if (state != State.IDLE) reset();
            return;
        }

        switch (state) {
            case IDLE -> {
                if (canMineFrom(player, currentTarget)) {
                    state = State.MINING;
                    movementController.stop();
                    currentPath = Collections.emptyList();
                } else {
                    computePathToTarget(client, player);
                }
            }
            case PATHFINDING -> state = State.IDLE;
            case WALKING -> handleWalking(client, player);
            case MINING -> handleMining(client, player);
        }
    }

    private void findTarget(MinecraftClient client, ClientPlayerEntity player, ModConfig cfg, double range) {
        int radius = Math.max(1, (int) Math.ceil(range));
        double rangeSq = range * range;
        BlockPos base = player.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = base.add(x, y, z);
                    if (!isValidTarget(client, pos, cfg, range)) continue;

                    double distSq = player.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (distSq > rangeSq) continue;

                    BlockState state = client.world.getBlockState(pos);
                    double score = distSq + Math.max(0.0f, state.getHardness(client.world, pos)) * 3.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos.toImmutable();
                    }
                }
            }
        }

        if (currentTarget != null && isValidTarget(client, currentTarget, cfg, range)) {
            double currentDist = player.squaredDistanceTo(Vec3d.ofCenter(currentTarget));
            if (best == null || currentDist <= bestScore + 2.25) {
                best = currentTarget;
            }
        }

        if (best != null && !best.equals(currentTarget)) {
            currentTarget = best;
            state = State.IDLE;
            currentPath = Collections.emptyList();
            repathCooldown = 0;
            DebugLogger.log("AutoMine", "Targeting " + Registries.BLOCK.getId(client.world.getBlockState(best).getBlock())
                + " at " + best.toShortString());
        } else if (best == null) {
            currentTarget = null;
        }
    }

    private boolean isValidTarget(MinecraftClient client, BlockPos pos, ModConfig cfg, double range) {
        if (client.player == null || client.world == null) return false;

        BlockState state = client.world.getBlockState(pos);
        if (state.isAir()) return false;

        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        ModConfig.EspEntry filter = cfg.getBlockFilters().get(blockId);
        if (filter == null || !filter.enabled) return false;

        float hardness = state.getHardness(client.world, pos);
        if (hardness < 0.0f) return false;
        if (client.player.squaredDistanceTo(Vec3d.ofCenter(pos)) > range * range) return false;

        return true;
    }

    private void computePathToTarget(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget == null || client.world == null) return;

        List<BlockPos> candidates = findMiningSpots(client, player, currentTarget);
        if (candidates.isEmpty()) {
            state = State.IDLE;
            currentPath = Collections.emptyList();
            return;
        }

        state = State.PATHFINDING;
        BlockPos playerPos = player.getBlockPos();
        List<PathNode> bestPath = Collections.emptyList();

        for (BlockPos candidate : candidates) {
            List<BlockPos> grid = PathfindingService.scanWalkableGrid(client.world, playerPos, candidate, 4, 3);
            List<BlockPos> blockPath = PathfindingService.findBlockPath(client.world, grid, playerPos, candidate);
            if (!blockPath.isEmpty() && blockPath.size() >= 2) {
                bestPath = PathfindingService.toPathNodes(blockPath);
                movementController.startBlockPath(blockPath, grid);
                break;
            }

            List<PathNode> path = PathfindingService.findPath(client.world, playerPos, candidate);
            if (!path.isEmpty() && path.size() >= 2) {
                bestPath = path;
                movementController.startPath(bestPath);
                break;
            }
        }

        if (bestPath.isEmpty()) {
            state = State.IDLE;
            currentPath = Collections.emptyList();
            return;
        }

        currentPath = bestPath;
        state = State.WALKING;
        repathCooldown = REPATH_INTERVAL;
    }

    private List<BlockPos> findMiningSpots(MinecraftClient client, ClientPlayerEntity player, BlockPos target) {
        List<BlockPos> candidates = new ArrayList<>();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                for (int y = -1; y <= 1; y++) {
                    BlockPos spot = target.add(x, y, z);
                    if (!WalkabilityChecker.isWalkable(client.world, spot)) continue;
                    if (spot.getSquaredDistance(target) > BREAK_RANGE * BREAK_RANGE) continue;
                    candidates.add(spot.toImmutable());
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> player.getBlockPos().getSquaredDistance(pos)));
        return candidates;
    }

    private void handleWalking(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget == null) {
            reset();
            return;
        }

        if (canMineFrom(player, currentTarget)) {
            movementController.stop();
            currentPath = Collections.emptyList();
            state = State.MINING;
            return;
        }

        aimController.setBlockTarget(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ());
        aimController.setFastTracking(false);
        aimController.tick();
        movementController.tick();

        MovementController.WalkState walkState = movementController.getState();
        switch (walkState) {
            case ARRIVED -> {
                state = canMineFrom(player, currentTarget) ? State.MINING : State.IDLE;
                currentPath = Collections.emptyList();
            }
            case STUCK, IDLE -> {
                movementController.stop();
                currentPath = Collections.emptyList();
                state = State.IDLE;
                repathCooldown = 0;
            }
            default -> {
                if (--repathCooldown <= 0) {
                    movementController.stop();
                    currentPath = Collections.emptyList();
                    state = State.IDLE;
                    repathCooldown = REPATH_INTERVAL;
                }
            }
        }
    }

    private void handleMining(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget == null) {
            reset();
            return;
        }

        if (!canMineFrom(player, currentTarget)) {
            state = State.IDLE;
            releaseKeys();
            return;
        }

        aimController.setBlockTarget(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ());
        aimController.setFastTracking(true);
        aimController.tick();

        if (!aimController.isOnTarget(7.0f)) return;

        client.interactionManager.attackBlock(currentTarget, Direction.UP);
        client.interactionManager.updateBlockBreakingProgress(currentTarget, Direction.UP);
        if ((player.age & 1) == 0) {
            player.swingHand(Hand.MAIN_HAND);
        }
    }

    private boolean canMineFrom(ClientPlayerEntity player, BlockPos target) {
        return player.squaredDistanceTo(Vec3d.ofCenter(target)) <= BREAK_RANGE * BREAK_RANGE && canSee(player, target);
    }

    private boolean canSee(ClientPlayerEntity player, BlockPos target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;

        BlockHitResult hit = client.world.raycast(new RaycastContext(
            player.getEyePos(),
            Vec3d.ofCenter(target),
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            player
        ));

        return hit.getType() == HitResult.Type.MISS || target.equals(hit.getBlockPos());
    }

    private boolean hasEnabledWhitelist(ModConfig cfg) {
        for (Map.Entry<String, ModConfig.EspEntry> entry : cfg.getBlockFilters().entrySet()) {
            if (entry.getValue().enabled) return true;
        }
        return false;
    }

    private void reset() {
        state = State.IDLE;
        currentTarget = null;
        currentPath = Collections.emptyList();
        movementController.stop();
        aimController.clearTarget();
        releaseKeys();
        scanCooldown = 0;
    }

    private void releaseKeys() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        client.options.forwardKey.setPressed(false);
    }

    public void render(WorldRenderContext context) {
        if (!ModConfig.getInstance().isAutoMineEnabled() || currentTarget == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        MatrixStack matrices = context.matrices();
        Vec3d cam = RenderUtils.getCameraPos();
        float tickDelta = RenderUtils.getTickDelta();
        var consumers = context.consumers();
        if (consumers == null) return;

        Box box = new Box(
            currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(),
            currentTarget.getX() + 1.0, currentTarget.getY() + 1.0, currentTarget.getZ() + 1.0
        );

        var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
        RenderUtils.renderEspOutlines(matrices, lineConsumer, List.of(box), TARGET_BOX_COLOR, 0.85f, cam);
        RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);

        if (currentPath.isEmpty() || state != State.WALKING) return;

        Vec3d playerPos = new Vec3d(
            MathHelper.lerp(tickDelta, client.player.lastRenderX, client.player.getX()),
            MathHelper.lerp(tickDelta, client.player.lastRenderY, client.player.getY()),
            MathHelper.lerp(tickDelta, client.player.lastRenderZ, client.player.getZ())
        );

        var pathConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
        RenderUtils.renderEspPath(matrices, pathConsumer,
            currentPath, movementController.getCurrentNodeIndex(), playerPos,
            PATH_LINE_COLOR, PATH_NODE_COLOR, PATH_CURRENT_COLOR, PATH_TARGET_COLOR, cam);
        RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
    }
}
