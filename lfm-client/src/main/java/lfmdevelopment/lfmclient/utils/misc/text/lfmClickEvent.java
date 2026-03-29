/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.utils.misc.text;

import lfmdevelopment.lfmclient.mixin.ScreenMixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This class does nothing except ensure that {@link ClickEvent}'s containing lfm Client commands can only be executed if they come from the client.
 * @see ScreenMixin#onHandleBasicClickEvent(ClickEvent, MinecraftClient, Screen, CallbackInfo)
 */
public class lfmClickEvent implements ClickEvent {
    public final String value;

    public lfmClickEvent(String value) {
        this.value = value;
    }

    @Override
    public Action getAction() {
        return Action.RUN_COMMAND;
    }
}
