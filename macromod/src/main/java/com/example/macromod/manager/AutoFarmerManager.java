package com.example.macromod.manager;

import com.example.macromod.util.MouseInputHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.util.shape.VoxelShape;

/**
 * Auto Farmer — zig-zag movement with held left-click for crop harvesting.
 *
 * <p>Movement: walk in a horizontal direction (LEFT or RIGHT relative to the
 * player's facing) until a collision is detected, then step FORWARD one row,
 * switch horizontal direction, and repeat.
 *
 * <p>Collision detection uses a small raycast ahead of the player.
 */
@Environment(EnvType.CLIENT)
public class AutoFarmerManager {

    // ── Singleton ───────────────────────────────────────────────────
    private static AutoFarmerManager INSTANCE;

    public static AutoFarmerManager getInstance() {
        if (INSTANCE == null) INSTANCE = new AutoFarmerManager();
        return INSTANCE;
    }

    private AutoFarmerManager() {}

    // ── Direction enum ──────────────────────────────────────────────
    public enum HorizontalDir { LEFT, RIGHT }

    // ── State ────────────────────────────────────────────────────────
    private boolean enabled = false;
    private HorizontalDir startDirection = HorizontalDir.LEFT;
    private HorizontalDir currentDirection = HorizontalDir.LEFT;

    /** Current phase of the zig-zag loop. */
    private enum Phase { HORIZONTAL, FORWARD }
    private Phase phase = Phase.HORIZONTAL;

    /** How many ticks we've been blocked in the FORWARD phase. */
    private int forwardBlockedTicks = 0;

    /** Distance threshold for collision (blocks ahead). */
    private static final double COLLISION_DISTANCE = 0.35;

    // ── Config API ──────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }

    public HorizontalDir getStartDirection() { return startDirection; }

    public void setStartDirection(HorizontalDir dir) { this.startDirection = dir; }

    public void toggle() {
        if (enabled) {
            disable();
        } else {
            enable();
        }
    }

    public void enable() {
        enabled = true;
        currentDirection = startDirection;
        phase = Phase.HORIZONTAL;
        forwardBlockedTicks = 0;
    }

    public void disable() {
        enabled = false;
        releaseAllKeys();
    }

    // ── Tick ─────────────────────────────────────────────────────────

    public void tick() {
        if (!enabled) return;

        // Priority system: yield to active combat
        if (AutoAttackManager.getInstance().getCurrentTarget() != null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null) return;

        ClientPlayerEntity player = client.player;

        // Continuously drive block breaking through the left-click pipeline.
        MouseInputHelper.continueLeftClick(client);

        switch (phase) {

            case HORIZONTAL -> {
                // Move in current horizontal direction
                if (isBlockedInDirection(player, currentDirection)) {
                    // Hit wall → stop horizontal, start moving forward
                    setHorizontalKey(currentDirection, false);
                    phase = Phase.FORWARD;
                    forwardBlockedTicks = 0;
                    client.options.forwardKey.setPressed(true);
                } else {
                    setHorizontalKey(currentDirection, true);
                }
            }

            case FORWARD -> {
                if (isBlockedForward(player)) {
                    forwardBlockedTicks++;
                    // Wait a couple ticks to confirm we're truly blocked
                    if (forwardBlockedTicks >= 3) {
                        // Arrived at next row — switch horizontal direction
                        client.options.forwardKey.setPressed(false);
                        currentDirection = (currentDirection == HorizontalDir.LEFT)
                                ? HorizontalDir.RIGHT : HorizontalDir.LEFT;
                        phase = Phase.HORIZONTAL;
                    }
                } else {
                    forwardBlockedTicks = 0;
                    client.options.forwardKey.setPressed(true);
                }
            }
        }
    }

    // ── Collision detection ──────────────────────────────────────────

    private boolean isBlockedInDirection(ClientPlayerEntity player, HorizontalDir dir) {
        float yaw = player.getYaw();
        // LEFT = yaw - 90, RIGHT = yaw + 90
        float angle = yaw + (dir == HorizontalDir.LEFT ? -90f : 90f);
        return isBlockedAtAngle(player, angle);
    }

    private boolean isBlockedForward(ClientPlayerEntity player) {
        return isBlockedAtAngle(player, player.getYaw());
    }

    /**
     * Check if the player would hit a solid block within {@link #COLLISION_DISTANCE}
     * in the given yaw direction, at foot and chest height.
     */
    private boolean isBlockedAtAngle(ClientPlayerEntity player, float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);

        double px = player.getX() + dx * COLLISION_DISTANCE;
        double pz = player.getZ() + dz * COLLISION_DISTANCE;

        // Check at foot level and head level
        BlockPos foot = BlockPos.ofFloored(px, player.getY(), pz);
        BlockPos head = BlockPos.ofFloored(px, player.getY() + 1, pz);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;

        BlockState footState = client.world.getBlockState(foot);
        BlockState headState = client.world.getBlockState(head);

        // A block is "blocking" if its collision shape is not empty
        VoxelShape footShape = footState.getCollisionShape(client.world, foot);
        VoxelShape headShape = headState.getCollisionShape(client.world, head);

        return !footShape.isEmpty() || !headShape.isEmpty();
    }

    // ── Key helpers ─────────────────────────────────────────────────

    private void setHorizontalKey(HorizontalDir dir, boolean pressed) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (dir == HorizontalDir.LEFT) {
            client.options.leftKey.setPressed(pressed);
        } else {
            client.options.rightKey.setPressed(pressed);
        }
    }

    private void releaseAllKeys() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.options.attackKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.forwardKey.setPressed(false);
    }
}
