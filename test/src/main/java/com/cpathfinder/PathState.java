package com.cpathfinder;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Shared mutable state between the pathfinder, movement handler, renderer and command.
 * Thread-safe via volatile: background A* thread writes to pendingPath,
 * the main tick thread drains it into currentPath.
 */
public final class PathState {

    private static volatile List<BlockPos> currentPath = null;
    private static volatile List<BlockPos> pendingPath = null;
    private static volatile boolean navigating = false;

    private PathState() {}

    // ── Main-thread read ──────────────────────────────────────────────────────

    public static List<BlockPos> getPath() {
        return currentPath;
    }

    public static boolean isNavigating() {
        return navigating;
    }

    public static void setPath(List<BlockPos> path) {
        currentPath = path;
        navigating = true;
    }

    public static void clear() {
        currentPath = null;
        pendingPath = null;
        navigating = false;
    }

    // ── Cross-thread communication (A* → main tick) ──────────────────────────

    /** Called by the background A* thread to post a completed path. */
    public static void postPath(List<BlockPos> path) {
        pendingPath = path;
    }

    /**
     * Called on the main tick thread to consume a finished path (if any).
     * Returns null if nothing is ready yet.
     */
    public static List<BlockPos> pollPendingPath() {
        List<BlockPos> p = pendingPath;
        pendingPath = null;
        return p;
    }
}
