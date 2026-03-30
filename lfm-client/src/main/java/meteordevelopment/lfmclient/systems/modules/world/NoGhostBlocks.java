/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.modules.world;

import lfmdevelopment.lfmclient.events.entity.player.BreakBlockEvent;
import lfmdevelopment.lfmclient.events.entity.player.PlaceBlockEvent;
import lfmdevelopment.lfmclient.settings.BoolSetting;
import lfmdevelopment.lfmclient.settings.Setting;
import lfmdevelopment.lfmclient.settings.SettingGroup;
import lfmdevelopment.lfmclient.systems.modules.Categories;
import lfmdevelopment.lfmclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;

public class NoGhostBlocks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> breaking = sgGeneral.add(new BoolSetting.Builder()
        .name("breaking")
        .description("Whether to apply for block breaking actions.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> placing = sgGeneral.add(new BoolSetting.Builder()
        .name("placing")
        .description("Whether to apply for block placement actions.")
        .defaultValue(true)
        .build()
    );

    public NoGhostBlocks() {
        super(Categories.World, "no-ghost-blocks", "Attempts to prevent ghost blocks arising.");
    }

    @EventHandler
    private void onBreakBlock(BreakBlockEvent event) {
        if (mc.isInSingleplayer() || !breaking.get()) return;

        event.cancel();

        BlockState blockState = mc.world.getBlockState(event.blockPos);
        blockState.getBlock().onBreak(mc.world, event.blockPos, blockState, mc.player);
    }

    @EventHandler
    private void onPlaceBlock(PlaceBlockEvent event) {
        if (!placing.get()) return;

        event.cancel();
    }
}
