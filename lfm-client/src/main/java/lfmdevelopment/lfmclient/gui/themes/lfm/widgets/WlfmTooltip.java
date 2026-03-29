/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.themes.lfm.widgets;

import lfmdevelopment.lfmclient.gui.renderer.GuiRenderer;
import lfmdevelopment.lfmclient.gui.themes.lfm.lfmWidget;
import lfmdevelopment.lfmclient.gui.widgets.WTooltip;

public class WlfmTooltip extends WTooltip implements lfmWidget {
    public WlfmTooltip(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.quad(this, theme().backgroundColor.get());
    }
}
