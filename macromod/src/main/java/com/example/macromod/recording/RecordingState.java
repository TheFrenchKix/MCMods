package com.example.macromod.recording;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * State machine states for the macro recorder.
 */
@Environment(EnvType.CLIENT)
public enum RecordingState {
    /** No recording active. */
    IDLE,
    /** Actively recording waypoints and blocks. */
    RECORDING,
    /** Recording temporarily paused. */
    PAUSED
}
