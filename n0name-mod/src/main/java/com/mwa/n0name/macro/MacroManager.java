package com.mwa.n0name.macro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mwa.n0name.DebugLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages CRUD operations and JSON persistence for macros.
 * Each macro is stored as an individual JSON file in the macros folder.
 */
public class MacroManager {

    private static final String MACROS_DIR = "n0name-macros";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Macro> macros = new LinkedHashMap<>();
    private final Path macrosFolder;

    public MacroManager() {
        macrosFolder = FabricLoader.getInstance().getGameDir().resolve(MACROS_DIR);
        ensureFolder();
        loadAll();
    }

    private void ensureFolder() {
        try {
            if (!Files.exists(macrosFolder)) {
                Files.createDirectories(macrosFolder);
            }
        } catch (IOException e) {
            DebugLogger.log("MacroManager", "Failed to create macros folder: " + e.getMessage());
        }
    }

    public Path getMacrosFolder() { return macrosFolder; }

    public List<String> getMacroNames() {
        return new ArrayList<>(macros.keySet());
    }

    public List<Macro> getAllMacros() {
        return new ArrayList<>(macros.values());
    }

    public Macro getMacro(String name) {
        return macros.get(name);
    }

    public void addMacro(Macro macro) {
        macros.put(macro.getName(), macro);
        saveMacro(macro);
        DebugLogger.log("MacroManager", "Added macro: " + macro.getName());
    }

    public void deleteMacro(String name) {
        macros.remove(name);
        Path file = macrosFolder.resolve(sanitizeFilename(name) + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            DebugLogger.log("MacroManager", "Failed to delete macro file: " + e.getMessage());
        }
        DebugLogger.log("MacroManager", "Deleted macro: " + name);
    }

    public void renameMacro(String oldName, String newName) {
        Macro macro = macros.remove(oldName);
        if (macro == null) return;
        Path oldFile = macrosFolder.resolve(sanitizeFilename(oldName) + ".json");
        try {
            Files.deleteIfExists(oldFile);
        } catch (IOException e) {
            DebugLogger.log("MacroManager", "Failed to delete old macro file: " + e.getMessage());
        }
        macro.setName(newName);
        macros.put(newName, macro);
        saveMacro(macro);
    }

    public void updateMacro(Macro macro) {
        macros.put(macro.getName(), macro);
        saveMacro(macro);
    }

    public void saveMacro(Macro macro) {
        ensureFolder();
        Path file = macrosFolder.resolve(sanitizeFilename(macro.getName()) + ".json");
        try {
            String json = GSON.toJson(macro.toJson());
            Files.writeString(file, json);
        } catch (IOException e) {
            DebugLogger.log("MacroManager", "Failed to save macro '" + macro.getName() + "': " + e.getMessage());
        }
    }

    public void loadAll() {
        macros.clear();
        ensureFolder();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(macrosFolder, "*.json")) {
            for (Path file : stream) {
                try {
                    String content = Files.readString(file);
                    JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                    Macro macro = Macro.fromJson(obj);
                    macros.put(macro.getName(), macro);
                } catch (Exception e) {
                    DebugLogger.log("MacroManager", "Failed to load macro from " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            DebugLogger.log("MacroManager", "Failed to scan macros folder: " + e.getMessage());
        }
        DebugLogger.log("MacroManager", "Loaded " + macros.size() + " macros");
    }

    public void saveAll() {
        for (Macro macro : macros.values()) {
            saveMacro(macro);
        }
    }

    /**
     * Import a macro from a JSON file path.
     */
    public Macro importMacro(Path file) {
        try {
            String content = Files.readString(file);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            Macro macro = Macro.fromJson(obj);
            addMacro(macro);
            return macro;
        } catch (Exception e) {
            DebugLogger.log("MacroManager", "Failed to import macro: " + e.getMessage());
            return null;
        }
    }

    /**
     * Export a macro to a specified file path.
     */
    public boolean exportMacro(String name, Path destination) {
        Macro macro = macros.get(name);
        if (macro == null) return false;
        try {
            String json = GSON.toJson(macro.toJson());
            Files.writeString(destination, json);
            return true;
        } catch (IOException e) {
            DebugLogger.log("MacroManager", "Failed to export macro: " + e.getMessage());
            return false;
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
