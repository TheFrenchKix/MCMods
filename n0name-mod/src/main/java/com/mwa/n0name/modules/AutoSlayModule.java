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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private static final int SCAN_INTERVAL = 5;
    private static final int REPATH_INTERVAL = 40;

    // RED target box and path colors
    private static final int TARGET_BOX_COLOR = 0xFFFF4444;
    private static final int PATH_LINE_COLOR = 0xFF6644;
    private static final int PATH_NODE_COLOR = 0xFF8833;
    private static final int PATH_CURRENT_COLOR = 0xFFFF44;
    private static final int PATH_TARGET_COLOR = 0xFF3333;

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
                DebugLogger.log("AutoSlay", "Target out of range");
                reset();
                scanCooldown = 0;
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
                    computePathToTarget(client, player);
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
        BlockPos playerPos = player.getBlockPos();
        BlockPos targetPos = new BlockPos(
            (int) Math.floor(currentTarget.getX()),
            (int) Math.floor(currentTarget.getY()),
            (int) Math.floor(currentTarget.getZ())
        );

        if (!WalkabilityChecker.hasGroundBelow(client.world, targetPos, 4)) {
            DebugLogger.log("AutoSlay", "Target position unreachable (no ground)");
            return;
        }

        state = State.PATHFINDING;
        List<PathNode> path = AStarPathfinder.findPath(client.world, playerPos, targetPos);

        if (path.isEmpty() || path.size() < 2) {
            state = State.IDLE;
            currentPath = Collections.emptyList();
            return;
        }

        currentPath = path;
        movementController.startPath(path);
        state = State.WALKING;
        repathCooldown = REPATH_INTERVAL;
    }

    private void handleWalking(MinecraftClient client, ClientPlayerEntity player, double dist, double attackRange) {
        if (dist <= attackRange) {
            movementController.stop();
            currentPath = Collections.emptyList();
            state = State.ATTACKING;
            return;
        }

        double entityHeight = getEntityHeight(currentTarget);
        aimController.setEntityTarget(
            currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), entityHeight);
        aimController.setFastTracking(false);
        aimController.tick();

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
            return;
        }

        if (config.isAutoFarmRequireLineOfSight() && !player.canSee(currentTarget)) {
            state = State.IDLE;
            releaseKeys();
            return;
        }

        double entityHeight = getEntityHeight(currentTarget);
        aimController.setEntityTarget(
            currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), entityHeight);
        aimController.setFastTracking(true);
        aimController.tick();

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
    }

    private void releaseKeys() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.options == null) return;
        c.options.forwardKey.setPressed(false);
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
