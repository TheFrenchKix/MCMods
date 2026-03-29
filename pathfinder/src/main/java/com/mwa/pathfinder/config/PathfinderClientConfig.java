package com.mwa.pathfinder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathfinderClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "pathfinder-client.json";

    public boolean cameraSync = false;
    public boolean ignoreY = false;

    public boolean overlayEnabled = true;
    public boolean overlayBlockTarget = true;
    public boolean overlayFollowEntity = true;

    public int selectedTab = 0;

    public boolean allowSprint = true;
    public boolean allowParkour = true;
    public boolean freeLook = true;
    public boolean renderPath = true;
    public boolean renderGoal = true;
    public boolean allowBreak = false;
    public boolean allowPlace = false;
    public boolean antiCheatCompatibility = false;
    public long primaryTimeoutMs = 2000L;
    public double randomLooking = 0.0d;

    public static PathfinderClientConfig load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            PathfinderClientConfig defaults = new PathfinderClientConfig();
            defaults.save();
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            PathfinderClientConfig loaded = GSON.fromJson(reader, PathfinderClientConfig.class);
            if (loaded == null) {
                loaded = new PathfinderClientConfig();
            }
            return loaded;
        } catch (IOException ignored) {
            return new PathfinderClientConfig();
        }
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
