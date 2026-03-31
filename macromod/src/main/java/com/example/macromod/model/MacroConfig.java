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

    /** Whether to stop execution on danger (low health, hostile mobs). */
    private boolean stopOnDanger;

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

    public MacroConfig() {
        this.loop = false;
        this.skipMismatch = true;
        this.stopOnDanger = true;
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

    public boolean isStopOnDanger() {
        return stopOnDanger;
    }

    public void setStopOnDanger(boolean stopOnDanger) {
        this.stopOnDanger = stopOnDanger;
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

    /**
     * Creates a copy of this config.
     */
    public MacroConfig copy() {
        MacroConfig copy = new MacroConfig();
        copy.loop = loop;
        copy.skipMismatch = skipMismatch;
        copy.stopOnDanger = stopOnDanger;
        copy.miningDelay = miningDelay;
        copy.moveTimeout = moveTimeout;
        copy.arrivalRadius = arrivalRadius;
        copy.attackEnabled = attackEnabled;
        copy.attackWhitelistOnly = attackWhitelistOnly;
        copy.attackWhitelist = new ArrayList<>(attackWhitelist);
        copy.attackRange = attackRange;
        return copy;
    }
}
