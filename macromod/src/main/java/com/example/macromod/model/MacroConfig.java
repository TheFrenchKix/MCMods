package com.example.macromod.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-macro execution configuration.
 */
@Environment(EnvType.CLIENT)
public class MacroConfig {

    /** Whether to loop the macro after completion. */
    private boolean loop;

    /** Whether to skip blocks that don't match the expected type. */
    private boolean skipMismatch;

    /** Whether to attack entities on danger (low health, hostile mobs). */
    private boolean attackDanger;

    /** Attack CPS (clicks per second) when attackDanger is active (1–20). */
    private int attackCPS = 10;

    /** Delay in milliseconds between mining each block. */
    private int miningDelay;

    /** Timeout in milliseconds for navigation to a waypoint. */
    private int moveTimeout;

    /** Distance to consider the player has arrived at a waypoint. */
    private float arrivalRadius;

    /** Whether to attack entities during execution. */
    private boolean attackEnabled = false;

    /** If true, only attack entities whose type is in {@link #attackWhitelist}. */
    private boolean attackWhitelistOnly = false;

    /** Entity type IDs to attack when whitelist mode is active (e.g. "minecraft:zombie"). */
    private List<String> attackWhitelist = new ArrayList<>();

    /** Radius in blocks to scan for entities to attack (2–50). */
    private int attackRange = 10;

    /** Whether pathfinding should avoid jumping (only ground-level traversal). */
    private boolean onlyGround = false;

    /** Whether to keep crosshair locked on target while allowing camera movement. */
    private boolean lockCrosshair = false;

    /** Macro execution mode type (NORMAL or LINE_FARM). */
    private MacroType macroType = MacroType.NORMAL;

    /** Enables steering-based movement (arrival + acceleration). */
    private boolean steeringEnabled = true;

    /** Maximum horizontal steering speed in blocks per tick. */
    private double steeringMaxSpeed = 0.23;

    /** Acceleration factor per tick when approaching desired velocity. */
    private double steeringAcceleration = 0.18;

    /** Radius (blocks) where arrival slowdown starts. */
    private double steeringSlowingRadius = 2.0;

    /** Enables subtle target jitter for humanized motion. */
    private boolean steeringNoiseEnabled = false;

    /** Maximum jitter amplitude in blocks (XZ only). */
    private double steeringNoiseAmplitude = 0.05;

