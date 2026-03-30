package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.model.BlockTarget;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroConfig;
import com.example.macromod.model.MacroStep;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Screen for editing a macro's name, description, configuration, and viewing its steps.
 */
@Environment(EnvType.CLIENT)
public class MacroEditScreen extends Screen {

    private final Macro macro;
    private final Screen parent;

    private TextFieldWidget nameField;
    private TextFieldWidget descriptionField;

    // Editing working copy
    private String editName;
    private String editDescription;
    private boolean editLoop;
    private boolean editSkipMismatch;
    private boolean editStopOnDanger;
    private int editMiningDelay;
    private int editMoveTimeout;
    private float editArrivalRadius;

    // Step list scroll
    private int stepScrollOffset = 0;
    private int selectedStepIndex = -1;

    public MacroEditScreen(Macro macro, Screen parent) {
        super(Text.translatable("macromod.screen.edit.title"));
        this.macro = macro;
        this.parent = parent;

        // Load working copy
        this.editName = macro.getName();
        this.editDescription = macro.getDescription() != null ? macro.getDescription() : "";
        MacroConfig cfg = macro.getConfig();
        this.editLoop = cfg.isLoop();
        this.editSkipMismatch = cfg.isSkipMismatch();
        this.editStopOnDanger = cfg.isStopOnDanger();
        this.editMiningDelay = cfg.getMiningDelay();
        this.editMoveTimeout = cfg.getMoveTimeout();
        this.editArrivalRadius = cfg.getArrivalRadius();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldWidth = 200;
        int y = 30;
        int lineH = 24;

        // ─── Name field ─────────────────────────────────────────
        nameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, y, fieldWidth, 18,
                Text.translatable("macromod.label.name"));
        nameField.setMaxLength(64);
        nameField.setText(editName);
        nameField.setChangedListener(s -> editName = s);
        addSelectableChild(nameField);
        addDrawable(nameField);
        y += lineH;

