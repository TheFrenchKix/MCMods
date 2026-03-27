package com.mwa.n0name.gui;

import com.mwa.n0name.ModConfig;
import com.mwa.n0name.modules.AutoWalkModule;
import com.mwa.n0name.modules.PatchCreatorModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vape-style configuration GUI for all modules.
 * Draggable panel, expandable sublists, scrollable.
 */
public class n0nameScreen extends Screen {

    // External module references (set from n0nameMod)
    private static AutoWalkModule autoWalkModule;
    private static PatchCreatorModule patchCreatorModule;

    public static void setModuleReferences(AutoWalkModule aw, PatchCreatorModule pc) {
        autoWalkModule = aw;
        patchCreatorModule = pc;
    }

    // Panel position (draggable)
    private int panelX = 10;
    private int panelY = 50;
    private boolean dragging = false;

    // Expansion states
    private boolean blockExpanded    = false;
    private boolean entityExpanded   = false;
    private boolean autoFarmExpanded = false;
    private boolean patchExpanded    = false;

    // Scroll offsets
    private int blockScroll  = 0;
    private int entityScroll = 0;
    private int routeScroll  = 0;

    // Y positions stored during render for click handling
    private int yAfkRow, yAutoWalkRow, yPatchRow, yPatchSubY;
    private int yAutoFarmRow, yAutoFarmSubY;
    private int yBlockRow, yBlockSubY, yEntityRow, yEntitySubY;
    private int yDebugRow;

    // Dimensions
    private static final int W       = 220;
    private static final int HDR_H   = 17;
    private static final int ROW_H   = 15;
    private static final int SUB_H   = 13;
    private static final int PAD     = 6;
    private static final int MAX_VIS = 8;

    // Palette
    private static final int C_BG      = 0xF00C0C0F;
    private static final int C_HEADER  = 0xFF0F0F26;
    private static final int C_ACCENT  = 0xFF4466EE;
    private static final int C_BORDER  = 0xFF1C1C36;
    private static final int C_HOVER   = 0x14FFFFFF;
    private static final int C_TEXT    = 0xFFCCCCCC;
    private static final int C_ON      = 0xFF55FF55;
    private static final int C_OFF     = 0xFFAA3333;
    private static final int C_DOT_ON  = 0xFF44EE44;
    private static final int C_DOT_OFF = 0xFF2A2A44;
    private static final int C_MUTED   = 0xFF555577;
    private static final int C_SUB_BG  = 0x08FFFFFF;
    private static final int C_SWBORD  = 0xFF222233;
    private static final int C_BTN     = 0xFF333355;
    private static final int C_BTN_ACT = 0xFF4455AA;

    // PatchCreator route name input
    private String routeNameInput = "route1";

