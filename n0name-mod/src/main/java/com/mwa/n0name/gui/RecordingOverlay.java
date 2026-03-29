package com.mwa.n0name.gui;

import com.mwa.n0name.macro.MacroManagerModule;
import com.mwa.n0name.macro.MacroRecorder;
import com.mwa.n0name.macro.Macro;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * HUD overlay displayed during macro recording.
 * Shows pulsing "Recording..." indicator, key guide, step counter, and STOP hint.
 */
public class RecordingOverlay {

    private static MacroManagerModule macroModule;

    public static void setMacroModule(MacroManagerModule module) {
        macroModule = module;
    }

    /**
     * Render the overlay. Called from HudRenderCallback.
     * Only draws when recording is active.
     */
    public static void render(DrawContext ctx, float tickDelta) {
        if (macroModule == null || !macroModule.isRecording()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int screenW = client.getWindow().getScaledWidth();
        MacroRecorder recorder = macroModule.getRecorder();
        Macro currentMacro = recorder.getCurrentMacro();

        // --- Recording indicator (top center) ---
        float pulse = (float) (Math.sin(System.currentTimeMillis() / 400.0) * 0.4 + 0.6);
        int dotAlpha = (int) (pulse * 255);
        int dotColor = (dotAlpha << 24) | 0xFF4444;

        String recText = "Recording...";
        int recW = tr.getWidth(recText) + 16; // 16 for dot + padding
        int recX = (screenW - recW) / 2;
        int recY = 6;

        // Background pill
        int pillW = recW + 12;
        int pillH = 16;
        int pillX = recX - 6;
        ctx.fill(pillX + 2, recY - 2, pillX + pillW - 2, recY + pillH, 0xCC1A1A1A);
        ctx.fill(pillX, recY, pillX + 2, recY + pillH - 2, 0xCC1A1A1A);
        ctx.fill(pillX + pillW - 2, recY, pillX + pillW, recY + pillH - 2, 0xCC1A1A1A);

        // Red pulsing dot
        ctx.fill(recX, recY + 3, recX + 6, recY + 9, dotColor);

        // "Recording..." text
        ctx.drawTextWithShadow(tr, recText, recX + 10, recY + 3, 0xFFFF6666);

        // Step count
        int stepCount = currentMacro != null ? currentMacro.stepCount() : 0;
        String stepText = "Steps: " + stepCount;
        ctx.drawTextWithShadow(tr, stepText, recX + recW + 8, recY + 3, 0xFFCCCCCC);

        // --- Key guide panel (below indicator) ---
        int guideW = 180;
        int guideH = 62;
        int guideX = (screenW - guideW) / 2;
        int guideY = recY + pillH + 6;

        // Guide background
        ctx.fill(guideX + 2, guideY, guideX + guideW - 2, guideY + guideH, 0xCC1A1A2A);
        ctx.fill(guideX, guideY + 2, guideX + 2, guideY + guideH - 2, 0xCC1A1A2A);
        ctx.fill(guideX + guideW - 2, guideY + 2, guideX + guideW, guideY + guideH - 2, 0xCC1A1A2A);

        // Top accent line
        ctx.fill(guideX + 4, guideY, guideX + guideW - 4, guideY + 1, 0xAAFF4444);

        int textX = guideX + 10;
        int textY = guideY + 6;

        // Key hints
        ctx.drawTextWithShadow(tr, "\u00BB SHIFT", textX, textY, 0xFF66FF44);
        ctx.drawTextWithShadow(tr, "\u2192 Walk point", textX + 52, textY, 0xFFAAAAAA);
        textY += 14;

        ctx.drawTextWithShadow(tr, "\u00BB L-Click", textX, textY, 0xFFFFAA44);
        ctx.drawTextWithShadow(tr, "\u2192 Mine block", textX + 52, textY, 0xFFAAAAAA);
        textY += 14;

        ctx.drawTextWithShadow(tr, "\u00BB R-Click", textX, textY, 0xFF44AAFF);
        ctx.drawTextWithShadow(tr, "\u2192 Teleport", textX + 52, textY, 0xFFAAAAAA);
        textY += 14;

        ctx.drawTextWithShadow(tr, "Press [ESC] to stop", guideX + (guideW - tr.getWidth("Press [ESC] to stop")) / 2,
                textY, 0xFF777788);
    }
}
