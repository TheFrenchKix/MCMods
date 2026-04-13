package com.example.macromod.manager;

import com.example.macromod.MacroModClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Detects Hypixel-style "death teleports" and disables all active automation modules.
 *
 * Two triggers:
 *  1. Position delta > 10 blocks in a single tick (teleport on death).
 *  2. Health drops near 0 (≤ 2.0) then jumps back to full (≥ 14.0) within 3 seconds.
 */
public class DeathDetector {

    private static final DeathDetector INSTANCE = new DeathDetector();

    private static final double TELEPORT_THRESHOLD_SQ = 10.0 * 10.0; // 10 blocks
    private static final float  HEALTH_LOW_THRESHOLD  = 2.0f;         // 1 heart
    private static final float  HEALTH_HIGH_THRESHOLD = 14.0f;         // 7 hearts
    private static final long   COOLDOWN_MS           = 3_000L;
    private static final long   HEALTH_WINDOW_MS      = 3_000L;

    private Vec3d  prevPos        = null;
    private float  prevHealth     = -1f;
    private boolean healthWasLow  = false;
    private long   lowHealthAtMs  = -1L;
    private long   lastDeathMs    = -1L;
    /** Tracks previous-tick active state to detect rising edge (inactive→active). */
    private boolean prevWasActive  = false;

    private DeathDetector() {}

    public static DeathDetector getInstance() {
        return INSTANCE;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            prevPos = null;
            prevHealth = -1f;
            healthWasLow = false;
            return;
        }

        // Only run death detection if Macro or AutoFishing is enabled
        MacroExecutor exec = MacroModClient.getExecutor();
        AutoFishingManager afm = AutoFishingManager.getInstance();
        boolean nowActive = (exec != null && exec.isRunning()) || afm.isEnabled();
        if (!nowActive) {
            prevWasActive = false;
            return;
        }

        // Rising edge: module just became active — discard stale position from last session
        if (!prevWasActive) {
            prevPos = null;
            prevHealth = -1f;
            healthWasLow = false;
        }
        prevWasActive = true;

        Vec3d pos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        float health = client.player.getHealth();
        long now = System.currentTimeMillis();

        // Check cooldown to avoid re-triggering rapidly
        boolean inCooldown = lastDeathMs >= 0 && now - lastDeathMs < COOLDOWN_MS;

        if (!inCooldown) {
            // --- Trigger 1: teleport detection ---
            if (prevPos != null) {
                double dx = pos.x - prevPos.x;
                double dy = pos.y - prevPos.y;
                double dz = pos.z - prevPos.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > TELEPORT_THRESHOLD_SQ) {
                    onDeathDetected(client, "teleport");
                    prevPos = pos;
                    prevHealth = health;
                    return;
                }
            }

            // --- Trigger 2: health near-zero then full-restore ---
            if (health <= HEALTH_LOW_THRESHOLD) {
                if (!healthWasLow) {
                    healthWasLow = true;
                    lowHealthAtMs = now;
                }
            } else if (healthWasLow && health >= HEALTH_HIGH_THRESHOLD) {
                long elapsed = now - lowHealthAtMs;
                if (elapsed <= HEALTH_WINDOW_MS) {
                    onDeathDetected(client, "health-restore");
                }
                healthWasLow = false;
                lowHealthAtMs = -1L;
            } else if (!healthWasLow) {
                // Reset low-health state if health recovered without triggering
                if (health > HEALTH_LOW_THRESHOLD + 2f) {
                    lowHealthAtMs = -1L;
                }
            }
        }

        prevPos = pos;
        prevHealth = health;
    }

    private void onDeathDetected(MinecraftClient client, String reason) {
        lastDeathMs = System.currentTimeMillis();

        // Stop macro executor
        MacroExecutor exec = MacroModClient.getExecutor();
        if (exec != null && exec.isRunning()) {
            exec.stop();
        }

        // Disable other auto modules
        AutoFishingManager.getInstance().setDisable();
        AutoAttackManager.getInstance().disable();
        AutoFarmerManager.getInstance().disable();

        // Notify player
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("\u00a7c[MacroMod] Death detected (" + reason + ") \u2014 all modules disabled."),
                false
            );
        }
    }
}
