package com.mwa.n0name.gui;

import com.mwa.n0name.gui.components.*;
import com.mwa.n0name.macro.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern animated Macro Manager screen.
 * Sidebar (Macros / Settings / About) + scrollable content with macro cards.
 */
public class MacroManagerScreen extends Screen {

    private final MacroManagerModule module;

    // Layout
    private static final int PANEL_W = 600;
    private static final int PANEL_H = 400;
    private static final int SIDEBAR_W = 140;
    private static final int CONTENT_W = PANEL_W - SIDEBAR_W;
    private static final int HEADER_H = 30;

    // Theme
    private static final int C_BG       = 0xF0121218;
    private static final int C_HEADER   = 0xFF1A1A30;
    private static final int C_CONTENT  = 0xF0161622;
    private static final int C_BORDER   = 0xFF333350;
    private static final int C_TEXT     = 0xFFE0E0E8;
    private static final int C_MUTED    = 0xFF707088;
    private static final int C_ACCENT   = 0xFF66FF44;
    private static final int C_DIM      = 0x80000000;

    // Animation
    private float panelSlide = 0f;
    private float tabTransition = 1f; // 0 = fading out old, 1 = showing current
    private int pendingTab = -1;

    // Components
    private SidebarComponent sidebar;
    private ScrollablePanel contentPanel;
    private final List<CardComponent> macroCards = new ArrayList<>();
    private ModalComponent editModal;
    private TextInputComponent editNameInput;
    private ButtonComponent editTypeButton;
    private ModalComponent createModal;
    private TextInputComponent createNameInput;
    private ButtonComponent createTypeButton;
    private MacroType createType = MacroType.CUSTOM;

    // Editing state
    private String editingMacroName = null;
    private MacroType editType = MacroType.CUSTOM;

    // Tab indices
    private static final int TAB_MACROS = 0;
    private static final int TAB_SETTINGS = 1;
    private static final int TAB_ABOUT = 2;
    private int currentTab = TAB_MACROS;

    public MacroManagerScreen(MacroManagerModule module) {
        super(Text.of("Macro Manager"));
        this.module = module;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        panelSlide = 0f;
        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        // Sidebar
        sidebar = new SidebarComponent(px, py + HEADER_H, SIDEBAR_W, PANEL_H - HEADER_H, this::onTabSelect);
        sidebar.addItem("\u25A0", "Macros", 0xFF44DDFF);
        sidebar.addItem("\u2699", "Settings", 0xFFCCCC44);
        sidebar.addItem("\u2139", "About", 0xFFAAAAAA);

        // Content panel
        int contentX = px + SIDEBAR_W;
        int contentY = py + HEADER_H;
        contentPanel = new ScrollablePanel(contentX, contentY, CONTENT_W, PANEL_H - HEADER_H);

        // Edit modal
        editModal = new ModalComponent(320, 260, "Edit Macro", () -> editingMacroName = null);
        editModal.setScreenSize(width, height);

        editNameInput = new TextInputComponent(0, 0, 200, 20, "Macro name...", 32, C_ACCENT);
        editTypeButton = new ButtonComponent(0, 0, 80, 20, "Type", C_ACCENT, () -> {
            if (editingMacroName != null) {
                MacroType[] types = MacroType.values();
                editType = types[(editType.ordinal() + 1) % types.length];
                editTypeButton.setLabel(editType.label);
                editTypeButton.setAccentColor(editType.color);
            }
        });

        // Create modal
        createModal = new ModalComponent(300, 180, "Create Macro", () -> {});
        createModal.setScreenSize(width, height);

        createNameInput = new TextInputComponent(0, 0, 200, 20, "Macro name...", 32, C_ACCENT);
        createTypeButton = new ButtonComponent(0, 0, 80, 20, "Custom", 0xFF5588FF, () -> {
            MacroType[] types = MacroType.values();
            createType = types[(createType.ordinal() + 1) % types.length];
            createTypeButton.setLabel(createType.label);
            createTypeButton.setAccentColor(createType.color);
        });

        rebuildCards();
    }

    private void onTabSelect(int index) {
        if (index == currentTab) return;
        pendingTab = index;
        tabTransition = 0f;
    }