    public n0nameScreen() {
        super(Text.empty());
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ModConfig cfg = ModConfig.getInstance();
        List<String> blockList  = new ArrayList<>(cfg.getBlockFilters().keySet());
        List<String> entityList = new ArrayList<>(cfg.getEntityFilters().keySet());
        List<String> routeList  = cfg.getRouteNames();

        int ph = computePanelH(blockList, entityList, routeList);

        panelX = Math.max(0, Math.min(panelX, width  - W));
        panelY = Math.max(0, Math.min(panelY, height - ph));

        // Background & borders
        ctx.fill(panelX, panelY, panelX + W, panelY + ph, C_BG);
        ctx.fill(panelX, panelY, panelX + W, panelY + 1, C_ACCENT);
        ctx.fill(panelX, panelY + 1, panelX + 1, panelY + ph, C_BORDER);
        ctx.fill(panelX + W - 1, panelY + 1, panelX + W, panelY + ph, C_BORDER);
        ctx.fill(panelX + 1, panelY + ph - 1, panelX + W - 1, panelY + ph, C_BORDER);

        // Header
        ctx.fill(panelX + 1, panelY + 1, panelX + W - 1, panelY + HDR_H, C_HEADER);
        ctx.drawTextWithShadow(textRenderer, "n0name Mod", panelX + PAD, panelY + 4, C_ACCENT);
        ctx.drawTextWithShadow(textRenderer, "x", panelX + W - 10, panelY + 4, C_MUTED);
        ctx.fill(panelX + 1, panelY + HDR_H, panelX + W - 1, panelY + HDR_H + 1, C_BORDER);

        int y = panelY + HDR_H + 1;

        // Anti-AFK
        yAfkRow = y;
        y = drawModRow(ctx, mx, my, y, "Anti-AFK", cfg.isAntiAfkEnabled(), false, false);

        // AutoWalk
        yAutoWalkRow = y;
        y = drawModRow(ctx, mx, my, y, "AutoWalk", cfg.isAutoWalkEnabled(), false, false);

        // PatchCreator
        yPatchRow = y;
        y = drawModRow(ctx, mx, my, y, "PatchCreator", patchCreatorModule != null && patchCreatorModule.isRecording(), true, patchExpanded);
        yPatchSubY = y;
        if (patchExpanded) {
            y = drawPatchCreatorSubList(ctx, mx, my, y, routeList);
        }

        // AutoFarm
        yAutoFarmRow = y;
        y = drawModRow(ctx, mx, my, y, "AutoFarm", cfg.isAutoFarmEnabled(), true, autoFarmExpanded);
        yAutoFarmSubY = y;
        if (autoFarmExpanded) {
            y = drawAutoFarmSubList(ctx, mx, my, y, cfg);
        }

        // Block ESP
        yBlockRow = y;
        y = drawModRow(ctx, mx, my, y, "Block ESP", cfg.isBlockEspEnabled(), true, blockExpanded);
        yBlockSubY = y;
        if (blockExpanded) {
            y = drawEspSubList(ctx, mx, my, y, blockList, cfg.getBlockFilters(), blockScroll, "block");
        }

        // Entity ESP
        yEntityRow = y;
        y = drawModRow(ctx, mx, my, y, "Entity ESP", cfg.isEntityEspEnabled(), true, entityExpanded);
        yEntitySubY = y;
        if (entityExpanded) {
            y = drawEspSubList(ctx, mx, my, y, entityList, cfg.getEntityFilters(), entityScroll, "entity");
        }

        // Debug
        yDebugRow = y;
        drawModRow(ctx, mx, my, y, "Debug", cfg.isDebugEnabled(), false, false);
    }

    private int drawModRow(DrawContext ctx, int mx, int my, int y,
                            String name, boolean on, boolean expandable, boolean expanded) {
        if (inRow(mx, my, y, ROW_H)) ctx.fill(panelX + 1, y, panelX + W - 1, y + ROW_H, C_HOVER);

        int mid = y + ROW_H / 2;
        ctx.fill(panelX + PAD, mid - 2, panelX + PAD + 4, mid + 2, on ? C_DOT_ON : C_DOT_OFF);
        ctx.drawTextWithShadow(textRenderer, name, panelX + PAD + 8, mid - 3, on ? C_ON : C_TEXT);

        int statusX = expandable ? panelX + W - 38 : panelX + W - 22;
        ctx.drawTextWithShadow(textRenderer, on ? "ON" : "OFF", statusX, mid - 3, on ? C_ON : C_OFF);

        if (expandable) {
            ctx.drawTextWithShadow(textRenderer, expanded ? "-" : "+", panelX + W - 12, mid - 3, C_MUTED);
        }
        return y + ROW_H;
    }

