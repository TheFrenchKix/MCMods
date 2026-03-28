package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * Hypixel-oriented auto fish helper.
 * Reel/cast when armor stand marker "!!!" is detected near the bobber.
 * Optional: attack newly-spawned entities near the bobber.
 */
public class AutoFishModule {

    private final Set<Integer> seenEntityIds = new HashSet<>();
    private int useCooldown = 0;

    public void tick() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.isAutoFishEnabled()) {
            seenEntityIds.clear();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) return;

        if (useCooldown > 0) useCooldown--;

        FishingBobberEntity bobber = getOwnedBobber(client, player);
        if (bobber == null) {
            // Keep rod out and cast if possible.
            if (useCooldown == 0) {
                client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                player.swingHand(Hand.MAIN_HAND);
                useCooldown = 20;
            }
            return;
        }

        if (detectExclamationArmorStand(client, bobber, cfg.getAutoFishRange()) && useCooldown == 0) {
            // Reel and recast rhythm.
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            player.swingHand(Hand.MAIN_HAND);
            useCooldown = 8;
            DebugLogger.log("AutoFish", "Detected !!! marker, reeling");
            return;
        }

        if (cfg.isAutoFishKillEntity()) {
            killNewEntitiesNearBobber(client, player, bobber, cfg);
        }
    }

    private FishingBobberEntity getOwnedBobber(MinecraftClient client, ClientPlayerEntity player) {
        for (Entity e : client.world.getEntities()) {
            if (e instanceof FishingBobberEntity b && b.getOwner() == player) {
                return b;
            }
        }
        return null;
    }

    private boolean detectExclamationArmorStand(MinecraftClient client, FishingBobberEntity bobber, double range) {
        Vec3d bp = new Vec3d(bobber.getX(), bobber.getY(), bobber.getZ());
        double r2 = range * range;
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof ArmorStandEntity)) continue;
            if (!e.hasCustomName()) continue;
            String name = e.getName().getString();
            if (!name.contains("!!!")) continue;
            if (e.squaredDistanceTo(bp) <= r2) {
                return true;
            }
        }
        return false;
    }

    private void killNewEntitiesNearBobber(MinecraftClient client,
                                           ClientPlayerEntity player,
                                           FishingBobberEntity bobber,
                                           ModConfig cfg) {
        Vec3d bp = new Vec3d(bobber.getX(), bobber.getY(), bobber.getZ());
        double r2 = cfg.getAutoFishRange() * cfg.getAutoFishRange();

        Entity candidate = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : client.world.getEntities()) {
            int id = e.getId();
            if (!seenEntityIds.add(id)) continue; // new-spawn event behavior

            if (e == player || e == bobber) continue;
            if (!e.isAlive()) continue;
            if (e instanceof ArmorStandEntity) continue;

            double d2Bobber = e.squaredDistanceTo(bp);
            if (d2Bobber > r2) continue;

            // CLOSE mode: only attack if actually close to player.
            if (cfg.getAutoFishKillMode() == ModConfig.AutoFishKillMode.CLOSE) {
                if (player.squaredDistanceTo(e) > 16.0) continue;
            }

            if (d2Bobber < bestDist) {
                bestDist = d2Bobber;
                candidate = e;
            }
        }

        if (candidate != null) {
            client.interactionManager.attackEntity(player, candidate);
            player.swingHand(Hand.MAIN_HAND);
            if (cfg.getAutoFishKillMode() == ModConfig.AutoFishKillMode.DISTANCE) {
                player.sendMessage(Text.literal("[n0name] AutoFish kill (distance): " + candidate.getName().getString()), true);
            }
        }
    }
}
