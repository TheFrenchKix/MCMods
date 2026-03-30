package com.example.macromod.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * State machine states for macro execution.
 */
@Environment(EnvType.CLIENT)
public enum MacroState {
    /** No macro running. */
    IDLE,
    /** Computing path to next waypoint. */
    PATHFINDING,
    /** Moving along computed path. */
    MOVING,
    /** Mining block targets at the current step. */
    MINING,
    /** Transitioning to the next step. */
    NEXT_STEP,
    /** Execution paused by user. */
    PAUSED,
    /** Macro completed successfully. */
    COMPLETED,
    /** An error occurred during execution. */
    ERROR
}
