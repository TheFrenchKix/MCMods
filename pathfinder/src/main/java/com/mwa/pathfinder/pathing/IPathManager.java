package com.mwa.pathfinder.pathing;

import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public interface IPathManager {
    String getName();

    boolean isAvailable();

    boolean isPathing();

    void configureDefaults();

    void tick();

    void stop();

    void moveTo(BlockPos pos);

    void moveTo(BlockPos pos, boolean ignoreY);

    void moveInDirection(float yaw);

    void followEntity(UUID entityId);

    float getTargetYaw();

    float getTargetPitch();

    String getCurrentGoalText();

    boolean isFollowing();

    default UUID getFollowedEntityId() { return null; }

    default boolean isAllowSprint() { return false; }

    default boolean toggleAllowSprint() { return false; }

    default void setAllowSprint(boolean value) { }

    default boolean isAllowParkour() { return false; }

    default boolean toggleAllowParkour() { return false; }

    default void setAllowParkour(boolean value) { }

    default boolean isFreeLook() { return false; }

    default boolean toggleFreeLook() { return false; }

    default void setFreeLook(boolean value) { }

    default boolean isRenderPath() { return false; }

    default boolean toggleRenderPath() { return false; }

    default void setRenderPath(boolean value) { }

    default boolean isRenderGoal() { return false; }

    default boolean toggleRenderGoal() { return false; }

    default void setRenderGoal(boolean value) { }

    default boolean isAllowBreak() { return false; }

    default boolean toggleAllowBreak() { return false; }

    default void setAllowBreak(boolean value) { }

    default boolean isAllowPlace() { return false; }

    default boolean toggleAllowPlace() { return false; }

    default void setAllowPlace(boolean value) { }

    default boolean isAntiCheatCompatibility() { return false; }

    default boolean toggleAntiCheatCompatibility() { return false; }

    default void setAntiCheatCompatibility(boolean value) { }

    default long getPrimaryTimeoutMs() { return 0L; }

    default void setPrimaryTimeoutMs(long timeoutMs) { }

    default double getRandomLooking() { return 0.0d; }

    default void setRandomLooking(double value) { }

    default void saveSettings() { }
}