    private void rebuildCards() {
        macroCards.clear();
        contentPanel.clearChildren();

        if (currentTab != TAB_MACROS) return;

        List<Macro> macros = module.getAllMacros();
        int px = (width - PANEL_W) / 2;
        int contentX = px + SIDEBAR_W;
        int contentY = (height - PANEL_H) / 2 + HEADER_H;
        int cardW = CONTENT_W - 24;
        int cardH = 80;
        int y = contentY + 42; // after header buttons

        for (Macro macro : macros) {
            CardComponent card = new CardComponent(contentX + 10, y, cardW, cardH);
            card.setTitle(macro.getName());
            card.setBadge(macro.getType().label, macro.getType().color);
            card.setSubtitle(macro.stepCount() + " steps");

            final String macroName = macro.getName();

            // Play button
            card.addActionButton(new ButtonComponent(0, 0, 36, 20, "\u25B6", "Play",
                    0xFF44FF44, () -> {
                module.playMacro(macroName);
                close();
            }));
            // Record button
            card.addActionButton(new ButtonComponent(0, 0, 36, 20, "\u25CF", "Rec",
                    0xFFFF4444, () -> {
                Macro m = module.getMacro(macroName);
                if (m != null) {
                    module.startRecording(macroName + "_rec", m.getType());
                    close();
                }
            }));
            // Edit button
            card.addActionButton(new ButtonComponent(0, 0, 36, 20, "\u270E", "Edit",
                    0xFF44DDFF, () -> openEditModal(macroName)));
            // Delete button
            card.addActionButton(new ButtonComponent(0, 0, 36, 20, "\u2716", "Del",
                    0xFFFF5555, () -> {
                module.deleteMacro(macroName);
                rebuildCards();
            }));
            // Export button
            card.addActionButton(new ButtonComponent(0, 0, 36, 20, "\u21E9", "Exp",
                    0xFFCCCC44, () -> exportMacro(macroName)));

            // Repeat toggle
            ToggleComponent repeat = new ToggleComponent(0, 0, "Repeat", macro.isRepeat(),
                    C_ACCENT, () -> module.toggleRepeat(macroName));
            card.setRepeatToggle(repeat);

            card.layout();
            macroCards.add(card);
            contentPanel.addChild(card);

            y += cardH + 6;
        }

        contentPanel.setContentHeight(y - contentY);
    }

    private void openEditModal(String macroName) {
        editingMacroName = macroName;
        Macro macro = module.getMacro(macroName);
        if (macro == null) return;

        editNameInput.setValue(macro.getName());
        editNameInput.setFocused(false);
        editType = macro.getType();
        editTypeButton.setLabel(editType.label);
        editTypeButton.setAccentColor(editType.color);

        // Position fields inside modal
        int mx = (width - 320) / 2;
        int my = (height - 260) / 2;
        editNameInput.setPosition(mx + 20, my + 42);
        editTypeButton.setPosition(mx + 230, my + 42);

        editModal.clearChildren();
        editModal.addChild(editNameInput);
        editModal.addChild(editTypeButton);

        // Step list (read-only)
        int stepY = my + 76;
        List<MacroStep> steps = macro.getSteps();
        int maxShow = Math.min(steps.size(), 8);
        for (int i = 0; i < maxShow; i++) {
            MacroStep step = steps.get(i);
            String text = (i + 1) + ". " + step.getType().label + "  (" +
                    step.getBlockX() + ", " + step.getBlockY() + ", " + step.getBlockZ() + ")";
            // Simple text label as a button
            final int fi = i;
            ButtonComponent stepLabel = new ButtonComponent(mx + 20, stepY + i * 18, 280, 16,
                    text, 0xFF555566, () -> {});
            editModal.addChild(stepLabel);
        }
        if (steps.size() > maxShow) {
            ButtonComponent moreLabel = new ButtonComponent(mx + 20, stepY + maxShow * 18, 280, 16,
                    "... +" + (steps.size() - maxShow) + " more steps", 0xFF555566, () -> {});
            editModal.addChild(moreLabel);
        }

        // Save button
        ButtonComponent saveBtn = new ButtonComponent(mx + 20, my + 226, 100, 22,
                "Save", C_ACCENT, () -> {
            if (editingMacroName != null) {
                Macro m = module.getMacro(editingMacroName);
                if (m != null) {
                    String newName = editNameInput.getValue().trim();
                    if (!newName.isEmpty() && !newName.equals(editingMacroName)) {
                        module.getManager().renameMacro(editingMacroName, newName);
                    }
                    m = module.getMacro(newName.isEmpty() ? editingMacroName : newName);
                    if (m != null) {
                        m.setType(editType);
                        module.getManager().updateMacro(m);
                    }
                }
                editModal.close();
                editingMacroName = null;
                rebuildCards();
            }
        });

        // Cancel button
        ButtonComponent cancelBtn = new ButtonComponent(mx + 130, my + 226, 100, 22,
                "Cancel", 0xFF707088, () -> {
            editModal.close();
            editingMacroName = null;
        });

        editModal.addChild(saveBtn);
        editModal.addChild(cancelBtn);
        editModal.open();
    }

