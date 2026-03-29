package com.mwa.pathfinder.pathing;

public final class PathManagers {
    private static final IPathManager INSTANCE = create();

    private PathManagers() {
    }

    public static IPathManager get() {
        return INSTANCE;
    }

    private static IPathManager create() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            return new BaritonePathManager();
        } catch (Throwable ignored) {
            return new NopPathManager();
        }
    }
}
