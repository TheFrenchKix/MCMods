package com.mwa.n0name.movement;

import com.mwa.n0name.DebugLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Smooth aim controller using exponential smoothing.
 * Translates aim.py's smoothstep logic to tick-based Java.
 */
public class AimController {

    private Vec3d target = null;
    private boolean active = false;

    // Smoothing parameters
    private float smoothingFactor = 0.15f;    // 0.0=no movement, 1.0=instant snap
    private float maxRotationSpeed = 15.0f;   // max degrees per tick
    private boolean fastTracking = false;      // use higher factor for combat

    public void setTarget(Vec3d target) {
        this.target = target;
        this.active = true;
    }

    public void setEntityTarget(double x, double y, double z, double entityHeight) {
        // Aim at center of hitbox
        setTarget(new Vec3d(x, y + entityHeight * 0.5, z));
    }

    public void setBlockTarget(int bx, int by, int bz) {
        setTarget(new Vec3d(bx + 0.5, by + 0.5, bz + 0.5));
    }

    public void clearTarget() {
        this.target = null;
        this.active = false;
    }

    public boolean isActive() { return active; }

    public void setFastTracking(boolean fast) {
        this.fastTracking = fast;
        this.smoothingFactor = fast ? 0.45f : 0.15f;
    }

    public void setSmoothingFactor(float factor) {
        this.smoothingFactor = MathHelper.clamp(factor, 0.01f, 1.0f);
    }

    public void setMaxRotationSpeed(float speed) {
        this.maxRotationSpeed = Math.max(1.0f, speed);
    }

    /**
     * Called every client tick. Smoothly rotates player toward target.
     */
    public void tick() {
        if (!active || target == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Compute target angles
        double dx = target.x - player.getX();
        double dy = target.y - player.getEyeY();
        double dz = target.z - player.getZ();

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ < 0.001) return;

        float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float)Math.toDegrees(Math.atan2(-dy, distXZ));
        targetPitch = MathHelper.clamp(targetPitch, -90.0f, 90.0f);

        // Current angles
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        // Yaw delta normalized to [-180, 180]
        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = targetPitch - currentPitch;

        // Exponential smoothing
        float yawStep = yawDelta * smoothingFactor;
        float pitchStep = pitchDelta * smoothingFactor;

        // Clamp to max rotation speed
        yawStep = MathHelper.clamp(yawStep, -maxRotationSpeed, maxRotationSpeed);
        pitchStep = MathHelper.clamp(pitchStep, -maxRotationSpeed, maxRotationSpeed);

        // Apply
        player.setYaw(currentYaw + yawStep);
        player.setPitch(MathHelper.clamp(currentPitch + pitchStep, -90.0f, 90.0f));

        DebugLogger.log("Aim", String.format("yaw=%.1f pitch=%.1f delta=%.1f",
            player.getYaw(), player.getPitch(), Math.abs(yawDelta) + Math.abs(pitchDelta)));
    }

    /**
     * Returns true if aim is close enough to the target (within threshold degrees).
     */
    public boolean isOnTarget(float thresholdDegrees) {
        if (!active || target == null) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        double dx = target.x - player.getX();
        double dy = target.y - player.getEyeY();
        double dz = target.z - player.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ < 0.001) return true;

        float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float)Math.toDegrees(Math.atan2(-dy, distXZ));

        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - player.getYaw()));
        float pitchDiff = Math.abs(targetPitch - player.getPitch());

        return yawDiff < thresholdDegrees && pitchDiff < thresholdDegrees;
    }
}