    private int drawEspSubList(DrawContext ctx, int mx, int my, int y,
                                List<String> list, Map<String, ModConfig.EspEntry> filters,
                                int scroll, String kind) {
        ctx.fill(panelX + 1, y, panelX + W - 1, y + 1, C_BORDER);
        y += 1;

        if (list.isEmpty()) {
            ctx.fill(panelX + 1, y, panelX + W - 1, y + SUB_H, C_SUB_BG);
            ctx.drawTextWithShadow(textRenderer, "No " + kind + " detected",
                panelX + PAD + 10, y + SUB_H / 2 - 3, C_MUTED);
            y += SUB_H;
        } else {
            int count = Math.min(list.size() - scroll, MAX_VIS);
            for (int i = 0; i < count; i++) {
                int idx = scroll + i;
                if (idx >= list.size()) break;
                String id = list.get(idx);
                ModConfig.EspEntry entry = filters.get(id);
                if (entry == null) { y += SUB_H; continue; }

                ctx.fill(panelX + 1, y, panelX + W - 1, y + SUB_H, C_SUB_BG);
                if (inRow(mx, my, y, SUB_H)) ctx.fill(panelX + 1, y, panelX + W - 1, y + SUB_H, C_HOVER);

                int mid = y + SUB_H / 2;
                ctx.fill(panelX + PAD + 4, mid - 2, panelX + PAD + 8, mid + 2, entry.enabled ? C_DOT_ON : C_DOT_OFF);

                String shortName = id.contains(":") ? id.split(":")[1] : id;
                if (shortName.length() > 22) shortName = shortName.substring(0, 22) + "..";
                ctx.drawTextWithShadow(textRenderer, shortName, panelX + PAD + 12, mid - 3,
                    entry.enabled ? C_ON : C_MUTED);

                int sw = panelX + W - 20;
                ctx.fill(sw, y + 2, sw + 12, y + SUB_H - 2, entry.color | 0xFF000000);
                ctx.fill(sw - 1, y + 1, sw + 13, y + 2, C_SWBORD);
                ctx.fill(sw - 1, y + SUB_H - 2, sw + 13, y + SUB_H - 1, C_SWBORD);
                ctx.fill(sw - 1, y + 2, sw, y + SUB_H - 2, C_SWBORD);
                ctx.fill(sw + 12, y + 2, sw + 13, y + SUB_H - 2, C_SWBORD);

                y += SUB_H;
            }
        }

        ctx.fill(panelX + 1, y, panelX + W - 1, y + 1, C_BORDER);
        return y + 2;
    }

    private int drawPatchCreatorSubList(DrawContext ctx, int mx, int my, int y, List<String> routes) {
        ctx.fill(panelX + 1, y, panelX + W - 1, y + 1, C_BORDER);
        y += 1;

        // Buttons row: [Record] [Stop] [Save]
        int btnW = 50;
        int btnH = 12;
        int bx = panelX + PAD;

        boolean recording = patchCreatorModule != null && patchCreatorModule.isRecording();

        // Record button
        ctx.fill(bx, y, bx + btnW, y + btnH, recording ? C_BTN_ACT : C_BTN);
        ctx.drawTextWithShadow(textRenderer, recording ? "REC..." : "Record", bx + 3, y + 2, C_TEXT);
        bx += btnW + 3;

        // Stop button
        ctx.fill(bx, y, bx + btnW, y + btnH, C_BTN);
        ctx.drawTextWithShadow(textRenderer, "Stop", bx + 10, y + 2, C_TEXT);
        bx += btnW + 3;

        // Save button
        ctx.fill(bx, y, bx + btnW, y + btnH, C_BTN);
        ctx.drawTextWithShadow(textRenderer, "Save", bx + 10, y + 2, C_TEXT);

        y += btnH + 2;

        // Route name input
        ctx.fill(panelX + PAD, y, panelX + W - PAD, y + 12, 0xFF1A1A2E);
        ctx.drawTextWithShadow(textRenderer, "Name: " + routeNameInput + "_", panelX + PAD + 3, y + 2, C_TEXT);
        y += 14;

        // Saved routes list
        if (routes.isEmpty()) {
            ctx.fill(panelX + 1, y, panelX + W - 1, y + SUB_H, C_SUB_BG);
            ctx.drawTextWithShadow(textRenderer, "No saved routes", panelX + PAD + 10, y + SUB_H / 2 - 3, C_MUTED);
            y += SUB_H;
        } else {
            int count = Math.min(routes.size() - routeScroll, MAX_VIS);
            for (int i = 0; i < count; i++) {
                int idx = routeScroll + i;
                if (idx >= routes.size()) break;
                String name = routes.get(idx);

                ctx.fill(panelX + 1, y, panelX + W - 1, y + SUB_H, C_SUB_BG);
                if (inRow(mx, my, y, SUB_H)) ctx.fill(panelX + 1, y, panelX + W - 1, y + SUB_H, C_HOVER);

                int mid = y + SUB_H / 2;
                ctx.drawTextWithShadow(textRenderer, name, panelX + PAD + 4, mid - 3, C_TEXT);

                // Run button
                int rbx = panelX + W - 40;
                ctx.fill(rbx, y + 1, rbx + 18, y + SUB_H - 1, C_BTN_ACT);
                ctx.drawTextWithShadow(textRenderer, ">", rbx + 6, mid - 3, C_ON);

                // Delete button
                int dbx = panelX + W - 18;
                ctx.fill(dbx, y + 1, dbx + 14, y + SUB_H - 1, 0xFF442222);
                ctx.drawTextWithShadow(textRenderer, "x", dbx + 4, mid - 3, C_OFF);

                y += SUB_H;
            }
        }

        ctx.fill(panelX + 1, y, panelX + W - 1, y + 1, C_BORDER);
        return y + 2;
    }

