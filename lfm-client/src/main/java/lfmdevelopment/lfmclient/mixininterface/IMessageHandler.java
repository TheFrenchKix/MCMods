/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixininterface;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;

public interface IMessageHandler {
    /** Only valid inside of {@link net.minecraft.client.gui.hud.ChatHud#addMessage(Text, MessageSignatureData, MessageIndicator)} call */
    GameProfile lfm$getSender();
}
