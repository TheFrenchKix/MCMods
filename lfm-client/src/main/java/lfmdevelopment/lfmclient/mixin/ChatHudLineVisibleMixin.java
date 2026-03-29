/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import com.mojang.authlib.GameProfile;
import lfmdevelopment.lfmclient.mixininterface.IChatHudLineVisible;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChatHudLine.Visible.class)
public abstract class ChatHudLineVisibleMixin implements IChatHudLineVisible {
    @Shadow @Final private OrderedText content;
    @Unique private int id;
    @Unique private GameProfile sender;
    @Unique private boolean startOfEntry;

    @Override
    public String lfm$getText() {
        StringBuilder sb = new StringBuilder();

        content.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });

        return sb.toString();
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

    @Override
    public boolean lfm$isStartOfEntry() {
        return startOfEntry;
    }

    @Override
    public void lfm$setStartOfEntry(boolean start) {
        startOfEntry = start;
    }
}
