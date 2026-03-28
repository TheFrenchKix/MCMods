package com.mwa.n0name.pathfinding;

import com.mwa.n0name.DebugLogger;

final class PathfindingTrace {

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private PathfindingTrace() {
    }

    static void begin(boolean enabled, int maxLines) {
        State state = STATE.get();
        state.enabled = enabled;
        state.remainingLines = Math.max(0, maxLines);
        state.truncated = false;
    }

    static void end() {
        State state = STATE.get();
        if (state.enabled && state.truncated) {
            DebugLogger.log("PathTrace", "Trace output truncated (budget reached)");
        }
        state.enabled = false;
        state.remainingLines = 0;
        state.truncated = false;
    }

    static void log(String module, String message) {
        State state = STATE.get();
        if (!state.enabled) return;
        if (state.remainingLines <= 0) {
            state.truncated = true;
            return;
        }
        state.remainingLines--;
        DebugLogger.log(module, message);
    }

    private static final class State {
        private boolean enabled;
        private int remainingLines;
        private boolean truncated;
    }
}