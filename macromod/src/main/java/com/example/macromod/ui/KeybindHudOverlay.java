package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.manager.MacroExecutor;
import com.example.macromod.model.MacroState;
import com.example.macromod.recording.MacroRecorder;
import com.example.macromod.recording.RecordingState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a top-right HUD listing active keybinds during macro recording or playback.
 * Toggled on/off via the toggle keybind HUD key.
 */
@Environment(EnvType.CLIENT)
public class KeybindHudOverlay {

    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int BG_COLOR = 0x90000000;
    private static final int TITLE_COLOR = 0xFFFFAA00;
    private static final int KEY_COLOR = 0xFFFFFF55;
    private static final int LABEL_COLOR = 0xFFDDDDDD;
    private static final int DIVIDER_COLOR = 0xFF555555;

    /**
     * Called each frame by HudRenderCallback.
     */
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!MacroModClient.getConfigManager().getConfig().isKeybindHudVisible()) return;

        MacroExecutor executor = MacroModClient.getExecutor();
        MacroRecorder recorder = MacroModClient.getRecorder();

        boolean executorActive = executor.isRunning() || executor.getState() == MacroState.PAUSED;
        boolean recorderActive = recorder.getState() != RecordingState.IDLE;

        if (!executorActive && !recorderActive) return;

        TextRenderer tr = client.textRenderer;

        // Build entry list: pairs of [keyLabel, actionLabel]
        List<String[]> entries = new ArrayList<>();

        if (recorderActive) {
            entries.add(entry(MacroModClient.getAddWaypointKey(), "key.macromod.add_waypoint"));
            entries.add(entry(MacroModClient.getAddTeleportKey(), "key.macromod.add_teleport"));
            entries.add(entry(MacroModClient.getAddBlockBreakKey(), "key.macromod.add_block_break"));
            entries.add(entry(MacroModClient.getToggleRecordingKey(), "key.macromod.toggle_recording"));
        } else {
            entries.add(entry(MacroModClient.getStopMacroKey(), "key.macromod.stop_macro"));
        }
        entries.add(entry(MacroModClient.getOpenGuiKey(), "key.macromod.open_gui"));
        entries.add(entry(MacroModClient.getToggleKeybindHudKey(), "key.macromod.toggle_keybind_hud"));

        String title = Text.translatable("macromod.hud.keybinds_title").getString();

        // Measure maximum line width
        int maxWidth = tr.getWidth(title);
        for (String[] e : entries) {
            int w = tr.getWidth("[" + e[0] + "] " + e[1]);
            if (w > maxWidth) maxWidth = w;
        }

        int contentWidth = maxWidth + PADDING * 2;
        // title line + divider + entry lines, all with padding
        int contentHeight = LINE_HEIGHT + 3 + LINE_HEIGHT * entries.size() + PADDING * 2;

        int screenWidth = client.getWindow().getScaledWidth();
        int bgX = screenWidth - contentWidth - PADDING;
        int bgY = PADDING;

        // Background
        context.fill(bgX, bgY, bgX + contentWidth, bgY + contentHeight, BG_COLOR);

        int textX = bgX + PADDING;
        int textY = bgY + PADDING;

        // Title
        context.drawTextWithShadow(tr, Text.literal(title).formatted(Formatting.GOLD), textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT;

        // Divider line
        context.fill(bgX, textY, bgX + contentWidth, textY + 1, DIVIDER_COLOR);
        textY += 3;

        // Entries
        for (String[] entry : entries) {
            String bracket = "[" + entry[0] + "] ";
            int keyW = tr.getWidth(bracket);
            context.drawTextWithShadow(tr, Text.literal(bracket), textX, textY, KEY_COLOR);
            context.drawTextWithShadow(tr, Text.literal(entry[1]), textX + keyW, textY, LABEL_COLOR);
            textY += LINE_HEIGHT;
        }
    }

    /**
     * Builds a display entry: [bound key name, localized action label].
     */
    private static String[] entry(KeyBinding key, String translationKey) {
        String keyName = key != null ? key.getBoundKeyLocalizedText().getString() : "?";
        String label = Text.translatable(translationKey).getString();
        return new String[]{keyName, label};
    }
}
