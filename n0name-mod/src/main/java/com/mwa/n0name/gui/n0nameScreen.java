package com.mwa.n0name.gui;

import com.mwa.n0name.ModConfig;
import com.mwa.n0name.modules.PatchCreatorModule;
import com.mwa.n0name.modules.PathfinderDebugModule;
import com.mwa.n0name.modules.RouteHeatmapModule;
import com.mwa.n0name.modules.TimeLoggerModule;
import com.mwa.n0name.modules.WaypointManagerModule;
import com.mwa.n0name.render.BlockESP;
import com.mwa.n0name.render.EntityESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Taunahi+-style ClickGUI with sidebar categories and scrollable content panel.
 */
public class n0nameScreen extends Screen {

    // --- Module references ---
    private static PatchCreatorModule patchCreatorModule;
    private static WaypointManagerModule waypointManagerModule;
    private static TimeLoggerModule timeLoggerModule;
    private static RouteHeatmapModule routeHeatmapModule;
    private static PathfinderDebugModule pathfinderDebugModule;
    private static BlockESP blockEspModule;
    private static EntityESP entityEspModule;

    public static void setModuleReferences(PatchCreatorModule pc) { patchCreatorModule = pc; }
    public static void setModuleReferences(PatchCreatorModule pc, WaypointManagerModule wp, TimeLoggerModule tl) {
        patchCreatorModule = pc; waypointManagerModule = wp; timeLoggerModule = tl;
    }
    public static void setModuleReferences(PatchCreatorModule pc, WaypointManagerModule wp,
                                           TimeLoggerModule tl, RouteHeatmapModule rh) {
        patchCreatorModule = pc; waypointManagerModule = wp; timeLoggerModule = tl; routeHeatmapModule = rh;
    }
    public static void setModuleReferences(PatchCreatorModule pc, WaypointManagerModule wp,
                                           TimeLoggerModule tl, RouteHeatmapModule rh,
                                           BlockESP be, EntityESP ee,
                                           PathfinderDebugModule pd) {
        patchCreatorModule = pc;
        waypointManagerModule = wp;
        timeLoggerModule = tl;
        routeHeatmapModule = rh;
        blockEspModule = be;
        entityEspModule = ee;
        pathfinderDebugModule = pd;
    }

    // --- Category ---
    private enum Cat {
        FARMING("Farming",     0xFF55AA55),
        COMBAT("Combat",       0xFFFF5555),
        FISHING("Fishing",     0xFF5588FF),
        VISUAL("Visual",       0xFF55DDDD),
        UTILITIES("Utilities", 0xFFCCCC44),
        HYPIXEL("Hypixel",     0xFFFF8844),
        MISC("Misc",           0xFFAAAAAA);
        final String label; final int color;
        Cat(String l, int c) { label = l; color = c; }
    }

    // --- GUI state ---
    private Cat selectedCat = Cat.FARMING;
    private boolean dragging = false;
    private int dragOffX, dragOffY;
    private int panelX = -1, panelY = -1;
    private int contentScroll = 0;
    private final Map<String, Boolean> expanded = new HashMap<>();
    private final Map<String, Float> toggleAnims = new HashMap<>();
    private float panelSlide = 0f;
    private boolean friendSubmenuOpen = false;
    private int blockEspScroll = 0, entityEspScroll = 0, routeScroll = 0;
    private String routeNameInput = "route1";
    private String activeSlider = null;
    private int lastContentHeight = 0;

    // --- Dimensions ---
    private static final int SIDEBAR_W = 140;
    private static final int CONTENT_W = 380;
    private static final int TOTAL_W   = SIDEBAR_W + CONTENT_W;
    private static final int PANEL_H   = 420;
    private static final int HDR_H     = 30;
    private static final int CAT_H     = 26;
    private static final int ROW_H     = 22;
    private static final int SUB_H     = 18;
    private static final int PAD       = 8;
    private static final int MAX_VIS   = 8;

    // --- Dark theme ---
    private static final int C_BG       = 0xF0121218;
    private static final int C_SIDEBAR  = 0xF01A1A28;
    private static final int C_CONTENT  = 0xF0161622;
    private static final int C_HEADER   = 0xFF1A1A30;
    private static final int C_CARD     = 0xF01E1E32;
    private static final int C_CARD_HOV = 0x14FFFFFF;
    private static final int C_CAT_SEL  = 0xFF2A2A48;
    private static final int C_CAT_HOV  = 0xFF222238;
    private static final int C_BORDER   = 0xFF333350;
    private static final int C_TEXT     = 0xFFE0E0E8;
    private static final int C_MUTED    = 0xFF707088;
    private static final int C_ON       = 0xFF44FF44;
    private static final int C_OFF      = 0xFF666666;
    private static final int C_DOT_ON   = 0xFF44FF44;
    private static final int C_DOT_OFF  = 0xFF333344;
    private static final int C_BTN      = 0xFF2D2D50;
    private static final int C_BTN_ACT  = 0xFF4834D4;
    private static final int C_SUB_SEL  = 0x2433AAFF;
    private static final int C_DIM      = 0x80000000;
    private static final int C_ACCENT   = 0xFF66FF44;
    private static final int C_SUB_BG   = 0x08FFFFFF;

    public n0nameScreen() { super(Text.empty()); }

    @Override protected void init() {
        panelSlide = 0f;
        if (panelX < 0) { panelX = (width - TOTAL_W) / 2; panelY = (height - PANEL_H) / 2; }
    }

    @Override public boolean shouldPause() { return false; }

    /* =========================================================
     *  RENDER
     * ========================================================= */
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        panelSlide += (1f - panelSlide) * 0.18f;
        if (panelSlide > 0.998f) panelSlide = 1f;

        ModConfig cfg = ModConfig.getInstance();
        animateToggles(cfg);

        int slideOff = (int)((1f - panelSlide) * -40);
        int px = clamp(panelX, 0, width - TOTAL_W);
        int py = clamp(panelY + slideOff, 0, height - PANEL_H);

        ctx.fill(0, 0, width, height, C_DIM);
        ctx.fill(px, py, px + TOTAL_W, py + PANEL_H, C_BG);

        // header
        ctx.fill(px, py, px + TOTAL_W, py + HDR_H, C_HEADER);
        ctx.fill(px, py + HDR_H - 1, px + TOTAL_W, py + HDR_H, C_BORDER);
        float pulse = (float)(Math.sin(System.currentTimeMillis() / 800.0) * 0.3 + 0.7);
        ctx.fill(px, py, px + TOTAL_W, py + 2, ((int)(pulse * 255) << 24) | (C_ACCENT & 0x00FFFFFF));
        ctx.drawTextWithShadow(textRenderer, "n0name", px + 12, py + 10, C_ACCENT);
        ctx.drawTextWithShadow(textRenderer, "v2.0", px + 12 + textRenderer.getWidth("n0name") + 6, py + 10, C_MUTED);
        ctx.drawTextWithShadow(textRenderer, "\u00D7", px + TOTAL_W - 16, py + 10, C_MUTED);

        // sidebar
        int sy = py + HDR_H;
        ctx.fill(px, sy, px + SIDEBAR_W, py + PANEL_H, C_SIDEBAR);
        ctx.fill(px + SIDEBAR_W - 1, sy, px + SIDEBAR_W, py + PANEL_H, C_BORDER);
        int catY = sy + 8;
        for (Cat c : Cat.values()) {
            boolean sel = c == selectedCat;
            boolean hov = mx >= px && mx < px + SIDEBAR_W && my >= catY && my < catY + CAT_H;
            if (sel)      ctx.fill(px, catY, px + SIDEBAR_W - 1, catY + CAT_H, C_CAT_SEL);
            else if (hov) ctx.fill(px, catY, px + SIDEBAR_W - 1, catY + CAT_H, C_CAT_HOV);
            if (sel)      ctx.fill(px, catY, px + 3, catY + CAT_H, c.color);
            ctx.drawTextWithShadow(textRenderer, c.label, px + 14, catY + (CAT_H - 8) / 2,
                sel ? c.color : (hov ? C_TEXT : C_MUTED));
            catY += CAT_H;
        }

        // content
        int cx = px + SIDEBAR_W, cy = sy, cw = CONTENT_W, ch = PANEL_H - HDR_H;
        ctx.fill(cx, cy, cx + cw, cy + ch, C_CONTENT);
        ctx.enableScissor(cx, cy + 4, cx + cw, cy + ch - 4);
        int contentH = renderContent(ctx, cx, cy + 4 - contentScroll, cw, mx, my, cfg);
        lastContentHeight = contentH;
        contentScroll = clamp(contentScroll, 0, maxContentScroll());
        ctx.disableScissor();

        // scrollbar
        int visH = ch - 8;
        if (contentH > visH) {
            int maxS = contentH - visH;
            float pct = (float) contentScroll / maxS;
            int bH = Math.max(20, visH * visH / contentH);
            int bY = cy + 4 + (int)(pct * (visH - bH));
            ctx.fill(cx + cw - 4, bY, cx + cw - 1, bY + bH, 0x40FFFFFF);
        }

