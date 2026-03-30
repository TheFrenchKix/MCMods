package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.manager.MacroManager;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroStep;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Main macro management screen. Shows a list of saved macros on the left
 * and details of the selected macro on the right.
 */
@Environment(EnvType.CLIENT)
public class MacroScreen extends Screen {

    private MacroListWidget macroList;
    private Macro selectedMacro;

    // Buttons
    private ButtonWidget runButton;
    private ButtonWidget editButton;
    private ButtonWidget deleteButton;
    private ButtonWidget duplicateButton;

    public MacroScreen() {
        super(Text.translatable("macromod.screen.title"));
    }

    @Override
    protected void init() {
        int listWidth = this.width / 3;
        int detailX = listWidth + 10;
        int detailWidth = this.width - detailX - 10;

        // ─── Macro list (left panel) ────────────────────────────
        macroList = new MacroListWidget(this.client, listWidth, this.height - 60, 20, 28);
        macroList.setX(0);
        addSelectableChild(macroList);
        refreshMacroList();

        // ─── Bottom action buttons ──────────────────────────────
        int bottomY = this.height - 30;
        int buttonWidth = 100;
        int buttonSpacing = 5;

        addDrawableChild(ButtonWidget.builder(Text.translatable("macromod.button.new"), button -> {
            String name = "Macro " + (MacroModClient.getManager().getAll().size() + 1);
            Macro macro = MacroModClient.getManager().create(name);
            refreshMacroList();
            selectMacro(macro);
        }).dimensions(5, bottomY, buttonWidth, 20).build());

        // ─── Detail panel buttons ───────────────────────────────
        int detailButtonY = 22;
        int dbw = 60;

        runButton = ButtonWidget.builder(Text.translatable("macromod.button.run"), button -> {
            if (selectedMacro != null) {
                MacroModClient.getExecutor().start(selectedMacro.getId(), null);
                close();
            }
        }).dimensions(detailX, detailButtonY, dbw, 20).build();
        addDrawableChild(runButton);

        editButton = ButtonWidget.builder(Text.translatable("macromod.button.edit"), button -> {
            if (selectedMacro != null && client != null) {
                client.setScreen(new MacroEditScreen(selectedMacro, this));
            }
        }).dimensions(detailX + dbw + buttonSpacing, detailButtonY, dbw, 20).build();
        addDrawableChild(editButton);

        deleteButton = ButtonWidget.builder(Text.translatable("macromod.button.delete"), button -> {
            if (selectedMacro != null) {
                MacroModClient.getManager().delete(selectedMacro.getId());
                selectedMacro = null;
                refreshMacroList();
            }
        }).dimensions(detailX + (dbw + buttonSpacing) * 2, detailButtonY, dbw, 20).build();
        addDrawableChild(deleteButton);

        duplicateButton = ButtonWidget.builder(Text.translatable("macromod.button.duplicate"), button -> {
            if (selectedMacro != null) {
                Macro dup = MacroModClient.getManager().duplicate(selectedMacro.getId());
                refreshMacroList();
                if (dup != null) selectMacro(dup);
            }
        }).dimensions(detailX + (dbw + buttonSpacing) * 3, detailButtonY, dbw, 20).build();
        addDrawableChild(duplicateButton);

        updateButtonStates();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Render the list manually
        macroList.render(context, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        int listWidth = this.width / 3;
        int detailX = listWidth + 10;

        // ─── Title ──────────────────────────────────────────────
        context.drawCenteredTextWithShadow(tr, this.title, this.width / 2, 6, 0xFFFFFF);

        // ─── Detail panel ───────────────────────────────────────
        if (selectedMacro != null) {
            int y = 48;
            int lineH = 12;

            // Name
            context.drawTextWithShadow(tr,
                    Text.literal(selectedMacro.getName()).formatted(Formatting.WHITE, Formatting.BOLD),
                    detailX, y, 0xFFFFFF);
            y += lineH + 2;

            // Description
            if (selectedMacro.getDescription() != null && !selectedMacro.getDescription().isEmpty()) {
                context.drawTextWithShadow(tr,
                        Text.literal(selectedMacro.getDescription()).formatted(Formatting.GRAY),
                        detailX, y, 0xAAAAAA);
                y += lineH;
            }

            // Created date
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            context.drawTextWithShadow(tr,
                    Text.translatable("macromod.label.created", sdf.format(new Date(selectedMacro.getCreatedAt()))),
                    detailX, y, 0xAAAAAA);
            y += lineH;

            // Step count
            context.drawTextWithShadow(tr,
                    Text.translatable("macromod.label.steps", selectedMacro.getSteps().size()),
                    detailX, y, 0xFFFFFF);
            y += lineH;

            // Config info
            String cfgInfo = String.format("Loop: %s | Skip: %s | Danger: %s",
                    selectedMacro.getConfig().isLoop() ? "ON" : "OFF",
                    selectedMacro.getConfig().isSkipMismatch() ? "ON" : "OFF",
                    selectedMacro.getConfig().isStopOnDanger() ? "ON" : "OFF");
            context.drawTextWithShadow(tr,
                    Text.literal(cfgInfo).formatted(Formatting.GRAY),
                    detailX, y, 0x999999);
            y += lineH + 4;

            // Steps list
            context.drawTextWithShadow(tr,
                    Text.literal("─── Steps ───").formatted(Formatting.GOLD),
                    detailX, y, 0xFFAA00);
            y += lineH;

            for (int i = 0; i < selectedMacro.getSteps().size(); i++) {
                MacroStep step = selectedMacro.getSteps().get(i);
                String stepText = String.format("%d. %s (%d,%d,%d) - %d blocks",
                        i + 1, step.getLabel(),
                        step.getDestination().getX(), step.getDestination().getY(), step.getDestination().getZ(),
                        step.getTargets().size());
                context.drawTextWithShadow(tr,
                        Text.literal(stepText).formatted(Formatting.AQUA),
                        detailX + 4, y, 0x55FFFF);
                y += lineH;

                if (y > this.height - 40) {
                    context.drawTextWithShadow(tr,
                            Text.literal("...").formatted(Formatting.GRAY),
                            detailX + 4, y, 0xAAAAAA);
                    break;
                }
            }
        } else {
            // No selection message
            context.drawCenteredTextWithShadow(tr,
                    Text.translatable("macromod.screen.no_selection").formatted(Formatting.GRAY),
                    listWidth + (this.width - listWidth) / 2,
                    this.height / 2,
                    0x888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (macroList.mouseClicked(mouseX, mouseY, button)) {
            MacroListEntry selected = macroList.getSelectedOrNull();
            if (selected != null) {
                selectMacro(selected.macro);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (macroList.isMouseOver(mouseX, mouseY)) {
            return macroList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void selectMacro(Macro macro) {
        this.selectedMacro = macro;
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedMacro != null;
        runButton.active = hasSelection;
        editButton.active = hasSelection;
        deleteButton.active = hasSelection;
        duplicateButton.active = hasSelection;
    }

    private void refreshMacroList() {
        if (macroList == null) return;
        macroList.clearEntries();
        List<Macro> macros = MacroModClient.getManager().getAll();
        for (Macro macro : macros) {
            macroList.addMacroEntry(new MacroListEntry(macro));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Inner classes
    // ═══════════════════════════════════════════════════════════════

    /**
     * Scrollable list widget for macros.
     */
    private class MacroListWidget extends AlwaysSelectedEntryListWidget<MacroListEntry> {

        public MacroListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }

        public void clearEntries() {
            this.children().clear();
        }

        public int addMacroEntry(MacroListEntry entry) {
            return super.addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return this.width - 10;
        }
    }

    /**
     * A single entry in the macro list.
     */
    private class MacroListEntry extends AlwaysSelectedEntryListWidget.Entry<MacroListEntry> {
        final Macro macro;

        MacroListEntry(Macro macro) {
            this.macro = macro;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickDelta) {
            TextRenderer tr = MacroScreen.this.textRenderer;

            // Name
            context.drawTextWithShadow(tr,
                    Text.literal(macro.getName()).formatted(Formatting.WHITE),
                    x + 4, y + 2, 0xFFFFFF);

            // Step count + block count
            String info = macro.getSteps().size() + " steps, " + macro.getTotalBlockCount() + " blocks";
            context.drawTextWithShadow(tr,
                    Text.literal(info).formatted(Formatting.GRAY),
                    x + 4, y + 14, 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            selectMacro(this.macro);
            return true;
        }

        @Override
        public Text getNarration() {
            return Text.literal(macro.getName());
        }
    }
}
