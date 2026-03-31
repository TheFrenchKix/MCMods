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
    /** Pre-computing all step paths before movement begins. */
    PRECOMPUTING,
    /** Computing path to next waypoint (live fallback / stuck recovery). */
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
