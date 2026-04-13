package com.example.macromod.util;

import com.example.macromod.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Routes automation through the same client-side left-click pipeline used by the user,
 * instead of directly toggling keybind state or calling entity attack methods.
 */
public final class MouseInputHelper {

    private MouseInputHelper() {}

    public static boolean isCrosshairOnEntity(MinecraftClient client, Entity entity) {
        return client != null
                && entity != null
                && client.crosshairTarget instanceof EntityHitResult entityHit
                && entityHit.getEntity() == entity;
    }

    public static boolean isCrosshairOnBlock(MinecraftClient client, BlockPos blockPos) {
        return client != null
                && blockPos != null
                && client.crosshairTarget instanceof BlockHitResult blockHit
                && blockPos.equals(blockHit.getBlockPos());
    }

    /** Simulates one real left mouse click using the client's normal attack pipeline. */
    public static boolean leftClick(MinecraftClient client) {
        if (client == null || client.player == null || client.interactionManager == null) return false;
        return ((MinecraftClientAccessor) client).macromod$doAttack();
    }

    /** Simulates one real right mouse click using the client's normal use pipeline. */
    public static boolean rightClick(MinecraftClient client) {
        if (client == null || client.player == null || client.interactionManager == null) return false;
        ((MinecraftClientAccessor) client).macromod$doItemUse();
        return true;
    }

    /**
     * Simulates held left-click for block breaking using the normal breaking path.
     * Falls back to a single click for non-block crosshair targets.
     */
    public static boolean continueLeftClick(MinecraftClient client) {
        if (client == null || client.player == null || client.interactionManager == null) return false;

        HitResult target = client.crosshairTarget;
        if (target instanceof BlockHitResult blockHit) {
            boolean progressed = client.interactionManager.updateBlockBreakingProgress(
                    blockHit.getBlockPos(), blockHit.getSide());
            client.player.swingHand(Hand.MAIN_HAND);
            return progressed;
        }

        return leftClick(client);
    }
}