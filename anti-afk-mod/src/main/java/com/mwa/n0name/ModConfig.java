package com.mwa.n0name;

import com.google.gson.*;
import com.mwa.n0name.pathfinding.PathNode;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ModConfig {

    private static final ModConfig INSTANCE = new ModConfig();
    public static ModConfig getInstance() { return INSTANCE; }

    // --- Module toggles ---
    private boolean antiAfkEnabled   = false;
    private boolean blockEspEnabled  = false;
    private boolean entityEspEnabled = false;
    private boolean autoWalkEnabled  = false;
    private boolean autoFarmEnabled  = false;
    private boolean debugEnabled     = false;

    // --- AutoFarm settings ---
    private boolean cpsMode    = false; // false=cooldown, true=CPS
    private int     targetCps  = 10;
    private double  autoFarmRange = 10.0;
    private double  attackRange   = 3.0;

    // --- ESP ---
    public static final int[] PRESET_COLORS = {
        0xFFFF4444, 0xFF44FF44, 0xFF4488FF, 0xFFFFFF44,
        0xFF44FFFF, 0xFFFF44FF, 0xFFFF8844, 0xFFFFFFFF, 0xFF888888
    };
    public static final String[] PRESET_COLOR_NAMES = {
        "Red", "Green", "Blue", "Yellow", "Cyan", "Magenta", "Orange", "White", "Gray"
    };

    public static class EspEntry {
        public boolean enabled = false;
        public int colorIndex;
        public int color;

        public EspEntry(int colorIndex) {
            this.colorIndex = colorIndex;
            this.color = PRESET_COLORS[colorIndex % PRESET_COLORS.length];
        }
    }

    private final Map<String, EspEntry> entityFilters = new LinkedHashMap<>();
    private final Map<String, EspEntry> blockFilters  = new LinkedHashMap<>();

    // --- Routes ---
    private final Map<String, List<PathNode>> savedRoutes = new LinkedHashMap<>();

    // --- PatchCreator state ---
    private boolean patchCreatorRecording = false;
    private String  currentRouteName = "";
    private final List<String> routeNames = new ArrayList<>();

    // --- Getters / Setters ---
    public boolean isAntiAfkEnabled()    { return antiAfkEnabled; }
    public void setAntiAfkEnabled(boolean v)   { antiAfkEnabled  = v; }
    public boolean isBlockEspEnabled()   { return blockEspEnabled; }
    public void setBlockEspEnabled(boolean v)  { blockEspEnabled  = v; }
    public boolean isEntityEspEnabled()  { return entityEspEnabled; }
    public void setEntityEspEnabled(boolean v) { entityEspEnabled = v; }
    public boolean isAutoWalkEnabled()   { return autoWalkEnabled; }
    public void setAutoWalkEnabled(boolean v)  { autoWalkEnabled = v; }
    public boolean isAutoFarmEnabled()   { return autoFarmEnabled; }
    public void setAutoFarmEnabled(boolean v)  { autoFarmEnabled = v; }
    public boolean isDebugEnabled()      { return debugEnabled; }
    public void setDebugEnabled(boolean v) {
        debugEnabled = v;
        DebugLogger.setEnabled(v);
    }

    public boolean isCpsMode()           { return cpsMode; }
    public void setCpsMode(boolean v)    { cpsMode = v; }
    public int getTargetCps()            { return targetCps; }
    public void setTargetCps(int v)      { targetCps = Math.max(1, Math.min(20, v)); }
    public double getAutoFarmRange()     { return autoFarmRange; }
    public void setAutoFarmRange(double v){ autoFarmRange = v; }
    public double getAttackRange()       { return attackRange; }
    public void setAttackRange(double v) { attackRange = v; }

    public boolean isPatchCreatorRecording() { return patchCreatorRecording; }
    public void setPatchCreatorRecording(boolean v) { patchCreatorRecording = v; }
    public String getCurrentRouteName()  { return currentRouteName; }
    public void setCurrentRouteName(String n) { currentRouteName = n; }

    // --- ESP entries ---
    public Map<String, EspEntry> getEntityFilters() { return entityFilters; }
    public Map<String, EspEntry> getBlockFilters()  { return blockFilters; }

    public EspEntry getOrCreateEntityEntry(String typeId) {
        return entityFilters.computeIfAbsent(typeId,
            k -> new EspEntry(entityFilters.size() % PRESET_COLORS.length));
    }

    public EspEntry getOrCreateBlockEntry(String blockId) {
        return blockFilters.computeIfAbsent(blockId,
            k -> new EspEntry(blockFilters.size() % PRESET_COLORS.length));
    }

    public void cycleColor(String id, boolean isEntity) {
        EspEntry e = (isEntity ? entityFilters : blockFilters).get(id);
        if (e != null) {
            e.colorIndex = (e.colorIndex + 1) % PRESET_COLORS.length;
            e.color = PRESET_COLORS[e.colorIndex];
        }
    }

    // --- Route storage ---
    public Map<String, List<PathNode>> getAllRoutes() { return savedRoutes; }
    public List<String> getRouteNames() {
        routeNames.clear();
        routeNames.addAll(savedRoutes.keySet());
        return routeNames;
    }

    public void saveRoute(String name, List<PathNode> nodes) {
        savedRoutes.put(name, new ArrayList<>(nodes));
        saveRoutesToFile();
    }

    public List<PathNode> getRoute(String name) {
        return savedRoutes.getOrDefault(name, Collections.emptyList());
    }

    public void deleteRoute(String name) {
        savedRoutes.remove(name);
        saveRoutesToFile();
    }

    // --- JSON persistence ---
    private Path getRoutesFilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("n0name-routes.json");
    }

    public void loadRoutesFromFile() {
        Path path = getRoutesFilePath();
        if (!Files.exists(path)) return;

        try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            savedRoutes.clear();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                List<PathNode> nodes = new ArrayList<>();
                for (JsonElement el : entry.getValue().getAsJsonArray()) {
                    nodes.add(PathNode.fromJson(el.getAsJsonObject()));
                }
                savedRoutes.put(entry.getKey(), nodes);
            }
            DebugLogger.log("Config", "Loaded " + savedRoutes.size() + " routes");
        } catch (Exception e) {
            DebugLogger.info("Failed to load routes: " + e.getMessage());
        }
    }

    public void saveRoutesToFile() {
        Path path = getRoutesFilePath();
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<PathNode>> entry : savedRoutes.entrySet()) {
            JsonArray arr = new JsonArray();
            for (PathNode node : entry.getValue()) {
                arr.add(node.toJson());
            }
            root.add(entry.getKey(), arr);
        }

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
            DebugLogger.log("Config", "Saved " + savedRoutes.size() + " routes");
        } catch (Exception e) {
            DebugLogger.info("Failed to save routes: " + e.getMessage());
        }
    }
}
