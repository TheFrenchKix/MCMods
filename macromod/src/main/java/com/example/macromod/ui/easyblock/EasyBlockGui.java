package com.example.macromod.ui.easyblock;

import com.example.macromod.MacroModClient;
import com.example.macromod.config.ModConfig;
import com.example.macromod.manager.AutoAttackManager;
import com.example.macromod.manager.AutoFarmerManager;
import com.example.macromod.manager.AutoFishingManager;
import com.example.macromod.manager.FreelookManager;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroStep;
import com.example.macromod.ui.MacroEditScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Main ClickGUI — dark theme with blue accent, LiquidBounce-inspired layout.
 * Two tabs: Macros (sidebar + detail) and Auto Farm (settings).
 */
@Environment(EnvType.CLIENT)
public class EasyBlockGui extends BasePopupScreen {

    // ═══════════════════════════════════════════════════════════════════
    // Public color palette (used by sub-screens)
    // ═══════════════════════════════════════════════════════════════════
    public static final int C_BG         = 0xFF0D161D;
    public static final int C_PANEL_HEAD = 0xFF132935;
    public static final int C_CARD       = 0xFF1A2F3A;
    public static final int C_CARD_HOV   = 0xFF20404D;
    public static final int C_CARD_SEL   = 0xFF245765;
    public static final int C_DIVIDER    = 0xFF2E505E;
    public static final int C_ACCENT     = 0xFF2F9FAA;
    public static final int C_ACCENT_HI  = 0xFF42BBC6;
    public static final int C_DANGER     = 0xFFD96363;
    public static final int C_SUCCESS    = 0xFF58D38A;
    public static final int C_TEXT       = 0xFFEAF3F6;
    public static final int C_TEXT2      = 0xFFC6D8E0;
    public static final int C_TEXT3      = 0xFF8CA5B0;
    public static final int C_NAV_BG     = 0xFF233A45;
    public static final int C_NAV_ACT    = 0xFF2B4B59;

    // ═══════════════════════════════════════════════════════════════════
    // Layout constants
    // ═══════════════════════════════════════════════════════════════════
    private static final int PANEL_MAX_W = 720, PANEL_MAX_H = 480;
    private static final int HEADER_H = 36;
    private static final int SIDEBAR_W = 200;
    private static final int CARD_H = 48, CARD_GAP = 3, CARD_PX = 6;
    private static final int FOOTER_H = 36;
    private static final int ACT_W = 68, ACT_H = 22;
    private static final int R = 8, RB = 5;

    // ═══════════════════════════════════════════════════════════════════
    // Panel bounds (computed in init)
    // ═══════════════════════════════════════════════════════════════════
    private int px, py, pw, ph;

    // ═══════════════════════════════════════════════════════════════════
    // State — Tabs
    // ═══════════════════════════════════════════════════════════════════
    private int activeTab = 0; // 0 = Macros, 1 = Auto Farm, 2 = Visuals, 3 = Misc
    private int tabMacrosX, tabFarmX, tabVisualsX, tabMiscX, tabY, tabW, tabH;

    // ═══════════════════════════════════════════════════════════════════
    // State — Macros tab
    // ═══════════════════════════════════════════════════════════════════
    private Macro selectedMacro;
    private int scrollY = 0, maxScrollY = 0;
    private float scrollSmooth = 0;
    private boolean hoverClose, hoverNew;
    private boolean hoverRun, hoverEdit, hoverDelete, hoverDup;
    private int[] actX = new int[4];
    private int actY;
    private final int[][] chipBounds = new int[5][3]; // [idx][x, y, w]

    // ═══════════════════════════════════════════════════════════════════
    // State — Auto Farm tab
    // ═══════════════════════════════════════════════════════════════════
    private boolean optAutoFish, optFishAttack, optFishAttackDistance, optAttackMobs, optCameraJitter, optAutoDeposit, optFishMove;
    private int optFishAttackSlot = -1;
    private int optFarmRadius = 8;
    private float animFish, animFishAtk, animMobs, animCameraJitter, animAutoDeposit, animFishMove;
    private final int[][] fishTgBounds = new int[6][4]; // 0=fish, 1=fishAtk, 2=mobs, 3=jitter, 4=deposit, 5=move
    private final int[][] hotbar = new int[9][2];
    private int modeBtnX, modeBtnY, modeBtnW, modeBtnH;
    private int radLeftX, radRightX, radBtnY;

    // ═══════════════════════════════════════════════════════════════════
    // State — Auto Farmer
    // ═══════════════════════════════════════════════════════════════════
    private boolean optAutoFarmer;
    private AutoFarmerManager.HorizontalDir optFarmerDir;
    private float animFarmer;
    private final int[] farmerTgBounds = new int[4]; // x, y, w, h
    private int farmerDirBtnX, farmerDirBtnY, farmerDirBtnW, farmerDirBtnH;

    // ═══════════════════════════════════════════════════════════════════
    // State — Auto Attack
    // ═══════════════════════════════════════════════════════════════════
    private boolean optAutoAttack;
    private AutoAttackManager.PriorityMode optAttackPriority;
    private float optAttackRange;
    private int optAttackItemSlot = -1;
    private boolean optRandomCps;
    private float animAutoAttack;
    private float animRandomCps;
    private final int[] attackTgBounds = new int[4];
    private final int[] randomCpsTgBounds = new int[4];
    private final int[][] attackHotbar = new int[9][2];
    private int attackPriorityBtnX, attackPriorityBtnY, attackPriorityBtnW, attackPriorityBtnH;
    private int attackRangeLeftX, attackRangeRightX, attackRangeBtnY;

    // ═══════════════════════════════════════════════════════════════════
    // State — Auto Mining
    // ═══════════════════════════════════════════════════════════════════
    private boolean optAutoMining;
    private float animAutoMining;
    private final int[] miningTgBounds = new int[4];

    // ═══════════════════════════════════════════════════════════════════
    // State — Visuals tab
    // ═══════════════════════════════════════════════════════════════════
    private boolean optTargetEsp, optEntitiesEsp, optBlockEsp, optFairySoulsEsp, optHotspotEsp;
    private float animTargetEsp, animEntitiesEsp, animBlockEsp, animFairySoulsEsp, animHotspotEsp;
    private final int[] targetEspTgBounds = new int[4];
    private final int[] entitiesEspTgBounds = new int[4];
    private final int[] blockEspTgBounds = new int[4];
    private final int[] fairySoulsEspTgBounds = new int[4];
    private final int[] hotspotEspTgBounds = new int[4];
    private int blockRadiusLeftX, blockRadiusRightX, blockRadiusBtnY;
    private int optBlockEspRadius;

