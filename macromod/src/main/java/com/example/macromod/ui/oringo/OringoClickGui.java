package com.example.macromod.ui.oringo;

import com.example.macromod.MacroModClient;
import com.example.macromod.config.ModConfig;
import com.example.macromod.manager.AutoAttackManager;
import com.example.macromod.manager.AutoFarmerManager;
import com.example.macromod.manager.AutoFishingManager;
import com.example.macromod.manager.AutoMiningManager;
import com.example.macromod.manager.FreelookManager;
import com.example.macromod.manager.MacroExecutor;
import com.example.macromod.manager.MacroManager;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroConfig;
import com.example.macromod.proxy.ProxyListScreen;
import com.example.macromod.ui.MacroEditScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Oringo-inspired ClickGUI replacement for MacroMod.
 * Uses draggable category panels and compact row controls.
 */
@Environment(EnvType.CLIENT)
public class OringoClickGui extends Screen {

    private static final int HEADER_H = 18;
    private static final int PANEL_PAD = 4;
    private static final int ROW_H = 14;
    private static final int MAX_MACRO_ROWS = 9;

    private static final int C_BACKDROP = 0xAA070B10;
    private static final int C_BACKDROP_ACCENT = 0x2A1FA3AE;
    private static final int C_PANEL = 0xE0182732;
    private static final int C_HEADER = 0xF01B3442;
    private static final int C_DIV = 0xFF36A9B4;
    private static final int C_TEXT = 0xFFEAF3F6;
    private static final int C_TEXT_DIM = 0xFF96AFBC;
    private static final int C_ROW = 0x66344757;
    private static final int C_ROW_HOVER = 0xAA3F667A;
    private static final int C_ROW_ACTIVE = 0xAA2B909B;
    private static final int C_ON = 0xFF58D38A;
    private static final int C_OFF = 0xFFFF7676;

    private static final int VISUAL_SCAN_INTERVAL = 40;
    private static final int MAX_VISUAL_LIST_ROWS = 6;

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

    private final Screen parent;
    private final List<OringoPanel> panels = new ArrayList<>();

    private final List<String> nearbyEntityTypes = new ArrayList<>();
    private final List<String> nearbyBlockTypes = new ArrayList<>();
    private final List<String> visibleEntityRows = new ArrayList<>();
    private final List<String> visibleBlockRows = new ArrayList<>();

    private OringoPanel draggingPanel;
    private Macro selectedMacro;
    private int macroScroll = 0;
    private int visualsScanCounter = 0;

    private int entityListScroll = 0;
    private int blockListScroll = 0;

    private int visualEntityX = -1;
    private int visualEntityY = -1;
    private int visualEntityW = -1;
    private int visualEntityH = -1;

    private int visualBlockX = -1;
    private int visualBlockY = -1;
    private int visualBlockW = -1;
    private int visualBlockH = -1;

    public OringoClickGui() {
        this(null);
    }

