/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.themes.lfm.widgets;

import lfmdevelopment.lfmclient.gui.renderer.GuiRenderer;
import lfmdevelopment.lfmclient.gui.themes.lfm.lfmWidget;
import lfmdevelopment.lfmclient.gui.widgets.WWidget;
import lfmdevelopment.lfmclient.gui.widgets.containers.WWindow;

public class WlfmWindow extends WWindow implements lfmWidget {
    public WlfmWindow(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new WlfmHeader(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0) {
            renderer.quad(x, y + header.height, width, height - header.height, theme().backgroundColor.get());
        }
    }

    private class WlfmHeader extends WHeader {
        public WlfmHeader(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            renderer.quad(this, theme().accentColor.get());
        }
    }
}
