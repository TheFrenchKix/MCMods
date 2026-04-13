package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.manager.AutoAttackManager;
import com.example.macromod.manager.AutoFishingManager;
import com.example.macromod.manager.MacroExecutor;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroState;
import com.example.macromod.model.MacroStep;
import com.example.macromod.recording.MacroRecorder;
import com.example.macromod.recording.RecordingState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;

/**
 * Renders HUD overlay showing macro execution and recording status.
 * Displayed in the top-left corner when a macro is active or recording.
 */
@Environment(EnvType.CLIENT)
public class HudOverlay {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int BG_COLOR = 0x80000000; // semi-transparent black
    private static final int BAR_BG_COLOR = 0xFF333333;
    private static final int BAR_FG_COLOR = 0xFF00CC00;
    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 6;

    /**
     * Called by HudRenderCallback each frame.
     */
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        TextRenderer textRenderer = client.textRenderer;
        AutoAttackManager aam = AutoAttackManager.getInstance();
        AutoFishingManager afm = AutoFishingManager.getInstance();
        boolean autoAttackActive = aam != null && aam.isEnabled();
        boolean autoFishActive = afm != null && afm.isEnabled();

        int x = PADDING + 2;
        int y = PADDING + 2;

        // Modules HUD (Auto Attack / Auto Fish) — always rendered when active,
        // independent of isHudVisible config and executor/recorder state.
        if (autoAttackActive || autoFishActive) {
            renderModulesHud(context, textRenderer, aam, afm, x, y);
            int moduleLines = (autoAttackActive ? 1 : 0) + (autoFishActive ? 1 : 0);
            y += moduleLines * LINE_HEIGHT + PADDING * 2 + 4; // gap below
        }

        // Executor / recorder HUD — gated by isHudVisible config
        var cfgMgr = MacroModClient.getConfigManager();
        if (cfgMgr == null || !cfgMgr.getConfig().isHudVisible()) return;

        MacroExecutor executor = MacroModClient.getExecutor();
        MacroRecorder recorder = MacroModClient.getRecorder();
        boolean executorActive = executor != null && (executor.isRunning() || executor.getState() == MacroState.PAUSED);
        boolean recorderActive = recorder != null && recorder.getState() != RecordingState.IDLE;

        if (!executorActive && !recorderActive) return;

