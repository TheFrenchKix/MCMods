package com.example.macromod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages loading and saving of the mod's general configuration.
 */
@Environment(EnvType.CLIENT)
public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "macromod.json";

    private ModConfig config;

    public ConfigManager() {
        this.config = new ModConfig();
    }

    /**
     * Returns the current configuration.
     */
    public ModConfig getConfig() {
        return config;
    }

    /**
     * Loads configuration from disk. Creates a default config file if none exists.
     */
    public void load() {
        Path configPath = getConfigPath();
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                config = GSON.fromJson(reader, ModConfig.class);
                if (config == null) {
                    config = new ModConfig();
                }
                LOGGER.info("Configuration loaded from {}", configPath);
            } catch (IOException e) {
                LOGGER.error("Failed to load configuration, using defaults", e);
                config = new ModConfig();
            }
        } else {
            config = new ModConfig();
            save();
            LOGGER.info("Created default configuration at {}", configPath);
        }
    }

    /**
     * Saves the current configuration to disk.
     */
    public void save() {
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration", e);
        }
    }

    private Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }
}