    private int drawAutoFarmSubList(DrawContext ctx, int mx, int my, int y, ModConfig cfg) {
        ctx.fill(panelX + 1, y, panelX + W - 1, y + 1, C_BORDER);
        y += 1;

        // Mode toggle: [Cooldown] / [CPS: 10]
        int mid = y + SUB_H / 2;
        ctx.fill(panelX + 1, y, panelX + W - 1, y + SUB_H, C_SUB_BG);
        ctx.drawTextWithShadow(textRenderer, "Mode:", panelX + PAD + 4, mid - 3, C_TEXT);

        String modeText = cfg.isCpsMode() ? "CPS: " + cfg.getTargetCps() : "Cooldown";
        int modeColor = cfg.isCpsMode() ? 0xFFFFAA44 : C_ON;
        ctx.drawTextWithShadow(textRenderer, modeText, panelX + PAD + 40, mid - 3, modeColor);

        // +/- buttons for CPS
        if (cfg.isCpsMode()) {
            int bx = panelX + W - 30;
            ctx.drawTextWithShadow(textRenderer, "-", bx, mid - 3, C_MUTED);
            ctx.drawTextWithShadow(textRenderer, "+", bx + 14, mid - 3, C_MUTED);
        }
        y += SUB_H;

        // Range display
        ctx.fill(panelX + 1, y, panelX + W - 1, y + SUB_H, C_SUB_BG);
        mid = y + SUB_H / 2;
        ctx.drawTextWithShadow(textRenderer, "Range: " + (int)cfg.getAutoFarmRange() + " blocks",
            panelX + PAD + 4, mid - 3, C_TEXT);
        y += SUB_H;

        ctx.fill(panelX + 1, y, panelX + W - 1, y + 1, C_BORDER);
        return y + 2;
    }

    // --- Input Handling ---

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (click.button() != 0) return true;
        int x = (int)click.x(), y = (int)click.y();
        ModConfig cfg = ModConfig.getInstance();
        List<String> blockList  = new ArrayList<>(cfg.getBlockFilters().keySet());
        List<String> entityList = new ArrayList<>(cfg.getEntityFilters().keySet());
        List<String> routeList  = cfg.getRouteNames();

        int ph = computePanelH(blockList, entityList, routeList);
        if (x < panelX || x >= panelX + W || y < panelY || y >= panelY + ph) return true;

        // Close button
        if (x >= panelX + W - 13 && x < panelX + W - 2 && y >= panelY + 2 && y < panelY + HDR_H) {
            close(); return true;
        }
        // Drag header
        if (y >= panelY && y < panelY + HDR_H) {
            dragging = true; return true;
        }

        // Anti-AFK
        if (inRow(x, y, yAfkRow, ROW_H)) {
            cfg.setAntiAfkEnabled(!cfg.isAntiAfkEnabled()); return true;
        }

        // AutoWalk
        if (inRow(x, y, yAutoWalkRow, ROW_H)) {
            cfg.setAutoWalkEnabled(!cfg.isAutoWalkEnabled()); return true;
        }

        // PatchCreator
        if (inRow(x, y, yPatchRow, ROW_H)) {
            if (x >= panelX + W - 15) { patchExpanded = !patchExpanded; routeScroll = 0; }
            return true;
        }

        // PatchCreator sub-list
        if (patchExpanded && y >= yPatchSubY && y < yAutoFarmRow) {
            return handlePatchCreatorClick(x, y, routeList);
        }

        // AutoFarm
        if (inRow(x, y, yAutoFarmRow, ROW_H)) {
            if (x >= panelX + W - 15) { autoFarmExpanded = !autoFarmExpanded; }
            else { cfg.setAutoFarmEnabled(!cfg.isAutoFarmEnabled()); }
            return true;
        }

