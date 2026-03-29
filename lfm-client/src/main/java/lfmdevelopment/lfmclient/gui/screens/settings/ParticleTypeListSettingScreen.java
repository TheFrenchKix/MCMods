/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.screens.settings;

import lfmdevelopment.lfmclient.gui.GuiTheme;
import lfmdevelopment.lfmclient.gui.screens.settings.base.CollectionListSettingScreen;
import lfmdevelopment.lfmclient.gui.widgets.WWidget;
import lfmdevelopment.lfmclient.settings.Setting;
import lfmdevelopment.lfmclient.utils.misc.Names;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;

import java.util.List;

public class ParticleTypeListSettingScreen extends CollectionListSettingScreen<ParticleType<?>> {
    public ParticleTypeListSettingScreen(GuiTheme theme, Setting<List<ParticleType<?>>> setting) {
        super(theme, "Select Particles", setting, setting.get(), Registries.PARTICLE_TYPE);
    }

    @Override
    protected boolean includeValue(ParticleType<?> value) {
        return value instanceof ParticleEffect;
    }

    @Override
    protected WWidget getValueWidget(ParticleType<?> value) {
        return theme.label(Names.get(value));
    }

    @Override
    protected String[] getValueNames(ParticleType<?> value) {
        return new String[]{
            Names.get(value),
            Registries.PARTICLE_TYPE.getId(value).toString()
        };
    }
}
