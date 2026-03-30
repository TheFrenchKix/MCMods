/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.events.packets;

import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;

public class InventoryEvent {
    private static final InventoryEvent INSTANCE = new InventoryEvent();

    public InventoryS2CPacket packet;

    public static InventoryEvent get(InventoryS2CPacket packet) {
        INSTANCE.packet = packet;
        return INSTANCE;
    }
}
