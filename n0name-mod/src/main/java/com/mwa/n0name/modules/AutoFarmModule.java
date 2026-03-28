package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.movement.AimController;
import com.mwa.n0name.movement.MovementController;
import com.mwa.n0name.pathfinding.AStarPathfinder;
import com.mwa.n0name.pathfinding.PathNode;
import com.mwa.n0name.pathfinding.WalkabilityChecker;
import com.mwa.n0name.render.N0nameRenderLayers;
import com.mwa.n0name.render.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * AutoFarm module: scans for entities within configurable range, pathfinds to priority target,
 * attacks when within range. Supports entity whitelist. Renders target box and path.
 */
public class AutoFarmModule {

    private enum State { IDLE, PATHFINDING, WALKING, ATTACKING }

    private final AimController aimController = new AimController();
    private final MovementController movementController = new MovementController();
    private final Map<net.minecraft.entity.EntityType<?>, Float> entityHeightCache = new HashMap<>();

    private State state = State.IDLE;
    private Entity currentTarget = null;
    private List<PathNode> currentPath = Collections.emptyList();
    private boolean wasActive = false;
    private long lastAttackTimeMs = 0;

    private int scanCooldown = 0;
    private int repathCooldown = 0;
    private int cropPauseTicks = 0;
    private int cropBreakFailures = 0;
    private int jitterCooldown = 0;
    private final Random random = new Random();
    private static final int SCAN_INTERVAL = 10;
    private static final int REPATH_INTERVAL = 40;

    // Render colors
    private static final int TARGET_BOX_COLOR = 0xFF00FF00;  // Green
    private static final int PATH_LINE_COLOR = 0xFF6644;
    private static final int PATH_NODE_COLOR = 0xFF8833;
    private static final int PATH_CURRENT_COLOR = 0xFFFF44;
    private static final int PATH_TARGET_COLOR = 0xFF3333;

    public AimController getAimController() { return aimController; }

    public void tick() {
        boolean active = ModConfig.getInstance().isAutoFarmEnabled();

        if (wasActive && !active) {
            reset();
            DebugLogger.log("AutoFarm", "Disabled");
        }
        wasActive = active;
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        ModConfig config = ModConfig.getInstance();
        if (config.getAutoFarmMode() == ModConfig.AutoFarmMode.CROPS) {
            tickCropFarm(client, player, config);
            return;
        }

        double scanRange = config.getAutoFarmRange();
        double attackRange = config.getAttackRange();

        // Periodic entity scan
        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            findTarget(client, player, config, scanRange);
        }

        // Validate current target
        if (currentTarget != null) {
            if (currentTarget.isRemoved() || !currentTarget.isAlive()) {
                DebugLogger.log("AutoFarm", "Target died/removed");
                reset();
                scanCooldown = 0;
                return;
            }
            double dist = Math.sqrt(player.squaredDistanceTo(currentTarget));
            if (dist > scanRange) {
                DebugLogger.log("AutoFarm", "Target out of range");
                reset();
                return;
            }
        }

        if (currentTarget == null) {
            if (state != State.IDLE) reset();
            return;
        }

        double dist = Math.sqrt(player.squaredDistanceTo(currentTarget));

