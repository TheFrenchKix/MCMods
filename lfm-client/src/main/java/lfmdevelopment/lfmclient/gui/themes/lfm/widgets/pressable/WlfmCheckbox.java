/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.themes.lfm.widgets.pressable;

import lfmdevelopment.lfmclient.gui.renderer.GuiRenderer;
import lfmdevelopment.lfmclient.gui.themes.lfm.lfmGuiTheme;
import lfmdevelopment.lfmclient.gui.themes.lfm.lfmWidget;
import lfmdevelopment.lfmclient.gui.widgets.pressable.WCheckbox;
import net.minecraft.util.math.MathHelper;

public class WlfmCheckbox extends WCheckbox implements lfmWidget {
    private double animProgress;

    public WlfmCheckbox(boolean checked) {
        super(checked);
        animProgress = checked ? 1 : 0;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        lfmGuiTheme theme = theme();

        animProgress += (checked ? 1 : -1) * delta * 14;
        animProgress = MathHelper.clamp(animProgress, 0, 1);

        renderBackground(renderer, this, pressed, mouseOver);

        if (animProgress > 0) {
            double cs = (width - theme.scale(2)) / 1.75 * animProgress;
            renderer.quad(x + (width - cs) / 2, y + (height - cs) / 2, cs, cs, theme.checkboxColor.get());
        }
    }
}
