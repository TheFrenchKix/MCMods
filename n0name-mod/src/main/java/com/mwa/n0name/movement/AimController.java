package com.mwa.n0name.movement;

import com.mwa.n0name.DebugLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimController {

    private static final float FAST_SMOOTHING = 0.22f;
    private static final float NORMAL_SMOOTHING = 0.08f;
    private static final float FAST_ROTATION_SPEED = 10.5f;
    private static final float NORMAL_ROTATION_SPEED = 6.5f;

    private Vec3d target = null;
    private boolean active = false;
    private float smoothingFactor = NORMAL_SMOOTHING;
    private float maxRotationSpeed = NORMAL_ROTATION_SPEED;
    private boolean fastTracking = false;
    private float yawVelocity = 0.0f;
    private float pitchVelocity = 0.0f;

    public void setTarget(Vec3d target) {
        this.target = target;
        this.active = true;
    }

    public void setEntityTarget(double x, double y, double z, double entityHeight) {
        setTarget(new Vec3d(x, y + entityHeight * 0.5, z));
    }

    public void setBlockTarget(int bx, int by, int bz) {
        setTarget(new Vec3d(bx + 0.5, by + 0.5, bz + 0.5));
    }

    public void clearTarget() {
        this.target = null;
        this.active = false;
        this.yawVelocity = 0.0f;
        this.pitchVelocity = 0.0f;
    }

    public boolean isActive() { return active; }

    public void setFastTracking(boolean fast) {
        this.fastTracking = fast;
        this.smoothingFactor = fast ? FAST_SMOOTHING : NORMAL_SMOOTHING;
        this.maxRotationSpeed = fast ? FAST_ROTATION_SPEED : NORMAL_ROTATION_SPEED;
    }

    public void setSmoothingFactor(float factor) {
        this.smoothingFactor = MathHelper.clamp(factor, 0.01f, 1.0f);
    }

    public void setMaxRotationSpeed(float speed) {
        this.maxRotationSpeed = Math.max(1.0f, speed);
    }

    public void tick() {
        if (!active || target == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        double dx = target.x - player.getX();
        double dy = target.y - player.getEyeY();
        double dz = target.z - player.getZ();

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ < 0.001) return;

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(Math.atan2(-dy, distXZ));
        targetPitch = MathHelper.clamp(targetPitch, -90.0f, 90.0f);

        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();
        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = targetPitch - currentPitch;

        float yawStep = smoothDelta(yawDelta, true);
        float pitchStep = smoothDelta(pitchDelta, false);

        player.setYaw(currentYaw + yawStep);
        player.setPitch(MathHelper.clamp(currentPitch + pitchStep, -90.0f, 90.0f));

        DebugLogger.log("Aim", String.format("yaw=%.1f pitch=%.1f delta=%.1f",
            player.getYaw(), player.getPitch(), Math.abs(yawDelta) + Math.abs(pitchDelta)));
    }

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

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(Math.atan2(-dy, distXZ));
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - player.getYaw()));
        float pitchDiff = Math.abs(targetPitch - player.getPitch());
        return yawDiff < thresholdDegrees && pitchDiff < thresholdDegrees;
    }

    private float smoothDelta(float delta, boolean yawAxis) {
        float absDelta = Math.abs(delta);
        float easing = smoothStep(0.0f, fastTracking ? 50.0f : 30.0f, absDelta);
        float desiredStep = delta * (smoothingFactor + easing * (fastTracking ? 0.18f : 0.12f));
        float maxStep = Math.min(maxRotationSpeed,
            (fastTracking ? 2.2f : 1.3f) + (float) Math.sqrt(absDelta) * (fastTracking ? 2.4f : 1.7f));

        if (yawAxis) {
            yawVelocity += (desiredStep - yawVelocity) * 0.34f;
            yawVelocity = MathHelper.clamp(yawVelocity, -maxStep, maxStep);
            if (absDelta < 0.35f) yawVelocity = delta;
            return yawVelocity;
        }

        pitchVelocity += (desiredStep - pitchVelocity) * 0.30f;
        pitchVelocity = MathHelper.clamp(pitchVelocity, -maxStep, maxStep);
        if (absDelta < 0.35f) pitchVelocity = delta;
        return pitchVelocity;
    }

    private float smoothStep(float edge0, float edge1, float value) {
        if (edge0 == edge1) return value >= edge1 ? 1.0f : 0.0f;
        float t = MathHelper.clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
