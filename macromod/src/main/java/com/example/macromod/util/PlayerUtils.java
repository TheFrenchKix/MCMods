package com.example.macromod.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Utility methods for player-related operations.
 */
@Environment(EnvType.CLIENT)
public final class PlayerUtils {

    private PlayerUtils() {
    }

    /**
     * Calculates the yaw angle (in degrees) the player should face to look at a target position.
     */
    public static float calculateYaw(Vec3d eyePos, BlockPos target) {
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double dx = targetCenter.x - eyePos.x;
        double dz = targetCenter.z - eyePos.z;
        return (float) (-Math.atan2(dx, dz) * (180.0 / Math.PI));
    }

    /**
     * Calculates the pitch angle (in degrees) the player should face to look at a target position.
     */
    public static float calculatePitch(Vec3d eyePos, BlockPos target) {
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double dx = targetCenter.x - eyePos.x;
        double dy = targetCenter.y - eyePos.y;
        double dz = targetCenter.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.atan2(dy, dist) * (180.0 / Math.PI));
    }

    /**
     * Smoothly interpolates the player's yaw toward a target yaw.
     *
     * @param current   current yaw
     * @param target    target yaw
     * @param speed     interpolation speed (0.0 to 1.0, where 1.0 is instant)
     * @return the interpolated yaw
     */
    public static float smoothYaw(float current, float target, float speed) {
        float diff = wrapAngle(target - current);
        return current + diff * speed;
    }

    /**
     * Smoothly interpolates the player's pitch toward a target pitch.
     */
    public static float smoothPitch(float current, float target, float speed) {
        float diff = target - current;
        return current + diff * speed;
    }

    /**
     * Wraps an angle to the range [-180, 180].
     */
    public static float wrapAngle(float angle) {
        angle = angle % 360.0f;
        if (angle >= 180.0f) {
            angle -= 360.0f;
        }
        if (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }

    /**
     * Checks if the player has arrived at a target position within the given radius.
     */
    public static boolean isArrived(ClientPlayerEntity player, BlockPos target, float radius) {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double dx = playerPos.x - targetCenter.x;
        double dz = playerPos.z - targetCenter.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double verticalDist = Math.abs(playerPos.y - targetCenter.y);
        return horizontalDist <= radius && verticalDist <= 2.0;
    }

    /**
     * Returns true if the player's health is below the danger threshold.
     */
    public static boolean isHealthLow(ClientPlayerEntity player) {
        return player.getHealth() < 12.0f;
    }

    /**
     * Returns true if the player is on fire.
     */
    public static boolean isOnFire(ClientPlayerEntity player) {
        return player.isOnFire();
    }

    /**
     * Returns true if there are hostile mobs within the given radius.
     */
    public static boolean hasHostileNearby(ClientPlayerEntity player, double radius) {
        Box box = player.getBoundingBox().expand(radius);
        List<HostileEntity> hostiles = player.getEntityWorld().getEntitiesByClass(
                HostileEntity.class, box, e -> true
        );
        return !hostiles.isEmpty();
    }

    /**
     * Returns true if the player is in any danger condition.
     */
    public static boolean isInDanger(ClientPlayerEntity player) {
        return isHealthLow(player) || isOnFire(player) || hasHostileNearby(player, 8.0);
    }

    /**
     * Returns the horizontal distance the player has moved since the given position.
     */
    public static double horizontalDistanceTo(ClientPlayerEntity player, Vec3d from) {
        Vec3d current = new Vec3d(player.getX(), player.getY(), player.getZ());
        double dx = current.x - from.x;
        double dz = current.z - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
