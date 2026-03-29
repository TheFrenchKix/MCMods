/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.modules.render;

import lfmdevelopment.lfmclient.events.render.RenderBossBarEvent;
import lfmdevelopment.lfmclient.settings.BoolSetting;
import lfmdevelopment.lfmclient.settings.DoubleSetting;
import lfmdevelopment.lfmclient.settings.Setting;
import lfmdevelopment.lfmclient.settings.SettingGroup;
import lfmdevelopment.lfmclient.systems.modules.Categories;
import lfmdevelopment.lfmclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class BossStack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> stack = sgGeneral.add(new BoolSetting.Builder()
        .name("stack")
        .description("Stacks boss bars and adds a counter to the text.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> hideName = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-name")
        .description("Hides the names of boss bars.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> spacing = sgGeneral.add(new DoubleSetting.Builder()
        .name("bar-spacing")
        .description("The spacing reduction between each boss bar.")
        .defaultValue(10)
        .min(0)
        .build()
    );

    public static final Map<ClientBossBar, Integer> barMap = new WeakHashMap<>();

    public BossStack() {
        super(Categories.Render, "boss-stack", "Stacks boss bars to make your HUD less cluttered.");
    }

    @EventHandler
    private void onFetchText(RenderBossBarEvent.BossText event) {
        if (hideName.get()) {
            event.name = Text.empty();
            return;
        } else if (barMap.isEmpty() || !stack.get()) return;
        ClientBossBar bar = event.bossBar;
        Integer integer = barMap.get(bar);
        barMap.remove(bar);
        if (integer != null && !hideName.get()) event.name = event.name.copy().append(" x" + integer);
    }

    @EventHandler
    private void onSpaceBars(RenderBossBarEvent.BossSpacing event) {
        event.spacing = spacing.get().intValue();
    }

    @EventHandler
    private void onGetBars(RenderBossBarEvent.BossIterator event) {
        if (stack.get()) {
            HashMap<String, ClientBossBar> chosenBarMap = new HashMap<>();
            event.iterator.forEachRemaining(bar -> {
                String name = bar.getName().getString();
                if (chosenBarMap.containsKey(name)) {
                    barMap.compute(chosenBarMap.get(name), (clientBossBar, integer) -> (integer == null) ? 2 : integer + 1);
                } else {
                    chosenBarMap.put(name, bar);
                }
            });
            event.iterator = chosenBarMap.values().iterator();
        }
    }
}
