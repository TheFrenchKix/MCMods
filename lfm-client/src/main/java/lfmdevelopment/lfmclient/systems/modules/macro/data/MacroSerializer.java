package lfmdevelopment.lfmclient.systems.modules.macro.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lfmdevelopment.lfmclient.lfmClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class MacroSerializer {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(MacroAction.class, new MacroAction.Serializer())
        .create();

    private MacroSerializer() {}

    public static File getMacroDir() {
        File dir = new File(lfmClient.FOLDER, "macros");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void save(MacroData macro) {
        File file = new File(getMacroDir(), sanitizeFilename(macro.name) + ".json");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(macro, w);
            lfmClient.LOG.info("Saved macro '{}' ({} actions)", macro.name, macro.actions.size());
        } catch (IOException e) {
            lfmClient.LOG.error("Failed to save macro '{}'", macro.name, e);
        }
    }

    public static MacroData load(String name) {
        File file = new File(getMacroDir(), sanitizeFilename(name) + ".json");
        if (!file.exists()) {
            lfmClient.LOG.warn("Macro file not found: {}", file.getAbsolutePath());
            return null;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, MacroData.class);
        } catch (Exception e) {
            lfmClient.LOG.error("Failed to load macro '{}'", name, e);
            return null;
        }
    }

    public static List<String> listMacros() {
        List<String> names = new ArrayList<>();
        File dir = getMacroDir();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                names.add(f.getName().replace(".json", ""));
            }
        }
        return names;
    }

    public static boolean delete(String name) {
        File file = new File(getMacroDir(), sanitizeFilename(name) + ".json");
        if (file.exists()) {
            try {
                Files.delete(file.toPath());
                return true;
            } catch (IOException e) {
                lfmClient.LOG.error("Failed to delete macro '{}'", name, e);
            }
        }
        return false;
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
