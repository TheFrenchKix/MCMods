package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Detached-camera FreeCam module.
 * Creates and controls a separate camera entity instead of switching player to fly mode.
 */
public class FreeCamModule {

    private static final int FREECAM_ENTITY_ID = -424242;
    private boolean active = false;
    private OtherClientPlayerEntity cameraEntity;
    private Entity previousCameraEntity;
    private Vec3d frozenPlayerPos = Vec3d.ZERO;
    private float frozenPlayerYaw = 0.0f;
    private float frozenPlayerPitch = 0.0f;

    private static final double BASE_SPEED = 0.70;
    private static final double SPRINT_SPEED = 1.25;

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        boolean enabled = ModConfig.getInstance().isFreeCamEnabled();

        if (player == null || client.world == null) {
            if (active) {
                hardDisable(client, player);
            }
            return;
        }

        if (enabled && !active) {
            activate(client, player);
            return;
        }

        if (!enabled && active) {
            deactivate(client, player);
            return;
        }

        if (active) {
            tickCamera(client, player);
        }
    }

    private void activate(MinecraftClient client, ClientPlayerEntity player) {
        previousCameraEntity = client.getCameraEntity();

        frozenPlayerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        frozenPlayerYaw = player.getYaw();
        frozenPlayerPitch = player.getPitch();

        GameProfile profile = new GameProfile(UUID.randomUUID(), "n0name_freecam");
        cameraEntity = new OtherClientPlayerEntity(client.world, profile);
        cameraEntity.setPosition(player.getX(), player.getY() + player.getEyeHeight(player.getPose()) - 1.62, player.getZ());
        cameraEntity.setYaw(player.getYaw());
        cameraEntity.setPitch(player.getPitch());
        cameraEntity.noClip = true;
        cameraEntity.setOnGround(false);

        client.world.addEntity(cameraEntity);

        client.setCameraEntity(cameraEntity);

        active = true;
        DebugLogger.log("FreeCam", "Enabled (detached camera)");
    }

    private void deactivate(MinecraftClient client, ClientPlayerEntity player) {
        if (previousCameraEntity != null) {
            client.setCameraEntity(previousCameraEntity);
        } else {
            client.setCameraEntity(player);
        }

        player.setPosition(frozenPlayerPos.x, frozenPlayerPos.y, frozenPlayerPos.z);
        player.setYaw(frozenPlayerYaw);
        player.setPitch(frozenPlayerPitch);
        player.setVelocity(Vec3d.ZERO);

        if (client.world != null) {
            client.world.removeEntity(FREECAM_ENTITY_ID, Entity.RemovalReason.DISCARDED);
        }

        cameraEntity = null;
        previousCameraEntity = null;

        active = false;
        DebugLogger.log("FreeCam", "Disabled");
    }

    private void hardDisable(MinecraftClient client, ClientPlayerEntity player) {
        if (player != null) {
            client.setCameraEntity(player);
        }
        if (client.world != null) {
            client.world.removeEntity(FREECAM_ENTITY_ID, Entity.RemovalReason.DISCARDED);
        }
        cameraEntity = null;
        previousCameraEntity = null;
        active = false;
        ModConfig.getInstance().setFreeCamEnabled(false);
    }

    private void tickCamera(MinecraftClient client, ClientPlayerEntity player) {
        if (cameraEntity == null || client.options == null) {
            hardDisable(client, player);
            return;
        }

        // Freeze player so only the detached camera moves.
        player.setPosition(frozenPlayerPos.x, frozenPlayerPos.y, frozenPlayerPos.z);
        player.setVelocity(Vec3d.ZERO);

        float yaw = cameraEntity.getYaw();
        float pitch = cameraEntity.getPitch();
        cameraEntity.setYaw(yaw);
        cameraEntity.setPitch(pitch);

        double speed = client.options.sprintKey.isPressed() ? SPRINT_SPEED : BASE_SPEED;

        Vec3d forward = Vec3d.fromPolar(0.0f, yaw);
        Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);
        Vec3d move = Vec3d.ZERO;

        if (client.options.forwardKey.isPressed()) move = move.add(forward);
        if (client.options.backKey.isPressed()) move = move.subtract(forward);
        if (client.options.rightKey.isPressed()) move = move.add(right);
        if (client.options.leftKey.isPressed()) move = move.subtract(right);
        if (client.options.jumpKey.isPressed()) move = move.add(0.0, 1.0, 0.0);
        if (client.options.sneakKey.isPressed()) move = move.add(0.0, -1.0, 0.0);

        if (move.lengthSquared() > 1.0e-6) {
            move = move.normalize().multiply(speed);
            Vec3d pos = new Vec3d(cameraEntity.getX(), cameraEntity.getY(), cameraEntity.getZ()).add(move);
            cameraEntity.setPosition(pos.x, pos.y, pos.z);
        }
    }
}
