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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AutoSlay module: scans for hostile/target entities, pathfinds to them, attacks.
 * ESP target boxes are RED. Separated from crop farming.
 */
public class AutoSlayModule {

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
    private int outOfRangeCooldown = 0;
    private static final int SCAN_INTERVAL = 5;
    private static final int REPATH_INTERVAL = 40;
    private static final int OUT_OF_RANGE_COOLDOWN_TICKS = 10;
    private static final int PATH_RETRY_COOLDOWN_TICKS = 8;
    private static final int MAX_PATH_FAILURES_BEFORE_SWITCH = 4;
    private static final int MAX_STEP_SUBGOAL_DISTANCE = 12;
    private static final int MIN_STEP_SUBGOAL_DISTANCE = 4;

    // RED target box and path colors
    private static final int TARGET_BOX_COLOR = 0xFFFF4444;
    private static final int PATH_LINE_COLOR = 0xFF6644;
    private static final int PATH_NODE_COLOR = 0xFF8833;
    private static final int PATH_CURRENT_COLOR = 0xFFFF44;
    private static final int PATH_TARGET_COLOR = 0xFF3333;

    private int consecutivePathFailures = 0;
    private int pathRetryCooldown = 0;
    private int attackNotVisibleTicks = 0;
    private static final int MAX_NOT_VISIBLE_ATTACK_TICKS = 6;

    public void frameUpdate() {
        if (aimController.isActive()) {
            aimController.tick();
        }
        movementController.frameUpdate();
    }

    public AimController getAimController() { return aimController; }

    public void tick() {
        boolean active = ModConfig.getInstance().isAutoSlayEnabled();

        if (wasActive && !active) {
            reset();
            DebugLogger.log("AutoSlay", "Disabled");
        }
        wasActive = active;
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        ModConfig config = ModConfig.getInstance();
        double scanRange = config.getAutoFarmRange();
        double attackRange = config.getAttackRange();

        if (outOfRangeCooldown > 0) {
            outOfRangeCooldown--;
        }
        if (pathRetryCooldown > 0) {
            pathRetryCooldown--;
        }

        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            findTarget(client, player, config, scanRange);
        }

