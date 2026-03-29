/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.widgets.pressable;

import lfmdevelopment.lfmclient.gui.renderer.packer.GuiTexture;

public abstract class WButton extends WPressable {
    protected String text;
    protected double textWidth;

    protected GuiTexture texture;

    public WButton(String text, GuiTexture texture) {
        this.text = text;
        this.texture = texture;

        if (text == null) instantTooltips = true;
    }

    @Override
    protected void onCalculateSize() {
        double pad = pad();

        String text = getText();

        if (text != null) {
            textWidth = theme.textWidth(text);

            width = pad + textWidth + pad;
            height = pad + theme.textHeight() + pad;
        }
        else {
            double s = theme.textHeight();

            width = pad + s + pad;
            height = pad + s + pad;
        }
    }

    public void set(String text) {
        if (this.text == null || Math.round(theme.textWidth(text)) != textWidth) invalidate();

        this.text = text;
    }

    public String getText() {
        return text;
    }
}