        // AutoFarm sub-list
        if (autoFarmExpanded && y >= yAutoFarmSubY && y < yBlockRow) {
            return handleAutoFarmClick(x, y, cfg);
        }

        // Block ESP
        if (inRow(x, y, yBlockRow, ROW_H)) {
            if (x >= panelX + W - 15) { blockExpanded = !blockExpanded; blockScroll = 0; }
            else { cfg.setBlockEspEnabled(!cfg.isBlockEspEnabled()); }
            return true;
        }

        // Block sub-list
        if (blockExpanded && y >= yBlockSubY && y < yEntityRow && !blockList.isEmpty()) {
            return handleEspSubListClick(x, y, blockList, cfg.getBlockFilters(), blockScroll, false);
        }

        // Entity ESP
        if (inRow(x, y, yEntityRow, ROW_H)) {
            if (x >= panelX + W - 15) { entityExpanded = !entityExpanded; entityScroll = 0; }
            else { cfg.setEntityEspEnabled(!cfg.isEntityEspEnabled()); }
            return true;
        }

        // Entity sub-list
        if (entityExpanded && y >= yEntitySubY && y < yDebugRow && !entityList.isEmpty()) {
            return handleEspSubListClick(x, y, entityList, cfg.getEntityFilters(), entityScroll, true);
        }

        // Debug
        if (inRow(x, y, yDebugRow, ROW_H)) {
            cfg.setDebugEnabled(!cfg.isDebugEnabled()); return true;
        }