        if (recorderActive) {
            renderRecordingHud(context, textRenderer, recorder, x, y);
        } else {
            renderExecutionHud(context, textRenderer, executor, x, y);
        }
    }

    private void renderRecordingHud(DrawContext context, TextRenderer textRenderer, MacroRecorder recorder, int x, int y) {
        Macro macro = recorder.getCurrentMacro();
        if (macro == null) return;

        int width = BAR_WIDTH + PADDING * 4;
        int height = LINE_HEIGHT * 5 + PADDING * 2;
        context.fill(x - PADDING, y - PADDING, x + width, y + height, BG_COLOR);

        // State
        String stateStr = recorder.getState() == RecordingState.RECORDING ? "⏺ Recording" : "⏸ Paused";
        context.drawTextWithShadow(textRenderer,
                Text.literal(stateStr).formatted(Formatting.RED),
                x, y, 0xFFFFFF);
        y += LINE_HEIGHT;

        // Macro name
        context.drawTextWithShadow(textRenderer,
                Text.literal("Recording: " + macro.getName()),
                x, y, 0xFFFF00);
        y += LINE_HEIGHT;

        // Step count
        context.drawTextWithShadow(textRenderer,
                Text.literal("Steps: " + macro.getSteps().size()),
                x, y, 0xFFFFFF);
        y += LINE_HEIGHT;

        // Blocks in current step
        MacroStep currentStep = recorder.getCurrentStep();
        int blockCount = currentStep != null ? currentStep.getTargets().size() : 0;
        context.drawTextWithShadow(textRenderer,
                Text.literal("Blocks in step: " + blockCount),
                x, y, 0xFFFFFF);
        y += LINE_HEIGHT;

        // Last added
        String lastAdded = recorder.getLastAddedInfo();
        if (!lastAdded.isEmpty()) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Last: " + lastAdded),
                    x, y, 0xAAAAAA);
        }
    }

    private void renderExecutionHud(DrawContext context, TextRenderer textRenderer, MacroExecutor executor, int x, int y) {
        Macro macro = executor.getCurrentMacro();
        if (macro == null) return;

        int width = BAR_WIDTH + PADDING * 4;
        int height = LINE_HEIGHT * 5 + BAR_HEIGHT + PADDING * 3;
        context.fill(x - PADDING, y - PADDING, x + width, y + height, BG_COLOR);

        // Macro name
        context.drawTextWithShadow(textRenderer,
                Text.literal("Macro: " + macro.getName()),
                x, y, 0x55FF55);
        y += LINE_HEIGHT;

        // State
        String stateStr = getStateDisplay(executor.getState());
        int stateColor = getStateColor(executor.getState());
        context.drawTextWithShadow(textRenderer,
                Text.literal("State: " + stateStr),
                x, y, stateColor);
        y += LINE_HEIGHT;

        // Step progress
        context.drawTextWithShadow(textRenderer,
                Text.literal("Step " + (executor.getCurrentStepIndex() + 1) + " / " + executor.getTotalSteps()),
                x, y, 0xFFFFFF);
        y += LINE_HEIGHT;

        // Block progress in current step
        context.drawTextWithShadow(textRenderer,
                Text.literal("Blocks: " + executor.getBlocksProcessedInStep() + " / " + executor.getTotalBlocksInStep()),
                x, y, 0xFFFFFF);
        y += LINE_HEIGHT;

        // Waypoint coordinates
        int stepIdx = executor.getCurrentStepIndex();
        if (stepIdx < macro.getSteps().size()) {
            MacroStep step = macro.getSteps().get(stepIdx);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("WP: " + step.getDestination().getX()
                            + ", " + step.getDestination().getY()
                            + ", " + step.getDestination().getZ()),
                    x, y, 0xCCCCCC);
            y += LINE_HEIGHT;
        }

        // Progress bar
        y += 2;
        float progress = executor.getTotalSteps() > 0
                ? (float) executor.getCurrentStepIndex() / executor.getTotalSteps()
                : 0;
        int filledWidth = (int) (BAR_WIDTH * progress);
        context.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BAR_BG_COLOR);
        if (filledWidth > 0) {
            context.fill(x, y, x + filledWidth, y + BAR_HEIGHT, BAR_FG_COLOR);
        }
    }

    private void renderModulesHud(DrawContext context, TextRenderer textRenderer,
                                    AutoAttackManager aam, AutoFishingManager afm, int x, int y) {
        // Build lines dynamically (only active modules)
        List<String> lines = new java.util.ArrayList<>();
        if (aam != null && aam.isEnabled()) {
            String targetName = aam.getCurrentTarget() != null
                    ? aam.getCurrentTarget().getName().getString()
                    : "scanning...";
            lines.add("[ATK] Auto Attack: " + targetName);
        }
        if (afm != null && afm.isEnabled()) {
            lines.add("[FISH] Auto Fish");
        }
        if (lines.isEmpty()) return;

        // Measure max text width to size background dynamically
        int maxWidth = 0;
        for (String line : lines) {
            int w = textRenderer.getWidth(line);
            if (w > maxWidth) maxWidth = w;
        }
        int contentWidth  = maxWidth + PADDING * 2;
        int contentHeight = lines.size() * LINE_HEIGHT + PADDING * 2;

        // Background
        context.fill(x - PADDING, y - PADDING, x - PADDING + contentWidth, y - PADDING + contentHeight, BG_COLOR);

        // Lines
        int lineY = y;
        for (String line : lines) {
            context.drawTextWithShadow(textRenderer, Text.literal(line), x, lineY, 0xFF55FF55);
            lineY += LINE_HEIGHT;
        }
    }

    private String getStateDisplay(MacroState state) {
        return switch (state) {
            case IDLE -> "Idle";
            case PRECOMPUTING -> "Computing paths...";
            case PATHFINDING -> "Pathfinding...";
            case MOVING -> "Moving...";
            case MINING -> "Mining...";
            case LINE_FARMING -> "Line Farming...";
            case NEXT_STEP -> "Next step...";
            case PAUSED -> "⏸ Paused";
            case COMPLETED -> "✅ Completed";
            case ERROR -> "❌ Error";
        };
    }

    private int getStateColor(MacroState state) {
        return switch (state) {
            case IDLE -> 0xAAAAAA;
            case PRECOMPUTING -> 0xAA55FF;
            case PATHFINDING, MOVING -> 0x55FFFF;
            case MINING, LINE_FARMING -> 0xFFAA00;
            case NEXT_STEP -> 0x55FF55;
            case PAUSED -> 0xFFFF55;
            case COMPLETED -> 0x55FF55;
            case ERROR -> 0xFF5555;
        };
    }
}
