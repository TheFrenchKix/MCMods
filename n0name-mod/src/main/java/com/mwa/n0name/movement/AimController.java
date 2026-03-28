package com.mwa.n0name.movement;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimController {

    private static final float STEADY_ROTATION_SPEED = 90.0f;    // deg/s
    private static final float BALANCED_ROTATION_SPEED = 120.0f; // deg/s
    private static final float SNAPPY_ROTATION_SPEED = 170.0f;   // deg/s
    private static final float STEADY_ACCELERATION = 320.0f;     // deg/s^2
    private static final float BALANCED_ACCELERATION = 420.0f;   // deg/s^2
    private static final float SNAPPY_ACCELERATION = 720.0f;     // deg/s^2
    private static final float STEADY_GAIN = 3.4f;
    private static final float BALANCED_GAIN = 4.5f;
    private static final float SNAPPY_GAIN = 6.2f;
    private static final float FAST_ROTATION_MULTIPLIER = 1.55f;
    private static final float FAST_ACCELERATION_MULTIPLIER = 1.90f;
    private static final float FAST_GAIN_MULTIPLIER = 1.28f;
    private static final long FAST_UPDATE_INTERVAL_NANOS = 5_000_000L;   // 5ms
    private static final long NORMAL_UPDATE_INTERVAL_NANOS = 8_000_000L; // 8ms
    private static final float DEADZONE_DEGREES = 0.08f;

    private Vec3d target = null;
    private boolean active = false;
    private float maxRotationSpeed = BALANCED_ROTATION_SPEED;
    private float maxAcceleration = BALANCED_ACCELERATION;
    private float responseGain = BALANCED_GAIN;
    private boolean fastTracking = false;
    private float yawVelocity = 0.0f;
    private float pitchVelocity = 0.0f;
    private int debugLogCooldown = 0;
    private long lastUpdateNanos = 0L;

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
        this.debugLogCooldown = 0;
        this.lastUpdateNanos = 0L;
    }

    public boolean isActive() { return active; }

    public void setFastTracking(boolean fast) {
        this.fastTracking = fast;
        refreshTuning();
    }

    public void setSmoothingFactor(float factor) {
        float f = MathHelper.clamp(factor, 0.01f, 1.0f);
        refreshTuning();
        this.responseGain *= f;
    }

    public void setMaxRotationSpeed(float speed) {
        this.maxRotationSpeed = Math.max(1.0f, speed);
    }

    public void tick() {
        if (!active || target == null) return;

        refreshTuning();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        long now = System.nanoTime();
        long minInterval = fastTracking ? FAST_UPDATE_INTERVAL_NANOS : NORMAL_UPDATE_INTERVAL_NANOS;
        if (lastUpdateNanos != 0L && now - lastUpdateNanos < minInterval) {
            return;
        }

        float dt = lastUpdateNanos == 0L ? 0.016f : (now - lastUpdateNanos) / 1_000_000_000.0f;
        dt = MathHelper.clamp(dt, 0.005f, 0.050f);
        lastUpdateNanos = now;

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

        float yawStep = integrateAxis(yawDelta, true, dt);
        float pitchStep = integrateAxis(pitchDelta, false, dt);

        player.setYaw(currentYaw + yawStep);
        player.setPitch(MathHelper.clamp(currentPitch + pitchStep, -90.0f, 90.0f));

        if (--debugLogCooldown <= 0) {
            debugLogCooldown = 10;
            double totalDelta = Math.abs(yawDelta) + Math.abs(pitchDelta);
            DebugLogger.log("Aim", String.format("yaw=%.1f pitch=%.1f delta=%.1f",
                player.getYaw(), player.getPitch(), totalDelta));
        }
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

    public void applyYawJitter(float yawOffset) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        float constrained = MathHelper.clamp(yawOffset, -1.0f, 1.0f);
        player.setYaw(player.getYaw() + constrained * 0.2f);
    }

    private void refreshTuning() {
        ModConfig.AimProfile profile = ModConfig.getInstance().getAimProfile();
        float baseSpeed;
        float baseAcceleration;
        float baseGain;

        switch (profile) {
            case STEADY -> {
                baseSpeed = STEADY_ROTATION_SPEED;
                baseAcceleration = STEADY_ACCELERATION;
                baseGain = STEADY_GAIN;
            }
            case SNAPPY -> {
                baseSpeed = SNAPPY_ROTATION_SPEED;
                baseAcceleration = SNAPPY_ACCELERATION;
                baseGain = SNAPPY_GAIN;
            }
            case BALANCED -> {
                baseSpeed = BALANCED_ROTATION_SPEED;
                baseAcceleration = BALANCED_ACCELERATION;
                baseGain = BALANCED_GAIN;
            }
            default -> {
                baseSpeed = BALANCED_ROTATION_SPEED;
                baseAcceleration = BALANCED_ACCELERATION;
                baseGain = BALANCED_GAIN;
            }
        }

        if (fastTracking) {
            maxRotationSpeed = baseSpeed * FAST_ROTATION_MULTIPLIER;
            maxAcceleration = baseAcceleration * FAST_ACCELERATION_MULTIPLIER;
            responseGain = baseGain * FAST_GAIN_MULTIPLIER;
        } else {
            maxRotationSpeed = baseSpeed;
            maxAcceleration = baseAcceleration;
            responseGain = baseGain;
        }
    }

    private float integrateAxis(float delta, boolean yawAxis, float dt) {
        if (Math.abs(delta) <= DEADZONE_DEGREES) {
            if (yawAxis) {
                yawVelocity = 0.0f;
            } else {
                pitchVelocity = 0.0f;
            }
            return delta;
        }

        float desiredVelocity = MathHelper.clamp(delta * responseGain, -maxRotationSpeed, maxRotationSpeed);
        float currentVelocity = yawAxis ? yawVelocity : pitchVelocity;
        float velocityDelta = desiredVelocity - currentVelocity;
        float maxVelocityDelta = maxAcceleration * dt;
        float nextVelocity;

        if (velocityDelta > maxVelocityDelta) {
            nextVelocity = currentVelocity + maxVelocityDelta;
        } else if (velocityDelta < -maxVelocityDelta) {
            nextVelocity = currentVelocity - maxVelocityDelta;
        } else {
            nextVelocity = desiredVelocity;
        }

        float step = nextVelocity * dt;
        if (Math.abs(step) > Math.abs(delta)) {
            step = delta;
            nextVelocity = 0.0f;
        }

        if (yawAxis) {
            yawVelocity = nextVelocity;
        } else {
            pitchVelocity = nextVelocity;
        }
        return step;
    }
}