        switch (state) {
            case IDLE -> {
                if (dist > attackRange) {
                    // Need to pathfind to target
                    computePathToTarget(client, player);
                } else {
                    state = State.ATTACKING;
                    movementController.stop();
                    currentPath = Collections.emptyList();
                }
            }
            case PATHFINDING -> {
                // Should not stay in this state (computed synchronously)
                state = State.IDLE;
            }
            case WALKING -> {
                handleWalking(client, player, dist, attackRange);
            }
            case ATTACKING -> {
                handleAttacking(client, player, config, dist, attackRange);
            }
        }
    }

    private void tickCropFarm(MinecraftClient client, ClientPlayerEntity player, ModConfig config) {
        // Crop mode does not use entity combat state.
        if (state != State.IDLE) {
            reset();
        }

        if (client.world == null || client.interactionManager == null) return;

        if (cropPauseTicks > 0) {
            cropPauseTicks--;
            return;
        }

        if (config.isFailsafeRandomPauseEnabled() && random.nextInt(80) == 0) {
            int min = config.getFailsafePauseMinTicks();
            int max = Math.max(min, config.getFailsafePauseMaxTicks());
            cropPauseTicks = min + random.nextInt(max - min + 1);
            return;
        }

        if (config.isFailsafeYawJitterEnabled() && --jitterCooldown <= 0) {
            float jitter = (random.nextFloat() - 0.5f) * 1.2f;
            aimController.applyYawJitter(jitter);
            jitterCooldown = 8 + random.nextInt(8);
        }

        double cropRange = Math.min(8.0, config.getAutoFarmRange());
        List<BlockPos> targets = findRipeCropTargets(player, cropRange, config.getCropType());
        if (targets.isEmpty()) return;

        if (config.isAutoFarmAutoTool()) {
            equipBestFarmingTool(player, config.getCropType());
        }

        int maxBreaks = config.getAutoFarmCropBlocksPerTick();
        int wheat = 0;
        int carrot = 0;
        int potato = 0;
        for (BlockPos pos : targets) {
            if (wheat + carrot + potato >= maxBreaks) break;

            BlockState cropState = client.world.getBlockState(pos);
            if (!isMatchingRipeCrop(cropState, config.getCropType())) continue;

            boolean didBreak = false;

            if (config.isAutoFarmSilentCropAim()) {
                didBreak = breakCrop(client, player, pos);
            } else {
                Vec3d center = Vec3d.ofCenter(pos);
                aimController.setTarget(center);
                aimController.setFastTracking(true);
                aimController.tick();
                if (aimController.isOnTarget(8.0f)) {
                    didBreak = breakCrop(client, player, pos);
                }
            }

            if (!didBreak) {
                cropBreakFailures++;
                continue;
            }

            if (cropState.isOf(Blocks.WHEAT)) wheat++;
            else if (cropState.isOf(Blocks.CARROTS)) carrot++;
            else if (cropState.isOf(Blocks.POTATOES)) potato++;

            cropBreakFailures = 0;

            if (config.isAutoFarmReplant()) {
                tryReplant(client, player, pos, cropState);
            }
        }

        if (wheat > 0) FarmStats.addWheatBreaks(wheat);
        if (carrot > 0) FarmStats.addCarrotBreaks(carrot);
        if (potato > 0) FarmStats.addPotatoBreaks(potato);

        if (config.isDesyncWatchdogEnabled() && cropBreakFailures >= config.getDesyncFailLimit()) {
            cropBreakFailures = 0;
            config.setAutoFarmCropBlocksPerTick(Math.max(1, config.getAutoFarmCropBlocksPerTick() - 1));
            cropPauseTicks = 10;
            DebugLogger.log("AutoFarm", "Desync watchdog: lowering crop speed");
        }
    }

    private List<BlockPos> findRipeCropTargets(ClientPlayerEntity player, double range, ModConfig.CropType cropType) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return Collections.emptyList();

        BlockPos center = player.getBlockPos();
        int r = Math.max(1, (int)Math.ceil(range));
        List<BlockPos> out = new java.util.ArrayList<>();

        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > range * range) continue;
                    BlockState state = client.world.getBlockState(pos);
                    if (!isMatchingRipeCrop(state, cropType)) continue;
                    out.add(pos);
                }
            }
        }

        out.sort((a, b) -> Double.compare(
            player.squaredDistanceTo(Vec3d.ofCenter(a)),
            player.squaredDistanceTo(Vec3d.ofCenter(b))));
        return out;
    }

    private boolean isMatchingRipeCrop(BlockState state, ModConfig.CropType cropType) {
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) {
            return false;
        }

        return switch (cropType) {
            case WHEAT -> state.isOf(Blocks.WHEAT);
            case CARROT -> state.isOf(Blocks.CARROTS);
            case POTATO -> state.isOf(Blocks.POTATOES);
            case ALL -> state.isOf(Blocks.WHEAT) || state.isOf(Blocks.CARROTS) || state.isOf(Blocks.POTATOES);
        };
    }

    private boolean breakCrop(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
        if (client.world == null || client.interactionManager == null) return false;
        if (client.world.isAir(pos)) return false;
        if (!(client.world.getBlockState(pos).getBlock() instanceof CropBlock)) return false;

        boolean attacked = client.interactionManager.attackBlock(pos, Direction.UP);
        if (attacked) {
            player.swingHand(Hand.MAIN_HAND);
        }
        return attacked;
    }

    private void equipBestFarmingTool(ClientPlayerEntity player, ModConfig.CropType cropType) {
        int current = player.getInventory().getSelectedSlot();
        ItemStack selected = player.getInventory().getStack(current);
        if (isGoodFarmingTool(selected, cropType)) return;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (isGoodFarmingTool(stack, cropType)) {
                player.getInventory().setSelectedSlot(slot);
                return;
            }
        }
    }

    private boolean isGoodFarmingTool(ItemStack stack, ModConfig.CropType cropType) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof HoeItem) return true;
        String name = stack.getName().getString().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("hoe") || name.contains("dicer") || name.contains("chopper")) return true;
        return switch (cropType) {
            case WHEAT -> name.contains("wheat");
            case CARROT -> name.contains("carrot");
            case POTATO -> name.contains("potato");
            case ALL -> false;
        };
    }

    private void tryReplant(MinecraftClient client, ClientPlayerEntity player, BlockPos cropPos, BlockState oldCropState) {
        if (client.world == null || client.interactionManager == null) return;
        if (!client.world.isAir(cropPos)) return;

        BlockPos farmland = cropPos.down();
        if (!client.world.getBlockState(farmland).isOf(Blocks.FARMLAND)) return;

        int seedSlot = findSeedSlot(player, oldCropState);
        if (seedSlot < 0) return;

        int prevSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(seedSlot);

        Vec3d hitPos = new Vec3d(farmland.getX() + 0.5, farmland.getY() + 1.0, farmland.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, farmland, false);
        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
        }

        player.getInventory().setSelectedSlot(prevSlot);
    }

    private int findSeedSlot(ClientPlayerEntity player, BlockState oldCropState) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s == null || s.isEmpty()) continue;
            if (oldCropState.isOf(Blocks.WHEAT) && s.isOf(Items.WHEAT_SEEDS)) return i;
            if (oldCropState.isOf(Blocks.CARROTS) && s.isOf(Items.CARROT)) return i;
            if (oldCropState.isOf(Blocks.POTATOES) && s.isOf(Items.POTATO)) return i;
        }
        return -1;
    }

    private void findTarget(MinecraftClient client, ClientPlayerEntity player, ModConfig config, double scanRange) {
        Box searchBox = player.getBoundingBox().expand(scanRange);
        Entity selected = null;
        double selectedScore = (config.getAutoFarmPriority() == ModConfig.AutoFarmPriority.HIGHEST_THREAT)
            ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        double selectedDistSq = Double.MAX_VALUE;

        for (Entity entity : client.world.getEntities()) {
            if (entity == player) continue;
            if (entity instanceof PlayerEntity) continue;
            if (entity instanceof ItemEntity) continue;
            if (entity instanceof ExperienceOrbEntity) continue;
            if (!entity.isAlive()) continue;
            if (!entity.getBoundingBox().intersects(searchBox)) continue;

            // Check whitelist
            String entityId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (!ModConfig.getInstance().isAutoFarmWhitelisted(entityId)) continue;

            double distSq = player.squaredDistanceTo(entity);
            double score = scoreTarget(config.getAutoFarmPriority(), entity, player, distSq);

            boolean better;
            if (config.getAutoFarmPriority() == ModConfig.AutoFarmPriority.HIGHEST_THREAT) {
                better = score > selectedScore + 1e-6 ||
                    (Math.abs(score - selectedScore) < 1e-6 && distSq < selectedDistSq);
            } else {
                better = score < selectedScore - 1e-6 ||
                    (Math.abs(score - selectedScore) < 1e-6 && distSq < selectedDistSq);
            }

            if (better) {
                selected = entity;
                selectedScore = score;
                selectedDistSq = distSq;
            }
        }

        if (currentTarget != null && currentTarget.isAlive() && currentTarget != selected) {
            double currentDist = player.squaredDistanceTo(currentTarget);
            double switchMarginSq = config.getAutoFarmSwitchMargin() * config.getAutoFarmSwitchMargin();
            if (selected == null || selectedDistSq + switchMarginSq >= currentDist) {
                selected = currentTarget;
            }
        }

        if (selected != currentTarget) {
            if (currentTarget != null && selected != null) {
                DebugLogger.log("AutoFarm", "Switching target to " + selected.getDisplayName().getString());
            }
            currentTarget = selected;
            if (selected != null) {
                state = State.IDLE; // Re-evaluate state for new target
                currentPath = Collections.emptyList();
                repathCooldown = 0;
            }
        }
    }

    private double scoreTarget(ModConfig.AutoFarmPriority priority, Entity entity,
                               ClientPlayerEntity player, double distSq) {
        return switch (priority) {
            case LOWEST_HEALTH -> {
                if (entity instanceof LivingEntity living) {
                    yield living.getHealth() + distSq * 0.005;
                }
                yield 1000.0 + distSq * 0.01;
            }
            case HIGHEST_THREAT -> computeThreatScore(entity, player, distSq);
            case NEAREST -> distSq;
        };
    }

    private double computeThreatScore(Entity entity, ClientPlayerEntity player, double distSq) {
        double threat = 0.0;

        if (entity instanceof HostileEntity) {
            threat += 4.0;
        }
        if (entity instanceof MobEntity mob && mob.getTarget() == player) {
            threat += 6.0;
        }
        if (entity instanceof LivingEntity living) {
            threat += Math.min(5.0, Math.max(0.0, living.getHealth() / 4.0));
        }

        double dist = Math.sqrt(distSq);
        threat += Math.max(0.0, 12.0 - dist * 1.2);

        if (!player.canSee(entity)) {
            threat -= 1.5;
        }

        return threat;
    }

    private void computePathToTarget(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        // Target Y should be at entity's feet level for ground-based pathfinding
        BlockPos targetPos = new BlockPos(
            (int)Math.floor(currentTarget.getX()),
            (int)Math.floor(currentTarget.getY()),
            (int)Math.floor(currentTarget.getZ())
        );

        // Check if target position is reachable (has ground)
        if (!WalkabilityChecker.hasGroundBelow(client.world, targetPos, 4)) {
            DebugLogger.log("AutoFarm", "Target position unreachable (no ground)");
            return;
        }

        state = State.PATHFINDING;
        List<PathNode> path = AStarPathfinder.findPath(client.world, playerPos, targetPos);

        if (path.isEmpty() || path.size() < 2) {
            DebugLogger.log("AutoFarm", "No path found to target");
            state = State.IDLE;
            currentPath = Collections.emptyList();
            return;
        }

        currentPath = path;
        movementController.startPath(path);
        state = State.WALKING;
        repathCooldown = REPATH_INTERVAL;
        DebugLogger.log("AutoFarm", "Path to " + currentTarget.getDisplayName().getString()
            + " (" + path.size() + " nodes)");
    }

    private void handleWalking(MinecraftClient client, ClientPlayerEntity player, double dist, double attackRange) {
        // Close enough to attack? Switch to attacking
        if (dist <= attackRange) {
            movementController.stop();
            currentPath = Collections.emptyList();
            state = State.ATTACKING;
            DebugLogger.log("AutoFarm", "Close enough, switching to attack");
            return;
        }

        // Aim at target while walking (smooth aiming via AimController)
        double entityHeight = getEntityHeight(currentTarget);
        aimController.setEntityTarget(
            currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), entityHeight);
        aimController.setFastTracking(false);
        aimController.tick();

        // Continue walking the path
        movementController.tick();

        MovementController.WalkState walkState = movementController.getState();
        switch (walkState) {
            case ARRIVED -> {
                // Arrived at path end; check if close enough
                if (dist <= attackRange) {
                    state = State.ATTACKING;
                } else {
                    // Target moved; re-path
                    state = State.IDLE;
                    currentPath = Collections.emptyList();
                }
            }
            case STUCK -> {
                DebugLogger.log("AutoFarm", "Stuck on path, re-pathfinding");
                movementController.stop();
                currentPath = Collections.emptyList();
                state = State.IDLE;
                repathCooldown = 0;
            }
            case IDLE -> {
                state = State.IDLE;
                currentPath = Collections.emptyList();
            }
            default -> {
                // Periodically re-path if entity moved significantly
                if (--repathCooldown <= 0) {
                    repathCooldown = REPATH_INTERVAL;
                    double targetMoveDist = currentTarget.getBlockPos()
                        .getSquaredDistance(currentPath.get(currentPath.size() - 1).toBlockPos());
                    if (targetMoveDist > 9) { // entity moved > 3 blocks from path end
                        DebugLogger.log("AutoFarm", "Target moved, re-pathfinding");
                        movementController.stop();
                        currentPath = Collections.emptyList();
                        state = State.IDLE;
                    }
                }
            }
        }
    }

    private void handleAttacking(MinecraftClient client, ClientPlayerEntity player, ModConfig config, double dist, double attackRange) {
        // If target moved away, go back to idle to re-path
        if (dist > attackRange + 1.0) {
            state = State.IDLE;
            releaseKeys();
            return;
        }

        if (config.isAutoFarmRequireLineOfSight() && !player.canSee(currentTarget)) {
            state = State.IDLE;
            releaseKeys();
            return;
        }

        // Aim at entity center (smooth aiming)
        double entityHeight = getEntityHeight(currentTarget);
        aimController.setEntityTarget(
            currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), entityHeight);
        aimController.setFastTracking(true);
        aimController.tick();

        // Strafe slightly toward entity if drifting away
        if (dist > attackRange * 0.8) {
            client.options.forwardKey.setPressed(true);
        } else {
            client.options.forwardKey.setPressed(false);
        }

        // Attack when on target
        if (aimController.isOnTarget(10.0f)) {
            tryAttack(client, player, config);
        }
    }

    private void tryAttack(MinecraftClient client, ClientPlayerEntity player, ModConfig config) {
        if (currentTarget == null) return;

        if (config.isCpsMode()) {
            long now = System.currentTimeMillis();
            long interval = 1000L / config.getTargetCps();
            if (now - lastAttackTimeMs >= interval) {
                lastAttackTimeMs = now;
                doAttack(client, player);
            }
        } else {
            if (player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                doAttack(client, player);
            }
        }
    }

    private void doAttack(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget == null) return;
        client.interactionManager.attackEntity(player, currentTarget);
        player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        DebugLogger.log("AutoFarm", "Attacked " + currentTarget.getDisplayName().getString());
    }

    private double getEntityHeight(Entity entity) {
        Float cached = entityHeightCache.get(entity.getType());
        if (cached != null) return cached;
        float height = entity.getHeight();
        entityHeightCache.put(entity.getType(), height);
        return height;
    }

    private void reset() {
        state = State.IDLE;
        currentTarget = null;
        currentPath = Collections.emptyList();
        movementController.stop();
        aimController.clearTarget();
        releaseKeys();
    }

    private void releaseKeys() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.options == null) return;
        c.options.forwardKey.setPressed(false);
    }

    /**
     * Render the path to target with ESP-style lines and rectangles, plus target box.
     */
    public void render(WorldRenderContext context) {
        if (!ModConfig.getInstance().isAutoFarmEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        MatrixStack matrices = context.matrices();
        Vec3d cam = RenderUtils.getCameraPos();
        float tickDelta = RenderUtils.getTickDelta();
        var consumers = context.consumers();
        if (consumers == null) return;

        // Render target box around current target
        if (currentTarget != null) {
            double ix = MathHelper.lerp(tickDelta, currentTarget.lastRenderX, currentTarget.getX());
            double iy = MathHelper.lerp(tickDelta, currentTarget.lastRenderY, currentTarget.getY());
            double iz = MathHelper.lerp(tickDelta, currentTarget.lastRenderZ, currentTarget.getZ());
            
            Box targetBox = currentTarget.getBoundingBox();
            Box espBox = new Box(
                ix - (targetBox.maxX - targetBox.minX) / 2,
                iy,
                iz - (targetBox.maxZ - targetBox.minZ) / 2,
                ix + (targetBox.maxX - targetBox.minX) / 2,
                iy + (targetBox.maxY - targetBox.minY),
                iz + (targetBox.maxZ - targetBox.minZ) / 2
            );
            
            // Render target box (outlines only)
            var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
            RenderUtils.renderEspOutlines(matrices, lineConsumer, java.util.Collections.singletonList(espBox), 
                TARGET_BOX_COLOR, 0.8f, cam);
            RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
        }

        // Render path when walking
        if (currentPath.isEmpty() || state != State.WALKING) return;

        Vec3d playerPos = new Vec3d(
            MathHelper.lerp(tickDelta, client.player.lastRenderX, client.player.getX()),
            MathHelper.lerp(tickDelta, client.player.lastRenderY, client.player.getY()),
            MathHelper.lerp(tickDelta, client.player.lastRenderZ, client.player.getZ())
        );

        var pathLineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
        RenderUtils.renderEspPath(matrices, pathLineConsumer,
            currentPath, movementController.getCurrentNodeIndex(), playerPos,
            PATH_LINE_COLOR, PATH_NODE_COLOR, PATH_CURRENT_COLOR, PATH_TARGET_COLOR, cam);

        RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
    }
}
