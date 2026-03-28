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

    public enum AutoFarmPriority {
        NEAREST,
        LOWEST_HEALTH,
        HIGHEST_THREAT
    }

    public enum AutoFarmMode {
        MOBS,
        CROPS
    }

    public enum CropType {
        WHEAT,
        CARROT,
        POTATO,
        ALL
    }

    public enum JacobPreset {
        BALANCED,
        SAFE,
        TURBO
    }

    public enum AimProfile {
        STEADY,
        BALANCED,
        SNAPPY
    }

    private static final ModConfig INSTANCE = new ModConfig();
    public static ModConfig getInstance() { return INSTANCE; }

    // --- Module toggles ---
    private boolean antiAfkEnabled   = false;
    private boolean blockEspEnabled  = false;
    private boolean entityEspEnabled = false;
    private boolean autoFarmEnabled  = false;
    private boolean autoMineEnabled  = false;
    private boolean autoSlayEnabled  = false;
    private boolean autoCropEnabled  = false;
    private boolean blockNukerEnabled = false;
    private boolean autoFishEnabled  = false;
    private boolean fairySoulFinderEnabled = false;
    private boolean playerEspEnabled = false;
    private boolean friendListEnabled = false;
    private boolean debugToolsEnabled = false;
    private boolean debugEnabled     = false;

    // --- AutoFarm settings ---
    private boolean cpsMode    = false; // false=cooldown, true=CPS
    private int     targetCps  = 10;
    private double  autoFarmRange = 25.0;
    private double  autoMineRange = 18.0;
    private double  attackRange   = 3.0;
    private boolean autoFarmRequireLineOfSight = true;
    private double  autoFarmSwitchMargin = 2.5;
    private AutoFarmPriority autoFarmPriority = AutoFarmPriority.NEAREST;
    private boolean autoFarmWhitelistMode = false; // false=all, true=whitelist only
    private final Set<String> autoFarmWhitelist = new HashSet<>();
    private boolean pathSmoothingEnabled = true;
    private AutoFarmMode autoFarmMode = AutoFarmMode.MOBS;
    private CropType cropType = CropType.ALL;
    private boolean autoFarmSilentCropAim = true;
    private boolean autoFarmAutoTool = true;
    private int autoFarmCropBlocksPerTick = 2;
    private boolean autoFarmReplant = true;
    private JacobPreset jacobPreset = JacobPreset.BALANCED;
    private AimProfile aimProfile = AimProfile.BALANCED;

    // --- Skyblock automation safeguards ---
    private boolean gardenLaneLoopEnabled = false;
    private String gardenLaneRouteName = "";
    private boolean visitorAlarmEnabled = true;
    private String visitorAlarmKeyword = "visitor";
    private boolean autoPauseOnVisitor = true;
    private int inventoryActionThresholdPct = 92;
    private boolean inventoryAutoActionEnabled = true;
    private int inventoryActionCooldownSec = 30;
    private boolean desyncWatchdogEnabled = true;
    private int desyncFailLimit = 8;
    private boolean failsafeRandomPauseEnabled = true;
    private boolean failsafeYawJitterEnabled = true;
    private int failsafePauseMinTicks = 2;
    private int failsafePauseMaxTicks = 6;
    private boolean waypointChainEnabled = false;

    // --- Bazaar helper (manual prices) ---
    private double wheatPrice = 3.0;
    private double carrotPrice = 3.0;
    private double potatoPrice = 3.0;

    // --- Command macro settings ---
    private String commandMacro1 = "/is";
    private String commandMacro2 = "/warp garden";
    private String commandMacro3 = "/hub";

    // --- AutoFish settings ---
    public enum AutoFishKillMode {
        CLOSE,
        DISTANCE
    }
    private boolean autoFishKillEntity = false;
    private AutoFishKillMode autoFishKillMode = AutoFishKillMode.CLOSE;
    private double autoFishRange = 16.0;

    // --- Fairy soul finder settings ---
    private int fairySoulScanRange = 96;
    private boolean routeHeatmapEnabled = false;
    private int routeHeatmapProfileIndex = 1;
    private int routeHeatmapX = 8;
    private int routeHeatmapY = 8;
    private int routeHeatmapSize = 120;

    // --- Hypixel stats HUD settings ---
    private boolean hypixelStatsHudEnabled = false;
    private int hypixelStatsHudX = -1;
    private int hypixelStatsHudY = 8;
    private float hypixelStatsHudScale = 1.0f;
    private boolean hypixelStatsShowFarming = true;
    private boolean hypixelStatsShowWorth = true;
    private boolean hypixelStatsShowSkills = true;

    // --- Player ESP / Friend list ---
    private boolean playerEspShowHealth = true;
    private boolean playerEspShowWeapon = true;
    private boolean playerEspShowArmor = true;
    private boolean friendListAutoNearby = true;
    private final Set<String> friendNames = new HashSet<>();

    // --- Debug tab settings ---
    private boolean debugTargetBlockEnabled = false;
    private boolean debugScanBlocksEnabled = false;
    private boolean debugScanEntitiesEnabled = false;
    private int debugScanRange = 8;

    // --- BlockNuker settings ---
    private double blockNukerRange = 6.0;
    private boolean blockNukerWhitelistMode = true;
    private final Set<String> blockNukerWhitelist = new LinkedHashSet<>(List.of(
        "minecraft:short_grass",
        "minecraft:tall_grass",
        "minecraft:fern",
        "minecraft:large_fern",
        "minecraft:dandelion",
        "minecraft:poppy",
        "minecraft:blue_orchid",
        "minecraft:allium",
        "minecraft:azure_bluet",
        "minecraft:red_tulip",
        "minecraft:orange_tulip",
        "minecraft:white_tulip",
        "minecraft:pink_tulip",
        "minecraft:oxeye_daisy",
        "minecraft:cornflower",
        "minecraft:lily_of_the_valley",
        "minecraft:sunflower",
        "minecraft:rose_bush",
        "minecraft:peony",
        "minecraft:lilac",
        "minecraft:brown_mushroom",
        "minecraft:red_mushroom",
        "minecraft:dead_bush",
        "minecraft:vine"
    ));
    private boolean blockNukerSilent = true;
    private boolean blockNukerRequireLineOfSight = false;
    private double blockNukerMaxHardness = 0.3;
    private int blockNukerBlocksPerTick = 3;
    private boolean blockNukerSkipBlockEntities = true;

    // --- Utilities settings ---
    private boolean preventLedgeFall = false;
    private int ledgeMaxDrop = 1;
    private boolean inventoryHudEnabled = false;
    private int inventoryHudX = 8;
    private int inventoryHudY = 8;
    private float inventoryHudScale = 1.0f;

    // --- ESP range settings ---
    private double  entityEspRange = 50.0;
    private double  blockEspRange = 25.0;

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
    public boolean isAutoFarmEnabled()   { return autoFarmEnabled; }
    public void setAutoFarmEnabled(boolean v)  { autoFarmEnabled = v; }
    public boolean isAutoMineEnabled()   { return autoMineEnabled; }
    public void setAutoMineEnabled(boolean v)  { autoMineEnabled = v; }
    public boolean isAutoSlayEnabled()   { return autoSlayEnabled; }
    public void setAutoSlayEnabled(boolean v)  { autoSlayEnabled = v; }
    public boolean isAutoCropEnabled()   { return autoCropEnabled; }
    public void setAutoCropEnabled(boolean v)  { autoCropEnabled = v; }
    public boolean isBlockNukerEnabled() { return blockNukerEnabled; }
    public void setBlockNukerEnabled(boolean v) { blockNukerEnabled = v; }
    public boolean isAutoFishEnabled() { return autoFishEnabled; }
    public void setAutoFishEnabled(boolean v) { autoFishEnabled = v; }
    public boolean isFairySoulFinderEnabled() { return fairySoulFinderEnabled; }
    public void setFairySoulFinderEnabled(boolean v) { fairySoulFinderEnabled = v; }
    public boolean isPlayerEspEnabled() { return playerEspEnabled; }
    public void setPlayerEspEnabled(boolean v) { playerEspEnabled = v; }
    public boolean isFriendListEnabled() { return friendListEnabled; }
    public void setFriendListEnabled(boolean v) { friendListEnabled = v; }
    public boolean isDebugToolsEnabled() { return debugToolsEnabled; }
    public void setDebugToolsEnabled(boolean v) { debugToolsEnabled = v; }
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
    public void setAutoFarmRange(double v){ autoFarmRange = Math.max(5.0, Math.min(100.0, v)); }
    public double getAutoMineRange()     { return autoMineRange; }
    public void setAutoMineRange(double v){ autoMineRange = Math.max(4.0, Math.min(48.0, v)); }
    public double getAttackRange()       { return attackRange; }
    public void setAttackRange(double v) { attackRange = Math.max(1.0, Math.min(10.0, v)); }
    public boolean isAutoFarmRequireLineOfSight() { return autoFarmRequireLineOfSight; }
    public void setAutoFarmRequireLineOfSight(boolean v) { autoFarmRequireLineOfSight = v; }
    public double getAutoFarmSwitchMargin() { return autoFarmSwitchMargin; }
    public void setAutoFarmSwitchMargin(double v) { autoFarmSwitchMargin = Math.max(0.0, Math.min(8.0, v)); }
    public AutoFarmPriority getAutoFarmPriority() { return autoFarmPriority; }
    public void setAutoFarmPriority(AutoFarmPriority v) {
        autoFarmPriority = (v == null) ? AutoFarmPriority.NEAREST : v;
    }
    public boolean isPathSmoothingEnabled() { return pathSmoothingEnabled; }
    public void setPathSmoothingEnabled(boolean v) { pathSmoothingEnabled = v; }
    public AutoFarmMode getAutoFarmMode() { return autoFarmMode; }
    public void setAutoFarmMode(AutoFarmMode v) {
        autoFarmMode = (v == null) ? AutoFarmMode.MOBS : v;
    }
    public CropType getCropType() { return cropType; }
    public void setCropType(CropType v) {
        cropType = (v == null) ? CropType.ALL : v;
    }
    public boolean isAutoFarmSilentCropAim() { return autoFarmSilentCropAim; }
    public void setAutoFarmSilentCropAim(boolean v) { autoFarmSilentCropAim = v; }
    public boolean isAutoFarmAutoTool() { return autoFarmAutoTool; }
    public void setAutoFarmAutoTool(boolean v) { autoFarmAutoTool = v; }
    public int getAutoFarmCropBlocksPerTick() { return autoFarmCropBlocksPerTick; }
    public void setAutoFarmCropBlocksPerTick(int v) { autoFarmCropBlocksPerTick = Math.max(1, Math.min(6, v)); }
    public boolean isAutoFarmReplant() { return autoFarmReplant; }
    public void setAutoFarmReplant(boolean v) { autoFarmReplant = v; }
    public JacobPreset getJacobPreset() { return jacobPreset; }
    public void setJacobPreset(JacobPreset v) {
        jacobPreset = (v == null) ? JacobPreset.BALANCED : v;
    }
    public AimProfile getAimProfile() { return aimProfile; }
    public void setAimProfile(AimProfile v) {
        aimProfile = (v == null) ? AimProfile.BALANCED : v;
    }

    public boolean isGardenLaneLoopEnabled() { return gardenLaneLoopEnabled; }
    public void setGardenLaneLoopEnabled(boolean v) { gardenLaneLoopEnabled = v; }
    public String getGardenLaneRouteName() { return gardenLaneRouteName; }
    public void setGardenLaneRouteName(String v) { gardenLaneRouteName = (v == null) ? "" : v.trim(); }
    public boolean isVisitorAlarmEnabled() { return visitorAlarmEnabled; }
    public void setVisitorAlarmEnabled(boolean v) { visitorAlarmEnabled = v; }
    public String getVisitorAlarmKeyword() { return visitorAlarmKeyword; }
    public void setVisitorAlarmKeyword(String v) {
        visitorAlarmKeyword = (v == null || v.isBlank()) ? "visitor" : v.trim().toLowerCase(java.util.Locale.ROOT);
    }
    public boolean isAutoPauseOnVisitor() { return autoPauseOnVisitor; }
    public void setAutoPauseOnVisitor(boolean v) { autoPauseOnVisitor = v; }
    public int getInventoryActionThresholdPct() { return inventoryActionThresholdPct; }
    public void setInventoryActionThresholdPct(int v) { inventoryActionThresholdPct = Math.max(50, Math.min(100, v)); }
    public boolean isInventoryAutoActionEnabled() { return inventoryAutoActionEnabled; }
    public void setInventoryAutoActionEnabled(boolean v) { inventoryAutoActionEnabled = v; }
    public int getInventoryActionCooldownSec() { return inventoryActionCooldownSec; }
    public void setInventoryActionCooldownSec(int v) { inventoryActionCooldownSec = Math.max(5, Math.min(300, v)); }
    public boolean isDesyncWatchdogEnabled() { return desyncWatchdogEnabled; }
    public void setDesyncWatchdogEnabled(boolean v) { desyncWatchdogEnabled = v; }
    public int getDesyncFailLimit() { return desyncFailLimit; }
    public void setDesyncFailLimit(int v) { desyncFailLimit = Math.max(3, Math.min(30, v)); }
    public boolean isFailsafeRandomPauseEnabled() { return failsafeRandomPauseEnabled; }
    public void setFailsafeRandomPauseEnabled(boolean v) { failsafeRandomPauseEnabled = v; }
    public boolean isFailsafeYawJitterEnabled() { return failsafeYawJitterEnabled; }
    public void setFailsafeYawJitterEnabled(boolean v) { failsafeYawJitterEnabled = v; }
    public int getFailsafePauseMinTicks() { return failsafePauseMinTicks; }
    public void setFailsafePauseMinTicks(int v) { failsafePauseMinTicks = Math.max(0, Math.min(20, v)); }
    public int getFailsafePauseMaxTicks() { return failsafePauseMaxTicks; }
    public void setFailsafePauseMaxTicks(int v) { failsafePauseMaxTicks = Math.max(failsafePauseMinTicks, Math.min(40, v)); }
    public boolean isWaypointChainEnabled() { return waypointChainEnabled; }
    public void setWaypointChainEnabled(boolean v) { waypointChainEnabled = v; }

    public double getWheatPrice() { return wheatPrice; }
    public void setWheatPrice(double v) { wheatPrice = Math.max(0.0, Math.min(1000.0, v)); }
    public double getCarrotPrice() { return carrotPrice; }
    public void setCarrotPrice(double v) { carrotPrice = Math.max(0.0, Math.min(1000.0, v)); }
    public double getPotatoPrice() { return potatoPrice; }
    public void setPotatoPrice(double v) { potatoPrice = Math.max(0.0, Math.min(1000.0, v)); }

    public void applyJacobPreset(JacobPreset preset) {
        setJacobPreset(preset);
        switch (jacobPreset) {
            case SAFE -> {
                setAutoFarmCropBlocksPerTick(1);
                setAutoFarmSilentCropAim(false);
                setAutoFarmAutoTool(true);
                setFailsafeRandomPauseEnabled(true);
                setFailsafeYawJitterEnabled(true);
            }
            case TURBO -> {
                setAutoFarmCropBlocksPerTick(5);
                setAutoFarmSilentCropAim(true);
                setAutoFarmAutoTool(true);
                setFailsafeRandomPauseEnabled(false);
            }
            case BALANCED -> {
                setAutoFarmCropBlocksPerTick(3);
                setAutoFarmSilentCropAim(true);
                setAutoFarmAutoTool(true);
                setFailsafeRandomPauseEnabled(true);
                setFailsafeYawJitterEnabled(true);
            }
        }
    }

    public String getCommandMacro1() { return commandMacro1; }
    public void setCommandMacro1(String v) { commandMacro1 = (v == null) ? "" : v.trim(); }
    public String getCommandMacro2() { return commandMacro2; }
    public void setCommandMacro2(String v) { commandMacro2 = (v == null) ? "" : v.trim(); }
    public String getCommandMacro3() { return commandMacro3; }
    public void setCommandMacro3(String v) { commandMacro3 = (v == null) ? "" : v.trim(); }

    public boolean isAutoFishKillEntity() { return autoFishKillEntity; }
    public void setAutoFishKillEntity(boolean v) { autoFishKillEntity = v; }
    public AutoFishKillMode getAutoFishKillMode() { return autoFishKillMode; }
    public void setAutoFishKillMode(AutoFishKillMode v) {
        autoFishKillMode = (v == null) ? AutoFishKillMode.CLOSE : v;
    }
    public double getAutoFishRange() { return autoFishRange; }
    public void setAutoFishRange(double v) { autoFishRange = Math.max(4.0, Math.min(40.0, v)); }

    public int getFairySoulScanRange() { return fairySoulScanRange; }
    public void setFairySoulScanRange(int v) { fairySoulScanRange = Math.max(16, Math.min(192, v)); }
    public boolean isRouteHeatmapEnabled() { return routeHeatmapEnabled; }
    public void setRouteHeatmapEnabled(boolean v) { routeHeatmapEnabled = v; }
    public int getRouteHeatmapProfileIndex() { return routeHeatmapProfileIndex; }
    public void setRouteHeatmapProfileIndex(int v) { routeHeatmapProfileIndex = Math.max(1, Math.min(5, v)); }
    public int getRouteHeatmapX() { return routeHeatmapX; }
    public void setRouteHeatmapX(int v) { routeHeatmapX = Math.max(0, Math.min(4000, v)); }
    public int getRouteHeatmapY() { return routeHeatmapY; }
    public void setRouteHeatmapY(int v) { routeHeatmapY = Math.max(0, Math.min(4000, v)); }
    public int getRouteHeatmapSize() { return routeHeatmapSize; }
    public void setRouteHeatmapSize(int v) { routeHeatmapSize = Math.max(80, Math.min(260, v)); }

    public boolean isHypixelStatsHudEnabled() { return hypixelStatsHudEnabled; }
    public void setHypixelStatsHudEnabled(boolean v) { hypixelStatsHudEnabled = v; }
    public int getHypixelStatsHudX() { return hypixelStatsHudX; }
    public void setHypixelStatsHudX(int v) { hypixelStatsHudX = Math.max(0, Math.min(4000, v)); }
    public int getHypixelStatsHudY() { return hypixelStatsHudY; }
    public void setHypixelStatsHudY(int v) { hypixelStatsHudY = Math.max(0, Math.min(4000, v)); }
    public float getHypixelStatsHudScale() { return hypixelStatsHudScale; }
    public void setHypixelStatsHudScale(float v) { hypixelStatsHudScale = Math.max(0.7f, Math.min(2.0f, v)); }
    public boolean isHypixelStatsShowFarming() { return hypixelStatsShowFarming; }
    public void setHypixelStatsShowFarming(boolean v) { hypixelStatsShowFarming = v; }
    public boolean isHypixelStatsShowWorth() { return hypixelStatsShowWorth; }
    public void setHypixelStatsShowWorth(boolean v) { hypixelStatsShowWorth = v; }
    public boolean isHypixelStatsShowSkills() { return hypixelStatsShowSkills; }
    public void setHypixelStatsShowSkills(boolean v) { hypixelStatsShowSkills = v; }

    public boolean isPlayerEspShowHealth() { return playerEspShowHealth; }
    public void setPlayerEspShowHealth(boolean v) { playerEspShowHealth = v; }
    public boolean isPlayerEspShowWeapon() { return playerEspShowWeapon; }
    public void setPlayerEspShowWeapon(boolean v) { playerEspShowWeapon = v; }
    public boolean isPlayerEspShowArmor() { return playerEspShowArmor; }
    public void setPlayerEspShowArmor(boolean v) { playerEspShowArmor = v; }
    public boolean isFriendListAutoNearby() { return friendListAutoNearby; }
    public void setFriendListAutoNearby(boolean v) { friendListAutoNearby = v; }
    public Set<String> getFriendNames() { return friendNames; }
    public boolean isFriend(String name) {
        return name != null && friendNames.contains(name.toLowerCase(java.util.Locale.ROOT));
    }
    public void addFriend(String name) {
        if (name != null && !name.isBlank()) friendNames.add(name.toLowerCase(java.util.Locale.ROOT));
    }
    public void removeFriend(String name) {
        if (name != null) friendNames.remove(name.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isDebugTargetBlockEnabled() { return debugTargetBlockEnabled; }
    public void setDebugTargetBlockEnabled(boolean v) { debugTargetBlockEnabled = v; }
    public boolean isDebugScanBlocksEnabled() { return debugScanBlocksEnabled; }
    public void setDebugScanBlocksEnabled(boolean v) { debugScanBlocksEnabled = v; }
    public boolean isDebugScanEntitiesEnabled() { return debugScanEntitiesEnabled; }
    public void setDebugScanEntitiesEnabled(boolean v) { debugScanEntitiesEnabled = v; }
    public int getDebugScanRange() { return debugScanRange; }
    public void setDebugScanRange(int v) { debugScanRange = Math.max(2, Math.min(32, v)); }

    public double getBlockNukerRange() { return blockNukerRange; }
    public void setBlockNukerRange(double v) { blockNukerRange = Math.max(1.0, Math.min(12.0, v)); }
    public boolean isBlockNukerWhitelistMode() { return blockNukerWhitelistMode; }
    public void setBlockNukerWhitelistMode(boolean v) { blockNukerWhitelistMode = v; }
    public Set<String> getBlockNukerWhitelist() { return blockNukerWhitelist; }
    public void addToBlockNukerWhitelist(String blockId) { blockNukerWhitelist.add(blockId); }
    public void removeFromBlockNukerWhitelist(String blockId) { blockNukerWhitelist.remove(blockId); }
    public void clearBlockNukerWhitelist() { blockNukerWhitelist.clear(); }
    public boolean isBlockNukerWhitelisted(String blockId) {
        return !blockNukerWhitelistMode || blockNukerWhitelist.contains(blockId);
    }
    public boolean isBlockNukerSilent() { return blockNukerSilent; }
    public void setBlockNukerSilent(boolean v) { blockNukerSilent = v; }
    public boolean isBlockNukerRequireLineOfSight() { return blockNukerRequireLineOfSight; }
    public void setBlockNukerRequireLineOfSight(boolean v) { blockNukerRequireLineOfSight = v; }
    public double getBlockNukerMaxHardness() { return blockNukerMaxHardness; }
    public void setBlockNukerMaxHardness(double v) { blockNukerMaxHardness = Math.max(0.0, Math.min(5.0, v)); }
    public int getBlockNukerBlocksPerTick() { return blockNukerBlocksPerTick; }
    public void setBlockNukerBlocksPerTick(int v) { blockNukerBlocksPerTick = Math.max(1, Math.min(8, v)); }
    public boolean isBlockNukerSkipBlockEntities() { return blockNukerSkipBlockEntities; }
    public void setBlockNukerSkipBlockEntities(boolean v) { blockNukerSkipBlockEntities = v; }

    public boolean isPreventLedgeFall() { return preventLedgeFall; }
    public void setPreventLedgeFall(boolean v) { preventLedgeFall = v; }
    public int getLedgeMaxDrop() { return ledgeMaxDrop; }
    public void setLedgeMaxDrop(int v) { ledgeMaxDrop = Math.max(0, Math.min(3, v)); }
    public boolean isInventoryHudEnabled() { return inventoryHudEnabled; }
    public void setInventoryHudEnabled(boolean v) { inventoryHudEnabled = v; }
    public int getInventoryHudX() { return inventoryHudX; }
    public void setInventoryHudX(int v) { inventoryHudX = Math.max(0, Math.min(4000, v)); }
    public int getInventoryHudY() { return inventoryHudY; }
    public void setInventoryHudY(int v) { inventoryHudY = Math.max(0, Math.min(4000, v)); }
    public float getInventoryHudScale() { return inventoryHudScale; }
    public void setInventoryHudScale(float v) { inventoryHudScale = Math.max(0.5f, Math.min(2.5f, v)); }

    public void applyBlockNukerPlantPreset() {
        // Keep a conservative PvE-focused preset for fast plot cleanup.
        blockNukerWhitelist.clear();
        blockNukerWhitelist.addAll(List.of(
            "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
            "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium",
            "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip",
            "minecraft:pink_tulip", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
            "minecraft:sunflower", "minecraft:rose_bush", "minecraft:peony", "minecraft:lilac",
            "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:dead_bush", "minecraft:vine"
        ));
    }

    public void syncBlockNukerWhitelistFromEnabledBlockEsp() {
        for (Map.Entry<String, EspEntry> e : blockFilters.entrySet()) {
            if (e.getValue().enabled) {
                blockNukerWhitelist.add(e.getKey());
            }
        }
    }

    public double getEntityEspRange()    { return entityEspRange; }
    public void setEntityEspRange(double v) { entityEspRange = Math.max(10.0, Math.min(200.0, v)); }
    public double getBlockEspRange()     { return blockEspRange; }
    public void setBlockEspRange(double v) { blockEspRange = Math.max(10.0, Math.min(200.0, v)); }

    public boolean isAutoFarmWhitelistMode() { return autoFarmWhitelistMode; }
    public void setAutoFarmWhitelistMode(boolean v) { autoFarmWhitelistMode = v; }
    public Set<String> getAutoFarmWhitelist() { return autoFarmWhitelist; }
    public void addToAutoFarmWhitelist(String entityId) { autoFarmWhitelist.add(entityId); }
    public void removeFromAutoFarmWhitelist(String entityId) { autoFarmWhitelist.remove(entityId); }
    public boolean isAutoFarmWhitelisted(String entityId) {
        return !autoFarmWhitelistMode || autoFarmWhitelist.contains(entityId);
    }

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