        // ─── Description field ──────────────────────────────────
        descriptionField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, y, fieldWidth, 18,
                Text.translatable("macromod.label.description"));
        descriptionField.setMaxLength(256);
        descriptionField.setText(editDescription);
        descriptionField.setChangedListener(s -> editDescription = s);
        addSelectableChild(descriptionField);
        addDrawable(descriptionField);
        y += lineH + 4;

        // ─── Config toggles ────────────────────────────────────
        int toggleWidth = 150;
        int toggleX = centerX - fieldWidth / 2;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("ON"), Text.literal("OFF"))
                .initially(editLoop)
                .build(toggleX, y, toggleWidth, 20,
                        Text.translatable("macromod.label.loop"),
                        (button, value) -> editLoop = value));
        y += lineH;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("ON"), Text.literal("OFF"))
                .initially(editSkipMismatch)
                .build(toggleX, y, toggleWidth, 20,
                        Text.translatable("macromod.label.skip_mismatch"),
                        (button, value) -> editSkipMismatch = value));
        y += lineH;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("ON"), Text.literal("OFF"))
                .initially(editStopOnDanger)
                .build(toggleX, y, toggleWidth, 20,
                        Text.translatable("macromod.label.stop_on_danger"),
                        (button, value) -> editStopOnDanger = value));
        y += lineH;

        // ─── Numeric config (displayed as labels + buttons) ────
        addDrawableChild(ButtonWidget.builder(Text.literal("Delay: " + editMiningDelay + "ms"), button -> {
            editMiningDelay = (editMiningDelay + 50) % 500;
            if (editMiningDelay == 0) editMiningDelay = 50;
            button.setMessage(Text.literal("Delay: " + editMiningDelay + "ms"));
        }).dimensions(toggleX, y, toggleWidth, 20).build());
        y += lineH;

        addDrawableChild(ButtonWidget.builder(Text.literal("Timeout: " + editMoveTimeout + " ticks"), button -> {
            editMoveTimeout = (editMoveTimeout + 100) % 1000;
            if (editMoveTimeout == 0) editMoveTimeout = 100;
            button.setMessage(Text.literal("Timeout: " + editMoveTimeout + " ticks"));
        }).dimensions(toggleX, y, toggleWidth, 20).build());
        y += lineH;

        addDrawableChild(ButtonWidget.builder(Text.literal("Radius: " + String.format("%.1f", editArrivalRadius)), button -> {
            editArrivalRadius += 0.5f;
            if (editArrivalRadius > 5.0f) editArrivalRadius = 0.5f;
            button.setMessage(Text.literal("Radius: " + String.format("%.1f", editArrivalRadius)));
        }).dimensions(toggleX, y, toggleWidth, 20).build());
        y += lineH + 8;

        // ─── Add step button ────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.translatable("macromod.button.add_step"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                BlockPos pos = client.player.getBlockPos();
                MacroStep step = new MacroStep("Step " + (macro.getSteps().size() + 1), pos);
                macro.addStep(step);
            }
        }).dimensions(toggleX, y, toggleWidth, 20).build());

        // ─── Step list navigation ───────────────────────────────
        int rightX = centerX + fieldWidth / 2 - 60;
        addDrawableChild(ButtonWidget.builder(Text.translatable("macromod.button.move_up"), button -> {
            if (selectedStepIndex > 0) {
                var steps = macro.getSteps();
                MacroStep step = steps.remove(selectedStepIndex);
                steps.add(selectedStepIndex - 1, step);
                selectedStepIndex--;
            }
        }).dimensions(rightX, y, 60, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("macromod.button.move_down"), button -> {
            if (selectedStepIndex >= 0 && selectedStepIndex < macro.getSteps().size() - 1) {
                var steps = macro.getSteps();
                MacroStep step = steps.remove(selectedStepIndex);
                steps.add(selectedStepIndex + 1, step);
                selectedStepIndex++;
            }
        }).dimensions(rightX + 65, y, 60, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("macromod.button.remove_step"), button -> {
            if (selectedStepIndex >= 0 && selectedStepIndex < macro.getSteps().size()) {
                macro.removeStep(selectedStepIndex);
                if (selectedStepIndex >= macro.getSteps().size()) {
                    selectedStepIndex = macro.getSteps().size() - 1;
                }
            }
        }).dimensions(rightX + 130, y, 60, 20).build());

        // ─── Save / Cancel buttons ──────────────────────────────
        int bottomY = this.height - 30;
        addDrawableChild(ButtonWidget.builder(Text.translatable("macromod.button.save"), button -> {
            applyChanges();
            if (client != null) {
                client.setScreen(parent);
            }
        }).dimensions(centerX - 105, bottomY, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("macromod.button.cancel"), button -> {
            if (client != null) {
                client.setScreen(parent);
            }
        }).dimensions(centerX + 5, bottomY, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;

        // Title
        context.drawCenteredTextWithShadow(tr, this.title, this.width / 2, 8, 0xFFFFFF);

        // Labels for text fields
        int centerX = this.width / 2;
        int fieldWidth = 200;
        int labelX = centerX - fieldWidth / 2 - 50;

        context.drawTextWithShadow(tr, Text.translatable("macromod.label.name"), labelX, 35, 0xCCCCCC);
        context.drawTextWithShadow(tr, Text.translatable("macromod.label.description"), labelX, 59, 0xCCCCCC);

        // ─── Step list (right panel) ────────────────────────────
        int stepListX = centerX + 10;
        int stepListY = 80;
        int stepListWidth = this.width / 2 - 20;

        context.drawTextWithShadow(tr,
                Text.literal("─── Steps ───").formatted(Formatting.GOLD),
                stepListX, stepListY - 14, 0xFFAA00);

        if (macro.getSteps().isEmpty()) {
            context.drawTextWithShadow(tr,
                    Text.literal("No steps yet").formatted(Formatting.GRAY),
                    stepListX, stepListY, 0x888888);
        } else {
            int maxVisible = (this.height - stepListY - 60) / 26;
            int end = Math.min(stepScrollOffset + maxVisible, macro.getSteps().size());

            for (int i = stepScrollOffset; i < end; i++) {
                MacroStep step = macro.getSteps().get(i);
                int entryY = stepListY + (i - stepScrollOffset) * 26;

                // Highlight selected
                if (i == selectedStepIndex) {
                    context.fill(stepListX - 2, entryY - 1, stepListX + stepListWidth, entryY + 23, 0x40FFFFFF);
                }

                // Step label
                String label = String.format("%d. %s", i + 1, step.getLabel());
                context.drawTextWithShadow(tr,
                        Text.literal(label).formatted(Formatting.WHITE),
                        stepListX + 2, entryY, 0xFFFFFF);

                // Coords + blocks
                String coords = String.format("(%d,%d,%d) - %d blocks",
                        step.getDestination().getX(), step.getDestination().getY(), step.getDestination().getZ(),
                        step.getTargets().size());
                context.drawTextWithShadow(tr,
                        Text.literal(coords).formatted(Formatting.GRAY),
                        stepListX + 2, entryY + 11, 0xAAAAAA);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Step list click detection
        int centerX = this.width / 2;
        int stepListX = centerX + 10;
        int stepListY = 80;
        int stepListWidth = this.width / 2 - 20;

        if (mouseX >= stepListX && mouseX <= stepListX + stepListWidth && mouseY >= stepListY) {
            int relY = (int) (mouseY - stepListY);
            int index = stepScrollOffset + relY / 26;
            if (index >= 0 && index < macro.getSteps().size()) {
                selectedStepIndex = index;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int centerX = this.width / 2;
        if (mouseX >= centerX) {
            stepScrollOffset -= (int) verticalAmount;
            stepScrollOffset = Math.max(0, Math.min(stepScrollOffset, Math.max(0, macro.getSteps().size() - 5)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void applyChanges() {
        macro.setName(editName);
        macro.setDescription(editDescription);

        MacroConfig config = macro.getConfig();
        config.setLoop(editLoop);
        config.setSkipMismatch(editSkipMismatch);
        config.setStopOnDanger(editStopOnDanger);
        config.setMiningDelay(editMiningDelay);
        config.setMoveTimeout(editMoveTimeout);
        config.setArrivalRadius(editArrivalRadius);

        MacroModClient.getManager().save(macro);
    }
}
