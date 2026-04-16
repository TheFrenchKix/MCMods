package com.example.macromod.pathfinding.oringo;

import net.minecraft.util.math.Vec3d;

/**
 * Oringo-inspired movement math utilities.
 *
 * <p>This ports the key input normalization strategy from Oringo's
 * MovementUtils#setMotion while staying Fabric/Yarn compatible.</p>
 */
public final class OringoMovementMath {

    private OringoMovementMath() {
    }

    public record InputSolution(float forward, float strafe, float yaw) {
    }

    /**
     * Normalizes forward/strafe input and applies diagonal yaw compensation.
     */
    public static InputSolution normalizeInput(float forward, float strafe, float yaw) {
        float f = forward;
        float s = strafe;
        float y = yaw;

        if (Math.abs(f) < 0.001f && Math.abs(s) < 0.001f) {
            return new InputSolution(0f, 0f, y);
        }

        if (Math.abs(f) > 0.001f) {
            if (s > 0f) {
                y += f > 0f ? -45f : 45f;
            } else if (s < 0f) {
                y += f > 0f ? 45f : -45f;
            }
            s = 0f;
            f = f > 0f ? 1f : -1f;
        }

        if (Math.abs(s) > 0.001f) {
            s = s > 0f ? 1f : -1f;
        }

        return new InputSolution(f, s, y);
    }

    /**
     * Computes horizontal velocity using Oringo's forward/strafe decomposition.
     */
    public static Vec3d motionFromInput(float forward, float strafe, float yaw, double speed) {
        InputSolution solved = normalizeInput(forward, strafe, yaw);
        double cos = Math.cos(Math.toRadians(solved.yaw() + 90f));
        double sin = Math.sin(Math.toRadians(solved.yaw() + 90f));

        double motionX = solved.forward() * speed * cos + solved.strafe() * speed * sin;
        double motionZ = solved.forward() * speed * sin - solved.strafe() * speed * cos;
        return new Vec3d(motionX, 0.0, motionZ);
    }

    public static double horizontalSpeed(Vec3d velocity) {
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }
}
