package com.example.macromod.ui;

import com.example.macromod.model.MacroStep;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Widget representing a single macro step entry in a scrollable list.
 */
@Environment(EnvType.CLIENT)
public class MacroStepWidget extends AlwaysSelectedEntryListWidget.Entry<MacroStepWidget> {

    private final MacroStep step;
    private final int index;

    public MacroStepWidget(MacroStep step, int index) {
        this.step = step;
        this.index = index;
    }

    @Override
    public void render(DrawContext context, int entryIndex, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean hovered, float tickDelta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        // Step label and index
        String label = String.format("Step %d: %s", index + 1, step.getLabel());
        context.drawTextWithShadow(textRenderer,
                Text.literal(label).formatted(Formatting.WHITE),
                x + 4, y + 2, 0xFFFFFF);

        // Coordinates
        String coords = String.format("(%d, %d, %d)",
                step.getDestination().getX(),
                step.getDestination().getY(),
                step.getDestination().getZ());
        context.drawTextWithShadow(textRenderer,
                Text.literal(coords).formatted(Formatting.GRAY),
                x + 4, y + 13, 0xAAAAAA);

        // Block target count
        String blocks = step.getTargets().size() + " block(s)";
        int blocksWidth = textRenderer.getWidth(blocks);
        context.drawTextWithShadow(textRenderer,
                Text.literal(blocks).formatted(Formatting.AQUA),
                x + entryWidth - blocksWidth - 8, y + 2, 0x55FFFF);
    }

    @Override
    public Text getNarration() {
        return Text.literal(String.format("Step %d: %s, %d blocks",
                index + 1, step.getLabel(), step.getTargets().size()));
    }

    /**
     * Returns the MacroStep associated with this widget entry.
     */
    public MacroStep getStep() {
        return step;
    }

    /**
     * Returns the step index.
     */
    public int getIndex() {
        return index;
    }
}
