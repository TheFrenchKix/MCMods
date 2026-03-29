/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixininterface;

import com.mojang.authlib.GameProfile;

public interface IChatHudLine {
    String lfm$getText();

    int lfm$getId();

    void lfm$setId(int id);

    GameProfile lfm$getSender();

    void lfm$setSender(GameProfile profile);
}
