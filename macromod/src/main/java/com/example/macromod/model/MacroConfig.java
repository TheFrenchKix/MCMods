package com.example.macromod.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

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

    /** Timeout in ticks for navigation to a waypoint. */
    private int moveTimeout;

    /** Distance to consider the player has arrived at a waypoint. */
    private float arrivalRadius;

    public MacroConfig() {
        this.loop = false;
        this.skipMismatch = true;
        this.stopOnDanger = true;
        this.miningDelay = 50;
        this.moveTimeout = 200;
        this.arrivalRadius = 1.5f;
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
        return copy;
    }
}