    public OringoClickGui(Screen parent) {
        super(Text.literal("MacroMod Oringo"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (panels.isEmpty()) {
            int baseX = Math.max(8, width / 2 - 230);
            int baseY = Math.max(16, height / 2 - 180);
            ModConfig cfg = MacroModClient.getConfigManager().getConfig();
            panels.add(new OringoPanel("macros", "Macros",
                cfg.getOringoMacrosPanelX() >= 0 ? cfg.getOringoMacrosPanelX() : baseX,
                cfg.getOringoMacrosPanelY() >= 0 ? cfg.getOringoMacrosPanelY() : baseY,
                220, HEADER_H));
            panels.add(new OringoPanel("path", "Pathing",
                cfg.getOringoPathPanelX() >= 0 ? cfg.getOringoPathPanelX() : baseX + 230,
                cfg.getOringoPathPanelY() >= 0 ? cfg.getOringoPathPanelY() : baseY,
                220, HEADER_H));
            panels.add(new OringoPanel("automation", "Automation",
                cfg.getOringoAutomationPanelX() >= 0 ? cfg.getOringoAutomationPanelX() : baseX,
                cfg.getOringoAutomationPanelY() >= 0 ? cfg.getOringoAutomationPanelY() : baseY + 185,
                220, HEADER_H));
            panels.add(new OringoPanel("visual", "Visuals",
                cfg.getOringoVisualPanelX() >= 0 ? cfg.getOringoVisualPanelX() : baseX + 230,
                cfg.getOringoVisualPanelY() >= 0 ? cfg.getOringoVisualPanelY() : baseY + 185,
                220, HEADER_H));
            panels.add(new OringoPanel("misc", "Misc",
                cfg.getOringoMiscPanelX() >= 0 ? cfg.getOringoMiscPanelX() : baseX + 115,
                cfg.getOringoMiscPanelY() >= 0 ? cfg.getOringoMiscPanelY() : baseY + 385,
                250, HEADER_H));
        }

        syncSelectedMacro();
        refreshVisualSources();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (key.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        int mx = (int) click.x();
        int my = (int) click.y();
        int button = click.button();

        for (int i = panels.size() - 1; i >= 0; i--) {
            OringoPanel panel = panels.get(i);
            if (!panel.isOverHeader(mx, my)) continue;

            bringToFront(panel);
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                panel.expanded = !panel.expanded;
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                draggingPanel = panel;
                panel.startDrag(mx, my);
                return true;
            }
            return true;
        }

        for (int i = panels.size() - 1; i >= 0; i--) {
            OringoPanel panel = panels.get(i);
            if (!panel.expanded) continue;
            int contentHeight = getPanelContentHeight(panel);
            if (!inside(mx, my, panel.x, panel.y + panel.headerHeight, panel.width, contentHeight)) continue;

            bringToFront(panel);
            return switch (panel.id) {
                case "macros" -> handleMacrosClick(panel, mx, my, button);
                case "path" -> handlePathClick(panel, mx, my, button);
                case "automation" -> handleAutomationClick(panel, mx, my, button);
                case "visual" -> handleVisualClick(panel, mx, my, button);
                case "misc" -> handleMiscClick(panel, mx, my, button);
                default -> false;
            };
        }

        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int mxi = (int) mx;
        int myi = (int) my;

        OringoPanel macrosPanel = panelById("macros");
        if (macrosPanel != null && macrosPanel.expanded
                && inside(mxi, myi,
                macrosPanel.x, macrosPanel.y + macrosPanel.headerHeight,
                macrosPanel.width, getPanelContentHeight(macrosPanel))) {
            int total = MacroModClient.getManager().getAll().size();
            int max = Math.max(0, total - MAX_MACRO_ROWS);
            if (max > 0) {
                int delta = vAmt > 0 ? -1 : 1;
                macroScroll = MathHelper.clamp(macroScroll + delta, 0, max);
            }
            return true;
        }

        OringoPanel visualPanel = panelById("visual");
        if (visualPanel != null && visualPanel.expanded
                && inside(mxi, myi,
                visualPanel.x, visualPanel.y + visualPanel.headerHeight,
                visualPanel.width, getPanelContentHeight(visualPanel))) {
            int dir = vAmt > 0 ? -1 : 1;

            int maxEntityScroll = Math.max(0, nearbyEntityTypes.size() - MAX_VISUAL_LIST_ROWS);
            int maxBlockScroll = Math.max(0, nearbyBlockTypes.size() - MAX_VISUAL_LIST_ROWS);

            if (visualEntityW > 0 && inside(mxi, myi, visualEntityX, visualEntityY, visualEntityW, visualEntityH)) {
                entityListScroll = MathHelper.clamp(entityListScroll + dir, 0, maxEntityScroll);
            } else if (visualBlockW > 0 && inside(mxi, myi, visualBlockX, visualBlockY, visualBlockW, visualBlockH)) {
                blockListScroll = MathHelper.clamp(blockListScroll + dir, 0, maxBlockScroll);
            }
            return true;
        }

        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        tickDragging(mx, my);
        tickVisualScan();

        ctx.fill(0, 0, width, height, C_BACKDROP);
        ctx.fill(0, 0, width, height / 3, C_BACKDROP_ACCENT);

        for (OringoPanel panel : panels) {
            renderPanel(ctx, panel, mx, my);
        }

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("LFM"), width / 2, 6, C_DIV);
        super.render(ctx, mx, my, delta);
    }

    private void tickVisualScan() {
        visualsScanCounter++;
        if (visualsScanCounter >= VISUAL_SCAN_INTERVAL) {
            visualsScanCounter = 0;
            refreshVisualSources();
        }
    }

    private void refreshVisualSources() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ModConfig cfg = MacroModClient.getConfigManager().getConfig();

        nearbyEntityTypes.clear();
        nearbyBlockTypes.clear();

        if (mc.player == null || mc.world == null) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        Box box = new Box(px - 20, py - 20, pz - 20, px + 20, py + 20, pz + 20);

        Set<String> entitySeen = new LinkedHashSet<>();
        for (Entity e : mc.world.getEntitiesByClass(LivingEntity.class, box,
                le -> le != mc.player && le.isAlive()
                        && !(le instanceof net.minecraft.entity.player.PlayerEntity)
                        && !(le instanceof ArmorStandEntity)
                        && !le.isInvisible())) {
            entitySeen.add(Registries.ENTITY_TYPE.getId(e.getType()).toString());
        }
        nearbyEntityTypes.addAll(entitySeen);

        BlockPos center = mc.player.getBlockPos();
        int radius = cfg.getBlockEspRadius();
        Set<String> blockSeen = new LinkedHashSet<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    String id = Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).toString();
                    if (!BORING_BLOCKS.contains(id)) {
                        blockSeen.add(id);
                    }
                }
            }
        }
        nearbyBlockTypes.addAll(blockSeen);

        int maxEntityScroll = Math.max(0, nearbyEntityTypes.size() - MAX_VISUAL_LIST_ROWS);
        int maxBlockScroll = Math.max(0, nearbyBlockTypes.size() - MAX_VISUAL_LIST_ROWS);
        entityListScroll = MathHelper.clamp(entityListScroll, 0, maxEntityScroll);
        blockListScroll = MathHelper.clamp(blockListScroll, 0, maxBlockScroll);
    }

    private void renderPanel(DrawContext ctx, OringoPanel panel, int mx, int my) {
        int contentHeight = panel.expanded ? getPanelContentHeight(panel) : 0;

        ctx.fill(panel.x, panel.y, panel.x + panel.width, panel.y + panel.headerHeight, C_HEADER);
        if (contentHeight > 0) {
            ctx.fill(panel.x, panel.y + panel.headerHeight,
                    panel.x + panel.width, panel.y + panel.headerHeight + contentHeight, C_PANEL);
        }
        ctx.fill(panel.x, panel.y + panel.headerHeight - 1, panel.x + panel.width, panel.y + panel.headerHeight, C_DIV);

        String marker = panel.expanded ? "[-]" : "[+]";
        ctx.drawTextWithShadow(textRenderer, Text.literal(panel.title + " " + marker), panel.x + 6, panel.y + 5, C_TEXT);

        if (!panel.expanded) return;

        switch (panel.id) {
            case "macros" -> renderMacros(ctx, panel, mx, my);
            case "path" -> renderPathing(ctx, panel, mx, my);
            case "automation" -> renderAutomation(ctx, panel, mx, my);
            case "visual" -> renderVisuals(ctx, panel, mx, my);
            case "misc" -> renderMisc(ctx, panel, mx, my);
            default -> {
            }
        }
    }

    private void renderMacros(DrawContext ctx, OringoPanel panel, int mx, int my) {
        MacroManager manager = MacroModClient.getManager();
        List<Macro> macros = manager.getAll();
        syncSelectedMacro();

        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        String selected = selectedMacro != null ? selectedMacro.getName() : "(none)";
        y = drawLabelRow(ctx, x, y, "Selected: " + selected, C_TEXT);

        MacroExecutor ex = MacroModClient.getExecutor();
        y = drawLabelRow(ctx, x, y, "State: " + ex.getState(), C_TEXT_DIM);

        int maxScroll = Math.max(0, macros.size() - MAX_MACRO_ROWS);
        macroScroll = MathHelper.clamp(macroScroll, 0, maxScroll);
        int shown = Math.min(MAX_MACRO_ROWS, Math.max(0, macros.size() - macroScroll));

        for (int i = 0; i < shown; i++) {
            Macro macro = macros.get(macroScroll + i);
            boolean rowHover = inside(mx, my, x, y, w, ROW_H);
            boolean active = selectedMacro != null && selectedMacro.getId().equals(macro.getId());
            int bg = active ? C_ROW_ACTIVE : (rowHover ? C_ROW_HOVER : C_ROW);
            ctx.fill(x, y, x + w, y + ROW_H - 1, bg);
            String label = macro.getName() + "  [" + macro.getSteps().size() + "]";
            ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 4, y + 3, C_TEXT);
            y += ROW_H;
        }

        if (macros.size() > MAX_MACRO_ROWS) {
            int max = Math.max(1, maxScroll);
            int shownIdx = macroScroll + 1;
            String info = "Scroll " + shownIdx + "/" + max;
            y = drawLabelRow(ctx, x, y, info, C_TEXT_DIM);
        }

        boolean runningThis = isSelectedRunning();
        y = drawActionRow(ctx, x, y, w, runningThis ? "Stop Selected Macro" : "Run Selected Macro", mx, my, true);
        y = drawActionRow(ctx, x, y, w, "Edit Selected Macro", mx, my, selectedMacro != null);
        y = drawActionRow(ctx, x, y, w, "Create New Macro", mx, my, true);
        y = drawActionRow(ctx, x, y, w, "Duplicate Selected", mx, my, selectedMacro != null);
        drawActionRow(ctx, x, y, w, "Delete Selected", mx, my, selectedMacro != null);
    }

    private void renderPathing(DrawContext ctx, OringoPanel panel, int mx, int my) {
        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        MacroExecutor ex = MacroModClient.getExecutor();
        syncSelectedMacro();

        y = drawLabelRow(ctx, x, y, "Executor: " + ex.getState(), C_TEXT);
        if (ex.getCurrentMacro() != null) {
            y = drawLabelRow(ctx, x, y,
                    "Step: " + (ex.getCurrentStepIndex() + 1) + "/" + Math.max(1, ex.getTotalSteps()), C_TEXT_DIM);
            y = drawLabelRow(ctx, x, y,
                    "Block: " + ex.getBlocksProcessedInStep() + "/" + ex.getTotalBlocksInStep(), C_TEXT_DIM);
            int pathSize = ex.getCurrentPath() != null ? ex.getCurrentPath().size() : 0;
            y = drawLabelRow(ctx, x, y, "Path: " + ex.getCurrentPathIndex() + "/" + pathSize, C_TEXT_DIM);
        } else {
            y = drawLabelRow(ctx, x, y, "Step: -", C_TEXT_DIM);
            y = drawLabelRow(ctx, x, y, "Block: -", C_TEXT_DIM);
            y = drawLabelRow(ctx, x, y, "Path: -", C_TEXT_DIM);
        }

        MacroConfig cfg = selectedMacro != null ? selectedMacro.getConfig() : null;
        y = drawToggleRow(ctx, x, y, w, "Loop", cfg != null && cfg.isLoop(), mx, my, cfg != null);
        y = drawToggleRow(ctx, x, y, w, "Skip Mismatch", cfg != null && cfg.isSkipMismatch(), mx, my, cfg != null);
        y = drawToggleRow(ctx, x, y, w, "Only Defined Blocks", cfg != null && cfg.isMineOnlyDefinedTargets(), mx, my, cfg != null);
        y = drawToggleRow(ctx, x, y, w, "Attack Danger", cfg != null && cfg.isAttackDanger(), mx, my, cfg != null);
        y = drawToggleRow(ctx, x, y, w, "Only Ground", cfg != null && cfg.isOnlyGround(), mx, my, cfg != null);
        y = drawToggleRow(ctx, x, y, w, "Lock Crosshair", cfg != null && cfg.isLockCrosshair(), mx, my, cfg != null);

        String arrival = cfg != null ? String.format("Arrival Radius: %.2f  (L+ / R-)", cfg.getArrivalRadius()) : "Arrival Radius: -";
        y = drawActionRow(ctx, x, y, w, arrival, mx, my, cfg != null);

        ModConfig global = MacroModClient.getConfigManager().getConfig();
        String maxNodes = "Max Path Nodes: " + global.getMaxPathNodes() + "  (L+ / R-)";
        drawActionRow(ctx, x, y, w, maxNodes, mx, my, true);
    }

    private void renderAutomation(DrawContext ctx, OringoPanel panel, int mx, int my) {
        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        AutoFishingManager afish = AutoFishingManager.getInstance();
        AutoFarmerManager afarm = AutoFarmerManager.getInstance();
        AutoAttackManager aatk = AutoAttackManager.getInstance();
        AutoMiningManager amin = AutoMiningManager.getInstance();

        y = drawToggleRow(ctx, x, y, w, "Auto Fishing", afish.isEnabled(), mx, my, true);
        y = drawToggleRow(ctx, x, y, w, "Fish Auto Combat", afish.isAttackEnabled(), mx, my, true);

        String fishMode = afish.isAttackModeDistance() ? "DISTANCE" : "CLOSE";
        y = drawActionRow(ctx, x, y, w, "Fish Combat Mode: " + fishMode, mx, my, afish.isAttackEnabled());

        String fishSlot = afish.getAttackHotbarSlot() >= 0 ? String.valueOf(afish.getAttackHotbarSlot() + 1) : "NONE";
        y = drawActionRow(ctx, x, y, w, "Fish Attack Slot: " + fishSlot + "  (L+/R-)", mx, my, afish.isAttackEnabled());

        y = drawToggleRow(ctx, x, y, w, "Fish Close Move", afish.isCloseMovementEnabled(), mx, my, afish.isAttackEnabled());
        y = drawToggleRow(ctx, x, y, w, "AFK Jitter", afish.isCameraJitterEnabled(), mx, my, true);
        y = drawToggleRow(ctx, x, y, w, "Auto Deposit", afish.isAutoDepositEnabled(), mx, my, true);

        y = drawToggleRow(ctx, x, y, w, "Auto Farmer", afarm.isEnabled(), mx, my, true);
        String dir = afarm.getStartDirection() == AutoFarmerManager.HorizontalDir.LEFT ? "LEFT" : "RIGHT";
        y = drawActionRow(ctx, x, y, w, "Farmer Start Dir: " + dir, mx, my, true);

        y = drawToggleRow(ctx, x, y, w, "Auto Attack", aatk.isEnabled(), mx, my, true);
        String prio = aatk.getPriorityMode().name();
        y = drawActionRow(ctx, x, y, w, "Attack Priority: " + prio, mx, my, aatk.isEnabled());
        y = drawActionRow(ctx, x, y, w, String.format("Attack Range: %.1f  (L+/R-)", aatk.getAttackRange()), mx, my, aatk.isEnabled());

        String atkSlot = aatk.getAttackItemSlot() >= 0 ? String.valueOf(aatk.getAttackItemSlot() + 1) : "NONE";
        y = drawActionRow(ctx, x, y, w, "Attack Item Slot: " + atkSlot + "  (L+/R-)", mx, my, aatk.isEnabled());

        y = drawToggleRow(ctx, x, y, w, "Random CPS", aatk.isRandomCps(), mx, my, aatk.isEnabled());

        drawToggleRow(ctx, x, y, w, "Auto Mining", amin.isEnabled(), mx, my, true);
    }

    private void renderVisuals(DrawContext ctx, OringoPanel panel, int mx, int my) {
        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        ModConfig cfg = MacroModClient.getConfigManager().getConfig();

        y = drawToggleRow(ctx, x, y, w, "HUD Visible", cfg.isHudVisible(), mx, my, true);
        y = drawToggleRow(ctx, x, y, w, "Keybind HUD", cfg.isKeybindHudVisible(), mx, my, true);

        y = drawToggleRow(ctx, x, y, w, "Target ESP", cfg.isTargetEspEnabled(), mx, my, true);
        y = drawToggleRow(ctx, x, y, w, "Entity ESP", cfg.isEntitiesEspEnabled(), mx, my, true);
        y = drawToggleRow(ctx, x, y, w, "Block ESP", cfg.isBlockEspEnabled(), mx, my, true);
        y = drawToggleRow(ctx, x, y, w, "Fairy Souls ESP", cfg.isFairySoulsEspEnabled(), mx, my, true);
        y = drawToggleRow(ctx, x, y, w, "Hotspot ESP", cfg.isHotspotEspEnabled(), mx, my, true);

        y = drawActionRow(ctx, x, y, w, "Block ESP Radius: " + cfg.getBlockEspRadius() + "  (L+/R-)", mx, my, true);

        y = drawLabelRow(ctx, x, y, "Entity Whitelist:", C_TEXT_DIM);
        visibleEntityRows.clear();
        int entityRows = Math.max(1, Math.min(MAX_VISUAL_LIST_ROWS, nearbyEntityTypes.size()));

        visualEntityX = x;
        visualEntityY = y;
        visualEntityW = w;
        visualEntityH = entityRows * ROW_H;

        if (nearbyEntityTypes.isEmpty()) {
            int bg = inside(mx, my, x, y, w, ROW_H) ? C_ROW_HOVER : C_ROW;
            ctx.fill(x, y, x + w, y + ROW_H - 1, bg);
            ctx.drawTextWithShadow(textRenderer, Text.literal("(no nearby entities)"), x + 4, y + 3, C_TEXT_DIM);
            y += ROW_H;
        } else {
            int start = MathHelper.clamp(entityListScroll, 0, Math.max(0, nearbyEntityTypes.size() - MAX_VISUAL_LIST_ROWS));
            int end = Math.min(nearbyEntityTypes.size(), start + MAX_VISUAL_LIST_ROWS);
            Set<String> wl = new HashSet<>(cfg.getEntityWhitelist());
            for (int i = start; i < end; i++) {
                String id = nearbyEntityTypes.get(i);
                visibleEntityRows.add(id);
                boolean active = wl.contains(id);
                boolean rowHover = inside(mx, my, x, y, w, ROW_H);
                int bg = active ? C_ROW_ACTIVE : (rowHover ? C_ROW_HOVER : C_ROW);
                ctx.fill(x, y, x + w, y + ROW_H - 1, bg);
                String label = (active ? "[x] " : "[ ] ") + shortId(id);
                ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 4, y + 3, C_TEXT);
                y += ROW_H;
            }
        }

        y = drawLabelRow(ctx, x, y, "Block Whitelist:", C_TEXT_DIM);
        visibleBlockRows.clear();
        int blockRows = Math.max(1, Math.min(MAX_VISUAL_LIST_ROWS, nearbyBlockTypes.size()));

        visualBlockX = x;
        visualBlockY = y;
        visualBlockW = w;
        visualBlockH = blockRows * ROW_H;

        if (nearbyBlockTypes.isEmpty()) {
            int bg = inside(mx, my, x, y, w, ROW_H) ? C_ROW_HOVER : C_ROW;
            ctx.fill(x, y, x + w, y + ROW_H - 1, bg);
            ctx.drawTextWithShadow(textRenderer, Text.literal("(no interesting blocks nearby)"), x + 4, y + 3, C_TEXT_DIM);
            y += ROW_H;
        } else {
            int start = MathHelper.clamp(blockListScroll, 0, Math.max(0, nearbyBlockTypes.size() - MAX_VISUAL_LIST_ROWS));
            int end = Math.min(nearbyBlockTypes.size(), start + MAX_VISUAL_LIST_ROWS);
            Set<String> wl = new HashSet<>(cfg.getBlockWhitelist());
            for (int i = start; i < end; i++) {
                String id = nearbyBlockTypes.get(i);
                visibleBlockRows.add(id);
                boolean active = wl.contains(id);
                boolean rowHover = inside(mx, my, x, y, w, ROW_H);
                int bg = active ? C_ROW_ACTIVE : (rowHover ? C_ROW_HOVER : C_ROW);
                ctx.fill(x, y, x + w, y + ROW_H - 1, bg);
                String label = (active ? "[x] " : "[ ] ") + shortId(id);
                ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 4, y + 3, C_TEXT);
                y += ROW_H;
            }
        }
    }

    private void renderMisc(DrawContext ctx, OringoPanel panel, int mx, int my) {
        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        ModConfig cfg = MacroModClient.getConfigManager().getConfig();
        FreelookManager fl = FreelookManager.getInstance();

        y = drawToggleRow(ctx, x, y, w, "Enable Freelook", fl.isEnabled(), mx, my, true);
        y = drawActionRow(ctx, x, y, w, "Freelook FOV: " + fl.getFreelookFov() + "  (L+/R-)", mx, my, true);
        y = drawToggleRow(ctx, x, y, w, "Debug Logging", cfg.isDebugLogging(), mx, my, true);

        y = drawActionRow(ctx, x, y, w, String.format("Base Lerp: %.3f  (L+/R-)", cfg.getSmoothAimBaseLerp()), mx, my, true);
        y = drawActionRow(ctx, x, y, w, String.format("Speed Close: %.2f  (L+/R-)", cfg.getSmoothAimSpeedZero()), mx, my, true);
        y = drawActionRow(ctx, x, y, w, String.format("Speed Mid: %.2f  (L+/R-)", cfg.getSmoothAimSpeedSlow()), mx, my, true);
        y = drawActionRow(ctx, x, y, w, String.format("Speed Far: %.2f  (L+/R-)", cfg.getSmoothAimSpeedFast()), mx, my, true);
        y = drawActionRow(ctx, x, y, w, String.format("Slow Zone: %.0f  (L+/R-)", cfg.getSmoothAimSlowZone()), mx, my, true);
        y = drawActionRow(ctx, x, y, w, String.format("Fast Zone: %.0f  (L+/R-)", cfg.getSmoothAimFastZone()), mx, my, true);

        drawActionRow(ctx, x, y, w, "Open Proxy Manager", mx, my, true);
    }

    private boolean handleMacrosClick(OringoPanel panel, int mx, int my, int button) {
        MacroManager manager = MacroModClient.getManager();
        List<Macro> macros = manager.getAll();
        syncSelectedMacro();

        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        y += ROW_H; // selected
        y += ROW_H; // state

        int maxScroll = Math.max(0, macros.size() - MAX_MACRO_ROWS);
        macroScroll = MathHelper.clamp(macroScroll, 0, maxScroll);
        int shown = Math.min(MAX_MACRO_ROWS, Math.max(0, macros.size() - macroScroll));

        for (int i = 0; i < shown; i++) {
            if (inside(mx, my, x, y, w, ROW_H)) {
                selectedMacro = macros.get(macroScroll + i);
                return true;
            }
            y += ROW_H;
        }

        if (macros.size() > MAX_MACRO_ROWS) {
            y += ROW_H;
        }

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (selectedMacro != null) {
                MacroExecutor ex = MacroModClient.getExecutor();
                if (isSelectedRunning()) {
                    ex.stop();
                } else {
                    ex.start(selectedMacro.getId(), null);
                    close();
                }
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (selectedMacro != null && client != null) {
                client.setScreen(new MacroEditScreen(selectedMacro, this));
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            String name = "Macro " + (manager.getAll().size() + 1);
            selectedMacro = manager.create(name);
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (selectedMacro != null) {
                selectedMacro = manager.duplicate(selectedMacro.getId());
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (selectedMacro != null) {
                if (isSelectedRunning()) {
                    MacroModClient.getExecutor().stop();
                }
                manager.delete(selectedMacro.getId());
                selectedMacro = null;
                syncSelectedMacro();
            }
            return true;
        }

        return false;
    }

    private boolean handlePathClick(OringoPanel panel, int mx, int my, int button) {
        syncSelectedMacro();

        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        y += ROW_H * 4; // status rows

        MacroConfig cfg = selectedMacro != null ? selectedMacro.getConfig() : null;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (cfg != null) {
                cfg.setLoop(!cfg.isLoop());
                MacroModClient.getManager().save(selectedMacro);
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (cfg != null) {
                cfg.setSkipMismatch(!cfg.isSkipMismatch());
                MacroModClient.getManager().save(selectedMacro);
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (cfg != null) {
                cfg.setMineOnlyDefinedTargets(!cfg.isMineOnlyDefinedTargets());
                MacroModClient.getManager().save(selectedMacro);
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (cfg != null) {
                cfg.setAttackDanger(!cfg.isAttackDanger());
                MacroModClient.getManager().save(selectedMacro);
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (cfg != null) {
                cfg.setOnlyGround(!cfg.isOnlyGround());
                MacroModClient.getManager().save(selectedMacro);
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (cfg != null) {
                cfg.setLockCrosshair(!cfg.isLockCrosshair());
                MacroModClient.getManager().save(selectedMacro);
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (cfg != null) {
                float delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -0.1f : 0.1f;
                cfg.setArrivalRadius(Math.max(0.3f, Math.min(4.0f, cfg.getArrivalRadius() + delta)));
                MacroModClient.getManager().save(selectedMacro);
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            ModConfig global = MacroModClient.getConfigManager().getConfig();
            int delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -250 : 250;
            global.setMaxPathNodes(Math.max(500, Math.min(10000, global.getMaxPathNodes() + delta)));
            MacroModClient.getConfigManager().save();
            return true;
        }

        return false;
    }

    private boolean handleAutomationClick(OringoPanel panel, int mx, int my, int button) {
        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        AutoFishingManager afish = AutoFishingManager.getInstance();
        AutoFarmerManager afarm = AutoFarmerManager.getInstance();
        AutoAttackManager aatk = AutoAttackManager.getInstance();
        AutoMiningManager amin = AutoMiningManager.getInstance();

        if (inside(mx, my, x, y, w, ROW_H)) {
            afish.setEnabled(!afish.isEnabled());
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            afish.setAttackConfig(!afish.isAttackEnabled(), afish.isAttackModeDistance(), afish.getAttackHotbarSlot());
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (afish.isAttackEnabled()) {
                afish.setAttackConfig(true, !afish.isAttackModeDistance(), afish.getAttackHotbarSlot());
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (afish.isAttackEnabled()) {
                int dir = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1;
                int slot = cycleSlot(afish.getAttackHotbarSlot(), dir);
                afish.setAttackConfig(true, afish.isAttackModeDistance(), slot);
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (afish.isAttackEnabled()) {
                afish.setCloseMovement(!afish.isCloseMovementEnabled());
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            afish.setCameraJitter(!afish.isCameraJitterEnabled());
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            afish.setAutoDeposit(!afish.isAutoDepositEnabled());
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            afarm.toggle();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            AutoFarmerManager.HorizontalDir next = afarm.getStartDirection() == AutoFarmerManager.HorizontalDir.LEFT
                    ? AutoFarmerManager.HorizontalDir.RIGHT
                    : AutoFarmerManager.HorizontalDir.LEFT;
            boolean wasOn = afarm.isEnabled();
            afarm.setStartDirection(next);
            if (wasOn) {
                afarm.disable();
                afarm.enable();
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (aatk.isEnabled()) aatk.disable(); else aatk.enable();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (aatk.isEnabled()) {
                AutoAttackManager.PriorityMode next = aatk.getPriorityMode() == AutoAttackManager.PriorityMode.NEAREST
                        ? AutoAttackManager.PriorityMode.LOWEST_HEALTH
                        : AutoAttackManager.PriorityMode.NEAREST;
                aatk.setPriorityMode(next);
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (aatk.isEnabled()) {
                float delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -0.5f : 0.5f;
                aatk.setAttackRange(Math.max(2.0f, Math.min(8.0f, aatk.getAttackRange() + delta)));
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (aatk.isEnabled()) {
                int dir = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1;
                aatk.setAttackItemSlot(cycleSlot(aatk.getAttackItemSlot(), dir));
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (aatk.isEnabled()) {
                aatk.setRandomCps(!aatk.isRandomCps());
            }
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (amin.isEnabled()) amin.disable(); else amin.enable();
            return true;
        }

        return false;
    }

    private boolean handleVisualClick(OringoPanel panel, int mx, int my, int button) {
        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        ModConfig cfg = MacroModClient.getConfigManager().getConfig();

        if (inside(mx, my, x, y, w, ROW_H)) {
            cfg.setHudVisible(!cfg.isHudVisible());
            MacroModClient.getConfigManager().save();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            cfg.setKeybindHudVisible(!cfg.isKeybindHudVisible());
            MacroModClient.getConfigManager().save();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            cfg.setTargetEspEnabled(!cfg.isTargetEspEnabled());
            MacroModClient.getConfigManager().save();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            cfg.setEntitiesEspEnabled(!cfg.isEntitiesEspEnabled());
            MacroModClient.getConfigManager().save();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            cfg.setBlockEspEnabled(!cfg.isBlockEspEnabled());
            MacroModClient.getConfigManager().save();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            cfg.setFairySoulsEspEnabled(!cfg.isFairySoulsEspEnabled());
            MacroModClient.getConfigManager().save();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            cfg.setHotspotEspEnabled(!cfg.isHotspotEspEnabled());
            MacroModClient.getConfigManager().save();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            int delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -2 : 2;
            cfg.setBlockEspRadius(cfg.getBlockEspRadius() + delta);
            MacroModClient.getConfigManager().save();
            refreshVisualSources();
            return true;
        }
        y += ROW_H;

        // Entity whitelist label row
        y += ROW_H;

        for (int i = 0; i < visibleEntityRows.size(); i++) {
            if (inside(mx, my, x, y, w, ROW_H)) {
                String id = visibleEntityRows.get(i);
                List<String> wl = cfg.getEntityWhitelist();
                if (wl.contains(id)) wl.remove(id); else wl.add(id);
                MacroModClient.getConfigManager().save();
                return true;
            }
            y += ROW_H;
        }

        // Block whitelist label row
        y += ROW_H;

        for (int i = 0; i < visibleBlockRows.size(); i++) {
            if (inside(mx, my, x, y, w, ROW_H)) {
                String id = visibleBlockRows.get(i);
                List<String> wl = cfg.getBlockWhitelist();
                if (wl.contains(id)) wl.remove(id); else wl.add(id);
                MacroModClient.getConfigManager().save();
                return true;
            }
            y += ROW_H;
        }

        return false;
    }

    private boolean handleMiscClick(OringoPanel panel, int mx, int my, int button) {
        int x = panel.x + PANEL_PAD;
        int w = panel.width - PANEL_PAD * 2;
        int y = panel.y + panel.headerHeight + PANEL_PAD;

        ModConfig cfg = MacroModClient.getConfigManager().getConfig();
        FreelookManager fl = FreelookManager.getInstance();

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (fl.isEnabled()) fl.disable(); else fl.enable();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            int delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -5 : 5;
            int nextFov = Math.max(30, Math.min(200, fl.getFreelookFov() + delta));
            fl.setFreelookFov(nextFov);
            cfg.setFreelookFov(nextFov);
            MacroModClient.getConfigManager().save();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            cfg.setDebugLogging(!cfg.isDebugLogging());
            MacroModClient.getConfigManager().save();
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            float delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -0.005f : 0.005f;
            cfg.setSmoothAimBaseLerp(cfg.getSmoothAimBaseLerp() + delta);
            applySmoothAim(cfg);
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            float delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -0.05f : 0.05f;
            cfg.setSmoothAimSpeedZero(cfg.getSmoothAimSpeedZero() + delta);
            applySmoothAim(cfg);
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            float delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -0.05f : 0.05f;
            cfg.setSmoothAimSpeedSlow(cfg.getSmoothAimSpeedSlow() + delta);
            applySmoothAim(cfg);
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            float delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -0.05f : 0.05f;
            cfg.setSmoothAimSpeedFast(cfg.getSmoothAimSpeedFast() + delta);
            applySmoothAim(cfg);
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            float delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1f : 1f;
            cfg.setSmoothAimSlowZone(cfg.getSmoothAimSlowZone() + delta);
            applySmoothAim(cfg);
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            float delta = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1f : 1f;
            cfg.setSmoothAimFastZone(cfg.getSmoothAimFastZone() + delta);
            applySmoothAim(cfg);
            return true;
        }
        y += ROW_H;

        if (inside(mx, my, x, y, w, ROW_H)) {
            if (client != null) {
                client.setScreen(new ProxyListScreen(this));
            }
            return true;
        }

        return false;
    }

    private void applySmoothAim(ModConfig cfg) {
        MacroModClient.getConfigManager().save();
        var sa = MacroModClient.getSmoothAim();
        if (sa != null) {
            sa.setBaseLerp(cfg.getSmoothAimBaseLerp());
            sa.setSpeedAtZero(cfg.getSmoothAimSpeedZero());
            sa.setSpeedAtSlow(cfg.getSmoothAimSpeedSlow());
            sa.setSpeedAtFast(cfg.getSmoothAimSpeedFast());
            sa.setSlowZoneDeg(cfg.getSmoothAimSlowZone());
            sa.setFastZoneDeg(cfg.getSmoothAimFastZone());
        }
    }

    private int drawLabelRow(DrawContext ctx, int x, int y, String text, int color) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x + 2, y + 3, color);
        return y + ROW_H;
    }

    private int drawActionRow(DrawContext ctx, int x, int y, int w, String label, int mx, int my, boolean enabled) {
        boolean hover = enabled && inside(mx, my, x, y, w, ROW_H);
        int bg = !enabled ? 0x44283038 : (hover ? C_ROW_HOVER : C_ROW);
        ctx.fill(x, y, x + w, y + ROW_H - 1, bg);
        int textColor = enabled ? C_TEXT : C_TEXT_DIM;
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 4, y + 3, textColor);
        return y + ROW_H;
    }

    private int drawToggleRow(DrawContext ctx, int x, int y, int w, String label, boolean on, int mx, int my, boolean enabled) {
        boolean hover = enabled && inside(mx, my, x, y, w, ROW_H);
        int bg = !enabled ? 0x44283038 : (hover ? C_ROW_HOVER : C_ROW);
        ctx.fill(x, y, x + w, y + ROW_H - 1, bg);

        int statusColor = on ? C_ON : C_OFF;
        String suffix = on ? "ON" : "OFF";
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 4, y + 3, enabled ? C_TEXT : C_TEXT_DIM);
        int statusW = textRenderer.getWidth(suffix);
        ctx.drawTextWithShadow(textRenderer, Text.literal(suffix), x + w - statusW - 4, y + 3,
                enabled ? statusColor : C_TEXT_DIM);
        return y + ROW_H;
    }

    private int getPanelContentHeight(OringoPanel panel) {
        return switch (panel.id) {
            case "macros" -> {
                int macroRows = Math.min(MAX_MACRO_ROWS, MacroModClient.getManager().getAll().size());
                int rows = 2 + macroRows + (MacroModClient.getManager().getAll().size() > MAX_MACRO_ROWS ? 1 : 0) + 5;
                yield PANEL_PAD * 2 + rows * ROW_H;
            }
            case "path" -> PANEL_PAD * 2 + 11 * ROW_H;
            case "automation" -> PANEL_PAD * 2 + 15 * ROW_H;
            case "visual" -> {
                int entityRows = Math.max(1, Math.min(MAX_VISUAL_LIST_ROWS, nearbyEntityTypes.size()));
                int blockRows = Math.max(1, Math.min(MAX_VISUAL_LIST_ROWS, nearbyBlockTypes.size()));
                int rows = 10 + entityRows + blockRows;
                yield PANEL_PAD * 2 + rows * ROW_H;
            }
            case "misc" -> PANEL_PAD * 2 + 10 * ROW_H;
            default -> PANEL_PAD * 2 + 4 * ROW_H;
        };
    }

    private void syncSelectedMacro() {
        MacroManager manager = MacroModClient.getManager();
        List<Macro> macros = manager.getAll();
        if (macros.isEmpty()) {
            selectedMacro = null;
            macroScroll = 0;
            return;
        }

        if (selectedMacro == null || manager.getById(selectedMacro.getId()) == null) {
            selectedMacro = macros.get(0);
        }
    }

    private boolean isSelectedRunning() {
        MacroExecutor ex = MacroModClient.getExecutor();
        Macro running = ex.getCurrentMacro();
        return ex.isRunning() && selectedMacro != null && running != null
                && selectedMacro.getId().equals(running.getId());
    }

    private void tickDragging(int mx, int my) {
        if (draggingPanel == null) return;
        if (client == null || client.getWindow() == null) return;

        long handle = client.getWindow().getHandle();
        if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
            draggingPanel.updateDrag(mx, my);
            draggingPanel.x = MathHelper.clamp(draggingPanel.x, 0, Math.max(0, width - draggingPanel.width));
            draggingPanel.y = MathHelper.clamp(draggingPanel.y, 0, Math.max(0, height - draggingPanel.headerHeight));
            return;
        }

        draggingPanel.stopDrag();
        savePanelPositions();
        draggingPanel = null;
    }

    private void savePanelPositions() {
        ModConfig cfg = MacroModClient.getConfigManager().getConfig();
        for (OringoPanel p : panels) {
            switch (p.id) {
                case "macros" -> {
                    cfg.setOringoMacrosPanelX(p.x);
                    cfg.setOringoMacrosPanelY(p.y);
                }
                case "path" -> {
                    cfg.setOringoPathPanelX(p.x);
                    cfg.setOringoPathPanelY(p.y);
                }
                case "automation" -> {
                    cfg.setOringoAutomationPanelX(p.x);
                    cfg.setOringoAutomationPanelY(p.y);
                }
                case "visual" -> {
                    cfg.setOringoVisualPanelX(p.x);
                    cfg.setOringoVisualPanelY(p.y);
                }
                case "misc" -> {
                    cfg.setOringoMiscPanelX(p.x);
                    cfg.setOringoMiscPanelY(p.y);
                }
                default -> {
                }
            }
        }
        MacroModClient.getConfigManager().save();
    }

    private OringoPanel panelById(String id) {
        for (OringoPanel panel : panels) {
            if (panel.id.equals(id)) return panel;
        }
        return null;
    }

    private void bringToFront(OringoPanel panel) {
        panels.remove(panel);
        panels.add(panel);
    }

    private static int cycleSlot(int currentSlot, int direction) {
        if (currentSlot < 0) {
            return direction > 0 ? 0 : 8;
        }
        return Math.floorMod(currentSlot + direction, 9);
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
