package com.mwa.pathfinder.pathing;

import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class NopPathManager implements IPathManager {
    @Override
    public String getName() {
        return "No Path Manager";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isPathing() {
        return false;
    }

    @Override
    public void configureDefaults() {
    }

    @Override
    public void tick() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void moveTo(BlockPos pos) {
    }

    @Override
    public void moveTo(BlockPos pos, boolean ignoreY) {
    }

    @Override
    public void moveInDirection(float yaw) {
    }

    @Override
    public void followEntity(UUID entityId) {
    }

    @Override
    public float getTargetYaw() {
        return 0.0f;
    }

    @Override
    public float getTargetPitch() {
        return 0.0f;
    }

    @Override
    public String getCurrentGoalText() {
        return "Baritone API not loaded";
    }

    @Override
    public boolean isFollowing() {
        return false;
    }
}