        if (currentTarget != null) {
            if (currentTarget.isRemoved() || !currentTarget.isAlive()) {
                DebugLogger.log("AutoSlay", "Target died/removed");
                reset();
                scanCooldown = 0;
                return;
            }
            double dist = Math.sqrt(player.squaredDistanceTo(currentTarget));
            if (dist > scanRange) {
                handleOutOfRangeTarget();
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
                    if (pathRetryCooldown <= 0) {
                        computePathToTarget(client, player);
                    }
                } else {
                    state = State.ATTACKING;
                    movementController.stop();
                    currentPath = Collections.emptyList();
                }
            }
            case PATHFINDING -> state = State.IDLE;
            case WALKING -> handleWalking(client, player, dist, attackRange);
            case ATTACKING -> handleAttacking(client, player, config, dist, attackRange);
        }
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
            if (!(entity instanceof LivingEntity)) continue;
            if (!entity.isAlive()) continue;
            if (!entity.getBoundingBox().intersects(searchBox)) continue;

            String entityId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (!config.isAutoFarmWhitelisted(entityId)) continue;

            if (config.isAutoFarmRequireLineOfSight() && !player.canSee(entity)) continue;

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
            currentTarget = selected;
            if (selected != null) {
                state = State.IDLE;
                currentPath = Collections.emptyList();
                repathCooldown = 0;
                consecutivePathFailures = 0;
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
        if (entity instanceof HostileEntity) threat += 4.0;
        if (entity instanceof MobEntity mob && mob.getTarget() == player) threat += 6.0;
        if (entity instanceof LivingEntity living) {
            threat += Math.min(5.0, Math.max(0.0, living.getHealth() / 4.0));
        }
        double dist = Math.sqrt(distSq);
        threat += Math.max(0.0, 12.0 - dist * 1.2);
        if (!player.canSee(entity)) threat -= 1.5;
        return threat;
    }

    private void computePathToTarget(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget == null || client.world == null) return;

        BlockPos playerPos = player.getBlockPos();
        playerPos = sanitizeWalkableAnchor(client, playerPos, "player");
        if (playerPos == null) {
            onPathFailed(player, "Player anchor is not walkable");
            return;
        }

        BlockPos targetPosRaw = new BlockPos(
            (int) Math.floor(currentTarget.getX()),
            (int) Math.floor(currentTarget.getY()),
            (int) Math.floor(currentTarget.getZ())
        );

        BlockPos targetPos = sanitizeWalkableAnchor(client, targetPosRaw, "target");
        if (targetPos == null) {
            onPathFailed(player, "Target anchor is not walkable");
            return;
        }

        state = State.PATHFINDING;
        List<PathNode> path = findPathWithFallback(client, playerPos, targetPos, "target");
        String pathKind = "direct";

        if (path.isEmpty() || path.size() < 2) {
            path = findStepByStepPath(client, playerPos, targetPos);
            pathKind = "step";
        }

        if (path.isEmpty() || path.size() < 2) {
            onPathFailed(player, "No path found to target");
            return;
        }

        consecutivePathFailures = 0;
        currentPath = path;
        movementController.startPath(path);
        state = State.WALKING;
        repathCooldown = REPATH_INTERVAL;
        DebugLogger.log("AutoSlay", "Path to " + currentTarget.getDisplayName().getString()
            + " (" + path.size() + " nodes, mode=" + pathKind + ")");
    }

    private void handleWalking(MinecraftClient client, ClientPlayerEntity player, double dist, double attackRange) {
        if (isTargetReachableNow(player, attackRange)) {
            movementController.stop();
            currentPath = Collections.emptyList();
            state = State.ATTACKING;
            DebugLogger.log("AutoSlay", "Target reachable, switching to attack");
            return;
        }

        double entityHeight = getEntityHeight(currentTarget);
        aimController.setEntityTarget(
            currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), entityHeight);
        aimController.setFastTracking(false);

        movementController.tick();

        MovementController.WalkState walkState = movementController.getState();
        switch (walkState) {
            case ARRIVED -> {
                if (dist <= attackRange) {
                    state = State.ATTACKING;
                } else {
                    state = State.IDLE;
                    currentPath = Collections.emptyList();
                }
            }
            case STUCK -> {
                movementController.stop();
                currentPath = Collections.emptyList();
                state = State.IDLE;
                repathCooldown = 0;
                onPathFailed(player, "Movement stuck, retrying");
            }
            case IDLE -> {
                state = State.IDLE;
                currentPath = Collections.emptyList();
            }
            default -> {
                if (--repathCooldown <= 0) {
                    repathCooldown = REPATH_INTERVAL;
                    double targetMoveDist = currentTarget.getBlockPos()
                        .getSquaredDistance(currentPath.get(currentPath.size() - 1).toBlockPos());
                    if (targetMoveDist > 9) {
                        DebugLogger.log("AutoSlay", "Target moved, refreshing path");
                        movementController.stop();
                        currentPath = Collections.emptyList();
                        state = State.IDLE;
                    }
                }
            }
        }
    }

    private void handleAttacking(MinecraftClient client, ClientPlayerEntity player, ModConfig config, double dist, double attackRange) {
        if (dist > attackRange + 1.0) {
            state = State.IDLE;
            releaseKeys();
            attackNotVisibleTicks = 0;
            return;
        }

        // Always require line of sight while attacking. If the target is close but hidden,
        // leave attack mode and let the state machine refresh positioning/pathing.
        if (!player.canSee(currentTarget)) {
            attackNotVisibleTicks++;
            releaseKeys();
            if (attackNotVisibleTicks > MAX_NOT_VISIBLE_ATTACK_TICKS) {
                attackNotVisibleTicks = 0;
                state = State.IDLE;
                DebugLogger.log("AutoSlay", "Target not visible in attack range, re-pathing");
            }
            return;
        }
        attackNotVisibleTicks = 0;

        double entityHeight = getEntityHeight(currentTarget);
        aimController.setEntityTarget(
            currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), entityHeight);
        aimController.setFastTracking(true);

        if (dist > attackRange * 0.8) {
            client.options.forwardKey.setPressed(true);
        } else {
            client.options.forwardKey.setPressed(false);
        }

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
        scanCooldown = 0;
        consecutivePathFailures = 0;
        pathRetryCooldown = 0;
        attackNotVisibleTicks = 0;
    }

    private void releaseKeys() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.options == null) return;
        c.options.forwardKey.setPressed(false);
    }

    private boolean isTargetReachableNow(ClientPlayerEntity player, double attackRange) {
        if (currentTarget == null) return false;
        double dist = Math.sqrt(player.squaredDistanceTo(currentTarget));
        if (dist <= attackRange) {
            return true;
        }
        return dist <= attackRange + 0.8 && player.canSee(currentTarget);
    }

    private void onPathFailed(ClientPlayerEntity player, String reason) {
        consecutivePathFailures++;
        state = State.IDLE;
        currentPath = Collections.emptyList();
        if (movementController.getState() != MovementController.WalkState.IDLE) {
            movementController.stop();
        }

        String targetName = currentTarget == null ? "-" : currentTarget.getDisplayName().getString();
        DebugLogger.log("AutoSlay", reason + " (target=" + targetName + ", fail=" + consecutivePathFailures + ")");

        if (consecutivePathFailures >= MAX_PATH_FAILURES_BEFORE_SWITCH) {
            DebugLogger.log("AutoSlay", "Switching target after repeated path failures");
            currentTarget = null;
            consecutivePathFailures = 0;
            scanCooldown = 0;
            pathRetryCooldown = 0;
            releaseKeys();
            return;
        }

        // Try scanning sooner after a failed attempt.
        scanCooldown = Math.min(scanCooldown, 2);
        pathRetryCooldown = PATH_RETRY_COOLDOWN_TICKS;
        if (player != null) {
            repathCooldown = 0;
        }
    }

    private void handleOutOfRangeTarget() {
        if (outOfRangeCooldown <= 0) {
            DebugLogger.log("AutoSlay", "Target out of range");
            outOfRangeCooldown = OUT_OF_RANGE_COOLDOWN_TICKS;
        } else {
            outOfRangeCooldown--;
        }

        currentTarget = null;
        state = State.IDLE;
        currentPath = Collections.emptyList();
        if (movementController.getState() != MovementController.WalkState.IDLE) {
            movementController.stop();
        }
        aimController.clearTarget();
        releaseKeys();
        scanCooldown = 0;
        pathRetryCooldown = 0;
    }

    private BlockPos sanitizeWalkableAnchor(MinecraftClient client, BlockPos anchor, String label) {
        if (client.world == null) return null;
        if (WalkabilityChecker.isWalkable(client.world, anchor)) {
            return anchor;
        }

        BlockPos nearest = findNearestWalkable(client, anchor, 6);
        if (nearest != null) {
            DebugLogger.log("AutoSlay", label + " snapped to walkable " + nearest.toShortString());
            return nearest;
        }

        return null;
    }

    private List<PathNode> findPathWithFallback(MinecraftClient client, BlockPos from, BlockPos to, String label) {
        if (client.world == null) return Collections.emptyList();

        List<PathNode> direct = AStarPathfinder.findPath(client.world, from, to, false);
        if (!direct.isEmpty()) {
            return direct;
        }

        List<BlockPos> candidates = collectWalkableCandidates(client, to, 4);
        for (BlockPos candidate : candidates) {
            List<PathNode> alt = AStarPathfinder.findPath(client.world, from, candidate, false);
            if (!alt.isEmpty()) {
                DebugLogger.log("AutoSlay", "Fallback " + label + " path via " + candidate.toShortString());
                return alt;
            }
        }

        return Collections.emptyList();
    }

    private List<PathNode> findStepByStepPath(MinecraftClient client, BlockPos from, BlockPos target) {
        if (client.world == null) return Collections.emptyList();

        Vec3d delta = new Vec3d(target.getX() - from.getX(), target.getY() - from.getY(), target.getZ() - from.getZ());
        double dist = delta.length();
        if (dist < 1.0e-4) {
            return Collections.emptyList();
        }

        Vec3d dir = delta.normalize();
        int maxStep = Math.min(MAX_STEP_SUBGOAL_DISTANCE, (int) Math.floor(dist));

        for (int step = maxStep; step >= MIN_STEP_SUBGOAL_DISTANCE; step -= 2) {
            BlockPos projected = BlockPos.ofFloored(
                from.getX() + dir.x * step,
                from.getY() + dir.y * step,
                from.getZ() + dir.z * step
            );

            BlockPos stepTarget = sanitizeWalkableAnchor(client, projected, "step");
            if (stepTarget == null) {
                continue;
            }

            List<PathNode> stepPath = findPathWithFallback(client, from, stepTarget, "step");
            if (!stepPath.isEmpty() && stepPath.size() >= 2) {
                DebugLogger.log("AutoSlay", "Step-by-step recovery using subgoal " + stepTarget.toShortString()
                    + " dist=" + String.format(Locale.ROOT, "%.1f", Math.sqrt(stepTarget.getSquaredDistance(target))));
                return stepPath;
            }
        }

        // Last attempt: try nearby walkable candidates around the target sorted by distance to target.
        List<BlockPos> candidates = collectWalkableCandidates(client, target, 8);
        for (BlockPos candidate : candidates) {
            List<PathNode> alt = AStarPathfinder.findPath(client.world, from, candidate, false);
            if (!alt.isEmpty() && alt.size() >= 2) {
                DebugLogger.log("AutoSlay", "Step-by-step fallback via nearby " + candidate.toShortString());
                return alt;
            }
        }

        return Collections.emptyList();
    }

    private BlockPos findNearestWalkable(MinecraftClient client, BlockPos around, int radius) {
        List<BlockPos> candidates = collectWalkableCandidates(client, around, radius);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<BlockPos> collectWalkableCandidates(MinecraftClient client, BlockPos around, int radius) {
        if (client.world == null) return Collections.emptyList();

        List<BlockPos> out = new ArrayList<>();
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos candidate = around.add(dx, dy, dz);
                        if (!WalkabilityChecker.isWalkable(client.world, candidate)) continue;
                        out.add(candidate.toImmutable());
                    }
                }
            }
            if (!out.isEmpty()) break;
        }

        out.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(around)));
        return out;
    }

    public void render(WorldRenderContext context) {
        if (!ModConfig.getInstance().isAutoSlayEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        MatrixStack matrices = context.matrices();
        Vec3d cam = RenderUtils.getCameraPos();
        float tickDelta = RenderUtils.getTickDelta();
        var consumers = context.consumers();
        if (consumers == null) return;

        if (currentTarget != null) {
            double ix = MathHelper.lerp(tickDelta, currentTarget.lastRenderX, currentTarget.getX());
            double iy = MathHelper.lerp(tickDelta, currentTarget.lastRenderY, currentTarget.getY());
            double iz = MathHelper.lerp(tickDelta, currentTarget.lastRenderZ, currentTarget.getZ());

            Box targetBox = currentTarget.getBoundingBox();
            Box espBox = new Box(
                ix - (targetBox.maxX - targetBox.minX) / 2, iy,
                iz - (targetBox.maxZ - targetBox.minZ) / 2,
                ix + (targetBox.maxX - targetBox.minX) / 2,
                iy + (targetBox.maxY - targetBox.minY),
                iz + (targetBox.maxZ - targetBox.minZ) / 2
            );

            var lineConsumer = consumers.getBuffer(N0nameRenderLayers.ESP_LINES);
            RenderUtils.renderEspOutlines(matrices, lineConsumer, java.util.Collections.singletonList(espBox),
                TARGET_BOX_COLOR, 0.8f, cam);
            RenderUtils.flush(consumers, N0nameRenderLayers.ESP_LINES);
        }

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
