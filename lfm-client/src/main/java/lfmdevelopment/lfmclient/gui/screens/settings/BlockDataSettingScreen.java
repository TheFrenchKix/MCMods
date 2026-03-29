/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.screens.settings;

import lfmdevelopment.lfmclient.gui.GuiTheme;
import lfmdevelopment.lfmclient.gui.renderer.GuiRenderer;
import lfmdevelopment.lfmclient.gui.screens.settings.base.CollectionMapSettingScreen;
import lfmdevelopment.lfmclient.gui.widgets.WWidget;
import lfmdevelopment.lfmclient.gui.widgets.pressable.WButton;
import lfmdevelopment.lfmclient.settings.BlockDataSetting;
import lfmdevelopment.lfmclient.settings.IBlockData;
import lfmdevelopment.lfmclient.utils.misc.IChangeable;
import lfmdevelopment.lfmclient.utils.misc.ICopyable;
import lfmdevelopment.lfmclient.utils.misc.ISerializable;
import lfmdevelopment.lfmclient.utils.misc.Names;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;

import static lfmdevelopment.lfmclient.lfmClient.mc;

public class BlockDataSettingScreen<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> extends CollectionMapSettingScreen<Block, T> {
    private final BlockDataSetting<T> setting;
    private boolean invalidate;

    public BlockDataSettingScreen(GuiTheme theme, BlockDataSetting<T> setting) {
        super(theme, "Configure Blocks", setting, setting.get(), Registries.BLOCK);

        this.setting = setting;
    }

    @Override
    protected boolean includeValue(Block value) {
        return value != Blocks.AIR;
    }

    @Override
    protected WWidget getValueWidget(Block block) {
        return theme.itemWithLabel(block.asItem().getDefaultStack(), Names.get(block));
    }

    @Override
    protected WWidget getDataWidget(Block block, @Nullable T blockData) {
        WButton edit = theme.button(GuiRenderer.EDIT);
        edit.action = () -> {
            T data = blockData;
            if (data == null) data = setting.defaultData.get().copy();

            mc.setScreen(data.createScreen(theme, block, setting));
            invalidate = true;
        };
        return edit;
    }

    @Override
    protected void onRenderBefore(DrawContext drawContext, float delta) {
        if (invalidate) {
            this.invalidateTable();
            invalidate = false;
        }
    }

    @Override
    protected String[] getValueNames(Block block) {
        return new String[]{
            Names.get(block),
            Registries.BLOCK.getId(block).toString()
        };
    }
}
