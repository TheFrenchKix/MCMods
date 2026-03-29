/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import com.mojang.authlib.GameProfile;
import lfmdevelopment.lfmclient.mixininterface.IChatHudLine;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ChatHudLine.class)
public abstract class ChatHudLineMixin implements IChatHudLine {
    @Shadow @Final private Text content;
    @Unique private int id;
    @Unique private GameProfile sender;

    @Override
    public String lfm$getText() {
        return content.getString();
    }

    @Override
    public int lfm$getId() {
        return id;
    }

    @Override
    public void lfm$setId(int id) {
        this.id = id;
    }

    @Override
    public GameProfile lfm$getSender() {
        return sender;
    }

    @Override
    public void lfm$setSender(GameProfile profile) {
        sender = profile;
    }
}
