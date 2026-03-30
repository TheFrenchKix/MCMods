/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.events.packets;

import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;

public class ContainerSlotUpdateEvent {
    private static final ContainerSlotUpdateEvent INSTANCE = new ContainerSlotUpdateEvent();

    public ScreenHandlerSlotUpdateS2CPacket packet;

    public static ContainerSlotUpdateEvent get(ScreenHandlerSlotUpdateS2CPacket packet) {
        INSTANCE.packet = packet;
        return INSTANCE;
    }
}
