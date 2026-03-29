/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.themes.lfm.widgets.pressable;

import lfmdevelopment.lfmclient.gui.renderer.GuiRenderer;
import lfmdevelopment.lfmclient.gui.renderer.packer.GuiTexture;
import lfmdevelopment.lfmclient.gui.themes.lfm.lfmGuiTheme;
import lfmdevelopment.lfmclient.gui.themes.lfm.lfmWidget;
import lfmdevelopment.lfmclient.gui.widgets.pressable.WButton;

public class WlfmButton extends WButton implements lfmWidget {
    public WlfmButton(String text, GuiTexture texture) {
        super(text, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        lfmGuiTheme theme = theme();
        double pad = pad();

        renderBackground(renderer, this, pressed, mouseOver);

        if (text != null) {
            renderer.text(text, x + width / 2 - textWidth / 2, y + pad, theme.textColor.get(), false);
        }
        else {
            double ts = theme.textHeight();
            renderer.quad(x + width / 2 - ts / 2, y + pad, ts, ts, texture, theme.textColor.get());
        }
    }
}
