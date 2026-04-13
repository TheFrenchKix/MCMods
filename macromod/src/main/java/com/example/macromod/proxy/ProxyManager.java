package com.example.macromod.proxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a list of proxy configurations with one active proxy.
 * Supports legacy single-proxy JSON migration.
 */
public class ProxyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod-proxy");
    private static final Gson   GSON   = new GsonBuilder().setPrettyPrinting().create();
    private static final ProxyManager INSTANCE = new ProxyManager();

    private List<ProxyConfig> proxies       = new ArrayList<>();
    private int               activeIndex   = -1;
    private boolean           globalEnabled = false;
    private boolean           lastTestSuccess = false;

    private ProxyManager() {}

    public static ProxyManager getInstance() { return INSTANCE; }

    // -- Accessors ---------------------------------------------------------

    public List<ProxyConfig> getProxies()    { return proxies; }
    public int  getActiveIndex()             { return activeIndex; }
    public boolean isGlobalEnabled()         { return globalEnabled; }
    public void setActiveIndex(int i)        { activeIndex = i; }
    public void setGlobalEnabled(boolean e)  { globalEnabled = e; }
    public boolean wasLastTestSuccessful()   { return lastTestSuccess; }
    public void setLastTestSuccess(boolean s){ lastTestSuccess = s; }

    /** Returns the active proxy config, or an empty (non-functional) config if none is active. */
    public ProxyConfig getConfig() {
        if (activeIndex >= 0 && activeIndex < proxies.size())
            return proxies.get(activeIndex);
        return new ProxyConfig();
    }

    /** True only when global enable is on and the active proxy has a valid host/port. */
    public boolean isEnabled() {
        if (!globalEnabled || activeIndex < 0 || activeIndex >= proxies.size()) return false;
        ProxyConfig p = proxies.get(activeIndex);
        return p.getHost() != null && !p.getHost().isEmpty()
            && p.getPort() >= 1 && p.getPort() <= 65535;
    }

    public InetSocketAddress getProxyAddress() {
        ProxyConfig cfg = getConfig();
        return new InetSocketAddress(cfg.getHost(), cfg.getPort());
    }

    // -- List management ---------------------------------------------------

    public void addProxy(ProxyConfig cfg) {
        proxies.add(cfg);
    }

    public void removeProxy(int idx) {
        if (idx < 0 || idx >= proxies.size()) return;
        proxies.remove(idx);
        if (activeIndex == idx)
            activeIndex = proxies.isEmpty() ? -1 : Math.min(idx, proxies.size() - 1);
        else if (activeIndex > idx)
            activeIndex--;
    }

    // -- Persistence -------------------------------------------------------

    /** Internal GSON-serialised format for the proxy list. */
    private static class SaveData {
        boolean           enabled;
        int               activeIndex = -1;
        List<ProxyConfig> proxies     = new ArrayList<>();
    }

    public void save() {
        SaveData data    = new SaveData();
        data.enabled     = globalEnabled;
        data.activeIndex = activeIndex;
        data.proxies     = new ArrayList<>(proxies);
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(data, w);
            }
            LOGGER.info("[Proxy] Saved {} proxies (active={})", proxies.size(), activeIndex);
        } catch (IOException e) {
            LOGGER.error("[Proxy] Failed to save config", e);
        }
    }

    public void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            LOGGER.info("[Proxy] No config file found, starting fresh");
            return;
        }
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (obj.has("proxies")) {
                // New list format
                SaveData data = GSON.fromJson(obj, SaveData.class);
                if (data != null) {
                    proxies       = data.proxies     != null ? data.proxies : new ArrayList<>();
                    activeIndex   = data.activeIndex;
                    globalEnabled = data.enabled;
                }
            } else if (obj.has("host")) {
                // Legacy single-proxy format - migrate automatically
                ProxyConfig old = GSON.fromJson(obj, ProxyConfig.class);
                if (old != null && old.getHost() != null && !old.getHost().isEmpty()) {
                    proxies.add(old);
                    activeIndex   = 0;
                    globalEnabled = old.isEnabled();
                    save();  // write in new format immediately
                    LOGGER.info("[Proxy] Migrated legacy single-proxy config");
                }
            }
            LOGGER.info("[Proxy] Loaded {} proxies (active={})", proxies.size(), activeIndex);
        } catch (Exception e) {
            LOGGER.error("[Proxy] Failed to load config", e);
        }
    }

    private Path getConfigPath() {
        String id   = System.getProperty("proxy.id");
        String name = (id != null && !id.isEmpty())
            ? "proxy_" + id.replaceAll("[^a-zA-Z0-9_]", "") + ".json"
            : "proxy.json";
        return FabricLoader.getInstance().getConfigDir().resolve(name);
    }
}