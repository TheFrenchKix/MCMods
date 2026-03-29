/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.screens.settings.base;

import lfmdevelopment.lfmclient.gui.GuiTheme;
import lfmdevelopment.lfmclient.gui.WindowScreen;
import lfmdevelopment.lfmclient.gui.renderer.GuiRenderer;
import lfmdevelopment.lfmclient.gui.widgets.WWidget;
import lfmdevelopment.lfmclient.gui.widgets.containers.WTable;
import lfmdevelopment.lfmclient.gui.widgets.input.WTextBox;
import lfmdevelopment.lfmclient.gui.widgets.pressable.WButton;
import lfmdevelopment.lfmclient.settings.Setting;
import lfmdevelopment.lfmclient.utils.misc.IChangeable;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;

public abstract class CollectionMapSettingScreen<K, V> extends WindowScreen {
    private final Setting<?> setting;
    protected final Map<K, V> map;
    private final Iterable<K> registry;

    private WTable table;
    private String filterText = "";

    public CollectionMapSettingScreen(GuiTheme theme, String title, Setting<?> setting, Map<K, V> map, Iterable<K> registry) {
        super(theme, title);

        this.setting = setting;
        this.map = map;
        this.registry = registry;
    }

    @Override
    public void initWidgets() {
        // Filter
        WTextBox filter = add(theme.textBox("")).minWidth(400).expandX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();

            table.clear();
            initTable();
        };

        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        Comparator<K> prioritizeChanged = Comparator.comparing(key -> !(map.get(key) instanceof IChangeable changeable && changeable.isChanged()));
        Iterable<K> sorted = SortingHelper.sortWithPriority(registry, this::includeValue, this::getValueNames, filterText, prioritizeChanged);

        sorted.forEach(t -> {
            @Nullable V data = map.get(t);
            boolean isChanged = data instanceof IChangeable changeable && changeable.isChanged();

            table.add(getValueWidget(t)).expandCellX();
            table.add(theme.label(isChanged ? "*" : " "));
            table.add(getDataWidget(t, data));

            WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
            reset.action = () -> removeValue(t);
            reset.tooltip = "Reset";

            table.row();
        });
    }

    protected void invalidateTable() {
        table.clear();
        initTable();
    }

    protected void removeValue(K value) {
        if (map.remove(value) != null) {
            setting.onChanged();
            invalidateTable();
        }
    }

    protected boolean includeValue(K value) {
        return true;
    }

    protected abstract WWidget getValueWidget(K value);

    protected abstract WWidget getDataWidget(K value, @Nullable V data);

    protected abstract String[] getValueNames(K value);
}