        return true;
    }

    private boolean handlePatchCreatorClick(int x, int y, List<String> routeList) {
        // Buttons row
        int btnW = 50;
        int btnH = 12;
        int btnY = yPatchSubY + 1;

        if (y >= btnY && y < btnY + btnH) {
            int bx = panelX + PAD;
            if (x >= bx && x < bx + btnW) {
                // Record
                if (patchCreatorModule != null) patchCreatorModule.startRecording();
                return true;
            }
            bx += btnW + 3;
            if (x >= bx && x < bx + btnW) {
                // Stop
                if (patchCreatorModule != null) {
                    if (patchCreatorModule.isRecording()) patchCreatorModule.stopRecording();
                    else if (patchCreatorModule.isExecuting()) patchCreatorModule.stopExecution();
                }
                return true;
            }
            bx += btnW + 3;
            if (x >= bx && x < bx + btnW) {
                // Save
                if (patchCreatorModule != null && !routeNameInput.isEmpty()) {
                    patchCreatorModule.saveRoute(routeNameInput);
                }
                return true;
            }
        }

        // Route list clicks
        int routeStartY = btnY + btnH + 2 + 14; // after buttons + name input
        if (!routeList.isEmpty()) {
            int rowY = routeStartY;
            int count = Math.min(routeList.size() - routeScroll, MAX_VIS);
            for (int i = 0; i < count; i++) {
                int idx = routeScroll + i;
                if (idx >= routeList.size()) break;
                if (y >= rowY && y < rowY + SUB_H) {
                    String name = routeList.get(idx);
                    int rbx = panelX + W - 40;
                    int dbx = panelX + W - 18;

                    if (x >= rbx && x < rbx + 18) {
                        // Run route
                        if (patchCreatorModule != null) patchCreatorModule.executeRoute(name);
                    } else if (x >= dbx && x < dbx + 14) {
                        // Delete route
                        ModConfig.getInstance().deleteRoute(name);
                    }
                    return true;
                }
                rowY += SUB_H;
            }
        }
        return true;
    }

    private boolean handleAutoFarmClick(int x, int y, ModConfig cfg) {
        int modeRowY = yAutoFarmSubY + 1;
        if (y >= modeRowY && y < modeRowY + SUB_H) {
            if (cfg.isCpsMode()) {
                int bx = panelX + W - 30;
                if (x >= bx && x < bx + 10) {
                    cfg.setTargetCps(cfg.getTargetCps() - 1);
                    return true;
                }
                if (x >= bx + 14 && x < bx + 24) {
                    cfg.setTargetCps(cfg.getTargetCps() + 1);
                    return true;
                }
            }
            // Toggle mode
            cfg.setCpsMode(!cfg.isCpsMode());
            return true;
        }
        return true;
    }

    private boolean handleEspSubListClick(int x, int y, List<String> list,
                                           Map<String, ModConfig.EspEntry> filters, int scroll,
                                           boolean isEntity) {
        int rowY = (isEntity ? yEntitySubY : yBlockSubY) + 1;
        int count = Math.min(list.size() - scroll, MAX_VIS);
        for (int i = 0; i < count; i++) {
            int idx = scroll + i;
            if (idx >= list.size()) break;
            if (y >= rowY && y < rowY + SUB_H) {
                String id = list.get(idx);
                ModConfig.EspEntry e = filters.get(id);
                if (e != null) {
                    if (x >= panelX + W - 21 && x < panelX + W - 7) {
                        ModConfig.getInstance().cycleColor(id, isEntity);
                    } else {
                        e.enabled = !e.enabled;
                    }
                }
                return true;
            }
            rowY += SUB_H;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging && click.button() == 0) {
            panelX += (int)dx;
            panelY += (int)dy;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int iy = (int)my;
        ModConfig cfg = ModConfig.getInstance();
        List<String> blockList  = new ArrayList<>(cfg.getBlockFilters().keySet());
        List<String> entityList = new ArrayList<>(cfg.getEntityFilters().keySet());
        List<String> routeList  = cfg.getRouteNames();

        if (blockExpanded && iy >= yBlockSubY && iy < yEntityRow) {
            blockScroll = clampScroll(blockScroll, blockList.size(), vAmt);
        } else if (entityExpanded && iy >= yEntitySubY && iy < yDebugRow) {
            entityScroll = clampScroll(entityScroll, entityList.size(), vAmt);
        } else if (patchExpanded && iy >= yPatchSubY && iy < yAutoFarmRow) {
            routeScroll = clampScroll(routeScroll, routeList.size(), vAmt);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (key.key() == GLFW.GLFW_KEY_ESCAPE || key.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close(); return true;
        }

        // Route name input when PatchCreator is expanded
        if (patchExpanded) {
            if (key.key() == GLFW.GLFW_KEY_BACKSPACE && !routeNameInput.isEmpty()) {
                routeNameInput = routeNameInput.substring(0, routeNameInput.length() - 1);
                return true;
            }
            // Accept alphanumeric and underscore
            char c = keyToChar(key.key());
            if (c != 0 && routeNameInput.length() < 20) {
                routeNameInput += c;
                return true;
            }
        }

        return super.keyPressed(key);
    }

    private char keyToChar(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return (char)('a' + (key - GLFW.GLFW_KEY_A));
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return (char)('0' + (key - GLFW.GLFW_KEY_0));
        if (key == GLFW.GLFW_KEY_MINUS) return '_';
        return 0;
    }

    // --- Utilities ---

    private int computePanelH(List<String> blockList, List<String> entityList, List<String> routeList) {
        int h = HDR_H + 1;
        h += ROW_H; // Anti-AFK
        h += ROW_H; // AutoWalk
        h += ROW_H; // PatchCreator
        if (patchExpanded) {
            h += 12 + 2 + 14; // buttons + name input
            h += (routeList.isEmpty() ? 1 : Math.min(routeList.size(), MAX_VIS)) * SUB_H + 3;
        }
        h += ROW_H; // AutoFarm
        if (autoFarmExpanded) {
            h += SUB_H * 2 + 3; // mode + range
        }
        h += ROW_H; // Block ESP
        if (blockExpanded) {
            h += (blockList.isEmpty() ? 1 : Math.min(blockList.size(), MAX_VIS)) * SUB_H + 3;
        }
        h += ROW_H; // Entity ESP
        if (entityExpanded) {
            h += (entityList.isEmpty() ? 1 : Math.min(entityList.size(), MAX_VIS)) * SUB_H + 3;
        }
        h += ROW_H; // Debug
        h += 4;
        return h;
    }

    private boolean inRow(int mx, int my, int y, int h) {
        return mx >= panelX + 1 && mx < panelX + W - 1 && my >= y && my < y + h;
    }

    private int clampScroll(int current, int listSize, double vAmt) {
        return Math.max(0, Math.min(
            Math.max(0, listSize - MAX_VIS),
            current - (int)Math.signum(vAmt)));
    }
}