    public MacroConfig() {
        this.loop = false;
        this.skipMismatch = true;
        this.attackDanger = false;
        this.miningDelay = 50;
        this.moveTimeout = 10000;
        this.arrivalRadius = 0.5f;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public boolean isSkipMismatch() {
        return skipMismatch;
    }

    public void setSkipMismatch(boolean skipMismatch) {
        this.skipMismatch = skipMismatch;
    }

    public boolean isAttackDanger() {
        return attackDanger;
    }

    public void setAttackDanger(boolean attackDanger) {
        this.attackDanger = attackDanger;
    }

    public int getAttackCPS() {
        return attackCPS;
    }

    public void setAttackCPS(int attackCPS) {
        this.attackCPS = Math.max(1, Math.min(20, attackCPS));
    }

    private boolean randomAttackCps = false;

    public boolean isRandomAttackCps() {
        return randomAttackCps;
    }

    public void setRandomAttackCps(boolean v) {
        this.randomAttackCps = v;
    }

    public int getMiningDelay() {
        return miningDelay;
    }

    public void setMiningDelay(int miningDelay) {
        this.miningDelay = miningDelay;
    }

    public int getMoveTimeout() {
        return moveTimeout;
    }

    public void setMoveTimeout(int moveTimeout) {
        this.moveTimeout = moveTimeout;
    }

    public float getArrivalRadius() {
        return arrivalRadius;
    }

    public void setArrivalRadius(float arrivalRadius) {
        this.arrivalRadius = arrivalRadius;
    }

    public boolean isAttackEnabled() {
        return attackEnabled;
    }

    public void setAttackEnabled(boolean attackEnabled) {
        this.attackEnabled = attackEnabled;
    }

    public boolean isAttackWhitelistOnly() {
        return attackWhitelistOnly;
    }

    public void setAttackWhitelistOnly(boolean attackWhitelistOnly) {
        this.attackWhitelistOnly = attackWhitelistOnly;
    }

    public List<String> getAttackWhitelist() {
        return attackWhitelist;
    }

    public void setAttackWhitelist(List<String> attackWhitelist) {
        this.attackWhitelist = attackWhitelist != null ? attackWhitelist : new ArrayList<>();
    }

    public int getAttackRange() {
        return attackRange;
    }

    public void setAttackRange(int attackRange) {
        this.attackRange = Math.max(2, Math.min(50, attackRange));
    }

    public boolean isOnlyGround() {
        return onlyGround;
    }

    public void setOnlyGround(boolean onlyGround) {
        this.onlyGround = onlyGround;
    }

    public boolean isLockCrosshair() {
        return lockCrosshair;
    }

    public void setLockCrosshair(boolean lockCrosshair) {
        this.lockCrosshair = lockCrosshair;
    }

    public MacroType getMacroType() {
        return macroType;
    }

    public void setMacroType(MacroType macroType) {
        this.macroType = macroType;
    }

    public boolean isSteeringEnabled() {
        return steeringEnabled;
    }

    public void setSteeringEnabled(boolean steeringEnabled) {
        this.steeringEnabled = steeringEnabled;
    }

    public double getSteeringMaxSpeed() {
        return steeringMaxSpeed;
    }

    public void setSteeringMaxSpeed(double steeringMaxSpeed) {
        this.steeringMaxSpeed = Math.max(0.08, Math.min(0.40, steeringMaxSpeed));
    }

    public double getSteeringAcceleration() {
        return steeringAcceleration;
    }

    public void setSteeringAcceleration(double steeringAcceleration) {
        this.steeringAcceleration = Math.max(0.05, Math.min(0.50, steeringAcceleration));
    }

    public double getSteeringSlowingRadius() {
        return steeringSlowingRadius;
    }

    public void setSteeringSlowingRadius(double steeringSlowingRadius) {
        this.steeringSlowingRadius = Math.max(0.5, Math.min(6.0, steeringSlowingRadius));
    }

    public boolean isSteeringNoiseEnabled() {
        return steeringNoiseEnabled;
    }

    public void setSteeringNoiseEnabled(boolean steeringNoiseEnabled) {
        this.steeringNoiseEnabled = steeringNoiseEnabled;
    }

    public double getSteeringNoiseAmplitude() {
        return steeringNoiseAmplitude;
    }

    public void setSteeringNoiseAmplitude(double steeringNoiseAmplitude) {
        this.steeringNoiseAmplitude = Math.max(0.0, Math.min(0.20, steeringNoiseAmplitude));
    }

    /**
     * Creates a copy of this config.
     */
    public MacroConfig copy() {
        MacroConfig copy = new MacroConfig();
        copy.loop = loop;
        copy.skipMismatch = skipMismatch;
        copy.attackDanger = attackDanger;
        copy.miningDelay = miningDelay;
        copy.moveTimeout = moveTimeout;
        copy.arrivalRadius = arrivalRadius;
        copy.attackEnabled = attackEnabled;
        copy.attackWhitelistOnly = attackWhitelistOnly;
        copy.attackWhitelist = new ArrayList<>(attackWhitelist);
        copy.attackRange = attackRange;
        copy.attackCPS = attackCPS;
        copy.randomAttackCps = randomAttackCps;
        copy.onlyGround = onlyGround;
        copy.lockCrosshair = lockCrosshair;
        copy.macroType = macroType;
        copy.steeringEnabled = steeringEnabled;
        copy.steeringMaxSpeed = steeringMaxSpeed;
        copy.steeringAcceleration = steeringAcceleration;
        copy.steeringSlowingRadius = steeringSlowingRadius;
        copy.steeringNoiseEnabled = steeringNoiseEnabled;
        copy.steeringNoiseAmplitude = steeringNoiseAmplitude;
        return copy;
    }
}