        // outer border
        ctx.fill(px, py, px + 1, py + PANEL_H, C_BORDER);
        ctx.fill(px + TOTAL_W - 1, py, px + TOTAL_W, py + PANEL_H, C_BORDER);
        ctx.fill(px, py + PANEL_H - 1, px + TOTAL_W, py + PANEL_H, C_BORDER);
    }

    private int renderContent(DrawContext ctx, int cx, int cy, int cw, int mx, int my, ModConfig cfg) {
        if (friendSubmenuOpen) return renderFriendSubmenu(ctx, cx, cy, cw, mx, my, cfg);
        return switch (selectedCat) {
            case FARMING   -> renderFarming(ctx, cx, cy, cw, mx, my, cfg);
            case COMBAT    -> renderCombat(ctx, cx, cy, cw, mx, my, cfg);
            case FISHING   -> renderFishing(ctx, cx, cy, cw, mx, my, cfg);
            case VISUAL    -> renderVisual(ctx, cx, cy, cw, mx, my, cfg);
            case UTILITIES -> renderUtilities(ctx, cx, cy, cw, mx, my, cfg);
            case HYPIXEL   -> renderHypixel(ctx, cx, cy, cw, mx, my, cfg);
            case MISC      -> renderMisc(ctx, cx, cy, cw, mx, my, cfg);
        };
    }

    /* =========================================================
     *  FARMING
     * ========================================================= */
    private int renderFarming(DrawContext ctx, int cx, int cy, int cw, int mx, int my, ModConfig cfg) {
        int y = cy + PAD;
        y = sectionTitle(ctx, cx, y, cw, "Crop Farming");
        y = modRow(ctx, cx, y, cw, mx, my, "AutoCrop", anim("autocrop"), Cat.FARMING.color);
        if (ex("autocrop")) {
            String ct = switch (cfg.getCropType()) { case WHEAT->"Wheat"; case CARROT->"Carrot"; case POTATO->"Potato"; case ALL->"All"; };
            y = cycleRow(ctx, cx, y, cw, mx, my, "Crop Type", ct);
            y = checkRow(ctx, cx, y, cw, mx, my, "Silent Aim",   cfg.isAutoFarmSilentCropAim());
            y = checkRow(ctx, cx, y, cw, mx, my, "Auto Tool",    cfg.isAutoFarmAutoTool());
            y = checkRow(ctx, cx, y, cw, mx, my, "Replant",      cfg.isAutoFarmReplant());
            y = valRow(ctx, cx, y, cw, mx, my, "Blocks/Tick", String.valueOf(cfg.getAutoFarmCropBlocksPerTick()));
            y = cycleRow(ctx, cx, y, cw, mx, my, "Jacob Preset", cfg.getJacobPreset().name());
            y = checkRow(ctx, cx, y, cw, mx, my, "Failsafe Pause", cfg.isFailsafeRandomPauseEnabled());
            y = checkRow(ctx, cx, y, cw, mx, my, "Yaw Jitter",     cfg.isFailsafeYawJitterEnabled());
            y = checkRow(ctx, cx, y, cw, mx, my, "Desync Watchdog", cfg.isDesyncWatchdogEnabled());
        }

        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "Block Farming");
        y = modRow(ctx, cx, y, cw, mx, my, "AutoMine", anim("automine"), Cat.FARMING.color);
        if (ex("automine")) {
            y = sliderRow(ctx, cx, y, cw, mx, my, "Mine Range", cfg.getAutoMineRange(), 4.0, 32.0, 0);
            y = infoRow(ctx, cx, y, cw, "Whitelist: enabled Block ESP filters");
        }
        return y + PAD - cy;
    }

    /* =========================================================
     *  COMBAT
     * ========================================================= */
    private int renderCombat(DrawContext ctx, int cx, int cy, int cw, int mx, int my, ModConfig cfg) {
        int y = cy + PAD;
        y = sectionTitle(ctx, cx, y, cw, "Mob Combat");
        y = modRow(ctx, cx, y, cw, mx, my, "AutoSlay", anim("autoslay"), Cat.COMBAT.color);
        if (ex("autoslay")) {
            String mode = cfg.isCpsMode() ? "CPS: " + cfg.getTargetCps() : "Cooldown";
            y = cycleRow(ctx, cx, y, cw, mx, my, "Attack Mode", mode);
            y = sliderRow(ctx, cx, y, cw, mx, my, "Scan Range", cfg.getAutoFarmRange(), 6.0, 48.0, 0);
            y = sliderRow(ctx, cx, y, cw, mx, my, "Attack Range", cfg.getAttackRange(), 1.5, 6.0, 1);
            y = valRow(ctx, cx, y, cw, mx, my, "Sticky Margin", fmt1(cfg.getAutoFarmSwitchMargin()) + "b");
            String p = switch (cfg.getAutoFarmPriority()) { case LOWEST_HEALTH->"Lowest HP"; case HIGHEST_THREAT->"Highest Threat"; default->"Nearest"; };
            y = cycleRow(ctx, cx, y, cw, mx, my, "Priority", p);
            y = checkRow(ctx, cx, y, cw, mx, my, "Require LoS",    cfg.isAutoFarmRequireLineOfSight());
            y = checkRow(ctx, cx, y, cw, mx, my, "Path Smoothing", cfg.isPathSmoothingEnabled());
            y = checkRow(ctx, cx, y, cw, mx, my, "Whitelist Mode", cfg.isAutoFarmWhitelistMode());
        }
        return y + PAD - cy;
    }

    /* =========================================================
     *  FISHING
     * ========================================================= */
    private int renderFishing(DrawContext ctx, int cx, int cy, int cw, int mx, int my, ModConfig cfg) {
        int y = cy + PAD;
        y = sectionTitle(ctx, cx, y, cw, "Auto Fishing");
        y = modRow(ctx, cx, y, cw, mx, my, "AutoFish", anim("autofish"), Cat.FISHING.color);
        if (ex("autofish")) {
            y = cycleRow(ctx, cx, y, cw, mx, my, "Kill Mode", cfg.getAutoFishKillMode().name());
            y = checkRow(ctx, cx, y, cw, mx, my, "Kill Entity", cfg.isAutoFishKillEntity());
            y = sliderRow(ctx, cx, y, cw, mx, my, "Fish Range", cfg.getAutoFishRange(), 4.0, 40.0, 0);
        }
        return y + PAD - cy;
    }

    /* =========================================================
     *  VISUAL
     * ========================================================= */
    private int renderVisual(DrawContext ctx, int cx, int cy, int cw, int mx, int my, ModConfig cfg) {
        int y = cy + PAD;
        y = sectionTitle(ctx, cx, y, cw, "ESP Overlays");

        // Block ESP
        y = modRow(ctx, cx, y, cw, mx, my, "Block ESP", anim("besp"), Cat.VISUAL.color);
        if (ex("besp")) {
            y = sliderRow(ctx, cx, y, cw, mx, my, "Range", cfg.getBlockEspRange(), 8.0, 128.0, 0);
            y = espList(ctx, cx, y, cw, mx, my, getVisibleBlockIds(cfg),
                cfg.getBlockFilters(), blockEspScroll, "block");
        }

        // Entity ESP
        y = modRow(ctx, cx, y, cw, mx, my, "Entity ESP", anim("eesp"), Cat.VISUAL.color);
        if (ex("eesp")) {
            y = sliderRow(ctx, cx, y, cw, mx, my, "Range", cfg.getEntityEspRange(), 8.0, 160.0, 0);
            y = espList(ctx, cx, y, cw, mx, my, getVisibleEntityIds(cfg),
                cfg.getEntityFilters(), entityEspScroll, "entity");
        }

        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "Player Overlay");

        // Player ESP with individual checkboxes
        y = modRow(ctx, cx, y, cw, mx, my, "Player ESP", anim("playeresp"), Cat.VISUAL.color);
        if (ex("playeresp")) {
            y = checkRow(ctx, cx, y, cw, mx, my, "Show Health", cfg.isPlayerEspShowHealth());
            y = checkRow(ctx, cx, y, cw, mx, my, "Show Weapon", cfg.isPlayerEspShowWeapon());
            y = checkRow(ctx, cx, y, cw, mx, my, "Show Armor",  cfg.isPlayerEspShowArmor());
        }

        // Friend List
        y = modRow(ctx, cx, y, cw, mx, my, "Friend List", anim("friends"), Cat.VISUAL.color);
        if (ex("friends")) {
            y = checkRow(ctx, cx, y, cw, mx, my, "Auto Nearby", cfg.isFriendListAutoNearby());
            y = btnRow(ctx, cx, y, cw, mx, my, "View Nearby Players (50b)");
        }
        return y + PAD - cy;
    }

    /* =========================================================
     *  UTILITIES
     * ========================================================= */
    private int renderUtilities(DrawContext ctx, int cx, int cy, int cw, int mx, int my, ModConfig cfg) {
        int y = cy + PAD;

        // BlockNuker
        y = sectionTitle(ctx, cx, y, cw, "Block Tools");
        y = modRow(ctx, cx, y, cw, mx, my, "Nuker", anim("nuker"), Cat.UTILITIES.color);
        if (ex("nuker")) {
            y = sliderRow(ctx, cx, y, cw, mx, my, "Range", cfg.getBlockNukerRange(), 1.0, 12.0, 1);
            y = checkRowSelected(ctx, cx, y, cw, mx, my, "Whitelist Only", cfg.isBlockNukerWhitelistMode());
            y = btnRow(ctx, cx, y, cw, mx, my, "Plant Preset");
            y = btnRow(ctx, cx, y, cw, mx, my, "From ESP");
            y = btnRow(ctx, cx, y, cw, mx, my, "Clear Whitelist");
            y = checkRowSelected(ctx, cx, y, cw, mx, my, "Silent",      cfg.isBlockNukerSilent());
            y = checkRowSelected(ctx, cx, y, cw, mx, my, "Require LoS", cfg.isBlockNukerRequireLineOfSight());
            y = valRow(ctx, cx, y, cw, mx, my, "Max Hardness", fmt1(cfg.getBlockNukerMaxHardness()));
            y = valRow(ctx, cx, y, cw, mx, my, "Blocks/Tick",  String.valueOf(cfg.getBlockNukerBlocksPerTick()));
            y = checkRowSelected(ctx, cx, y, cw, mx, my, "Safe Tiles",  cfg.isBlockNukerSkipBlockEntities());
        }

        // Edge Guard
        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "Safety");
        y = modRow(ctx, cx, y, cw, mx, my, "Edge Guard", cfg.isPreventLedgeFall() ? 1f : 0f, Cat.UTILITIES.color);
        if (ex("edgeguard")) {
            y = valRow(ctx, cx, y, cw, mx, my, "Max Drop", cfg.getLedgeMaxDrop() + "b");
        }

        // PatchCreator / Routes
        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "Routes");
        boolean rec = patchCreatorModule != null && patchCreatorModule.isRecording();
        y = modRow(ctx, cx, y, cw, mx, my, "PatchCreator", rec ? 1f : 0f, Cat.UTILITIES.color);
        if (ex("patchcreator")) {
            y = btnRow(ctx, cx, y, cw, mx, my, rec ? "\u25CF Recording..." : "Record");
            y = btnRow(ctx, cx, y, cw, mx, my, "Stop");
            y = btnRow(ctx, cx, y, cw, mx, my, "Save");
            // route name input
            int left = cx + PAD + 12, right = cx + cw - PAD;
            ctx.fill(left, y, right, y + SUB_H, 0xFF1A1A2E);
            ctx.fill(left, y, left + 2, y + SUB_H, C_ACCENT);
            ctx.drawTextWithShadow(textRenderer, routeNameInput + "_", left + 8, y + 4, C_TEXT);
            y += SUB_H + 2;
            // route list
            y = routeList(ctx, cx, y, cw, mx, my, cfg.getRouteNames());
        }

        // Inventory HUD
        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "HUD");
        y = modRow(ctx, cx, y, cw, mx, my, "Inventory HUD", cfg.isInventoryHudEnabled() ? 1f : 0f, Cat.UTILITIES.color);
        if (ex("inventoryhud")) {
            y = valRow(ctx, cx, y, cw, mx, my, "Pos X", String.valueOf(cfg.getInventoryHudX()));
            y = valRow(ctx, cx, y, cw, mx, my, "Pos Y", String.valueOf(cfg.getInventoryHudY()));
            y = valRow(ctx, cx, y, cw, mx, my, "Scale", fmt2(cfg.getInventoryHudScale()));
        }
        return y + PAD - cy;
    }

    /* =========================================================
     *  HYPIXEL
     * ========================================================= */
    private int renderHypixel(DrawContext ctx, int cx, int cy, int cw, int mx, int my, ModConfig cfg) {
        int y = cy + PAD;

        y = sectionTitle(ctx, cx, y, cw, "Fairy Souls & Heatmap");
        y = modRow(ctx, cx, y, cw, mx, my, "Fairy Soul Finder", anim("fairy"), Cat.HYPIXEL.color);
        if (ex("fairy")) {
            y = sliderRow(ctx, cx, y, cw, mx, my, "Scan Range", cfg.getFairySoulScanRange(), 16.0, 192.0, 0);
        }
        y = modRow(ctx, cx, y, cw, mx, my, "Route Heatmap", anim("heatmap"), Cat.HYPIXEL.color);
        if (ex("heatmap")) {
            y = valRow(ctx, cx, y, cw, mx, my, "Profile", "P" + cfg.getRouteHeatmapProfileIndex());
            y = btnRow(ctx, cx, y, cw, mx, my, "Clear Current Profile");
            y = valRow(ctx, cx, y, cw, mx, my, "Heatmap Size", String.valueOf(cfg.getRouteHeatmapSize()));
        }

        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "Stats HUD");
        y = modRow(ctx, cx, y, cw, mx, my, "Stats HUD", anim("statshud"), Cat.HYPIXEL.color);
        if (ex("statshud")) {
            y = valRow(ctx, cx, y, cw, mx, my, "Pos X", String.valueOf(Math.max(0, cfg.getHypixelStatsHudX())));
            y = valRow(ctx, cx, y, cw, mx, my, "Pos Y", String.valueOf(cfg.getHypixelStatsHudY()));
            y = valRow(ctx, cx, y, cw, mx, my, "Scale", fmt2(cfg.getHypixelStatsHudScale()));
            y = checkRow(ctx, cx, y, cw, mx, my, "Show Farming", cfg.isHypixelStatsShowFarming());
            y = checkRow(ctx, cx, y, cw, mx, my, "Show Worth",   cfg.isHypixelStatsShowWorth());
            y = checkRow(ctx, cx, y, cw, mx, my, "Show Skills",  cfg.isHypixelStatsShowSkills());
        }

        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "Skyblock Settings");
        y = checkRow(ctx, cx, y, cw, mx, my, "Garden Lane Loop",  cfg.isGardenLaneLoopEnabled());
        y = checkRow(ctx, cx, y, cw, mx, my, "Visitor Alarm",     cfg.isVisitorAlarmEnabled());
        y = checkRow(ctx, cx, y, cw, mx, my, "Pause On Visitor",  cfg.isAutoPauseOnVisitor());
        y = checkRow(ctx, cx, y, cw, mx, my, "Waypoint Chain",    cfg.isWaypointChainEnabled());
        y = checkRow(ctx, cx, y, cw, mx, my, "Inv Auto Action",   cfg.isInventoryAutoActionEnabled());
        return y + PAD - cy;
    }

    /* =========================================================
     *  MISC
     * ========================================================= */
    private int renderMisc(DrawContext ctx, int cx, int cy, int cw, int mx, int my, ModConfig cfg) {
        int y = cy + PAD;

        y = sectionTitle(ctx, cx, y, cw, "Movement");
        y = modRow(ctx, cx, y, cw, mx, my, "Anti-AFK", anim("afk"), Cat.MISC.color);

        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "Debug & System");
        y = modRow(ctx, cx, y, cw, mx, my, "Debug Tools", anim("dbgtools"), Cat.MISC.color);
        if (ex("dbgtools")) {
            y = checkRow(ctx, cx, y, cw, mx, my, "Target Block",   cfg.isDebugTargetBlockEnabled());
            y = checkRow(ctx, cx, y, cw, mx, my, "Scan Blocks",    cfg.isDebugScanBlocksEnabled());
            y = checkRow(ctx, cx, y, cw, mx, my, "Scan Entities",  cfg.isDebugScanEntitiesEnabled());
            y = sliderRow(ctx, cx, y, cw, mx, my, "Scan Range", cfg.getDebugScanRange(), 2.0, 32.0, 0);
        }
        y = modRow(ctx, cx, y, cw, mx, my, "Debug Log", anim("debug"), Cat.MISC.color);

        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "Pathfinder Debug");
        y = btnRow(ctx, cx, y, cw, mx, my, "Set Start Position");
        y = btnRow(ctx, cx, y, cw, mx, my, "Set Stop Position");
        y = btnRow(ctx, cx, y, cw, mx, my, "Go");
        y = btnRow(ctx, cx, y, cw, mx, my, "Stop");
        y = btnRow(ctx, cx, y, cw, mx, my, "Clear Start/Stop");
        if (pathfinderDebugModule != null) {
            y = cycleRow(ctx, cx, y, cw, mx, my, "Log Mode", pathfinderDebugModule.getVerbosityText().replace("Verbosity: ", ""));
            y = checkRow(ctx, cx, y, cw, mx, my, "Snap To Walkable", pathfinderDebugModule.isSnapToNearestWalkable());
            y = infoRow(ctx, cx, y, cw, pathfinderDebugModule.getStartText());
            y = infoRow(ctx, cx, y, cw, pathfinderDebugModule.getStopText());
            y = infoRow(ctx, cx, y, cw, pathfinderDebugModule.getStatusText());
        }

        y += 4;
        y = sectionTitle(ctx, cx, y, cw, "Info & Hotkeys");
        if (waypointManagerModule != null) {
            y = infoRow(ctx, cx, y, cw, waypointManagerModule.getSlotText(1) + " | Shift=WP2 Alt=WP3");
        }
        if (timeLoggerModule != null) {
            y = infoRow(ctx, cx, y, cw, timeLoggerModule.getSummaryText());
        }
        y = infoRow(ctx, cx, y, cw, "Macros: F6/F7/F8 | WP: F9/F10");
        y = infoRow(ctx, cx, y, cw, "Crop: F1 Silent | F2 Tool | F3 Mode | F4 Type");
        y = infoRow(ctx, cx, y, cw, "F5 Jacob | F11 Lane | F12 WP Chain");
        return y + PAD - cy;
    }

    /* =========================================================
     *  FRIEND SUBMENU
     * ========================================================= */
    private int renderFriendSubmenu(DrawContext ctx, int cx, int cy, int cw, int mx, int my, ModConfig cfg) {
        int y = cy + PAD;
        y = sectionTitle(ctx, cx, y, cw, "Nearby Players (50b)");
        y = btnRow(ctx, cx, y, cw, mx, my, "\u2190 Back to Visual");

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null) {
            for (PlayerEntity pl : client.world.getPlayers()) {
                if (pl == client.player) continue;
                if (pl.squaredDistanceTo(client.player) > 2500.0) continue;
                String name = pl.getName().getString();
                boolean fr = cfg.isFriend(name);
                double dist = Math.sqrt(pl.squaredDistanceTo(client.player));

                int left = cx + PAD, right = cx + cw - PAD;
                ctx.fill(left, y, right, y + ROW_H, C_CARD);
                if (mx >= left && mx < right && my >= y && my < y + ROW_H)
                    ctx.fill(left, y, right, y + ROW_H, C_CARD_HOV);
                int mid = y + ROW_H / 2;
                ctx.fill(left + 6, mid - 3, left + 12, mid + 3, fr ? C_DOT_ON : C_DOT_OFF);
                ctx.drawTextWithShadow(textRenderer,
                    name + " (" + fmt0(dist) + "b)", left + 18, mid - 4, fr ? C_ON : C_TEXT);

                String bt = fr ? "Remove" : "Add";
                int bc = fr ? 0xFF884444 : C_BTN_ACT;
                int bx = right - 50;
                ctx.fill(bx, y + 3, bx + 42, y + ROW_H - 3, bc);
                ctx.drawTextWithShadow(textRenderer, bt, bx + 6, mid - 4, C_TEXT);
                y += ROW_H + 2;
            }
        }
        return y + PAD - cy;
    }

    /* =========================================================
     *  DRAW HELPERS
     * ========================================================= */
    private int sectionTitle(DrawContext ctx, int cx, int y, int cw, String title) {
        ctx.drawTextWithShadow(textRenderer, title, cx + PAD + 4, y + 2, C_MUTED);
        ctx.fill(cx + PAD, y + 14, cx + cw - PAD, y + 15, 0x18FFFFFF);
        return y + 18;
    }

    private int modRow(DrawContext ctx, int cx, int y, int cw, int mx, int my,
                        String name, float tp, int catColor) {
        int left = cx + PAD, right = cx + cw - PAD;
        ctx.fill(left, y, right, y + ROW_H, C_CARD);
        if (mx >= left && mx < right && my >= y && my < y + ROW_H)
            ctx.fill(left, y, right, y + ROW_H, C_CARD_HOV);
        ctx.fill(left, y + 2, left + 3, y + ROW_H - 2, catColor);
        int mid = y + ROW_H / 2;
        int dc = lerp(C_DOT_OFF, C_DOT_ON, tp);
        int ds = 3 + (int)(tp * 2);
        ctx.fill(left + 10, mid - ds / 2, left + 10 + ds, mid + ds / 2 + 1, dc);
        ctx.drawTextWithShadow(textRenderer, name, left + 18, mid - 4, lerp(C_OFF, C_TEXT, Math.max(0.3f, tp)));
        String st = tp > 0.5f ? "ON" : "OFF";
        ctx.drawTextWithShadow(textRenderer, st, right - 40, mid - 4, lerp(C_OFF, C_ON, tp));
        String key = toKey(name);
        ctx.drawTextWithShadow(textRenderer, ex(key) ? "\u25B4" : "\u25BE", right - 14, mid - 4, C_MUTED);
        ctx.fill(left + 4, y + ROW_H - 1, right - 4, y + ROW_H, 0x08FFFFFF);
        return y + ROW_H;
    }

    private int checkRow(DrawContext ctx, int cx, int y, int cw, int mx, int my, String label, boolean on) {
        int left = cx + PAD + 12, right = cx + cw - PAD;
        ctx.fill(left, y, right, y + SUB_H, C_SUB_BG);
        if (mx >= left && mx < right && my >= y && my < y + SUB_H)
            ctx.fill(left, y, right, y + SUB_H, C_CARD_HOV);
        int mid = y + SUB_H / 2;
        int cbx = left + 6;
        ctx.fill(cbx, mid - 4, cbx + 10, mid + 4, C_BORDER);
        if (on) ctx.fill(cbx + 1, mid - 3, cbx + 9, mid + 3, C_ON);
        ctx.drawTextWithShadow(textRenderer, label, cbx + 16, mid - 4, on ? C_TEXT : C_MUTED);
        return y + SUB_H;
    }

    private int checkRowSelected(DrawContext ctx, int cx, int y, int cw, int mx, int my, String label, boolean on) {
        int left = cx + PAD + 12, right = cx + cw - PAD;
        ctx.fill(left, y, right, y + SUB_H, on ? C_SUB_SEL : C_SUB_BG);
        if (mx >= left && mx < right && my >= y && my < y + SUB_H)
            ctx.fill(left, y, right, y + SUB_H, C_CARD_HOV);
        int mid = y + SUB_H / 2;
        int cbx = left + 6;
        ctx.fill(cbx, mid - 4, cbx + 10, mid + 4, C_BORDER);
        if (on) ctx.fill(cbx + 1, mid - 3, cbx + 9, mid + 3, C_ON);
        ctx.drawTextWithShadow(textRenderer, label, cbx + 16, mid - 4, on ? C_TEXT : C_MUTED);
        return y + SUB_H;
    }

    private int valRow(DrawContext ctx, int cx, int y, int cw, int mx, int my, String label, String val) {
        int left = cx + PAD + 12, right = cx + cw - PAD;
        ctx.fill(left, y, right, y + SUB_H, C_SUB_BG);
        if (mx >= left && mx < right && my >= y && my < y + SUB_H)
            ctx.fill(left, y, right, y + SUB_H, C_CARD_HOV);
        int mid = y + SUB_H / 2;
        ctx.drawTextWithShadow(textRenderer, label + ": " + val, left + 8, mid - 4, C_TEXT);
        int bx = right - 34;
        ctx.fill(bx, y + 2, bx + 14, y + SUB_H - 2, C_BTN);
        ctx.drawTextWithShadow(textRenderer, "-", bx + 4, mid - 4, C_MUTED);
        ctx.fill(bx + 17, y + 2, bx + 31, y + SUB_H - 2, C_BTN);
        ctx.drawTextWithShadow(textRenderer, "+", bx + 21, mid - 4, C_MUTED);
        return y + SUB_H;
    }

    private int sliderRow(DrawContext ctx, int cx, int y, int cw, int mx, int my,
                          String label, double value, double min, double max, int decimals) {
        int left = cx + PAD + 12, right = cx + cw - PAD;
        ctx.fill(left, y, right, y + SUB_H, C_SUB_BG);
        if (mx >= left && mx < right && my >= y && my < y + SUB_H)
            ctx.fill(left, y, right, y + SUB_H, C_CARD_HOV);
        int mid = y + SUB_H / 2;
        ctx.drawTextWithShadow(textRenderer, label, left + 8, mid - 4, C_TEXT);

        String valueText = fmtSlider(value, decimals) + "b";
        int valueWidth = textRenderer.getWidth(valueText);
        int trackLeft = left + 110;
        int trackRight = right - valueWidth - 18;
        int trackTop = mid - 2;
        int trackBottom = mid + 2;
        float t = (float) ((value - min) / Math.max(0.0001, max - min));
        t = Math.max(0.0f, Math.min(1.0f, t));
        int knobX = trackLeft + (int) ((trackRight - trackLeft) * t);

        ctx.fill(trackLeft, trackTop, trackRight, trackBottom, C_BORDER);
        ctx.fill(trackLeft, trackTop, knobX, trackBottom, C_BTN_ACT);
        ctx.fill(knobX - 3, mid - 5, knobX + 3, mid + 5, C_ACCENT);
        ctx.drawTextWithShadow(textRenderer, valueText, right - valueWidth - 8, mid - 4, C_MUTED);
        return y + SUB_H;
    }

    private int cycleRow(DrawContext ctx, int cx, int y, int cw, int mx, int my, String label, String val) {
        int left = cx + PAD + 12, right = cx + cw - PAD;
        ctx.fill(left, y, right, y + SUB_H, C_SUB_BG);
        if (mx >= left && mx < right && my >= y && my < y + SUB_H)
            ctx.fill(left, y, right, y + SUB_H, C_CARD_HOV);
        int mid = y + SUB_H / 2;
        ctx.drawTextWithShadow(textRenderer, label + ": " + val, left + 8, mid - 4, C_TEXT);
        int bx = right - 34;
        ctx.fill(bx, y + 2, bx + 14, y + SUB_H - 2, C_BTN);
        ctx.drawTextWithShadow(textRenderer, "<", bx + 4, mid - 4, C_MUTED);
        ctx.fill(bx + 17, y + 2, bx + 31, y + SUB_H - 2, C_BTN);
        ctx.drawTextWithShadow(textRenderer, ">", bx + 21, mid - 4, C_MUTED);
        return y + SUB_H;
    }

    private int btnRow(DrawContext ctx, int cx, int y, int cw, int mx, int my, String label) {
        int left = cx + PAD + 12, right = cx + cw - PAD;
        boolean hov = mx >= left && mx < right && my >= y && my < y + SUB_H;
        ctx.fill(left, y, right, y + SUB_H, hov ? C_BTN_ACT : C_BTN);
        ctx.drawTextWithShadow(textRenderer, label, left + 8, y + 4, C_TEXT);
        return y + SUB_H + 2;
    }

    private int infoRow(DrawContext ctx, int cx, int y, int cw, String text) {
        ctx.fill(cx + PAD + 12, y, cx + cw - PAD, y + SUB_H, C_SUB_BG);
        ctx.drawTextWithShadow(textRenderer, text, cx + PAD + 18, y + 4, C_MUTED);
        return y + SUB_H;
    }

    private int espList(DrawContext ctx, int cx, int y, int cw, int mx, int my,
                         List<String> list, Map<String, ModConfig.EspEntry> filters, int scroll, String kind) {
        int left = cx + PAD + 12, right = cx + cw - PAD;
        if (list.isEmpty()) {
            ctx.fill(left, y, right, y + SUB_H, C_SUB_BG);
            ctx.drawTextWithShadow(textRenderer, "No " + kind + " detected", left + 8, y + 4, C_MUTED);
            return y + SUB_H;
        }
        for (String id : list) {
            ModConfig.EspEntry e = filters.get(id);
            if (e == null) { y += SUB_H; continue; }
            ctx.fill(left, y, right, y + SUB_H, C_SUB_BG);
            if (mx >= left && mx < right && my >= y && my < y + SUB_H)
                ctx.fill(left, y, right, y + SUB_H, C_CARD_HOV);
            int mid = y + SUB_H / 2;
            int cbx = left + 6;
            ctx.fill(cbx, mid - 4, cbx + 10, mid + 4, C_BORDER);
            if (e.enabled) ctx.fill(cbx + 1, mid - 3, cbx + 9, mid + 3, C_ON);
            String sn = id.contains(":") ? id.split(":")[1] : id;
            if (sn.length() > 24) sn = sn.substring(0, 24) + "..";
            ctx.drawTextWithShadow(textRenderer, sn, cbx + 16, mid - 4, e.enabled ? C_TEXT : C_MUTED);
            int sw = right - 20;
            ctx.fill(sw - 1, y + 2, sw + 13, y + SUB_H - 2, C_BORDER);
            ctx.fill(sw, y + 3, sw + 12, y + SUB_H - 3, e.color | 0xFF000000);
            y += SUB_H;
        }
        return y;
    }

    private int routeList(DrawContext ctx, int cx, int y, int cw, int mx, int my, List<String> routes) {
        int left = cx + PAD + 12, right = cx + cw - PAD;
        if (routes.isEmpty()) {
            ctx.fill(left, y, right, y + SUB_H, C_SUB_BG);
            ctx.drawTextWithShadow(textRenderer, "No saved routes", left + 8, y + 4, C_MUTED);
            return y + SUB_H;
        }
        int count = Math.min(routes.size() - routeScroll, MAX_VIS);
        for (int i = 0; i < count; i++) {
            int idx = routeScroll + i;
            if (idx >= routes.size()) break;
            String n = routes.get(idx);
            ctx.fill(left, y, right, y + SUB_H, C_SUB_BG);
            if (mx >= left && mx < right && my >= y && my < y + SUB_H)
                ctx.fill(left, y, right, y + SUB_H, C_CARD_HOV);
            int mid = y + SUB_H / 2;
            ctx.drawTextWithShadow(textRenderer, n, left + 8, mid - 4, C_TEXT);
            int rbx = right - 44;
            ctx.fill(rbx, y + 2, rbx + 18, y + SUB_H - 2, C_BTN_ACT);
            ctx.drawTextWithShadow(textRenderer, "\u25B6", rbx + 4, mid - 4, C_ON);
            int dbx = right - 20;
            ctx.fill(dbx, y + 2, dbx + 16, y + SUB_H - 2, 0xFF442233);
            ctx.drawTextWithShadow(textRenderer, "\u00D7", dbx + 4, mid - 4, 0xFFFF6666);
            y += SUB_H;
        }
        return y;
    }

    /* =========================================================
     *  CLICK HANDLING
     * ========================================================= */
    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (click.button() != 0) return true;
        int x = (int) click.x(), y = (int) click.y();
        ModConfig cfg = ModConfig.getInstance();

        int px = clamp(panelX, 0, width - TOTAL_W);
        int py = clamp(panelY, 0, height - PANEL_H);
        if (x < px || x >= px + TOTAL_W || y < py || y >= py + PANEL_H) return true;

        // close
        if (x >= px + TOTAL_W - 20 && y >= py && y < py + HDR_H) { close(); return true; }
        // drag header
        if (y >= py && y < py + HDR_H) { dragging = true; dragOffX = x - panelX; dragOffY = y - panelY; return true; }

        // sidebar
        int catY = py + HDR_H + 8;
        if (x >= px && x < px + SIDEBAR_W) {
            for (Cat c : Cat.values()) {
                if (y >= catY && y < catY + CAT_H) {
                    if (selectedCat != c) { selectedCat = c; contentScroll = 0; friendSubmenuOpen = false; }
                    return true;
                }
                catY += CAT_H;
            }
            return true;
        }

        // content - calculate y offset
        int cy = py + HDR_H + 4 - contentScroll;
        if (friendSubmenuOpen) return clickFriendSubmenu(cx(px), cy, CONTENT_W, x, y, cfg);
        return switch (selectedCat) {
            case FARMING   -> clickFarming(cx(px), cy, CONTENT_W, x, y, cfg);
            case COMBAT    -> clickCombat(cx(px), cy, CONTENT_W, x, y, cfg);
            case FISHING   -> clickFishing(cx(px), cy, CONTENT_W, x, y, cfg);
            case VISUAL    -> clickVisual(cx(px), cy, CONTENT_W, x, y, cfg);
            case UTILITIES -> clickUtilities(cx(px), cy, CONTENT_W, x, y, cfg);
            case HYPIXEL   -> clickHypixel(cx(px), cy, CONTENT_W, x, y, cfg);
            case MISC      -> clickMisc(cx(px), cy, CONTENT_W, x, y, cfg);
        };
    }

    private int cx(int px) { return px + SIDEBAR_W; }

    // --- Farming clicks ---
    private boolean clickFarming(int cx, int cy, int cw, int x, int y, ModConfig cfg) {
        int ry = cy + PAD + 18;
        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "autocrop", () -> cfg.setAutoCropEnabled(!cfg.isAutoCropEnabled())); return true; }
        ry += ROW_H;
        if (ex("autocrop")) {
            if (hitSub(x, y, cx, ry, cw)) { cycleCropType(cfg, dir(x, cx, cw)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setAutoFarmSilentCropAim(!cfg.isAutoFarmSilentCropAim()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setAutoFarmAutoTool(!cfg.isAutoFarmAutoTool()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setAutoFarmReplant(!cfg.isAutoFarmReplant()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setAutoFarmCropBlocksPerTick(cfg.getAutoFarmCropBlocksPerTick() - 1), () -> cfg.setAutoFarmCropBlocksPerTick(cfg.getAutoFarmCropBlocksPerTick() + 1)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cycleJacob(cfg, dir(x, cx, cw)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setFailsafeRandomPauseEnabled(!cfg.isFailsafeRandomPauseEnabled()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setFailsafeYawJitterEnabled(!cfg.isFailsafeYawJitterEnabled()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setDesyncWatchdogEnabled(!cfg.isDesyncWatchdogEnabled()); return true; }
            ry += SUB_H;
        }

        ry += 4 + 18;
        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "automine", () -> cfg.setAutoMineEnabled(!cfg.isAutoMineEnabled())); return true; }
        ry += ROW_H;
        if (!ex("automine")) return true;

        if (hitSub(x, y, cx, ry, cw)) { startSlider("automine.range", x); return true; }
        return true;
    }

    // --- Combat clicks ---
    private boolean clickCombat(int cx, int cy, int cw, int x, int y, ModConfig cfg) {
        int ry = cy + PAD + 18;
        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "autoslay", () -> cfg.setAutoSlayEnabled(!cfg.isAutoSlayEnabled())); return true; }
        ry += ROW_H;
        if (!ex("autoslay")) return true;

        if (hitSub(x, y, cx, ry, cw)) { int d = dir(x, cx, cw); if (d != 0 && cfg.isCpsMode()) cfg.setTargetCps(cfg.getTargetCps() + d); else cfg.setCpsMode(!cfg.isCpsMode()); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { startSlider("autoslay.scanRange", x); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { startSlider("autoslay.attackRange", x); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setAutoFarmSwitchMargin(cfg.getAutoFarmSwitchMargin() - 0.5), () -> cfg.setAutoFarmSwitchMargin(cfg.getAutoFarmSwitchMargin() + 0.5)); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { cyclePriority(cfg, dir(x, cx, cw)); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { cfg.setAutoFarmRequireLineOfSight(!cfg.isAutoFarmRequireLineOfSight()); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { cfg.setPathSmoothingEnabled(!cfg.isPathSmoothingEnabled()); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { cfg.setAutoFarmWhitelistMode(!cfg.isAutoFarmWhitelistMode()); return true; }
        return true;
    }

    // --- Fishing clicks ---
    private boolean clickFishing(int cx, int cy, int cw, int x, int y, ModConfig cfg) {
        int ry = cy + PAD + 18;
        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "autofish", () -> cfg.setAutoFishEnabled(!cfg.isAutoFishEnabled())); return true; }
        ry += ROW_H;
        if (!ex("autofish")) return true;

        if (hitSub(x, y, cx, ry, cw)) { ModConfig.AutoFishKillMode[] m = ModConfig.AutoFishKillMode.values(); int d = dir(x, cx, cw); if (d == 0) d = 1; cfg.setAutoFishKillMode(m[(cfg.getAutoFishKillMode().ordinal() + d + m.length) % m.length]); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { cfg.setAutoFishKillEntity(!cfg.isAutoFishKillEntity()); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { startSlider("autofish.range", x); return true; }
        return true;
    }

    // --- Visual clicks ---
    private boolean clickVisual(int cx, int cy, int cw, int x, int y, ModConfig cfg) {
        int ry = cy + PAD + 18;

        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "besp", () -> cfg.setBlockEspEnabled(!cfg.isBlockEspEnabled())); return true; }
        ry += ROW_H;
        if (ex("besp")) {
            if (hitSub(x, y, cx, ry, cw)) { startSlider("blockesp.range", x); return true; } ry += SUB_H;
            for (String blockId : getVisibleBlockIds(cfg)) {
                if (y >= ry && y < ry + SUB_H) { handleEspClick(x, cx, cw, blockId, cfg.getBlockFilters(), false); return true; }
                ry += SUB_H;
            }
        }

        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "eesp", () -> cfg.setEntityEspEnabled(!cfg.isEntityEspEnabled())); return true; }
        ry += ROW_H;
        if (ex("eesp")) {
            if (hitSub(x, y, cx, ry, cw)) { startSlider("entityesp.range", x); return true; } ry += SUB_H;
            for (String entityId : getVisibleEntityIds(cfg)) {
                if (y >= ry && y < ry + SUB_H) { handleEspClick(x, cx, cw, entityId, cfg.getEntityFilters(), true); return true; }
                ry += SUB_H;
            }
        }

        ry += 4 + 18;
        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "playeresp", () -> cfg.setPlayerEspEnabled(!cfg.isPlayerEspEnabled())); return true; }
        ry += ROW_H;
        if (ex("playeresp")) {
            if (hitSub(x, y, cx, ry, cw)) { cfg.setPlayerEspShowHealth(!cfg.isPlayerEspShowHealth()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setPlayerEspShowWeapon(!cfg.isPlayerEspShowWeapon()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setPlayerEspShowArmor(!cfg.isPlayerEspShowArmor()); return true; } ry += SUB_H;
        }

        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "friends", () -> cfg.setFriendListEnabled(!cfg.isFriendListEnabled())); return true; }
        ry += ROW_H;
        if (ex("friends")) {
            if (hitSub(x, y, cx, ry, cw)) { cfg.setFriendListAutoNearby(!cfg.isFriendListAutoNearby()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { friendSubmenuOpen = true; return true; }
        }
        return true;
    }

    // --- Utilities clicks ---
    private boolean clickUtilities(int cx, int cy, int cw, int x, int y, ModConfig cfg) {
        int ry = cy + PAD + 18;

        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "nuker", () -> cfg.setBlockNukerEnabled(!cfg.isBlockNukerEnabled())); return true; }
        ry += ROW_H;
        if (ex("nuker")) {
            if (hitSub(x, y, cx, ry, cw)) { startSlider("nuker.range", x); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setBlockNukerWhitelistMode(!cfg.isBlockNukerWhitelistMode()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.applyBlockNukerPlantPreset(); return true; } ry += SUB_H + 2;
            if (hitSub(x, y, cx, ry, cw)) { cfg.syncBlockNukerWhitelistFromEnabledBlockEsp(); return true; } ry += SUB_H + 2;
            if (hitSub(x, y, cx, ry, cw)) { cfg.clearBlockNukerWhitelist(); return true; } ry += SUB_H + 2;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setBlockNukerSilent(!cfg.isBlockNukerSilent()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setBlockNukerRequireLineOfSight(!cfg.isBlockNukerRequireLineOfSight()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setBlockNukerMaxHardness(cfg.getBlockNukerMaxHardness() - 0.1), () -> cfg.setBlockNukerMaxHardness(cfg.getBlockNukerMaxHardness() + 0.1)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setBlockNukerBlocksPerTick(cfg.getBlockNukerBlocksPerTick() - 1), () -> cfg.setBlockNukerBlocksPerTick(cfg.getBlockNukerBlocksPerTick() + 1)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setBlockNukerSkipBlockEntities(!cfg.isBlockNukerSkipBlockEntities()); return true; } ry += SUB_H;
        }

        ry += 4 + 18;
        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "edgeguard", () -> cfg.setPreventLedgeFall(!cfg.isPreventLedgeFall())); return true; }
        ry += ROW_H;
        if (ex("edgeguard")) {
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setLedgeMaxDrop(cfg.getLedgeMaxDrop() - 1), () -> cfg.setLedgeMaxDrop(cfg.getLedgeMaxDrop() + 1)); return true; } ry += SUB_H;
        }

        ry += 4 + 18;
        if (hitModRow(x, y, cx, ry, cw)) { tog("patchcreator"); return true; }
        ry += ROW_H;
        if (ex("patchcreator")) {
            if (hitSub(x, y, cx, ry, cw)) { if (patchCreatorModule != null) patchCreatorModule.startRecording(); return true; } ry += SUB_H + 2;
            if (hitSub(x, y, cx, ry, cw)) { if (patchCreatorModule != null) { if (patchCreatorModule.isRecording()) patchCreatorModule.stopRecording(); else if (patchCreatorModule.isExecuting()) patchCreatorModule.stopExecution(); } return true; } ry += SUB_H + 2;
            if (hitSub(x, y, cx, ry, cw)) { if (patchCreatorModule != null && !routeNameInput.isEmpty()) patchCreatorModule.saveRoute(routeNameInput); return true; } ry += SUB_H + 2;
            ry += SUB_H + 2;
            List<String> routes = cfg.getRouteNames();
            if (!routes.isEmpty()) {
                int count = Math.min(routes.size() - routeScroll, MAX_VIS);
                int right = cx + cw - PAD;
                for (int i = 0; i < count; i++) {
                    int idx = routeScroll + i;
                    if (idx >= routes.size()) break;
                    if (y >= ry && y < ry + SUB_H) {
                        int rbx = right - 44, dbx = right - 20;
                        if (x >= rbx && x < rbx + 18 && patchCreatorModule != null) patchCreatorModule.executeRoute(routes.get(idx));
                        else if (x >= dbx && x < dbx + 16) cfg.deleteRoute(routes.get(idx));
                        return true;
                    }
                    ry += SUB_H;
                }
            }
        }

        ry += 4 + 18;
        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "inventoryhud", () -> cfg.setInventoryHudEnabled(!cfg.isInventoryHudEnabled())); return true; }
        ry += ROW_H;
        if (ex("inventoryhud")) {
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setInventoryHudX(cfg.getInventoryHudX() - 5), () -> cfg.setInventoryHudX(cfg.getInventoryHudX() + 5)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setInventoryHudY(cfg.getInventoryHudY() - 5), () -> cfg.setInventoryHudY(cfg.getInventoryHudY() + 5)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setInventoryHudScale(cfg.getInventoryHudScale() - 0.1f), () -> cfg.setInventoryHudScale(cfg.getInventoryHudScale() + 0.1f)); return true; }
        }
        return true;
    }

    // --- Hypixel clicks ---
    private boolean clickHypixel(int cx, int cy, int cw, int x, int y, ModConfig cfg) {
        int ry = cy + PAD + 18;

        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "fairy", () -> cfg.setFairySoulFinderEnabled(!cfg.isFairySoulFinderEnabled())); return true; }
        ry += ROW_H;
        if (ex("fairy")) {
            if (hitSub(x, y, cx, ry, cw)) { startSlider("fairy.range", x); return true; } ry += SUB_H;
        }

        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "heatmap", () -> cfg.setRouteHeatmapEnabled(!cfg.isRouteHeatmapEnabled())); return true; }
        ry += ROW_H;
        if (ex("heatmap")) {
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setRouteHeatmapProfileIndex(cfg.getRouteHeatmapProfileIndex() - 1), () -> cfg.setRouteHeatmapProfileIndex(cfg.getRouteHeatmapProfileIndex() + 1)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { if (routeHeatmapModule != null) routeHeatmapModule.clearCurrentProfile(); return true; } ry += SUB_H + 2;
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setRouteHeatmapSize(cfg.getRouteHeatmapSize() - 10), () -> cfg.setRouteHeatmapSize(cfg.getRouteHeatmapSize() + 10)); return true; } ry += SUB_H;
        }

        ry += 4 + 18;
        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "statshud", () -> cfg.setHypixelStatsHudEnabled(!cfg.isHypixelStatsHudEnabled())); return true; }
        ry += ROW_H;
        if (ex("statshud")) {
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setHypixelStatsHudX(Math.max(0, cfg.getHypixelStatsHudX()) - 5), () -> cfg.setHypixelStatsHudX(Math.max(0, cfg.getHypixelStatsHudX()) + 5)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setHypixelStatsHudY(cfg.getHypixelStatsHudY() - 5), () -> cfg.setHypixelStatsHudY(cfg.getHypixelStatsHudY() + 5)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { adj(x, cx, cw, () -> cfg.setHypixelStatsHudScale(cfg.getHypixelStatsHudScale() - 0.05f), () -> cfg.setHypixelStatsHudScale(cfg.getHypixelStatsHudScale() + 0.05f)); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setHypixelStatsShowFarming(!cfg.isHypixelStatsShowFarming()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setHypixelStatsShowWorth(!cfg.isHypixelStatsShowWorth()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setHypixelStatsShowSkills(!cfg.isHypixelStatsShowSkills()); return true; } ry += SUB_H;
        }

        ry += 4 + 18;
        if (hitSub(x, y, cx, ry, cw)) { cfg.setGardenLaneLoopEnabled(!cfg.isGardenLaneLoopEnabled()); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { cfg.setVisitorAlarmEnabled(!cfg.isVisitorAlarmEnabled()); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { cfg.setAutoPauseOnVisitor(!cfg.isAutoPauseOnVisitor()); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { cfg.setWaypointChainEnabled(!cfg.isWaypointChainEnabled()); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { cfg.setInventoryAutoActionEnabled(!cfg.isInventoryAutoActionEnabled()); return true; }
        return true;
    }

    // --- Misc clicks ---
    private boolean clickMisc(int cx, int cy, int cw, int x, int y, ModConfig cfg) {
        int ry = cy + PAD + 18;

        if (hitModRow(x, y, cx, ry, cw)) { cfg.setAntiAfkEnabled(!cfg.isAntiAfkEnabled()); return true; }
        ry += ROW_H + 4 + 18;

        if (hitModRow(x, y, cx, ry, cw)) { toggleOrExpand(x, cx, cw, "dbgtools", () -> cfg.setDebugToolsEnabled(!cfg.isDebugToolsEnabled())); return true; }
        ry += ROW_H;
        if (ex("dbgtools")) {
            if (hitSub(x, y, cx, ry, cw)) { cfg.setDebugTargetBlockEnabled(!cfg.isDebugTargetBlockEnabled()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setDebugScanBlocksEnabled(!cfg.isDebugScanBlocksEnabled()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { cfg.setDebugScanEntitiesEnabled(!cfg.isDebugScanEntitiesEnabled()); return true; } ry += SUB_H;
            if (hitSub(x, y, cx, ry, cw)) { startSlider("debug.range", x); return true; } ry += SUB_H;
        }

        if (hitModRow(x, y, cx, ry, cw)) { cfg.setDebugEnabled(!cfg.isDebugEnabled()); return true; }
        ry += ROW_H + 4 + 18;

        if (hitSub(x, y, cx, ry, cw)) { if (pathfinderDebugModule != null) pathfinderDebugModule.setStartFromPlayer(); return true; } ry += SUB_H + 2;
        if (hitSub(x, y, cx, ry, cw)) { if (pathfinderDebugModule != null) pathfinderDebugModule.setStopFromPlayer(); return true; } ry += SUB_H + 2;
        if (hitSub(x, y, cx, ry, cw)) { if (pathfinderDebugModule != null) pathfinderDebugModule.go(); return true; } ry += SUB_H + 2;
        if (hitSub(x, y, cx, ry, cw)) { if (pathfinderDebugModule != null) pathfinderDebugModule.stop(); return true; } ry += SUB_H + 2;
        if (hitSub(x, y, cx, ry, cw)) { if (pathfinderDebugModule != null) pathfinderDebugModule.clearPoints(); return true; } ry += SUB_H + 2;
        if (hitSub(x, y, cx, ry, cw)) { if (pathfinderDebugModule != null) pathfinderDebugModule.cycleVerbosity(dir(x, cx, cw)); return true; } ry += SUB_H;
        if (hitSub(x, y, cx, ry, cw)) { if (pathfinderDebugModule != null) pathfinderDebugModule.toggleSnapToNearestWalkable(); return true; }
        return true;
    }

    // --- Friend submenu clicks ---
    private boolean clickFriendSubmenu(int cx, int cy, int cw, int x, int y, ModConfig cfg) {
        int ry = cy + PAD + 18;
        if (hitSub(x, y, cx, ry, cw)) { friendSubmenuOpen = false; return true; }
        ry += SUB_H + 2;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null) {
            for (PlayerEntity pl : client.world.getPlayers()) {
                if (pl == client.player) continue;
                if (pl.squaredDistanceTo(client.player) > 2500.0) continue;
                if (y >= ry && y < ry + ROW_H) {
                    String name = pl.getName().getString();
                    int right = cx + cw - PAD;
                    if (x >= right - 50) {
                        if (cfg.isFriend(name)) cfg.removeFriend(name); else cfg.addFriend(name);
                    }
                    return true;
                }
                ry += ROW_H + 2;
            }
        }
        return true;
    }

    /* =========================================================
     *  MOUSE / KEY / SCROLL
     * ========================================================= */
    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (activeSlider != null) {
            updateSlider(activeSlider, (int) click.x());
            return true;
        }
        if (dragging && click.button() == 0) { panelX += (int) dx; panelY += (int) dy; }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        activeSlider = null;
        if (click.button() == 0) dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int px = clamp(panelX, 0, width - TOTAL_W);
        int py = clamp(panelY, 0, height - PANEL_H);
        int cxl = px + SIDEBAR_W, cy = py + HDR_H;

        if (mx >= cxl && mx < cxl + CONTENT_W && my >= cy && my < cy + PANEL_H - HDR_H) {
            contentScroll = Math.max(0, contentScroll - (int) (Math.signum(vAmt) * 16));
            contentScroll = clamp(contentScroll, 0, maxContentScroll());
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (key.key() == GLFW.GLFW_KEY_ESCAPE || key.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        if (ex("patchcreator")) {
            if (key.key() == GLFW.GLFW_KEY_BACKSPACE && !routeNameInput.isEmpty()) {
                routeNameInput = routeNameInput.substring(0, routeNameInput.length() - 1); return true;
            }
            char c = k2c(key.key());
            if (c != 0 && routeNameInput.length() < 20) { routeNameInput += c; return true; }
        }
        return super.keyPressed(key);
    }

    /* =========================================================
     *  UTILITIES
     * ========================================================= */
    private void animateToggles(ModConfig cfg) {
        aSet("autocrop", cfg.isAutoCropEnabled());
        aSet("automine", cfg.isAutoMineEnabled());
        aSet("autoslay", cfg.isAutoSlayEnabled());
        aSet("afk",      cfg.isAntiAfkEnabled());
        aSet("nuker",    cfg.isBlockNukerEnabled());
        aSet("autofish", cfg.isAutoFishEnabled());
        aSet("fairy",    cfg.isFairySoulFinderEnabled());
        aSet("heatmap",  cfg.isRouteHeatmapEnabled());
        aSet("statshud", cfg.isHypixelStatsHudEnabled());
        aSet("playeresp",cfg.isPlayerEspEnabled());
        aSet("friends",  cfg.isFriendListEnabled());
        aSet("dbgtools", cfg.isDebugToolsEnabled());
        aSet("besp",     cfg.isBlockEspEnabled());
        aSet("eesp",     cfg.isEntityEspEnabled());
        aSet("debug",    cfg.isDebugEnabled());
        aSet("pathdbg", pathfinderDebugModule != null && pathfinderDebugModule.isActive());
    }

    private void aSet(String k, boolean on) {
        float c = toggleAnims.getOrDefault(k, on ? 1f : 0f);
        float t = on ? 1f : 0f;
        c += (t - c) * 0.2f;
        if (Math.abs(c - t) < 0.005f) c = t;
        toggleAnims.put(k, c);
    }

    private float anim(String k) { return toggleAnims.getOrDefault(k, 0f); }
    private boolean ex(String k) { return expanded.getOrDefault(k, false); }
    private void tog(String k) { expanded.put(k, !ex(k)); }

    private void startSlider(String key, int mouseX) {
        activeSlider = key;
        updateSlider(key, mouseX);
    }

    private void updateSlider(String key, int mouseX) {
        ModConfig cfg = ModConfig.getInstance();
        double value = sliderValue(mouseX, sliderTrackLeft(), sliderTrackRight(), sliderMin(key), sliderMax(key));
        switch (key) {
            case "automine.range" -> cfg.setAutoMineRange(snap(value, 1.0));
            case "autoslay.scanRange" -> cfg.setAutoFarmRange(snap(value, 1.0));
            case "autoslay.attackRange" -> cfg.setAttackRange(snap(value, 0.1));
            case "autofish.range" -> cfg.setAutoFishRange(snap(value, 1.0));
            case "blockesp.range" -> cfg.setBlockEspRange(snap(value, 1.0));
            case "entityesp.range" -> cfg.setEntityEspRange(snap(value, 1.0));
            case "nuker.range" -> cfg.setBlockNukerRange(snap(value, 0.1));
            case "fairy.range" -> cfg.setFairySoulScanRange((int) snap(value, 1.0));
            case "debug.range" -> cfg.setDebugScanRange((int) snap(value, 1.0));
            default -> { }
        }
    }

    private int sliderTrackLeft() {
        int px = clamp(panelX, 0, width - TOTAL_W);
        int cx = px + SIDEBAR_W;
        return cx + PAD + 12 + 110;
    }

    private int sliderTrackRight() {
        int px = clamp(panelX, 0, width - TOTAL_W);
        int cx = px + SIDEBAR_W;
        return cx + CONTENT_W - PAD - 78;
    }

    private double sliderMin(String key) {
        return switch (key) {
            case "automine.range" -> 4.0;
            case "autoslay.scanRange" -> 6.0;
            case "autoslay.attackRange" -> 1.5;
            case "autofish.range" -> 4.0;
            case "blockesp.range" -> 8.0;
            case "entityesp.range" -> 8.0;
            case "nuker.range" -> 1.0;
            case "fairy.range" -> 16.0;
            case "debug.range" -> 2.0;
            default -> 0.0;
        };
    }

    private double sliderMax(String key) {
        return switch (key) {
            case "automine.range" -> 32.0;
            case "autoslay.scanRange" -> 48.0;
            case "autoslay.attackRange" -> 6.0;
            case "autofish.range" -> 40.0;
            case "blockesp.range" -> 128.0;
            case "entityesp.range" -> 160.0;
            case "nuker.range" -> 12.0;
            case "fairy.range" -> 192.0;
            case "debug.range" -> 32.0;
            default -> 1.0;
        };
    }

    private double sliderValue(int mouseX, int left, int right, double min, double max) {
        if (right <= left) return min;
        double t = (double) (mouseX - left) / (double) (right - left);
        t = Math.max(0.0, Math.min(1.0, t));
        return min + (max - min) * t;
    }

    private double snap(double value, double step) {
        return Math.round(value / step) * step;
    }

    private void toggleOrExpand(int clickX, int cx, int cw, String key, Runnable toggle) {
        int right = cx + cw - PAD;
        if (clickX >= right - 18) tog(key);
        else toggle.run();
    }

    private boolean hitModRow(int mx, int my, int cx, int ry, int cw) {
        return mx >= cx + PAD && mx < cx + cw - PAD && my >= ry && my < ry + ROW_H;
    }

    private boolean hitSub(int mx, int my, int cx, int ry, int cw) {
        return mx >= cx + PAD + 12 && mx < cx + cw - PAD && my >= ry && my < ry + SUB_H;
    }

    private void adj(int clickX, int cx, int cw, Runnable dec, Runnable inc) {
        int right = cx + cw - PAD, bx = right - 34;
        if (clickX >= bx && clickX < bx + 14) dec.run();
        else if (clickX >= bx + 17 && clickX < bx + 31) inc.run();
    }

    private int dir(int clickX, int cx, int cw) {
        int right = cx + cw - PAD, bx = right - 34;
        if (clickX >= bx && clickX < bx + 14) return -1;
        if (clickX >= bx + 17 && clickX < bx + 31) return 1;
        return 0;
    }

    private void cycleCropType(ModConfig cfg, int d) {
        ModConfig.CropType[] v = ModConfig.CropType.values();
        if (d == 0) d = 1;
        cfg.setCropType(v[(cfg.getCropType().ordinal() + d + v.length) % v.length]);
    }

    private void cycleJacob(ModConfig cfg, int d) {
        ModConfig.JacobPreset[] v = ModConfig.JacobPreset.values();
        if (d == 0) d = 1;
        cfg.applyJacobPreset(v[(cfg.getJacobPreset().ordinal() + d + v.length) % v.length]);
    }

    private void cyclePriority(ModConfig cfg, int d) {
        ModConfig.AutoFarmPriority[] v = ModConfig.AutoFarmPriority.values();
        if (d == 0) d = 1;
        cfg.setAutoFarmPriority(v[(cfg.getAutoFarmPriority().ordinal() + d + v.length) % v.length]);
    }

    private void handleEspClick(int clickX, int cx, int cw, String id,
                                 Map<String, ModConfig.EspEntry> filters, boolean isEntity) {
        int right = cx + cw - PAD;
        ModConfig.EspEntry e = filters.get(id);
        if (e != null) {
            if (clickX >= right - 24 && clickX < right - 6) ModConfig.getInstance().cycleColor(id, isEntity);
            else e.enabled = !e.enabled;
        }
    }

    private List<String> getVisibleBlockIds(ModConfig cfg) {
        if (blockEspModule != null) {
            return blockEspModule.getVisibleBlockIds();
        }
        List<String> ids = new ArrayList<>(cfg.getBlockFilters().keySet());
        ids.sort(String::compareTo);
        return ids;
    }

    private List<String> getVisibleEntityIds(ModConfig cfg) {
        if (entityEspModule != null) {
            return entityEspModule.getVisibleEntityTypeIds();
        }
        List<String> ids = new ArrayList<>(cfg.getEntityFilters().keySet());
        ids.sort(String::compareTo);
        return ids;
    }

    private int maxContentScroll() {
        int visible = PANEL_H - HDR_H - 8;
        return Math.max(0, lastContentHeight - visible);
    }

    private String toKey(String name) { return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", ""); }

    private char k2c(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return (char) ('a' + (key - GLFW.GLFW_KEY_A));
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return (char) ('0' + (key - GLFW.GLFW_KEY_0));
        if (key == GLFW.GLFW_KEY_MINUS) return '_';
        return 0;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static String fmt0(double v) { return String.format(java.util.Locale.ROOT, "%.0f", v); }
    private static String fmt1(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }
    private static String fmt2(float v)  { return String.format(java.util.Locale.ROOT, "%.2f", v); }
    private static String fmtSlider(double v, int decimals) {
        return switch (decimals) {
            case 0 -> String.format(java.util.Locale.ROOT, "%.0f", v);
            case 1 -> String.format(java.util.Locale.ROOT, "%.1f", v);
            default -> String.format(java.util.Locale.ROOT, "%.2f", v);
        };
    }

    private static int lerp(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int fa = (from >> 24) & 0xFF, fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >> 24) & 0xFF, tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        return ((int)(fa + (ta - fa) * t) << 24) | ((int)(fr + (tr - fr) * t) << 16) |
               ((int)(fg + (tg - fg) * t) << 8) | (int)(fb + (tb - fb) * t);
    }
}
