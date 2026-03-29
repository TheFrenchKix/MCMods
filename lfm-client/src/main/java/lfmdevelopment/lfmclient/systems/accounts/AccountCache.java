/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.accounts;

import com.mojang.util.UndashedUuid;
import lfmdevelopment.lfmclient.utils.misc.ISerializable;
import lfmdevelopment.lfmclient.utils.misc.NbtException;
import lfmdevelopment.lfmclient.utils.render.PlayerHeadTexture;
import lfmdevelopment.lfmclient.utils.render.PlayerHeadUtils;
import net.minecraft.nbt.NbtCompound;

import static lfmdevelopment.lfmclient.lfmClient.mc;

public class AccountCache implements ISerializable<AccountCache> {
    public String username = "";
    public String uuid = "";
    private PlayerHeadTexture headTexture;

    public PlayerHeadTexture getHeadTexture() {
        return headTexture != null ? headTexture : PlayerHeadUtils.STEVE_HEAD;
    }

    public void loadHead() {
        if (uuid == null || uuid.isBlank()) return;
        mc.execute(() -> headTexture = PlayerHeadUtils.fetchHead(UndashedUuid.fromStringLenient(uuid)));
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("username", username);
        tag.putString("uuid", uuid);

        return tag;
    }

    @Override
    public AccountCache fromTag(NbtCompound tag) {
        if (tag.getString("username").isEmpty() || tag.getString("uuid").isEmpty()) throw new NbtException();

        username = tag.getString("username").get();
        uuid = tag.getString("uuid").get();
        loadHead();

        return this;
    }
}
