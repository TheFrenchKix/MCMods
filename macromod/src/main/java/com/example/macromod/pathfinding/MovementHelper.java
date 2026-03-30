package com.example.macromod.pathfinding;

import com.example.macromod.util.PlayerUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulates player movement by manipulating keybinding states.
 * Never uses teleportation — all movement is through input simulation.
 */
@Environment(EnvType.CLIENT)
public class MovementHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");

    /** Speed of camera rotation interpolation (0.0 to 1.0). */
    private static final float LOOK_SPEED = 0.3f;

    /** Whether movement inputs are currently being simulated. */
    private boolean active = false;

    /**
     * Moves the player toward a target block position by simulating movement inputs.
     * Call this every tick when the player needs to navigate.
     *
     * @param player the client player
     * @param target the target position to walk toward
     * @return true if the movement inputs were applied
     */
    public boolean moveTowards(ClientPlayerEntity player, BlockPos target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return false;

        active = true;
        Vec3d eyePos = player.getEyePos();

        // Calculate target yaw to face the destination
        float targetYaw = PlayerUtils.calculateYaw(eyePos, target);
        float currentYaw = player.getYaw();

        // Smoothly rotate toward target
        float newYaw = PlayerUtils.smoothYaw(currentYaw, targetYaw, LOOK_SPEED);
        player.setYaw(newYaw);

        // Press forward key
        GameOptions options = client.options;
        options.forwardKey.setPressed(true);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);

        // Jump if we need to go up
        int yDiff = target.getY() - player.getBlockPos().getY();
        if (yDiff > 0) {
            options.jumpKey.setPressed(true);
        } else {
            options.jumpKey.setPressed(false);
        }

        // Sneak near ledges if we need to go down carefully
        if (yDiff < -2) {
            options.sneakKey.setPressed(true);
        } else {
            options.sneakKey.setPressed(false);
        }

        // Sprint if far away (more than 10 blocks)
        double dist = PlayerUtils.horizontalDistanceTo(player, Vec3d.ofCenter(target));
        options.sprintKey.setPressed(dist > 10.0);

        return true;
    }

    /**
     * Orients the player to look at a specific block position.
     * Uses smooth interpolation for natural camera movement.
     *
     * @param player the client player
     * @param target the block to look at
     * @param speed  interpolation speed (0.0 to 1.0, where 1.0 is instant)
     */
    public void lookAt(ClientPlayerEntity player, BlockPos target, float speed) {
        Vec3d eyePos = player.getEyePos();
        float targetYaw = PlayerUtils.calculateYaw(eyePos, target);
        float targetPitch = PlayerUtils.calculatePitch(eyePos, target);

        float newYaw = PlayerUtils.smoothYaw(player.getYaw(), targetYaw, speed);
        float newPitch = PlayerUtils.smoothPitch(player.getPitch(), targetPitch, speed);

        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

    /**
     * Checks if the player is looking approximately at the target block.
     */
    public boolean isLookingAt(ClientPlayerEntity player, BlockPos target, float tolerance) {
        Vec3d eyePos = player.getEyePos();
        float targetYaw = PlayerUtils.calculateYaw(eyePos, target);
        float targetPitch = PlayerUtils.calculatePitch(eyePos, target);

        float yawDiff = Math.abs(PlayerUtils.wrapAngle(player.getYaw() - targetYaw));
        float pitchDiff = Math.abs(player.getPitch() - targetPitch);

        return yawDiff <= tolerance && pitchDiff <= tolerance;
    }

    /**
     * Triggers a jump.
     */
    public void simulateJump(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            client.options.jumpKey.setPressed(true);
        }
    }

    /**
     * Checks if the player has arrived at the target within the given radius.
     */
    public boolean isArrived(ClientPlayerEntity player, BlockPos target, float radius) {
        return PlayerUtils.isArrived(player, target, radius);
    }

    /**
     * Releases all simulated inputs. Must be called when stopping movement
     * to prevent the player from walking forever.
     */
    public void releaseAllInputs() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
            client.options.sneakKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
        }
        active = false;
    }

    /**
     * Returns true if movement inputs are currently active.
     */
    public boolean isActive() {
        return active;
    }
}
