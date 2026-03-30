/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.events.entity.player;

public class BlockBreakingCooldownEvent {
    private static final BlockBreakingCooldownEvent INSTANCE = new BlockBreakingCooldownEvent();

    public int cooldown;

    public static BlockBreakingCooldownEvent get(int cooldown) {
        INSTANCE.cooldown = cooldown;
        return INSTANCE;
    }
}
