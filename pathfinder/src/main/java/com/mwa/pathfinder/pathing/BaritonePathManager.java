package com.mwa.pathfinder.pathing;

import baritone.api.BaritoneAPI;
import baritone.api.utils.SettingsUtil;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public class BaritonePathManager implements IPathManager {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private GoalDirection directionGoal;
    private UUID followedEntityId;

    @Override
    public String getName() {
        return "Baritone";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }

    @Override
    public void configureDefaults() {
        BaritoneAPI.getSettings().allowSprint.value = true;
        BaritoneAPI.getSettings().freeLook.value = true;
        BaritoneAPI.getSettings().allowParkour.value = true;
        BaritoneAPI.getSettings().allowBreak.value = false;
        BaritoneAPI.getSettings().allowPlace.value = false;
        BaritoneAPI.getSettings().randomLooking.value = 0.0d;
        BaritoneAPI.getSettings().primaryTimeoutMS.value = 2000L;
        BaritoneAPI.getSettings().renderPath.value = true;
        BaritoneAPI.getSettings().renderGoal.value = true;
    }

    @Override
    public void tick() {
        if (directionGoal == null) {
            return;
        }

        Goal currentGoal = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal();
        if (currentGoal != directionGoal) {
            directionGoal = null;
            return;
        }

        directionGoal.tick();
    }

    @Override
    public void stop() {
        directionGoal = null;
        followedEntityId = null;
        BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().cancel();
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    @Override
    public void moveTo(BlockPos pos) {
        moveTo(pos, false);
    }

    @Override
    public void moveTo(BlockPos pos, boolean ignoreY) {
        directionGoal = null;
        if (ignoreY) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ(pos.getX(), pos.getZ()));
            return;
        }

        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos));
    }

    @Override
    public void moveInDirection(float yaw) {
        followedEntityId = null;
        directionGoal = new GoalDirection(yaw);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(directionGoal);
    }

    @Override
    public void followEntity(UUID entityId) {
        directionGoal = null;
        followedEntityId = entityId;
        BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().follow(entity -> entity.getUuid().equals(entityId));
    }

    @Override
    public float getTargetYaw() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerRotations().getYaw();
    }

    @Override
    public float getTargetPitch() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerRotations().getPitch();
    }

    @Override
    public String getCurrentGoalText() {
        Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal();
        return goal == null ? "none" : goal.toString();
    }

    @Override
    public boolean isFollowing() {
        return followedEntityId != null && BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().currentFilter() != null;
    }

    @Override
    public UUID getFollowedEntityId() {
        return followedEntityId;
    }

    public boolean isAllowSprint() {
        return BaritoneAPI.getSettings().allowSprint.value;
    }

    public boolean toggleAllowSprint() {
        BaritoneAPI.getSettings().allowSprint.value = !BaritoneAPI.getSettings().allowSprint.value;
        return BaritoneAPI.getSettings().allowSprint.value;
    }

    @Override
    public void setAllowSprint(boolean value) {
        BaritoneAPI.getSettings().allowSprint.value = value;
    }

    public boolean isAllowParkour() {
        return BaritoneAPI.getSettings().allowParkour.value;
    }

    public boolean toggleAllowParkour() {
        BaritoneAPI.getSettings().allowParkour.value = !BaritoneAPI.getSettings().allowParkour.value;
        return BaritoneAPI.getSettings().allowParkour.value;
    }

    @Override
    public void setAllowParkour(boolean value) {
        BaritoneAPI.getSettings().allowParkour.value = value;
    }

    public boolean isFreeLook() {
        return BaritoneAPI.getSettings().freeLook.value;
    }

    public boolean toggleFreeLook() {
        BaritoneAPI.getSettings().freeLook.value = !BaritoneAPI.getSettings().freeLook.value;
        return BaritoneAPI.getSettings().freeLook.value;
    }

    @Override
    public void setFreeLook(boolean value) {
        BaritoneAPI.getSettings().freeLook.value = value;
    }

    public boolean isRenderPath() {
        return BaritoneAPI.getSettings().renderPath.value;
    }

    public boolean toggleRenderPath() {
        BaritoneAPI.getSettings().renderPath.value = !BaritoneAPI.getSettings().renderPath.value;
        return BaritoneAPI.getSettings().renderPath.value;
    }

    @Override
    public void setRenderPath(boolean value) {
        BaritoneAPI.getSettings().renderPath.value = value;
    }

    public boolean isRenderGoal() {
        return BaritoneAPI.getSettings().renderGoal.value;
    }

    public boolean toggleRenderGoal() {
        BaritoneAPI.getSettings().renderGoal.value = !BaritoneAPI.getSettings().renderGoal.value;
        return BaritoneAPI.getSettings().renderGoal.value;
    }

    @Override
    public void setRenderGoal(boolean value) {
        BaritoneAPI.getSettings().renderGoal.value = value;
    }

    public boolean isAllowBreak() {
        return BaritoneAPI.getSettings().allowBreak.value;
    }

    public boolean toggleAllowBreak() {
        BaritoneAPI.getSettings().allowBreak.value = !BaritoneAPI.getSettings().allowBreak.value;
        return BaritoneAPI.getSettings().allowBreak.value;
    }

    @Override
    public void setAllowBreak(boolean value) {
        BaritoneAPI.getSettings().allowBreak.value = value;
    }

    public boolean isAllowPlace() {
        return BaritoneAPI.getSettings().allowPlace.value;
    }

    public boolean toggleAllowPlace() {
        BaritoneAPI.getSettings().allowPlace.value = !BaritoneAPI.getSettings().allowPlace.value;
        return BaritoneAPI.getSettings().allowPlace.value;
    }

    @Override
    public void setAllowPlace(boolean value) {
        BaritoneAPI.getSettings().allowPlace.value = value;
    }

    public boolean isAntiCheatCompatibility() {
        return BaritoneAPI.getSettings().antiCheatCompatibility.value;
    }

    public boolean toggleAntiCheatCompatibility() {
        BaritoneAPI.getSettings().antiCheatCompatibility.value = !BaritoneAPI.getSettings().antiCheatCompatibility.value;
        return BaritoneAPI.getSettings().antiCheatCompatibility.value;
    }

    @Override
    public void setAntiCheatCompatibility(boolean value) {
        BaritoneAPI.getSettings().antiCheatCompatibility.value = value;
    }

    public long getPrimaryTimeoutMs() {
        return BaritoneAPI.getSettings().primaryTimeoutMS.value;
    }

    public void setPrimaryTimeoutMs(long timeoutMs) {
        BaritoneAPI.getSettings().primaryTimeoutMS.value = Math.max(250L, timeoutMs);
    }

    public double getRandomLooking() {
        return BaritoneAPI.getSettings().randomLooking.value;
    }

    public void setRandomLooking(double value) {
        BaritoneAPI.getSettings().randomLooking.value = Math.max(0.0d, value);
    }

    public void saveSettings() {
        SettingsUtil.save(BaritoneAPI.getSettings());
    }

    private final class GoalDirection implements Goal {
        private static final double SQRT_2 = Math.sqrt(2.0d);

        private final float yaw;
        private int x;
        private int z;
        private int timer;

        private GoalDirection(float yaw) {
            this.yaw = yaw;
            tick();
        }

        private void tick() {
            if (client.player == null) {
                return;
            }

            if (timer <= 0) {
                timer = 20;
                Vec3d pos = client.player.getPos();
                float theta = (float) Math.toRadians(yaw);
                x = MathHelper.floor(pos.x - MathHelper.sin(theta) * 64.0f);
                z = MathHelper.floor(pos.z + MathHelper.cos(theta) * 64.0f);
            }

            timer--;
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            return x == this.x && z == this.z;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            double xDiff = x - this.x;
            double zDiff = z - this.z;
            double absX = Math.abs(xDiff);
            double absZ = Math.abs(zDiff);
            double straight = absX < absZ ? absZ - absX : absX - absZ;
            double diagonal = Math.min(absX, absZ) * SQRT_2;
            return (straight + diagonal) * BaritoneAPI.getSettings().costHeuristic.value;
        }

        @Override
        public String toString() {
            return "GoalDirection{x=" + x + ", z=" + z + '}';
        }
    }
}
