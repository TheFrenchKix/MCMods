package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.movement.AimController;
import com.mwa.n0name.pathfinding.WalkabilityChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;

/**
 * AutoFarm module: entity detection, smooth aim, and attack.
 * Supports both cooldown-based and CPS-based attack modes.
 * Port of aim.py logic to Java tick-based system.
 */
public class AutoFarmModule {

    private final AimController aimController = new AimController();
    private final Map<EntityType<?>, Float> entityHeightCache = new HashMap<>();

    private Entity currentTarget = null;
    private boolean wasActive = false;
    private int attackCooldownTicks = 0;
    private long lastAttackTimeMs = 0;

    // Scan interval
    private int scanCooldown = 0;
    private static final int SCAN_INTERVAL = 10; // every 0.5s

    public AimController getAimController() { return aimController; }

    public void tick() {
        boolean active = ModConfig.getInstance().isAutoFarmEnabled();

        if (wasActive && !active) {
            aimController.clearTarget();
            currentTarget = null;
            releaseKeys();
            DebugLogger.log("AutoFarm", "Disabled");
        }
        wasActive = active;
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        ModConfig config = ModConfig.getInstance();

        // Periodic entity scan
        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            findTarget(client, player, config);
        }

        // Validate current target
        if (currentTarget != null) {
            if (currentTarget.isRemoved() || !currentTarget.isAlive()) {
                currentTarget = null;
                aimController.clearTarget();
                releaseKeys();
                scanCooldown = 0; // rescan immediately
                return;
            }

            double dist = player.squaredDistanceTo(currentTarget);
            double range = config.getAutoFarmRange();
            if (dist > range * range) {
                currentTarget = null;
                aimController.clearTarget();
                releaseKeys();
                return;
            }
        }

        if (currentTarget == null) {
            releaseKeys();
            return;
        }

        // Aim at entity center
        double entityHeight = getEntityHeight(currentTarget);
        aimController.setEntityTarget(
            currentTarget.getX(),
            currentTarget.getY(),
            currentTarget.getZ(),
            entityHeight
        );
        aimController.setFastTracking(true);
        aimController.tick();

        // Movement toward entity if too far
        double dist = Math.sqrt(player.squaredDistanceTo(currentTarget));
        double attackRange = config.getAttackRange();

        if (dist > attackRange) {
            // Walk toward entity
            client.options.forwardKey.setPressed(true);

            // Safety: check for void/cliff
            if (!WalkabilityChecker.hasGroundBelow(client.world,
                    currentTarget.getBlockPos(), 4)) {
                client.options.forwardKey.setPressed(false);
                DebugLogger.log("AutoFarm", "Cliff detected near target, stopping movement");
            }
        } else {
            client.options.forwardKey.setPressed(false);

            // Attack logic
            if (aimController.isOnTarget(10.0f)) {
                tryAttack(client, player, config);
            }
        }
    }

    private void findTarget(MinecraftClient client, ClientPlayerEntity player, ModConfig config) {
        double range = config.getAutoFarmRange();
        Box searchBox = player.getBoundingBox().expand(range);
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : client.world.getEntities()) {
            if (entity == player) continue;
            if (entity instanceof PlayerEntity) continue;
            if (entity instanceof ItemEntity) continue;
            if (entity instanceof ExperienceOrbEntity) continue;
            if (!entity.isAlive()) continue;
            if (!entity.getBoundingBox().intersects(searchBox)) continue;

            double dist = player.squaredDistanceTo(entity);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }

        if (closest != currentTarget) {
            currentTarget = closest;
            if (closest != null) {
                DebugLogger.log("AutoFarm", "New target: " + closest.getDisplayName().getString());
            }
        }
    }

    private void tryAttack(MinecraftClient client, ClientPlayerEntity player, ModConfig config) {
        if (config.isCpsMode()) {
            // CPS-based attack
            long now = System.currentTimeMillis();
            long interval = 1000L / config.getTargetCps();
            if (now - lastAttackTimeMs >= interval) {
                lastAttackTimeMs = now;
                doAttack(client, player);
            }
        } else {
            // Cooldown-based attack (vanilla 1.9+ combat)
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

    private void releaseKeys() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.options == null) return;
        c.options.forwardKey.setPressed(false);
    }
}