    private void openCreateModal() {
        createNameInput.setValue("");
        createNameInput.setFocused(true);
        createType = MacroType.CUSTOM;
        createTypeButton.setLabel(createType.label);
        createTypeButton.setAccentColor(createType.color);

        int mx = (width - 300) / 2;
        int my = (height - 180) / 2;
        createNameInput.setPosition(mx + 20, my + 42);
        createTypeButton.setPosition(mx + 230, my + 42);

        createModal.clearChildren();
        createModal.addChild(createNameInput);
        createModal.addChild(createTypeButton);

        // Create button
        ButtonComponent createBtn = new ButtonComponent(mx + 20, my + 80, 120, 22,
                "\u002B Create", C_ACCENT, () -> {
            String name = createNameInput.getValue().trim();
            if (!name.isEmpty()) {
                module.createMacro(name, createType);
                createModal.close();
                rebuildCards();
            }
        });

        // Record button
        ButtonComponent recBtn = new ButtonComponent(mx + 150, my + 80, 120, 22,
                "\u25CF Record", 0xFFFF4444, () -> {
            String name = createNameInput.getValue().trim();
            if (!name.isEmpty()) {
                module.startRecording(name, createType);
                createModal.close();
                close();
            }
        });

        createModal.addChild(createBtn);
        createModal.addChild(recBtn);
        createModal.open();
    }