    // ── Dynamic nearby scan state ──
    private List<String> nearbyEntityTypes = new ArrayList<>();
    private List<String> nearbyBlockTypes = new ArrayList<>();
    private int visualsScanCounter = 0;
    private static final int VISUALS_SCAN_INTERVAL = 40; // rescan every 40 frames
    private int visualsScrollY = 0;
    private int visualsMaxScrollY = 0;
    private int entityListY, blockListY;
    private static final int LIST_ITEM_H = 16;
    private static final Set<String> BORING_BLOCKS = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
            "minecraft:stone", "minecraft:dirt", "minecraft:grass_block",
            "minecraft:deepslate", "minecraft:netherrack", "minecraft:end_stone",
            "minecraft:bedrock", "minecraft:water", "minecraft:lava",
            "minecraft:sand", "minecraft:gravel", "minecraft:cobblestone",
            "minecraft:tuff", "minecraft:calcite", "minecraft:dripstone_block",
            "minecraft:smooth_basalt", "minecraft:andesite", "minecraft:diorite",
            "minecraft:granite", "minecraft:sandstone", "minecraft:red_sandstone",
            "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:basalt",
            "minecraft:blackstone", "minecraft:cobbled_deepslate"
    );

    // ═══════════════════════════════════════════════════════════════════
    // State — Misc tab
    // ═══════════════════════════════════════════════════════════════════
    private boolean optFreelook;
    private int optFOV;
    private boolean optDebugLogging;
    private float animFreelook, animDebugLogging;
    private final int[] freelookTgBounds = new int[4];
    private final int[] debugLogTgBounds = new int[4];
    private int fovLeftX, fovRightX, fovBtnY;

    // ── Smooth Aim slider state ──────────────────────────────────────────
    private float optSaBaseLerp, optSaSpeedZero, optSaSpeedSlow, optSaSpeedFast;
    private float optSaSlowZone, optSaFastZone;
    private int saBaseLerpLeftX, saBaseLerpRightX, saBaseLerpBtnY;
    private int saSpeedZeroLeftX, saSpeedZeroRightX, saSpeedZeroBtnY;
    private int saSpeedSlowLeftX, saSpeedSlowRightX, saSpeedSlowBtnY;
    private int saSpeedFastLeftX, saSpeedFastRightX, saSpeedFastBtnY;
    private int saSlowZoneLeftX, saSlowZoneRightX, saSlowZoneBtnY;
    private int saFastZoneLeftX, saFastZoneRightX, saFastZoneBtnY;

    // ═══════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════

    public EasyBlockGui() {
        super(Text.literal("MacroMod"), null);
        AutoFishingManager mgr = AutoFishingManager.getInstance();
        optAutoFish = mgr.isEnabled();
        optFishAttack = mgr.isAttackEnabled();
        optFishAttackDistance = mgr.isAttackModeDistance();
        optFishAttackSlot = mgr.getAttackHotbarSlot();
        optCameraJitter = mgr.isCameraJitterEnabled();
        optAutoDeposit  = mgr.isAutoDepositEnabled();
        optFishMove     = mgr.isCloseMovementEnabled();
        animFish = optAutoFish ? 1f : 0f;
        animFishAtk = optFishAttack ? 1f : 0f;
        animCameraJitter = optCameraJitter ? 1f : 0f;
        animAutoDeposit  = optAutoDeposit  ? 1f : 0f;
        animFishMove     = optFishMove     ? 1f : 0f;

        AutoFarmerManager farmer = AutoFarmerManager.getInstance();
        optAutoFarmer = farmer.isEnabled();
        optFarmerDir = farmer.getStartDirection();
        animFarmer = optAutoFarmer ? 1f : 0f;

        AutoAttackManager aam = AutoAttackManager.getInstance();
        optAutoAttack = aam.isEnabled();
        optAttackPriority = aam.getPriorityMode();
        optAttackRange = aam.getAttackRange();
        optAttackItemSlot = aam.getAttackItemSlot();
        optRandomCps = aam.isRandomCps();
        animAutoAttack = optAutoAttack ? 1f : 0f;
        animRandomCps = optRandomCps ? 1f : 0f;

        com.example.macromod.manager.AutoMiningManager amm =
                com.example.macromod.manager.AutoMiningManager.getInstance();
        optAutoMining = amm.isEnabled();
        animAutoMining = optAutoMining ? 1f : 0f;

        FreelookManager fl = FreelookManager.getInstance();
        optFreelook = fl.isEnabled();
        optFOV = fl.getFreelookFov();
        animFreelook = optFreelook ? 1f : 0f;

        ModConfig modCfg = MacroModClient.getConfigManager().getConfig();
        optDebugLogging = modCfg.isDebugLogging();
        animDebugLogging = optDebugLogging ? 1f : 0f;

        optSaBaseLerp  = modCfg.getSmoothAimBaseLerp();
        optSaSpeedZero = modCfg.getSmoothAimSpeedZero();
        optSaSpeedSlow = modCfg.getSmoothAimSpeedSlow();
        optSaSpeedFast = modCfg.getSmoothAimSpeedFast();
        optSaSlowZone  = modCfg.getSmoothAimSlowZone();
        optSaFastZone  = modCfg.getSmoothAimFastZone();

        optTargetEsp = modCfg.isTargetEspEnabled();
        optEntitiesEsp = modCfg.isEntitiesEspEnabled();
        optBlockEsp = modCfg.isBlockEspEnabled();
        optFairySoulsEsp = modCfg.isFairySoulsEspEnabled();
        optHotspotEsp = modCfg.isHotspotEspEnabled();
        optBlockEspRadius = modCfg.getBlockEspRadius();
        animTargetEsp = optTargetEsp ? 1f : 0f;
        animEntitiesEsp = optEntitiesEsp ? 1f : 0f;
        animBlockEsp = optBlockEsp ? 1f : 0f;
        animFairySoulsEsp = optFairySoulsEsp ? 1f : 0f;
        animHotspotEsp = optHotspotEsp ? 1f : 0f;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 40, PANEL_MAX_W);
        ph = Math.min(height - 40, PANEL_MAX_H);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        recalcScroll();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void drawScreen(DrawContext ctx, int mx, int my, float delta) {
        // Smooth scroll
        scrollSmooth = Anim.smooth(scrollSmooth, scrollY, 12f);

        // Panel background
        RoundedRectRenderer.draw(ctx, px, py, pw, ph, R, C_BG);

        // Header background
        ctx.fill(px, py, px + pw, py + HEADER_H, C_PANEL_HEAD);
        ctx.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, C_DIVIDER);

        drawHeader(ctx, mx, my);

        if (activeTab == 0) {
            drawMacrosTab(ctx, mx, my);
        } else if (activeTab == 1) {
            drawAutoFarmTab(ctx, mx, my);
        } else if (activeTab == 2) {
            drawVisualsTab(ctx, mx, my);
        } else {
            drawMiscTab(ctx, mx, my);
        }
    }

    // ─── Header ───────────────────────────────────────────────────────

    private void drawHeader(DrawContext ctx, int mx, int my) {
        // Title
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00A79\u2726 \u00A7f\u00A7lMacroMod"),
                px + 12, py + (HEADER_H - 8) / 2, C_TEXT);

        // Tab buttons (centered)
        tabW = 65; tabH = 22;
        int tabTotalW = tabW * 4 + 12;
        tabMacrosX = px + (pw - tabTotalW) / 2;
        tabFarmX = tabMacrosX + tabW + 4;
        tabVisualsX = tabFarmX + tabW + 4;
        tabMiscX = tabVisualsX + tabW + 4;
        tabY = py + (HEADER_H - tabH) / 2;

        boolean hovTab0 = mx >= tabMacrosX && mx < tabMacrosX + tabW && my >= tabY && my < tabY + tabH;
        boolean hovTab1 = mx >= tabFarmX && mx < tabFarmX + tabW && my >= tabY && my < tabY + tabH;
        boolean hovTab2 = mx >= tabVisualsX && mx < tabVisualsX + tabW && my >= tabY && my < tabY + tabH;
        boolean hovTab3 = mx >= tabMiscX && mx < tabMiscX + tabW && my >= tabY && my < tabY + tabH;

        RoundedRectRenderer.draw(ctx, tabMacrosX, tabY, tabW, tabH, RB,
                activeTab == 0 ? C_ACCENT : (hovTab0 ? C_NAV_ACT : C_NAV_BG));
        RoundedRectRenderer.draw(ctx, tabFarmX, tabY, tabW, tabH, RB,
                activeTab == 1 ? C_ACCENT : (hovTab1 ? C_NAV_ACT : C_NAV_BG));
        RoundedRectRenderer.draw(ctx, tabVisualsX, tabY, tabW, tabH, RB,
                activeTab == 2 ? C_ACCENT : (hovTab2 ? C_NAV_ACT : C_NAV_BG));
        RoundedRectRenderer.draw(ctx, tabMiscX, tabY, tabW, tabH, RB,
                activeTab == 3 ? C_ACCENT : (hovTab3 ? C_NAV_ACT : C_NAV_BG));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Macros"),
                tabMacrosX + tabW / 2, tabY + (tabH - 8) / 2, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Auto Farm"),
                tabFarmX + tabW / 2, tabY + (tabH - 8) / 2, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Visuals"),
                tabVisualsX + tabW / 2, tabY + (tabH - 8) / 2, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Misc"),
                tabMiscX + tabW / 2, tabY + (tabH - 8) / 2, C_TEXT);

        // Close button
        int cx = px + pw - 28, cy = py + (HEADER_H - 20) / 2;
        hoverClose = mx >= cx && mx < cx + 20 && my >= cy && my < cy + 20;
        RoundedRectRenderer.draw(ctx, cx, cy, 20, 20, RB,
                hoverClose ? 0xFFAA1818 : 0xFF6E1010);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2715"),
                cx + 10, cy + 6, C_TEXT);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Macros Tab
    // ═══════════════════════════════════════════════════════════════════

    private void drawMacrosTab(DrawContext ctx, int mx, int my) {
        // Sidebar / detail divider
        int vDiv = px + SIDEBAR_W;
        ctx.fill(vDiv, py + HEADER_H, vDiv + 1, py + ph, C_DIVIDER);
        // Footer divider
        ctx.fill(px, py + ph - FOOTER_H, px + pw, py + ph - FOOTER_H + 1, C_DIVIDER);

        drawSidebar(ctx, mx, my);
        drawDetail(ctx, mx, my);
    }

    // ─── Sidebar ──────────────────────────────────────────────────────

    private void drawSidebar(DrawContext ctx, int mx, int my) {
        List<Macro> macros = MacroModClient.getManager().getAll();
        int cardX = px + CARD_PX;
        int cardW = SIDEBAR_W - CARD_PX * 2;
        int listY0 = py + HEADER_H + 4;
        int listH = ph - HEADER_H - FOOTER_H - 8;

        // Scissor clip the macro list
        ctx.enableScissor(px, listY0, px + SIDEBAR_W, listY0 + listH);

        int smoothOff = (int) scrollSmooth;
        for (int i = 0; i < macros.size(); i++) {
            Macro m = macros.get(i);
            int cy = listY0 + i * (CARD_H + CARD_GAP) - smoothOff;
            if (cy + CARD_H < listY0 || cy > listY0 + listH) continue;

            boolean running = isRunning(m);
            boolean sel = isSel(m);
            boolean hov = mx >= cardX && mx < cardX + cardW && my >= cy && my < cy + CARD_H;

            RoundedRectRenderer.draw(ctx, cardX, cy, cardW, CARD_H, RB,
                    sel ? C_CARD_SEL : (hov ? C_CARD_HOV : C_CARD));

            // Left accent bar
            if (sel || running) {
                ctx.fill(cardX, cy + RB, cardX + 3, cy + CARD_H - RB,
                        running ? C_SUCCESS : C_ACCENT);
            }

            // Name
            String name = m.getName();
            if (name.length() > 20) name = name.substring(0, 18) + "..";
            ctx.drawTextWithShadow(textRenderer, Text.literal(name),
                    cardX + 8, cy + 8, C_TEXT);

            // Subtitle
            String sub = m.getSteps().size() + " steps \u00B7 " + m.getTotalBlockCount() + " blocks";
            ctx.drawTextWithShadow(textRenderer, Text.literal(sub),
                    cardX + 8, cy + 22, C_TEXT3);

            // Running indicator
            if (running) {
                ctx.drawTextWithShadow(textRenderer, Text.literal("\u25CF RUNNING"),
                        cardX + 8, cy + 34, C_SUCCESS);
            }
        }

        ctx.disableScissor();

        // New Macro button (in footer area)
        int nbW = 140, nbH = 22;
        int nbX = px + (SIDEBAR_W - nbW) / 2;
        int nbY = py + ph - FOOTER_H + (FOOTER_H - nbH) / 2;
        hoverNew = mx >= nbX && mx < nbX + nbW && my >= nbY && my < nbY + nbH;
        RoundedRectRenderer.draw(ctx, nbX, nbY, nbW, nbH, RB,
                hoverNew ? C_ACCENT_HI : C_ACCENT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("+ New Macro"),
                nbX + nbW / 2, nbY + 7, C_TEXT);
    }

    // ─── Detail panel ─────────────────────────────────────────────────

    private void drawDetail(DrawContext ctx, int mx, int my) {
        int dx = px + SIDEBAR_W + 12;
        int dw = pw - SIDEBAR_W - 22;
        int dy = py + HEADER_H + 10;

        if (selectedMacro == null) {
            String hint = "\u2190 Select a macro";
            ctx.drawTextWithShadow(textRenderer, Text.literal(hint),
                    dx + (dw - textRenderer.getWidth(hint)) / 2,
                    py + ph / 2 - 4, C_TEXT3);
            hoverRun = hoverEdit = hoverDelete = hoverDup = false;
            return;
        }

        Macro m = selectedMacro;

        // Name
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00A7f\u00A7l" + m.getName()),
                dx, dy, C_TEXT);
        dy += 14;

        // Description
        if (m.getDescription() != null && !m.getDescription().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(m.getDescription()),
                    dx, dy, C_TEXT2);
            dy += 12;
        }

        // Created date
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(m.getCreatedAt()));
        ctx.drawTextWithShadow(textRenderer, Text.literal("Created: \u00A77" + date),
                dx, dy, C_TEXT3);
        dy += 16;

        // Config chips
        int cx = dx;
        cx = drawChip(ctx, mx, my, cx, dy, "Loop", m.getConfig().isLoop(), 0) + 4;
        cx = drawChip(ctx, mx, my, cx, dy, "SkipMismatch", m.getConfig().isSkipMismatch(), 1) + 4;
        cx = drawChip(ctx, mx, my, cx, dy, "AttackDanger", m.getConfig().isAttackDanger(), 2) + 4;
        cx = drawChip(ctx, mx, my, cx, dy, "OnlyGround", m.getConfig().isOnlyGround(), 3) + 4;
        drawChip(ctx, mx, my, cx, dy, "LockCam", m.getConfig().isLockCrosshair(), 4);
        dy += 20;

        // Divider
        ctx.fill(dx, dy, dx + dw, dy + 1, C_DIVIDER);
        dy += 8;

        // Steps header
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("\u00A79Steps \u00A77(" + m.getSteps().size() + ")"),
                dx, dy, C_ACCENT);
        dy += 14;

        // Steps list
        int listBot = py + ph - FOOTER_H - 8;
        ctx.enableScissor(dx, dy, dx + dw, listBot);
        for (int i = 0; i < m.getSteps().size(); i++) {
            if (dy > listBot) break;
            MacroStep step = m.getSteps().get(i);
            String lbl = (i + 1) + ". " + (step.getLabel().length() > 28
                    ? step.getLabel().substring(0, 26) + ".." : step.getLabel());
            BlockPos dest = step.getDestination();
            String co = " \u00A77\u2192 " + dest.getX() + "," + dest.getY() + "," + dest.getZ()
                    + " (" + step.getTargets().size() + ")";
            ctx.drawTextWithShadow(textRenderer, Text.literal("\u00A7f" + lbl + "\u00A7a" + co),
                    dx + 4, dy, C_TEXT);
            dy += 12;
        }
        ctx.disableScissor();

        // Action buttons in footer
        actY = py + ph - FOOTER_H + (FOOTER_H - ACT_H) / 2;
        int gap = 6;
        actX[0] = dx;
        actX[1] = actX[0] + ACT_W + gap;
        actX[2] = actX[1] + ACT_W + gap;
        actX[3] = actX[2] + ACT_W + gap;

        boolean running = isRunning(m);

        hoverRun = hitBtn(mx, my, actX[0]);
        RoundedRectRenderer.draw(ctx, actX[0], actY, ACT_W, ACT_H, RB,
                running ? (hoverRun ? 0xFFAA1818 : 0xFF6E1010)
                        : (hoverRun ? 0xFF0CA844 : 0xFF0A6E32));
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(running ? "\u25A0 Stop" : "\u25B6 Run"),
                actX[0] + ACT_W / 2, actY + 7, C_TEXT);

        hoverEdit = hitBtn(mx, my, actX[1]);
        RoundedRectRenderer.draw(ctx, actX[1], actY, ACT_W, ACT_H, RB,
                hoverEdit ? C_ACCENT_HI : C_ACCENT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u270E Edit"),
                actX[1] + ACT_W / 2, actY + 7, C_TEXT);

        hoverDelete = hitBtn(mx, my, actX[2]);
        RoundedRectRenderer.draw(ctx, actX[2], actY, ACT_W, ACT_H, RB,
                hoverDelete ? 0xFFAA1818 : 0xFF6E1010);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2717 Delete"),
                actX[2] + ACT_W / 2, actY + 7, C_TEXT);

        hoverDup = hitBtn(mx, my, actX[3]);
        RoundedRectRenderer.draw(ctx, actX[3], actY, ACT_W, ACT_H, RB,
                hoverDup ? C_NAV_ACT : C_NAV_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2295 Copy"),
                actX[3] + ACT_W / 2, actY + 7, C_TEXT);
    }

    private int drawChip(DrawContext ctx, int mx, int my,
                          int x, int y, String label, boolean on, int idx) {
        String text = label + " " + (on ? "\u00A7aON" : "\u00A7cOFF");
        int w = textRenderer.getWidth(text) + 8;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 14;
        int bg = on ? (hov ? 0xFF0CA844 : 0xFF0A6E32) : (hov ? C_NAV_ACT : C_NAV_BG);
        RoundedRectRenderer.draw(ctx, x, y, w, 14, 3, bg);
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x + 4, y + 3, C_TEXT);
        chipBounds[idx][0] = x;
        chipBounds[idx][1] = y;
        chipBounds[idx][2] = w;
        return x + w;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Auto Farm Tab
    // ═══════════════════════════════════════════════════════════════════

    private void drawAutoFarmTab(DrawContext ctx, int mx, int my) {
        // Animate toggles (per-frame)
        animFish = Anim.smooth(animFish, optAutoFish ? 1f : 0f, 20f);
        animFishAtk = Anim.smooth(animFishAtk, optFishAttack ? 1f : 0f, 20f);
        animMobs = Anim.smooth(animMobs, optAttackMobs ? 1f : 0f, 20f);
        animCameraJitter = Anim.smooth(animCameraJitter, optCameraJitter ? 1f : 0f, 20f);
        animAutoDeposit  = Anim.smooth(animAutoDeposit,  optAutoDeposit  ? 1f : 0f, 20f);
        animFishMove     = Anim.smooth(animFishMove,     optFishMove     ? 1f : 0f, 20f);

        for (int[] b : fishTgBounds) b[2] = -1;
        for (int[] h : hotbar) h[0] = -1;
        modeBtnW = 0;

        int lx = px + 24;
        int rEdge = px + pw - 24;
        int dy = py + HEADER_H + 18;

        // ── Fishing section ───────────────────────────────────────────
        dy = drawSectionHeader(ctx, lx, dy, "Fishing");
        dy = drawFarmToggleRow(ctx, mx, my, lx, rEdge, dy, "Auto Fish", animFish, 0) + 10;

        if (optAutoFish) {
            ctx.fill(lx + 16, dy - 8, rEdge, dy - 7, C_DIVIDER);
            dy = drawFarmToggleRow(ctx, mx, my, lx + 16, rEdge, dy, "Attack fish", animFishAtk, 1) + 8;

            if (optFishAttack) {
                // Mode button
                String modeLabel = "Mode: " + (optFishAttackDistance ? "Distance" : "Close");
                int mw = textRenderer.getWidth(modeLabel) + 18, mh = 22;
                modeBtnX = lx + 32; modeBtnY = dy; modeBtnW = mw; modeBtnH = mh;
                boolean mhov = mx >= modeBtnX && mx < modeBtnX + mw && my >= dy && my < dy + mh;
                RoundedRectRenderer.draw(ctx, modeBtnX, modeBtnY, mw, mh, RB,
                        mhov ? C_NAV_ACT : C_NAV_BG);
                ctx.drawTextWithShadow(textRenderer, Text.literal(modeLabel),
                        modeBtnX + 9, modeBtnY + (mh - 8) / 2, C_TEXT2);
                dy += mh + 8;

                // Move toggle (only in close mode)
                if (!optFishAttackDistance) {
                    dy = drawFarmToggleRow(ctx, mx, my, lx + 32, rEdge, dy, "Move", animFishMove, 5) + 8;
                }

                // Hotbar item selector
                ctx.drawTextWithShadow(textRenderer, Text.literal("Attack item:"),
                        lx + 32, dy, C_TEXT2);
                dy += 14;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    int slot = 0;
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (stack.isEmpty()) { hotbar[i][0] = -1; continue; }
                        int bx = lx + 32 + slot * 22;
                        boolean sel = optFishAttackSlot == i;
                        boolean shov = mx >= bx && mx < bx + 20 && my >= dy && my < dy + 20;
                        RoundedRectRenderer.draw(ctx, bx, dy, 20, 20, 3,
                                sel ? C_ACCENT : (shov ? C_NAV_ACT : C_NAV_BG));
                        ctx.drawItem(stack, bx + 2, dy + 2);
                        hotbar[i][0] = bx; hotbar[i][1] = dy;
                        slot++;
                    }
                    dy += 26;
                }
            }
            dy = drawFarmToggleRow(ctx, mx, my, lx + 16, rEdge, dy, "AFK Jitter",   animCameraJitter, 3) + 8;
            dy = drawFarmToggleRow(ctx, mx, my, lx + 16, rEdge, dy, "Auto Deposit", animAutoDeposit,  4) + 8;
        }

        // ── Combat section ────────────────────────────────────────────
        dy += 6;
        dy = drawSectionHeader(ctx, lx, dy, "Combat");
        dy = drawFarmToggleRow(ctx, mx, my, lx, rEdge, dy, "Attack Mobs Near Farm", animMobs, 2) + 10;

        // Radius cycler
        String radVal = optFarmRadius + " blk";
        int radW = textRenderer.getWidth(radVal) + 12;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Radius:"), lx, dy + 4, C_TEXT2);
        int radValX = rEdge - radW - 28;
        radLeftX = radValX - 20; radRightX = radValX + radW + 4; radBtnY = dy;
        boolean hL = mx >= radLeftX && mx < radLeftX + 16 && my >= dy && my < dy + 18;
        boolean hR = mx >= radRightX && mx < radRightX + 16 && my >= dy && my < dy + 18;
        RoundedRectRenderer.draw(ctx, radLeftX, dy, 16, 18, RB, hL ? C_NAV_ACT : C_NAV_BG);
        RoundedRectRenderer.draw(ctx, radRightX, dy, 16, 18, RB, hR ? C_NAV_ACT : C_NAV_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), radLeftX + 8, dy + 5, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), radRightX + 8, dy + 5, C_TEXT);
        RoundedRectRenderer.draw(ctx, radValX, dy, radW, 18, RB, C_CARD);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(radVal),
                radValX + radW / 2, dy + 5, C_TEXT);
        dy += 30;

        // ── Auto Farmer section ───────────────────────────────────────
        dy += 6;
        dy = drawSectionHeader(ctx, lx, dy, "Auto Farmer");

        // Toggle
        animFarmer = Anim.smooth(animFarmer, optAutoFarmer ? 1f : 0f, 20f);
        int ftw = ToggleRenderer.TOGGLE_W, fth = ToggleRenderer.TOGGLE_H;
        int fRowH = Math.max(fth + 4, 20);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Auto Farmer"),
                lx, dy + (fRowH - 8) / 2, C_TEXT);
        int ftgX = rEdge - ftw, ftgY = dy + (fRowH - fth) / 2;
        ToggleRenderer.draw(ctx, ftgX, ftgY, animFarmer);
        farmerTgBounds[0] = ftgX; farmerTgBounds[1] = ftgY;
        farmerTgBounds[2] = ftw;  farmerTgBounds[3] = fth;
        dy += fRowH + 8;

        // Direction dropdown button
        String dirLabel = "Start Direction: " + optFarmerDir.name();
        int dirW = textRenderer.getWidth(dirLabel) + 18, dirH = 22;
        farmerDirBtnX = lx + 16; farmerDirBtnY = dy; farmerDirBtnW = dirW; farmerDirBtnH = dirH;
        boolean dirHov = mx >= farmerDirBtnX && mx < farmerDirBtnX + dirW && my >= dy && my < dy + dirH;
        RoundedRectRenderer.draw(ctx, farmerDirBtnX, farmerDirBtnY, dirW, dirH, RB,
                dirHov ? C_NAV_ACT : C_NAV_BG);
        ctx.drawTextWithShadow(textRenderer, Text.literal(dirLabel),
                farmerDirBtnX + 9, farmerDirBtnY + (dirH - 8) / 2, C_TEXT2);
        dy += dirH + 10;

        // ── Auto Attack section ───────────────────────────────────────────────
        dy += 6;
        dy = drawSectionHeader(ctx, lx, dy, "Auto Attack");

        // Toggle
        animAutoAttack = Anim.smooth(animAutoAttack, optAutoAttack ? 1f : 0f, 20f);
        animRandomCps  = Anim.smooth(animRandomCps,  optRandomCps  ? 1f : 0f, 20f);
        int aatw = ToggleRenderer.TOGGLE_W, aath = ToggleRenderer.TOGGLE_H;
        int aaRowH = Math.max(aath + 4, 20);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Auto Attack"),
                lx, dy + (aaRowH - 8) / 2, C_TEXT);
        int aaTogX = rEdge - aatw, aaTogY = dy + (aaRowH - aath) / 2;
        ToggleRenderer.draw(ctx, aaTogX, aaTogY, animAutoAttack);
        attackTgBounds[0] = aaTogX; attackTgBounds[1] = aaTogY;
        attackTgBounds[2] = aatw;   attackTgBounds[3] = aath;
        dy += aaRowH + 8;

        // Priority mode button
        String prioLabel = "Priority: " + optAttackPriority.name();
        int prioW = textRenderer.getWidth(prioLabel) + 18, prioH = 22;
        attackPriorityBtnX = lx + 16; attackPriorityBtnY = dy;
        attackPriorityBtnW = prioW;   attackPriorityBtnH = prioH;
        boolean prioHov = mx >= attackPriorityBtnX && mx < attackPriorityBtnX + prioW
                && my >= dy && my < dy + prioH;
        RoundedRectRenderer.draw(ctx, attackPriorityBtnX, attackPriorityBtnY, prioW, prioH, RB,
                prioHov ? C_NAV_ACT : C_NAV_BG);
        ctx.drawTextWithShadow(textRenderer, Text.literal(prioLabel),
                attackPriorityBtnX + 9, attackPriorityBtnY + (prioH - 8) / 2, C_TEXT2);
        dy += prioH + 10;

        // Attack range cycler
        String rangeVal = String.format("%.1f blk", optAttackRange);
        int rangeValW = textRenderer.getWidth(rangeVal) + 12;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Range:"), lx + 16, dy + 4, C_TEXT2);
        int rangeValX = rEdge - rangeValW - 28;
        attackRangeLeftX  = rangeValX - 20;
        attackRangeRightX = rangeValX + rangeValW + 4;
        attackRangeBtnY   = dy;
        boolean hAL = mx >= attackRangeLeftX  && mx < attackRangeLeftX  + 16 && my >= dy && my < dy + 18;
        boolean hAR = mx >= attackRangeRightX && mx < attackRangeRightX + 16 && my >= dy && my < dy + 18;
        RoundedRectRenderer.draw(ctx, attackRangeLeftX,  dy, 16, 18, RB, hAL ? C_NAV_ACT : C_NAV_BG);
        RoundedRectRenderer.draw(ctx, attackRangeRightX, dy, 16, 18, RB, hAR ? C_NAV_ACT : C_NAV_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), attackRangeLeftX + 8,  dy + 5, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), attackRangeRightX + 8, dy + 5, C_TEXT);
        RoundedRectRenderer.draw(ctx, rangeValX, dy, rangeValW, 18, RB, C_CARD);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(rangeVal),
                rangeValX + rangeValW / 2, dy + 5, C_TEXT);
        dy += 30;

        // Attack item selector
        ctx.drawTextWithShadow(textRenderer, Text.literal("Attack item:"),
                lx + 16, dy, C_TEXT2);
        dy += 14;
        MinecraftClient mc2 = MinecraftClient.getInstance();
        for (int[] ab : attackHotbar) ab[0] = -1;
        if (mc2.player != null) {
            int slot = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc2.player.getInventory().getStack(i);
                if (stack.isEmpty()) { attackHotbar[i][0] = -1; continue; }
                int bx = lx + 16 + slot * 22;
                boolean sel = optAttackItemSlot == i;
                boolean shov = mx >= bx && mx < bx + 20 && my >= dy && my < dy + 20;
                RoundedRectRenderer.draw(ctx, bx, dy, 20, 20, 3,
                        sel ? C_ACCENT : (shov ? C_NAV_ACT : C_NAV_BG));
                ctx.drawItem(stack, bx + 2, dy + 2);
                attackHotbar[i][0] = bx; attackHotbar[i][1] = dy;
                slot++;
            }
            dy += 26;
        }

        // Random CPS toggle
        int rcRowH = Math.max(aath + 4, 20);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Random CPS (7–11)"),
                lx + 16, dy + (rcRowH - 8) / 2, C_TEXT2);
        int rcTogX = rEdge - aatw, rcTogY = dy + (rcRowH - aath) / 2;
        ToggleRenderer.draw(ctx, rcTogX, rcTogY, animRandomCps);
        randomCpsTgBounds[0] = rcTogX; randomCpsTgBounds[1] = rcTogY;
        randomCpsTgBounds[2] = aatw;   randomCpsTgBounds[3] = aath;
        dy += rcRowH + 10;

        // Footer
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Settings apply while macros run."),
                lx, dy, C_TEXT3);
    }

    private int drawSectionHeader(DrawContext ctx, int x, int y, String title) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(title), x, y, C_TEXT);
        RoundedRectRenderer.draw(ctx, x, y + 12, textRenderer.getWidth(title), 2, 1, C_ACCENT);
        return y + 22;
    }

    private int drawFarmToggleRow(DrawContext ctx, int mx, int my,
                                   int x, int rEdge, int y, String label, float anim, int idx) {
        int tw = ToggleRenderer.TOGGLE_W, th = ToggleRenderer.TOGGLE_H;
        int rowH = Math.max(th + 4, 20);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label),
                x, y + (rowH - 8) / 2, C_TEXT);
        int tgX = rEdge - tw, tgY = y + (rowH - th) / 2;
        ToggleRenderer.draw(ctx, tgX, tgY, anim);
        fishTgBounds[idx][0] = tgX; fishTgBounds[idx][1] = tgY;
        fishTgBounds[idx][2] = tw;  fishTgBounds[idx][3] = th;
        return y + rowH;
    }

    private void drawMiscTab(DrawContext ctx, int mx, int my) {
        int lx = px + 24;
        int rEdge = px + pw - 24;
        int dy = py + HEADER_H + 18;

        // ── Freelook section ──────────────────────────────────────────
        dy = drawSectionHeader(ctx, lx, dy, "Freelook");

        animFreelook = Anim.smooth(animFreelook, optFreelook ? 1f : 0f, 20f);
        int tw = ToggleRenderer.TOGGLE_W, th = ToggleRenderer.TOGGLE_H;
        int rowH = Math.max(th + 4, 20);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Enable Freelook"),
                lx, dy + (rowH - 8) / 2, C_TEXT);
        int flTgX = rEdge - tw, flTgY = dy + (rowH - th) / 2;
        ToggleRenderer.draw(ctx, flTgX, flTgY, animFreelook);
        freelookTgBounds[0] = flTgX; freelookTgBounds[1] = flTgY;
        freelookTgBounds[2] = tw;    freelookTgBounds[3] = th;
        dy += rowH + 10;

        // FOV cycler
        String fovVal = optFOV + "\u00B0";
        int fovW = textRenderer.getWidth(fovVal) + 12;
        ctx.drawTextWithShadow(textRenderer, Text.literal("FOV:"), lx + 16, dy + 4, C_TEXT2);
        int fovValX = rEdge - fovW - 28;
        fovLeftX = fovValX - 20; fovRightX = fovValX + fovW + 4; fovBtnY = dy;
        boolean fhL = mx >= fovLeftX && mx < fovLeftX + 16 && my >= dy && my < dy + 18;
        boolean fhR = mx >= fovRightX && mx < fovRightX + 16 && my >= dy && my < dy + 18;
        RoundedRectRenderer.draw(ctx, fovLeftX, dy, 16, 18, RB, fhL ? C_NAV_ACT : C_NAV_BG);
        RoundedRectRenderer.draw(ctx, fovRightX, dy, 16, 18, RB, fhR ? C_NAV_ACT : C_NAV_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), fovLeftX + 8, dy + 5, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), fovRightX + 8, dy + 5, C_TEXT);
        RoundedRectRenderer.draw(ctx, fovValX, dy, fovW, 18, RB, C_CARD);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(fovVal),
                fovValX + fovW / 2, dy + 5, C_TEXT);
        dy += 30;

        // ── Debug section ─────────────────────────────────────────────
        dy += 6;
        dy = drawSectionHeader(ctx, lx, dy, "Debug");

        animDebugLogging = Anim.smooth(animDebugLogging, optDebugLogging ? 1f : 0f, 20f);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Enable Debug Logs"),
                lx, dy + (rowH - 8) / 2, C_TEXT);
        int dlTgX = rEdge - tw, dlTgY = dy + (rowH - th) / 2;
        ToggleRenderer.draw(ctx, dlTgX, dlTgY, animDebugLogging);
        debugLogTgBounds[0] = dlTgX; debugLogTgBounds[1] = dlTgY;
        debugLogTgBounds[2] = tw;    debugLogTgBounds[3] = th;
        dy += rowH + 10;

        // ── Smooth Aim section ────────────────────────────────────────

        dy += 6;
        dy = drawSectionHeader(ctx, lx, dy, "Smooth Aim");

        // Base Lerp
        {
            String val = String.format("%.3f", optSaBaseLerp);
            int vw = textRenderer.getWidth(val) + 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal("Base Lerp"), lx + 16, dy + 4, C_TEXT2);
            int vx = rEdge - vw - 28;
            saBaseLerpLeftX = vx - 20; saBaseLerpRightX = vx + vw + 4; saBaseLerpBtnY = dy;
            boolean hl = mx >= saBaseLerpLeftX && mx < saBaseLerpLeftX + 16 && my >= dy && my < dy + 18;
            boolean hr = mx >= saBaseLerpRightX && mx < saBaseLerpRightX + 16 && my >= dy && my < dy + 18;
            RoundedRectRenderer.draw(ctx, saBaseLerpLeftX, dy, 16, 18, RB, hl ? C_NAV_ACT : C_NAV_BG);
            RoundedRectRenderer.draw(ctx, saBaseLerpRightX, dy, 16, 18, RB, hr ? C_NAV_ACT : C_NAV_BG);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), saBaseLerpLeftX + 8, dy + 5, C_TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), saBaseLerpRightX + 8, dy + 5, C_TEXT);
            RoundedRectRenderer.draw(ctx, vx, dy, vw, 18, RB, C_CARD);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(val), vx + vw / 2, dy + 5, C_TEXT);
            dy += 26;
        }

        // Speed (close)
        {
            String val = String.format("%.2f", optSaSpeedZero);
            int vw = textRenderer.getWidth(val) + 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal("Speed (close)"), lx + 16, dy + 4, C_TEXT2);
            int vx = rEdge - vw - 28;
            saSpeedZeroLeftX = vx - 20; saSpeedZeroRightX = vx + vw + 4; saSpeedZeroBtnY = dy;
            boolean hl = mx >= saSpeedZeroLeftX && mx < saSpeedZeroLeftX + 16 && my >= dy && my < dy + 18;
            boolean hr = mx >= saSpeedZeroRightX && mx < saSpeedZeroRightX + 16 && my >= dy && my < dy + 18;
            RoundedRectRenderer.draw(ctx, saSpeedZeroLeftX, dy, 16, 18, RB, hl ? C_NAV_ACT : C_NAV_BG);
            RoundedRectRenderer.draw(ctx, saSpeedZeroRightX, dy, 16, 18, RB, hr ? C_NAV_ACT : C_NAV_BG);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), saSpeedZeroLeftX + 8, dy + 5, C_TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), saSpeedZeroRightX + 8, dy + 5, C_TEXT);
            RoundedRectRenderer.draw(ctx, vx, dy, vw, 18, RB, C_CARD);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(val), vx + vw / 2, dy + 5, C_TEXT);
            dy += 26;
        }

        // Speed (mid)
        {
            String val = String.format("%.2f", optSaSpeedSlow);
            int vw = textRenderer.getWidth(val) + 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal("Speed (mid)"), lx + 16, dy + 4, C_TEXT2);
            int vx = rEdge - vw - 28;
            saSpeedSlowLeftX = vx - 20; saSpeedSlowRightX = vx + vw + 4; saSpeedSlowBtnY = dy;
            boolean hl = mx >= saSpeedSlowLeftX && mx < saSpeedSlowLeftX + 16 && my >= dy && my < dy + 18;
            boolean hr = mx >= saSpeedSlowRightX && mx < saSpeedSlowRightX + 16 && my >= dy && my < dy + 18;
            RoundedRectRenderer.draw(ctx, saSpeedSlowLeftX, dy, 16, 18, RB, hl ? C_NAV_ACT : C_NAV_BG);
            RoundedRectRenderer.draw(ctx, saSpeedSlowRightX, dy, 16, 18, RB, hr ? C_NAV_ACT : C_NAV_BG);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), saSpeedSlowLeftX + 8, dy + 5, C_TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), saSpeedSlowRightX + 8, dy + 5, C_TEXT);
            RoundedRectRenderer.draw(ctx, vx, dy, vw, 18, RB, C_CARD);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(val), vx + vw / 2, dy + 5, C_TEXT);
            dy += 26;
        }

        // Speed (far)
        {
            String val = String.format("%.2f", optSaSpeedFast);
            int vw = textRenderer.getWidth(val) + 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal("Speed (far)"), lx + 16, dy + 4, C_TEXT2);
            int vx = rEdge - vw - 28;
            saSpeedFastLeftX = vx - 20; saSpeedFastRightX = vx + vw + 4; saSpeedFastBtnY = dy;
            boolean hl = mx >= saSpeedFastLeftX && mx < saSpeedFastLeftX + 16 && my >= dy && my < dy + 18;
            boolean hr = mx >= saSpeedFastRightX && mx < saSpeedFastRightX + 16 && my >= dy && my < dy + 18;
            RoundedRectRenderer.draw(ctx, saSpeedFastLeftX, dy, 16, 18, RB, hl ? C_NAV_ACT : C_NAV_BG);
            RoundedRectRenderer.draw(ctx, saSpeedFastRightX, dy, 16, 18, RB, hr ? C_NAV_ACT : C_NAV_BG);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), saSpeedFastLeftX + 8, dy + 5, C_TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), saSpeedFastRightX + 8, dy + 5, C_TEXT);
            RoundedRectRenderer.draw(ctx, vx, dy, vw, 18, RB, C_CARD);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(val), vx + vw / 2, dy + 5, C_TEXT);
            dy += 26;
        }

        // Slow zone (°)
        {
            String val = String.format("%.0f\u00B0", optSaSlowZone);
            int vw = textRenderer.getWidth(val) + 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal("Slow zone"), lx + 16, dy + 4, C_TEXT2);
            int vx = rEdge - vw - 28;
            saSlowZoneLeftX = vx - 20; saSlowZoneRightX = vx + vw + 4; saSlowZoneBtnY = dy;
            boolean hl = mx >= saSlowZoneLeftX && mx < saSlowZoneLeftX + 16 && my >= dy && my < dy + 18;
            boolean hr = mx >= saSlowZoneRightX && mx < saSlowZoneRightX + 16 && my >= dy && my < dy + 18;
            RoundedRectRenderer.draw(ctx, saSlowZoneLeftX, dy, 16, 18, RB, hl ? C_NAV_ACT : C_NAV_BG);
            RoundedRectRenderer.draw(ctx, saSlowZoneRightX, dy, 16, 18, RB, hr ? C_NAV_ACT : C_NAV_BG);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), saSlowZoneLeftX + 8, dy + 5, C_TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), saSlowZoneRightX + 8, dy + 5, C_TEXT);
            RoundedRectRenderer.draw(ctx, vx, dy, vw, 18, RB, C_CARD);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(val), vx + vw / 2, dy + 5, C_TEXT);
            dy += 26;
        }

        // Fast zone (°)
        {
            String val = String.format("%.0f\u00B0", optSaFastZone);
            int vw = textRenderer.getWidth(val) + 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal("Fast zone"), lx + 16, dy + 4, C_TEXT2);
            int vx = rEdge - vw - 28;
            saFastZoneLeftX = vx - 20; saFastZoneRightX = vx + vw + 4; saFastZoneBtnY = dy;
            boolean hl = mx >= saFastZoneLeftX && mx < saFastZoneLeftX + 16 && my >= dy && my < dy + 18;
            boolean hr = mx >= saFastZoneRightX && mx < saFastZoneRightX + 16 && my >= dy && my < dy + 18;
            RoundedRectRenderer.draw(ctx, saFastZoneLeftX, dy, 16, 18, RB, hl ? C_NAV_ACT : C_NAV_BG);
            RoundedRectRenderer.draw(ctx, saFastZoneRightX, dy, 16, 18, RB, hr ? C_NAV_ACT : C_NAV_BG);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), saFastZoneLeftX + 8, dy + 5, C_TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), saFastZoneRightX + 8, dy + 5, C_TEXT);
            RoundedRectRenderer.draw(ctx, vx, dy, vw, 18, RB, C_CARD);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(val), vx + vw / 2, dy + 5, C_TEXT);
            dy += 26;
        }

        // Footer
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Edit config file for advanced settings."),
                lx, dy, C_TEXT3);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Visuals Tab
    // ═══════════════════════════════════════════════════════════════════

    private void drawVisualsTab(DrawContext ctx, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ModConfig modCfg = MacroModClient.getConfigManager().getConfig();

        // Periodic nearby scan
        visualsScanCounter++;
        if (visualsScanCounter >= VISUALS_SCAN_INTERVAL) {
            visualsScanCounter = 0;
            scanNearbyEntities(mc);
            scanNearbyBlocks(mc, modCfg);
        }
        if (nearbyEntityTypes.isEmpty() && nearbyBlockTypes.isEmpty() && visualsScanCounter == 1) {
            scanNearbyEntities(mc);
            scanNearbyBlocks(mc, modCfg);
        }

        int lx = px + 24;
        int rEdge = px + pw - 24;
        int tw = ToggleRenderer.TOGGLE_W, th = ToggleRenderer.TOGGLE_H;
        int rowH = Math.max(th + 4, 20);

        // Compute total content height for scrolling
        int contentH = 0;
        contentH += 18 + rowH + 6 + 16; // Target ESP header+toggle+desc
        contentH += 10 + 18 + rowH + 8; // Entities ESP header+toggle
        contentH += 14 + nearbyEntityTypes.size() * LIST_ITEM_H + 8; // entity list
        contentH += 10 + 18 + rowH + 8; // Block ESP header+toggle
        contentH += 28 + 14; // radius cycler
        contentH += nearbyBlockTypes.size() * LIST_ITEM_H + 18; // block list + footer
        contentH += 10 + 18 + rowH + 6; // Hypixel header+Fairy Souls toggle+desc
        contentH += rowH + 6 + 18; // Hotspot ESP toggle+desc
        int availH = ph - HEADER_H - 18;
        visualsMaxScrollY = Math.max(0, contentH - availH);
        visualsScrollY = Math.min(visualsScrollY, visualsMaxScrollY);

        // Enable scissor for scrollable content
        ctx.enableScissor(px, py + HEADER_H, px + pw, py + ph);

        int dy = py + HEADER_H + 18 - visualsScrollY;

        Set<String> entityWL = new HashSet<>(modCfg.getEntityWhitelist());
        Set<String> blockWL  = new HashSet<>(modCfg.getBlockWhitelist());

        // ── Hypixel section ───────────────────────────────────────────
        dy += 10;
        dy = drawSectionHeader(ctx, lx, dy, "Hypixel");

        animFairySoulsEsp = Anim.smooth(animFairySoulsEsp, optFairySoulsEsp ? 1f : 0f, 20f);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Fairy Souls ESP"),
                lx, dy + (rowH - 8) / 2, C_TEXT);
        int fsTgX = rEdge - tw, fsTgY = dy + (rowH - th) / 2;
        ToggleRenderer.draw(ctx, fsTgX, fsTgY, animFairySoulsEsp);
        fairySoulsEspTgBounds[0] = fsTgX; fairySoulsEspTgBounds[1] = fsTgY;
        fairySoulsEspTgBounds[2] = tw;    fairySoulsEspTgBounds[3] = th;
        dy += rowH + 6;
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Cyan box around Fairy Souls (Armor Stands)"),
                lx + 16, dy, C_TEXT3);
        dy += 18;

        animHotspotEsp = Anim.smooth(animHotspotEsp, optHotspotEsp ? 1f : 0f, 20f);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Hotspot ESP"),
                lx, dy + (rowH - 8) / 2, C_TEXT);
        int hsTgX = rEdge - tw, hsTgY = dy + (rowH - th) / 2;
        ToggleRenderer.draw(ctx, hsTgX, hsTgY, animHotspotEsp);
        hotspotEspTgBounds[0] = hsTgX; hotspotEspTgBounds[1] = hsTgY;
        hotspotEspTgBounds[2] = tw;    hotspotEspTgBounds[3] = th;
        dy += rowH + 6;
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Pink circle on fishing hotspots (Hypixel SkyBlock)"),
                lx + 16, dy, C_TEXT3);
        dy += 18;

        // ── Target ESP section ────────────────────────────────────────
        dy = drawSectionHeader(ctx, lx, dy, "Target ESP");

        animTargetEsp = Anim.smooth(animTargetEsp, optTargetEsp ? 1f : 0f, 20f);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Show Target Box"),
                lx, dy + (rowH - 8) / 2, C_TEXT);
        int teTgX = rEdge - tw, teTgY = dy + (rowH - th) / 2;
        ToggleRenderer.draw(ctx, teTgX, teTgY, animTargetEsp);
        targetEspTgBounds[0] = teTgX; targetEspTgBounds[1] = teTgY;
        targetEspTgBounds[2] = tw;    targetEspTgBounds[3] = th;
        dy += rowH + 6;
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Red box around current attack target"),
                lx + 16, dy, C_TEXT3);
        dy += 18;

        // ── Entities ESP section ──────────────────────────────────────
        dy += 10;
        dy = drawSectionHeader(ctx, lx, dy, "Entities ESP");

        animEntitiesEsp = Anim.smooth(animEntitiesEsp, optEntitiesEsp ? 1f : 0f, 20f);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Enable Entities ESP"),
                lx, dy + (rowH - 8) / 2, C_TEXT);
        int eeTgX = rEdge - tw, eeTgY = dy + (rowH - th) / 2;
        ToggleRenderer.draw(ctx, eeTgX, eeTgY, animEntitiesEsp);
        entitiesEspTgBounds[0] = eeTgX; entitiesEspTgBounds[1] = eeTgY;
        entitiesEspTgBounds[2] = tw;    entitiesEspTgBounds[3] = th;
        dy += rowH + 8;

        // Entity list (clickable rows)
        ctx.drawTextWithShadow(textRenderer, Text.literal("Nearby (20 blk):"),
                lx + 8, dy, C_TEXT2);
        dy += 14;
        entityListY = dy;
        if (nearbyEntityTypes.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("  No entities nearby"),
                    lx + 8, dy, C_TEXT3);
            dy += LIST_ITEM_H;
        } else {
            for (String eId : nearbyEntityTypes) {
                boolean selected = entityWL.contains(eId);
                String label = shortId(eId);
                int itemColor = selected ? C_SUCCESS : C_TEXT3;
                String prefix = selected ? "\u2714 " : "\u2718 ";
                boolean hovered = mx >= lx + 8 && mx < rEdge && my >= dy && my < dy + LIST_ITEM_H;
                if (hovered) {
                    ctx.fill(lx + 4, dy - 1, rEdge, dy + LIST_ITEM_H - 1, 0x22FFFFFF);
                }
                ctx.drawTextWithShadow(textRenderer, Text.literal(prefix + label),
                        lx + 12, dy + 2, itemColor);
                dy += LIST_ITEM_H;
            }
        }
        dy += 8;

        // ── Blocks ESP section ────────────────────────────────────────
        dy += 10;
        dy = drawSectionHeader(ctx, lx, dy, "Blocks ESP");

        animBlockEsp = Anim.smooth(animBlockEsp, optBlockEsp ? 1f : 0f, 20f);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Enable Blocks ESP"),
                lx, dy + (rowH - 8) / 2, C_TEXT);
        int beTgX = rEdge - tw, beTgY = dy + (rowH - th) / 2;
        ToggleRenderer.draw(ctx, beTgX, beTgY, animBlockEsp);
        blockEspTgBounds[0] = beTgX; blockEspTgBounds[1] = beTgY;
        blockEspTgBounds[2] = tw;    blockEspTgBounds[3] = th;
        dy += rowH + 8;

        // Block ESP radius cycler
        String brVal = optBlockEspRadius + " blk";
        int brW = textRenderer.getWidth(brVal) + 12;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Scan Radius:"), lx + 16, dy + 4, C_TEXT2);
        int brValX = rEdge - brW - 28;
        blockRadiusLeftX  = brValX - 20;
        blockRadiusRightX = brValX + brW + 4;
        blockRadiusBtnY   = dy;
        boolean brHL = mx >= blockRadiusLeftX  && mx < blockRadiusLeftX  + 16 && my >= dy && my < dy + 18;
        boolean brHR = mx >= blockRadiusRightX && mx < blockRadiusRightX + 16 && my >= dy && my < dy + 18;
        RoundedRectRenderer.draw(ctx, blockRadiusLeftX,  dy, 16, 18, RB, brHL ? C_NAV_ACT : C_NAV_BG);
        RoundedRectRenderer.draw(ctx, blockRadiusRightX, dy, 16, 18, RB, brHR ? C_NAV_ACT : C_NAV_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), blockRadiusLeftX + 8,  dy + 5, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), blockRadiusRightX + 8, dy + 5, C_TEXT);
        RoundedRectRenderer.draw(ctx, brValX, dy, brW, 18, RB, C_CARD);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(brVal),
                brValX + brW / 2, dy + 5, C_TEXT);
        dy += 28;

        // Block list (clickable rows)
        ctx.drawTextWithShadow(textRenderer, Text.literal("Nearby blocks:"),
                lx + 8, dy, C_TEXT2);
        dy += 14;
        blockListY = dy;
        if (nearbyBlockTypes.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("  No interesting blocks nearby"),
                    lx + 8, dy, C_TEXT3);
            dy += LIST_ITEM_H;
        } else {
            for (String bId : nearbyBlockTypes) {
                boolean selected = blockWL.contains(bId);
                String label = shortId(bId);
                int itemColor = selected ? C_SUCCESS : C_TEXT3;
                String prefix = selected ? "\u2714 " : "\u2718 ";
                boolean hovered = mx >= lx + 8 && mx < rEdge && my >= dy && my < dy + LIST_ITEM_H;
                if (hovered) {
                    ctx.fill(lx + 4, dy - 1, rEdge, dy + LIST_ITEM_H - 1, 0x22FFFFFF);
                }
                ctx.drawTextWithShadow(textRenderer, Text.literal(prefix + label),
                        lx + 12, dy + 2, itemColor);
                dy += LIST_ITEM_H;
            }
        }
        dy += 8;

        ctx.disableScissor();
    }

    /** Strip "minecraft:" prefix for display, keep modded namespace. */
    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    /** Scan nearby living entities within 20 blocks and cache unique type IDs. */
    private void scanNearbyEntities(MinecraftClient mc) {
        nearbyEntityTypes.clear();
        if (mc.player == null || mc.world == null) return;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        double r = 20;
        Box box = new Box(px - r, py - r, pz - r, px + r, py + r, pz + r);
        Set<String> seen = new LinkedHashSet<>();
        for (Entity e : mc.world.getEntitiesByClass(LivingEntity.class, box,
                le -> le != mc.player && le.isAlive()
                        && !(le instanceof net.minecraft.entity.player.PlayerEntity)
                        && !(le instanceof ArmorStandEntity)
                        && !le.isInvisible())) {
            String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
            seen.add(id);
        }
        nearbyEntityTypes.addAll(seen);
    }

    /** Scan nearby blocks within ESP radius and cache unique non-boring type IDs. */
    private void scanNearbyBlocks(MinecraftClient mc, ModConfig cfg) {
        nearbyBlockTypes.clear();
        if (mc.player == null || mc.world == null) return;
        BlockPos center = mc.player.getBlockPos();
        int r = cfg.getBlockEspRadius();
        Set<String> seen = new LinkedHashSet<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    String id = Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).toString();
                    if (!BORING_BLOCKS.contains(id)) {
                        seen.add(id);
                    }
                }
            }
        }
        nearbyBlockTypes.addAll(seen);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Input
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();
        int imx = (int) mx, imy = (int) my;

        // Outside panel
        if (imx < px || imx > px + pw || imy < py || imy > py + ph) {
            animClose();
            return true;
        }

        // Close button
        if (hoverClose) { animClose(); return true; }

        // Tab buttons
        if (imy >= tabY && imy < tabY + tabH) {
            if (imx >= tabMacrosX && imx < tabMacrosX + tabW)     { activeTab = 0; return true; }
            if (imx >= tabFarmX && imx < tabFarmX + tabW)         { activeTab = 1; return true; }
            if (imx >= tabVisualsX && imx < tabVisualsX + tabW)   { activeTab = 2; return true; }
            if (imx >= tabMiscX && imx < tabMiscX + tabW)         { activeTab = 3; return true; }
        }

        if (activeTab == 0) return handleMacrosClick(imx, imy);
        else if (activeTab == 1) return handleAutoFarmClick(imx, imy);
        else if (activeTab == 2) return handleVisualsClick(imx, imy);
        else return handleMiscClick(imx, imy);
    }

    private boolean handleMacrosClick(int imx, int imy) {
        List<Macro> macros = MacroModClient.getManager().getAll();

        // Config chip toggles
        if (selectedMacro != null) {
            var cfg = selectedMacro.getConfig();
            for (int i = 0; i < 5; i++) {
                int cx = chipBounds[i][0], cy = chipBounds[i][1], cw = chipBounds[i][2];
                if (cw > 0 && imx >= cx && imx < cx + cw && imy >= cy && imy < cy + 14) {
                    switch (i) {
                        case 0 -> cfg.setLoop(!cfg.isLoop());
                        case 1 -> cfg.setSkipMismatch(!cfg.isSkipMismatch());
                        case 2 -> cfg.setAttackDanger(!cfg.isAttackDanger());
                        case 3 -> cfg.setOnlyGround(!cfg.isOnlyGround());
                        case 4 -> cfg.setLockCrosshair(!cfg.isLockCrosshair());
                    }
                    MacroModClient.getManager().save(selectedMacro);
                    return true;
                }
            }
        }

        // Sidebar macro cards
        int cardX = px + CARD_PX;
        int cardW = SIDEBAR_W - CARD_PX * 2;
        int listY0 = py + HEADER_H + 4;
        int listH = ph - HEADER_H - FOOTER_H - 8;

        if (imx >= cardX && imx < cardX + cardW
                && imy >= listY0 && imy < listY0 + listH) {
            int smoothOff = (int) scrollSmooth;
            for (int i = 0; i < macros.size(); i++) {
                int cy = listY0 + i * (CARD_H + CARD_GAP) - smoothOff;
                if (imy >= cy && imy < cy + CARD_H) {
                    selectedMacro = macros.get(i);
                    return true;
                }
            }
        }

        // New Macro button
        if (hoverNew) {
            String name = "Macro " + (macros.size() + 1);
            selectedMacro = MacroModClient.getManager().create(name);
            recalcScroll();
            return true;
        }

        // Action buttons
        if (selectedMacro != null) {
            if (hoverRun) {
                if (isRunning(selectedMacro)) {
                    MacroModClient.getExecutor().stop();
                } else {
                    MacroModClient.getExecutor().start(selectedMacro.getId(), null);
                    animClose();
                }
                return true;
            }
            if (hoverEdit && client != null) {
                client.setScreen(new MacroEditScreen(selectedMacro, this));
                return true;
            }
            if (hoverDelete) {
                MacroModClient.getManager().delete(selectedMacro.getId());
                selectedMacro = null;
                recalcScroll();
                return true;
            }
            if (hoverDup) {
                Macro dup = MacroModClient.getManager().duplicate(selectedMacro.getId());
                if (dup != null) selectedMacro = dup;
                recalcScroll();
                return true;
            }
        }

        return false;
    }

    private boolean handleAutoFarmClick(int imx, int imy) {
        // Fish toggles
        if (hitTg(imx, imy, fishTgBounds[0])) { optAutoFish    = !optAutoFish;    syncFish(); return true; }
        if (hitTg(imx, imy, fishTgBounds[1])) { optFishAttack  = !optFishAttack;  syncFish(); return true; }
        if (hitTg(imx, imy, fishTgBounds[2])) { optAttackMobs  = !optAttackMobs;  return true; }
        if (hitTg(imx, imy, fishTgBounds[3])) { optCameraJitter = !optCameraJitter; syncFish(); return true; }
        if (hitTg(imx, imy, fishTgBounds[4])) { optAutoDeposit   = !optAutoDeposit;  syncFish(); return true; }
        if (hitTg(imx, imy, fishTgBounds[5])) { optFishMove      = !optFishMove;     syncFish(); return true; }

        // Mode button
        if (modeBtnW > 0 && imx >= modeBtnX && imx < modeBtnX + modeBtnW
                && imy >= modeBtnY && imy < modeBtnY + modeBtnH) {
            optFishAttackDistance = !optFishAttackDistance;
            syncFish();
            return true;
        }

        // Hotbar slots
        for (int i = 0; i < 9; i++) {
            if (hotbar[i][0] < 0) continue;
            if (imx >= hotbar[i][0] && imx < hotbar[i][0] + 20
                    && imy >= hotbar[i][1] && imy < hotbar[i][1] + 20) {
                optFishAttackSlot = (optFishAttackSlot == i) ? -1 : i;
                syncFish();
                return true;
            }
        }

        // Radius cycler
        if (imy >= radBtnY && imy < radBtnY + 18) {
            if (imx >= radLeftX && imx < radLeftX + 16) {
                optFarmRadius = Math.max(1, optFarmRadius - 1); return true;
            }
            if (imx >= radRightX && imx < radRightX + 16) {
                optFarmRadius = Math.min(32, optFarmRadius + 1); return true;
            }
        }

        // Auto Farmer toggle
        if (farmerTgBounds[2] > 0
                && imx >= farmerTgBounds[0] && imx < farmerTgBounds[0] + farmerTgBounds[2]
                && imy >= farmerTgBounds[1] && imy < farmerTgBounds[1] + farmerTgBounds[3]) {
            optAutoFarmer = !optAutoFarmer;
            syncFarmer();
            return true;
        }

        // Auto Farmer direction button
        if (farmerDirBtnW > 0 && imx >= farmerDirBtnX && imx < farmerDirBtnX + farmerDirBtnW
                && imy >= farmerDirBtnY && imy < farmerDirBtnY + farmerDirBtnH) {
            optFarmerDir = (optFarmerDir == AutoFarmerManager.HorizontalDir.LEFT)
                    ? AutoFarmerManager.HorizontalDir.RIGHT
                    : AutoFarmerManager.HorizontalDir.LEFT;
            syncFarmer();
            return true;
        }

        // Auto Attack toggle
        if (attackTgBounds[2] > 0
                && imx >= attackTgBounds[0] && imx < attackTgBounds[0] + attackTgBounds[2]
                && imy >= attackTgBounds[1] && imy < attackTgBounds[1] + attackTgBounds[3]) {
            optAutoAttack = !optAutoAttack;
            syncAutoAttack();
            return true;
        }

        // Auto Attack priority button
        if (attackPriorityBtnW > 0
                && imx >= attackPriorityBtnX && imx < attackPriorityBtnX + attackPriorityBtnW
                && imy >= attackPriorityBtnY && imy < attackPriorityBtnY + attackPriorityBtnH) {
            optAttackPriority = (optAttackPriority == AutoAttackManager.PriorityMode.NEAREST)
                    ? AutoAttackManager.PriorityMode.LOWEST_HEALTH
                    : AutoAttackManager.PriorityMode.NEAREST;
            syncAutoAttack();
            return true;
        }

        // Auto Attack range cycler
        if (imy >= attackRangeBtnY && imy < attackRangeBtnY + 18) {
            if (imx >= attackRangeLeftX && imx < attackRangeLeftX + 16) {
                optAttackRange = Math.max(2.0f, optAttackRange - 0.5f);
                syncAutoAttack();
                return true;
            }
            if (imx >= attackRangeRightX && imx < attackRangeRightX + 16) {
                optAttackRange = Math.min(8.0f, optAttackRange + 0.5f);
                syncAutoAttack();
                return true;
            }
        }

        // Attack item hotbar slots
        for (int i = 0; i < 9; i++) {
            if (attackHotbar[i][0] < 0) continue;
            if (imx >= attackHotbar[i][0] && imx < attackHotbar[i][0] + 20
                    && imy >= attackHotbar[i][1] && imy < attackHotbar[i][1] + 20) {
                optAttackItemSlot = (optAttackItemSlot == i) ? -1 : i;
                syncAutoAttack();
                return true;
            }
        }

        // Random CPS toggle
        if (randomCpsTgBounds[2] > 0
                && imx >= randomCpsTgBounds[0] && imx < randomCpsTgBounds[0] + randomCpsTgBounds[2]
                && imy >= randomCpsTgBounds[1] && imy < randomCpsTgBounds[1] + randomCpsTgBounds[3]) {
            optRandomCps = !optRandomCps;
            syncAutoAttack();
            return true;
        }

        // Auto Mining toggle
        if (miningTgBounds[2] > 0
                && imx >= miningTgBounds[0] && imx < miningTgBounds[0] + miningTgBounds[2]
                && imy >= miningTgBounds[1] && imy < miningTgBounds[1] + miningTgBounds[3]) {
            optAutoMining = !optAutoMining;
            syncAutoMining();
            return true;
        }

        return false;
    }

    private boolean handleVisualsClick(int imx, int imy) {
        // Target ESP toggle
        if (hitTg(imx, imy, targetEspTgBounds)) {
            optTargetEsp = !optTargetEsp;
            syncVisuals();
            return true;
        }

        // Entities ESP toggle
        if (hitTg(imx, imy, entitiesEspTgBounds)) {
            optEntitiesEsp = !optEntitiesEsp;
            syncVisuals();
            return true;
        }

        // Block ESP toggle
        if (hitTg(imx, imy, blockEspTgBounds)) {
            optBlockEsp = !optBlockEsp;
            syncVisuals();
            return true;
        }

        // Fairy Souls ESP toggle
        if (hitTg(imx, imy, fairySoulsEspTgBounds)) {
            optFairySoulsEsp = !optFairySoulsEsp;
            syncVisuals();
            return true;
        }

        // Hotspot ESP toggle
        if (hitTg(imx, imy, hotspotEspTgBounds)) {
            optHotspotEsp = !optHotspotEsp;
            syncVisuals();
            return true;
        }

        // Block ESP radius cycler
        if (imy >= blockRadiusBtnY && imy < blockRadiusBtnY + 18) {
            if (imx >= blockRadiusLeftX && imx < blockRadiusLeftX + 16) {
                optBlockEspRadius = Math.max(4, optBlockEspRadius - 2);
                syncVisuals();
                return true;
            }
            if (imx >= blockRadiusRightX && imx < blockRadiusRightX + 16) {
                optBlockEspRadius = Math.min(32, optBlockEspRadius + 2);
                syncVisuals();
                return true;
            }
        }

        int lx = px + 24;
        int rEdge = px + pw - 24;

        // Entity list item click
        if (!nearbyEntityTypes.isEmpty() && imx >= lx + 4 && imx < rEdge) {
            for (int i = 0; i < nearbyEntityTypes.size(); i++) {
                int itemY = entityListY + i * LIST_ITEM_H;
                if (imy >= itemY && imy < itemY + LIST_ITEM_H) {
                    String id = nearbyEntityTypes.get(i);
                    ModConfig cfg = MacroModClient.getConfigManager().getConfig();
                    List<String> wl = cfg.getEntityWhitelist();
                    if (wl.contains(id)) {
                        wl.remove(id);
                    } else {
                        wl.add(id);
                    }
                    MacroModClient.getConfigManager().save();
                    return true;
                }
            }
        }

        // Block list item click
        if (!nearbyBlockTypes.isEmpty() && imx >= lx + 4 && imx < rEdge) {
            for (int i = 0; i < nearbyBlockTypes.size(); i++) {
                int itemY = blockListY + i * LIST_ITEM_H;
                if (imy >= itemY && imy < itemY + LIST_ITEM_H) {
                    String id = nearbyBlockTypes.get(i);
                    ModConfig cfg = MacroModClient.getConfigManager().getConfig();
                    List<String> wl = cfg.getBlockWhitelist();
                    if (wl.contains(id)) {
                        wl.remove(id);
                    } else {
                        wl.add(id);
                    }
                    MacroModClient.getConfigManager().save();
                    return true;
                }
            }
        }

        return false;
    }

    private boolean handleMiscClick(int imx, int imy) {
        // Freelook toggle
        if (hitTg(imx, imy, freelookTgBounds)) {
            optFreelook = !optFreelook;
            if (optFreelook) {
                FreelookManager.getInstance().enable();
            } else {
                FreelookManager.getInstance().disable();
            }
            return true;
        }

        // FOV cycler
        if (imy >= fovBtnY && imy < fovBtnY + 18) {
            if (imx >= fovLeftX && imx < fovLeftX + 16) {
                optFOV = Math.max(30, optFOV - 5);
                syncFOV();
                return true;
            }
            if (imx >= fovRightX && imx < fovRightX + 16) {
                optFOV = Math.min(200, optFOV + 5);
                syncFOV();
                return true;
            }
        }

        // Debug logging toggle
        if (hitTg(imx, imy, debugLogTgBounds)) {
            optDebugLogging = !optDebugLogging;
            syncDebug();
            return true;
        }

        // Smooth Aim cyclers
        if (imy >= saBaseLerpBtnY && imy < saBaseLerpBtnY + 18) {
            if (imx >= saBaseLerpLeftX && imx < saBaseLerpLeftX + 16) {
                optSaBaseLerp = Math.max(0.005f, optSaBaseLerp - 0.005f);
                syncSmoothAim(); return true;
            }
            if (imx >= saBaseLerpRightX && imx < saBaseLerpRightX + 16) {
                optSaBaseLerp = Math.min(0.15f, optSaBaseLerp + 0.005f);
                syncSmoothAim(); return true;
            }
        }
        if (imy >= saSpeedZeroBtnY && imy < saSpeedZeroBtnY + 18) {
            if (imx >= saSpeedZeroLeftX && imx < saSpeedZeroLeftX + 16) {
                optSaSpeedZero = Math.max(0.05f, optSaSpeedZero - 0.05f);
                syncSmoothAim(); return true;
            }
            if (imx >= saSpeedZeroRightX && imx < saSpeedZeroRightX + 16) {
                optSaSpeedZero = Math.min(3.0f, optSaSpeedZero + 0.05f);
                syncSmoothAim(); return true;
            }
        }
        if (imy >= saSpeedSlowBtnY && imy < saSpeedSlowBtnY + 18) {
            if (imx >= saSpeedSlowLeftX && imx < saSpeedSlowLeftX + 16) {
                optSaSpeedSlow = Math.max(0.1f, optSaSpeedSlow - 0.05f);
                syncSmoothAim(); return true;
            }
            if (imx >= saSpeedSlowRightX && imx < saSpeedSlowRightX + 16) {
                optSaSpeedSlow = Math.min(4.0f, optSaSpeedSlow + 0.05f);
                syncSmoothAim(); return true;
            }
        }
        if (imy >= saSpeedFastBtnY && imy < saSpeedFastBtnY + 18) {
            if (imx >= saSpeedFastLeftX && imx < saSpeedFastLeftX + 16) {
                optSaSpeedFast = Math.max(0.1f, optSaSpeedFast - 0.05f);
                syncSmoothAim(); return true;
            }
            if (imx >= saSpeedFastRightX && imx < saSpeedFastRightX + 16) {
                optSaSpeedFast = Math.min(5.0f, optSaSpeedFast + 0.05f);
                syncSmoothAim(); return true;
            }
        }
        if (imy >= saSlowZoneBtnY && imy < saSlowZoneBtnY + 18) {
            if (imx >= saSlowZoneLeftX && imx < saSlowZoneLeftX + 16) {
                optSaSlowZone = Math.max(3f, optSaSlowZone - 1f);
                syncSmoothAim(); return true;
            }
            if (imx >= saSlowZoneRightX && imx < saSlowZoneRightX + 16) {
                optSaSlowZone = Math.min(30f, optSaSlowZone + 1f);
                syncSmoothAim(); return true;
            }
        }
        if (imy >= saFastZoneBtnY && imy < saFastZoneBtnY + 18) {
            if (imx >= saFastZoneLeftX && imx < saFastZoneLeftX + 16) {
                optSaFastZone = Math.max(10f, optSaFastZone - 1f);
                syncSmoothAim(); return true;
            }
            if (imx >= saFastZoneRightX && imx < saFastZoneRightX + 16) {
                optSaFastZone = Math.min(90f, optSaFastZone + 1f);
                syncSmoothAim(); return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (activeTab == 0 && mx >= px && mx < px + SIDEBAR_W
                && my >= py + HEADER_H && my < py + ph - FOOTER_H) {
            scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (int) (vAmt * 14)));
            return true;
        }
        if (activeTab == 2 && mx >= px && mx < px + pw
                && my >= py + HEADER_H && my < py + ph) {
            visualsScrollY = Math.max(0, Math.min(visualsMaxScrollY,
                    visualsScrollY - (int) (vAmt * 14)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private boolean hitBtn(int mx, int my, int bx) {
        return mx >= bx && mx < bx + ACT_W && my >= actY && my < actY + ACT_H;
    }

    private boolean hitTg(int mx, int my, int[] b) {
        return b[2] > 0 && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    private boolean isRunning(Macro m) {
        return MacroModClient.getExecutor().isRunning()
                && MacroModClient.getExecutor().getCurrentMacro() != null
                && MacroModClient.getExecutor().getCurrentMacro().getId().equals(m.getId());
    }

    private boolean isSel(Macro m) {
        return selectedMacro != null && selectedMacro.getId().equals(m.getId());
    }

    private void recalcScroll() {
        int count = MacroModClient.getManager().getAll().size();
        int listH = ph - HEADER_H - FOOTER_H - 8;
        maxScrollY = Math.max(0, count * (CARD_H + CARD_GAP) - listH);
        scrollY = Math.min(scrollY, maxScrollY);
    }

    private void syncFish() {
        AutoFishingManager.getInstance().setEnabled(optAutoFish);
        AutoFishingManager.getInstance().setAttackConfig(optFishAttack, optFishAttackDistance, optFishAttackSlot);
        AutoFishingManager.getInstance().setCameraJitter(optCameraJitter);
        AutoFishingManager.getInstance().setAutoDeposit(optAutoDeposit);
        AutoFishingManager.getInstance().setCloseMovement(optFishMove);
    }

    private void syncFarmer() {
        AutoFarmerManager farmer = AutoFarmerManager.getInstance();
        farmer.setStartDirection(optFarmerDir);
        if (optAutoFarmer) {
            farmer.enable();
        } else {
            farmer.disable();
        }
    }

    private void syncAutoAttack() {
        AutoAttackManager aam = AutoAttackManager.getInstance();
        aam.setAttackRange(optAttackRange);
        aam.setPriorityMode(optAttackPriority);
        aam.setAttackItemSlot(optAttackItemSlot);
        aam.setRandomCps(optRandomCps);
        if (optAutoAttack) {
            aam.enable();
        } else {
            aam.disable();
        }
    }

    private void syncAutoMining() {
        com.example.macromod.manager.AutoMiningManager amm =
                com.example.macromod.manager.AutoMiningManager.getInstance();
        if (optAutoMining) {
            amm.enable();
        } else {
            amm.disable();
        }
    }

    private void syncVisuals() {
        ModConfig cfg = MacroModClient.getConfigManager().getConfig();
        cfg.setTargetEspEnabled(optTargetEsp);
        cfg.setEntitiesEspEnabled(optEntitiesEsp);
        cfg.setBlockEspEnabled(optBlockEsp);
        cfg.setFairySoulsEspEnabled(optFairySoulsEsp);
        cfg.setHotspotEspEnabled(optHotspotEsp);
        cfg.setBlockEspRadius(optBlockEspRadius);
        MacroModClient.getConfigManager().save();
    }

    private void syncFOV() {
        FreelookManager.getInstance().setFreelookFov(optFOV);
        ModConfig cfg = MacroModClient.getConfigManager().getConfig();
        cfg.setFreelookFov(optFOV);
        MacroModClient.getConfigManager().save();
    }

    private void syncDebug() {
        ModConfig cfg = MacroModClient.getConfigManager().getConfig();
        cfg.setDebugLogging(optDebugLogging);
        MacroModClient.getConfigManager().save();
    }

    private void syncSmoothAim() {
        ModConfig cfg = MacroModClient.getConfigManager().getConfig();
        cfg.setSmoothAimBaseLerp(optSaBaseLerp);
        cfg.setSmoothAimSpeedZero(optSaSpeedZero);
        cfg.setSmoothAimSpeedSlow(optSaSpeedSlow);
        cfg.setSmoothAimSpeedFast(optSaSpeedFast);
        cfg.setSmoothAimSlowZone(optSaSlowZone);
        cfg.setSmoothAimFastZone(optSaFastZone);
        MacroModClient.getConfigManager().save();

        var sa = MacroModClient.getSmoothAim();
        if (sa != null) {
            sa.setBaseLerp(optSaBaseLerp);
            sa.setSpeedAtZero(optSaSpeedZero);
            sa.setSpeedAtSlow(optSaSpeedSlow);
            sa.setSpeedAtFast(optSaSpeedFast);
            sa.setSlowZoneDeg(optSaSlowZone);
            sa.setFastZoneDeg(optSaFastZone);
        }
    }
}
