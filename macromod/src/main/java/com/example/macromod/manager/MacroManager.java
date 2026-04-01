package com.example.macromod.manager;

import com.example.macromod.model.Macro;
import com.example.macromod.util.JsonUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages CRUD operations and JSON persistence for macros.
 * All macros are stored as individual JSON files in the config/macromod/macros/ directory.
 */
@Environment(EnvType.CLIENT)
public class MacroManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");

    private final Map<String, Macro> macros = new LinkedHashMap<>();
    private final Path macrosDir;

    public MacroManager() {
        this.macrosDir = FabricLoader.getInstance().getConfigDir().resolve("macromod").resolve("macros");
    }

    /**
     * Loads all macro JSON files from the macros directory.
     */
    public void loadAll() {
        macros.clear();
        try {
            Files.createDirectories(macrosDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create macros directory", e);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(macrosDir, "*.json")) {
            for (Path file : stream) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    StringBuilder sb = new StringBuilder();
                    char[] buffer = new char[1024];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        sb.append(buffer, 0, read);
                    }
                    Macro macro = JsonUtils.fromJson(sb.toString());
                    if (macro != null && macro.getId() != null) {
                        macros.put(macro.getId(), macro);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load macro from {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list macros directory", e);
        }
        LOGGER.info("Loaded {} macros", macros.size());
    }

    /**
     * Saves a single macro to its JSON file.
     */
    public void save(Macro macro) {
        macro.markModified();
        macros.put(macro.getId(), macro);

        Path filePath = macrosDir.resolve(macro.getId() + ".json");
        try {
            Files.createDirectories(macrosDir);
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                writer.write(JsonUtils.toJson(macro));
            }
            LOGGER.info("Saved macro '{}' to {}", macro.getName(), filePath.getFileName());
        } catch (IOException e) {
            LOGGER.error("Failed to save macro '{}'", macro.getName(), e);
        }
    }

    /**
     * Deletes a macro by its ID, removing both the in-memory entry and the JSON file.
     */
    public void delete(String id) {
        Macro removed = macros.remove(id);
        if (removed != null) {
            Path filePath = macrosDir.resolve(id + ".json");
            try {
                Files.deleteIfExists(filePath);
                LOGGER.info("Deleted macro '{}' ({})", removed.getName(), id);
            } catch (IOException e) {
                LOGGER.error("Failed to delete macro file for '{}'", id, e);
            }
        }
    }

    /**
     * Returns all macros as an unmodifiable list.
     */
    public List<Macro> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(macros.values()));
    }

    /**
     * Returns a macro by its ID, or null if not found.
     */
    public Macro getById(String id) {
        return macros.get(id);
    }

    /**
     * Finds a macro by name (case-insensitive). Returns the first match or null.
     */
    public Macro getByName(String name) {
        for (Macro macro : macros.values()) {
            if (macro.getName().equalsIgnoreCase(name)) {
                return macro;
            }
        }
        return null;
    }

    /**
     * Finds a macro by name or ID. Tries ID first, then name.
     */
    public Macro getByNameOrId(String nameOrId) {
        Macro macro = getById(nameOrId);
        if (macro != null) {
            return macro;
        }
        return getByName(nameOrId);
    }

    /**
     * Creates a new empty macro with the given name and saves it.
     */
    public Macro create(String name) {
        Macro macro = new Macro(name);
        save(macro);
        LOGGER.info("Created macro '{}' with ID {}", name, macro.getId());
        return macro;
    }

    /**
     * Duplicates an existing macro. The copy gets a new ID and "(copy)" appended to its name.
     */
    public Macro duplicate(String id) {
        Macro original = getById(id);
        if (original == null) {
            return null;
        }
        Macro copy = original.duplicate();
        save(copy);
        LOGGER.info("Duplicated macro '{}' as '{}'", original.getName(), copy.getName());
        return copy;
    }

    /**
     * Returns all macro names (for command autocompletion).
     */
    public Collection<String> getAllNames() {
        List<String> names = new ArrayList<>();
        for (Macro macro : macros.values()) {
            names.add(macro.getName());
        }
        return names;
    }
}
