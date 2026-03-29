/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.screens.settings;

import lfmdevelopment.lfmclient.gui.GuiTheme;
import lfmdevelopment.lfmclient.gui.screens.settings.base.CollectionListSettingScreen;
import lfmdevelopment.lfmclient.gui.widgets.WWidget;
import lfmdevelopment.lfmclient.settings.Setting;
import lfmdevelopment.lfmclient.systems.modules.Module;
import lfmdevelopment.lfmclient.systems.modules.Modules;

import java.util.List;

public class ModuleListSettingScreen extends CollectionListSettingScreen<Module> {
    public ModuleListSettingScreen(GuiTheme theme, Setting<List<Module>> setting) {
        super(theme, "Select Modules", setting, setting.get(), Modules.get().getAll());
    }

    @Override
    protected WWidget getValueWidget(Module value) {
        return theme.label(value.title);
    }

    @Override
    protected String[] getValueNames(Module value) {
        String[] names = new String[value.aliases.length + 1];
        System.arraycopy(value.aliases, 0, names, 1, value.aliases.length);
        names[0] = value.title;
        return names;
    }
}
