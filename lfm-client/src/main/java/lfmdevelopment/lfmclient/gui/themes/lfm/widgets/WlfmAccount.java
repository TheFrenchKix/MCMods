/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.themes.lfm.widgets;

import lfmdevelopment.lfmclient.gui.WidgetScreen;
import lfmdevelopment.lfmclient.gui.themes.lfm.lfmWidget;
import lfmdevelopment.lfmclient.gui.widgets.WAccount;
import lfmdevelopment.lfmclient.systems.accounts.Account;
import lfmdevelopment.lfmclient.utils.render.color.Color;

public class WlfmAccount extends WAccount implements lfmWidget {
    public WlfmAccount(WidgetScreen screen, Account<?> account) {
        super(screen, account);
    }

    @Override
    protected Color loggedInColor() {
        return theme().loggedInColor.get();
    }

    @Override
    protected Color accountTypeColor() {
        return theme().textSecondaryColor.get();
    }
}