    private void importMacro() {
        // Run file dialog on a separate thread to avoid blocking render
        new Thread(() -> {
            try {
                FileDialog fd = new FileDialog((Frame) null, "Import Macro", FileDialog.LOAD);
                fd.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".json"));
                fd.setVisible(true);
                String file = fd.getFile();
                String dir = fd.getDirectory();
                if (file != null && dir != null) {
                    Path path = Path.of(dir, file);
                    MinecraftClient.getInstance().execute(() -> {
                        module.getManager().importMacro(path);
                        rebuildCards();
                    });
                }
            } catch (Exception ignored) {}
        }, "MacroImport").start();
    }

    private void exportMacro(String macroName) {
        new Thread(() -> {
            try {
                FileDialog fd = new FileDialog((Frame) null, "Export Macro", FileDialog.SAVE);
                fd.setFile(macroName + ".json");
                fd.setVisible(true);
                String file = fd.getFile();
                String dir = fd.getDirectory();
                if (file != null && dir != null) {
                    Path dest = Path.of(dir, file);
                    MinecraftClient.getInstance().execute(() ->
                        module.getManager().exportMacro(macroName, dest));
                }
            } catch (Exception ignored) {}
        }, "MacroExport").start();
    }

    // =========================================================
    //  RENDER
    // =========================================================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Panel open animation
        panelSlide += (1f - panelSlide) * 0.16f;
        if (panelSlide > 0.998f) panelSlide = 1f;

        // Tab transition
        if (pendingTab >= 0) {
            tabTransition += 0.25f;
            if (tabTransition >= 1f) {
                currentTab = pendingTab;
                pendingTab = -1;
                tabTransition = 1f;
                rebuildCards();
            }
        } else if (tabTransition < 1f) {
            tabTransition = Math.min(1f, tabTransition + 0.25f);
        }

        int slideOff = (int) ((1f - panelSlide) * -30);
        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2 + slideOff;

        // Dim backdrop
        ctx.fill(0, 0, width, height, C_DIM);

        // Panel background
        AnimatedComponent.drawRoundedRect(ctx, px, py, PANEL_W, PANEL_H, C_BG);

        // Header
        ctx.fill(px, py, px + PANEL_W, py + HEADER_H, C_HEADER);
        ctx.fill(px, py + HEADER_H - 1, px + PANEL_W, py + HEADER_H, C_BORDER);

        // Pulsing top accent
        float pulse = (float) (Math.sin(System.currentTimeMillis() / 800.0) * 0.3 + 0.7);
        ctx.fill(px, py, px + PANEL_W, py + 2, ((int) (pulse * 255) << 24) | (C_ACCENT & 0x00FFFFFF));

        // Title
        TextRenderer tr = textRenderer;
        ctx.drawTextWithShadow(tr, "Macro Manager", px + 14, py + 10, C_ACCENT);
        ctx.drawTextWithShadow(tr, "v2.1", px + 14 + tr.getWidth("Macro Manager") + 6, py + 10, C_MUTED);

        // Close button
        ctx.drawTextWithShadow(tr, "\u00D7", px + PANEL_W - 16, py + 10, C_MUTED);

        // Sidebar
        sidebar.setPosition(px, py + HEADER_H);
        sidebar.render(ctx, mouseX, mouseY, delta);

        // Content area background
        int cx = px + SIDEBAR_W;
        int cy = py + HEADER_H;
        ctx.fill(cx, cy, cx + CONTENT_W, cy + PANEL_H - HEADER_H, C_CONTENT);

        // Apply tab transition alpha
        float contentAlpha = tabTransition;

        // Content rendering
        ctx.enableScissor(cx, cy, cx + CONTENT_W, cy + PANEL_H - HEADER_H);

        switch (currentTab) {
            case TAB_MACROS -> renderMacrosTab(ctx, cx, cy, mouseX, mouseY, delta);
            case TAB_SETTINGS -> renderSettingsTab(ctx, cx, cy, mouseX, mouseY, delta);
            case TAB_ABOUT -> renderAboutTab(ctx, cx, cy);
        }

        ctx.disableScissor();

        // Render modals on top
        if (editModal.isOpen()) {
            editModal.render(ctx, mouseX, mouseY, delta);
        }
        if (createModal.isOpen()) {
            createModal.render(ctx, mouseX, mouseY, delta);
        }
    }

    private void renderMacrosTab(DrawContext ctx, int cx, int cy, int mouseX, int mouseY, float delta) {
        TextRenderer tr = textRenderer;

        // Header buttons row
        int btnY = cy + 6;
        int btnX = cx + 10;

        // Create button
        AnimatedComponent.drawRoundedRect(ctx, btnX, btnY, 80, 20, 0xFF2D2D50);
        ctx.drawTextWithShadow(tr, "+ Create", btnX + 10, btnY + 6, C_ACCENT);
        // Folder button
        AnimatedComponent.drawRoundedRect(ctx, btnX + 88, btnY, 80, 20, 0xFF2D2D50);
        ctx.drawTextWithShadow(tr, "\uD83D\uDCC1 Folder", btnX + 96, btnY + 6, 0xFFCCCC44);
        // Import button
        AnimatedComponent.drawRoundedRect(ctx, btnX + 176, btnY, 80, 20, 0xFF2D2D50);
        ctx.drawTextWithShadow(tr, "\u21E7 Import", btnX + 184, btnY + 6, 0xFF44DDFF);

        // Macro count
        int count = module.getAllMacros().size();
        ctx.drawTextWithShadow(tr, count + " macro" + (count != 1 ? "s" : ""),
                cx + CONTENT_W - 70, btnY + 6, C_MUTED);

        // Render card list via scroll panel
        contentPanel.setPosition(cx, cy + 32);
        contentPanel.setSize(CONTENT_W, PANEL_H - HEADER_H - 32);
        contentPanel.render(ctx, mouseX, mouseY, delta);
    }

    private void renderSettingsTab(DrawContext ctx, int cx, int cy, int mouseX, int mouseY, float delta) {
        TextRenderer tr = textRenderer;
        int y = cy + 14;

        ctx.drawTextWithShadow(tr, "Recording Keys", cx + 14, y, C_ACCENT);
        y += 18;
        ctx.drawTextWithShadow(tr, "SHIFT  \u2192  Walk point", cx + 20, y, C_TEXT);
        y += 14;
        ctx.drawTextWithShadow(tr, "Left Click  \u2192  Mine block", cx + 20, y, C_TEXT);
        y += 14;
        ctx.drawTextWithShadow(tr, "Right Click  \u2192  Teleport point", cx + 20, y, C_TEXT);
        y += 26;

        ctx.drawTextWithShadow(tr, "Visualization", cx + 14, y, C_ACCENT);
        y += 18;
        ctx.drawTextWithShadow(tr, "3D path nodes are shown when a macro is", cx + 20, y, C_MUTED);
        y += 12;
        ctx.drawTextWithShadow(tr, "selected, recording, or executing.", cx + 20, y, C_MUTED);
        y += 26;

        ctx.drawTextWithShadow(tr, "Anti-Stuck System", cx + 14, y, C_ACCENT);
        y += 18;
        ctx.drawTextWithShadow(tr, "6-phase escalation: jump, strafe, jump+strafe,", cx + 20, y, C_MUTED);
        y += 12;
        ctx.drawTextWithShadow(tr, "back+jump, skip node, give up (120 tick max).", cx + 20, y, C_MUTED);
        y += 26;

        ctx.drawTextWithShadow(tr, "Keybinds", cx + 14, y, C_ACCENT);
        y += 18;
        ctx.drawTextWithShadow(tr, "M  \u2192  Open Macro Manager", cx + 20, y, C_TEXT);
        y += 14;
        ctx.drawTextWithShadow(tr, "Right Shift  \u2192  Open n0name Menu", cx + 20, y, C_TEXT);
    }

    private void renderAboutTab(DrawContext ctx, int cx, int cy) {
        TextRenderer tr = textRenderer;
        int y = cy + 20;

        ctx.drawTextWithShadow(tr, "n0name Mod", cx + 14, y, C_ACCENT);
        y += 14;
        ctx.drawTextWithShadow(tr, "Macro Manager System v2.1", cx + 14, y, C_TEXT);
        y += 22;
        ctx.drawTextWithShadow(tr, "Features:", cx + 14, y, C_ACCENT);
        y += 16;
        ctx.drawTextWithShadow(tr, "\u2022 Record macros via player inputs", cx + 20, y, C_MUTED);
        y += 14;
        ctx.drawTextWithShadow(tr, "\u2022 A* pathfinding (BlockPos-based)", cx + 20, y, C_MUTED);
        y += 14;
        ctx.drawTextWithShadow(tr, "\u2022 23 step types in 5 categories", cx + 20, y, C_MUTED);
        y += 14;
        ctx.drawTextWithShadow(tr, "\u2022 6-phase anti-stuck system", cx + 20, y, C_MUTED);
        y += 14;
        ctx.drawTextWithShadow(tr, "\u2022 3D path visualization + minimap", cx + 20, y, C_MUTED);
        y += 14;
        ctx.drawTextWithShadow(tr, "\u2022 JSON import/export", cx + 20, y, C_MUTED);
        y += 14;
        ctx.drawTextWithShadow(tr, "\u2022 Repeat/loop macros", cx + 20, y, C_MUTED);
        y += 22;
        ctx.drawTextWithShadow(tr, "Minecraft 1.21.x \u2022 Fabric \u2022 Yarn", cx + 14, y, C_MUTED);
    }

    // =========================================================
    //  INPUT HANDLING
    // =========================================================

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        // Modals first
        if (editModal.isOpen()) {
            editNameInput.clickOutside(mouseX, mouseY);
            return editModal.mouseClicked(mouseX, mouseY, button);
        }
        if (createModal.isOpen()) {
            createNameInput.clickOutside(mouseX, mouseY);
            return createModal.mouseClicked(mouseX, mouseY, button);
        }

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        // Close button
        if (mouseX >= px + PANEL_W - 20 && mouseX < px + PANEL_W && mouseY >= py && mouseY < py + HEADER_H) {
            close();
            return true;
        }

        // Sidebar
        if (sidebar.mouseClicked(mouseX, mouseY, button)) return true;

        // Header buttons (Macros tab)
        if (currentTab == TAB_MACROS) {
            int cx = px + SIDEBAR_W;
            int cy = py + HEADER_H;
            int btnY = cy + 6;
            int btnX = cx + 10;

            // Create
            if (mouseX >= btnX && mouseX < btnX + 80 && mouseY >= btnY && mouseY < btnY + 20) {
                openCreateModal();
                return true;
            }
            // Folder
            if (mouseX >= btnX + 88 && mouseX < btnX + 168 && mouseY >= btnY && mouseY < btnY + 20) {
                module.openMacrosFolder();
                return true;
            }
            // Import
            if (mouseX >= btnX + 176 && mouseX < btnX + 256 && mouseY >= btnY && mouseY < btnY + 20) {
                importMacro();
                return true;
            }
        }

        // Content panel
        if (contentPanel.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (editModal.isOpen()) return editModal.mouseReleased(mouseX, mouseY, button);
        if (createModal.isOpen()) return createModal.mouseReleased(mouseX, mouseY, button);
        contentPanel.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (editModal.isOpen() || createModal.isOpen()) return true;
        if (contentPanel.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        int keyCode = key.key();
        // Handle text inputs in modals
        if (editModal.isOpen() && editNameInput.isFocused()) {
            if (editNameInput.keyPressed(keyCode)) return true;
        }
        if (createModal.isOpen() && createNameInput.isFocused()) {
            if (createNameInput.keyPressed(keyCode)) return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (editModal.isOpen()) {
                editModal.close();
                editingMacroName = null;
                return true;
            }
            if (createModal.isOpen()) {
                createModal.close();
                return true;
            }
            close();
            return true;
        }

        return super.keyPressed(key);
    }

    // charTyped handling for text input in modals
    public boolean handleCharTyped(char chr) {
        if (editModal.isOpen() && editNameInput.charTyped(chr)) return true;
        if (createModal.isOpen() && createNameInput.charTyped(chr)) return true;
        return false;
    }
}
