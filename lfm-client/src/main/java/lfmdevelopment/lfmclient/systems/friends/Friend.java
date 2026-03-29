/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.friends;

import com.mojang.util.UndashedUuid;
import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.utils.misc.ISerializable;
import lfmdevelopment.lfmclient.utils.network.FailedHttpResponse;
import lfmdevelopment.lfmclient.utils.network.Http;
import lfmdevelopment.lfmclient.utils.render.PlayerHeadTexture;
import lfmdevelopment.lfmclient.utils.render.PlayerHeadUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.UUID;

import static lfmdevelopment.lfmclient.lfmClient.mc;

public class Friend implements ISerializable<Friend>, Comparable<Friend> {
    public volatile String name;
    private volatile @Nullable UUID id;
    private volatile @Nullable PlayerHeadTexture headTexture;
    private volatile boolean updating;

    public Friend(String name, @Nullable UUID id) {
        this.name = name;
        this.id = id;
        this.headTexture = null;
    }

    public Friend(PlayerEntity player) {
        this(player.getName().getString(), player.getUuid());
    }
    public Friend(String name) {
        this(name, null);
    }

    public String getName() {
        return name;
    }

    public PlayerHeadTexture getHead() {
        return headTexture != null ? headTexture : PlayerHeadUtils.STEVE_HEAD;
    }

    public void updateInfo() {
        updating = true;
        HttpResponse<APIResponse> res = null;

        if (id != null) {
            res = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + UndashedUuid.toString(id))
                .exceptionHandler(e -> lfmClient.LOG.error("Error while trying to connect session server for friend '{}'", name))
                .sendJsonResponse(APIResponse.class);
        }

        // Fallback to name-based lookup
        if (res == null || res.statusCode() != 200) {
            res = Http.get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .exceptionHandler(e -> lfmClient.LOG.error("Error while trying to update info for friend '{}'", name))
                .sendJsonResponse(APIResponse.class);
        }

        if (res != null && res.statusCode() == 200) {
            name = res.body().name;
            id = UndashedUuid.fromStringLenient(res.body().id);
            mc.execute(() -> headTexture = PlayerHeadUtils.fetchHead(id));
        }

        // cracked accounts shouldn't be assigned ids
        else if (!(res instanceof FailedHttpResponse)) {
            id = null;
        }

        updating = false;
    }

    public boolean headTextureNeedsUpdate() {
        return !this.updating && headTexture == null;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", name);
        if (id != null) tag.putString("id", UndashedUuid.toString(id));

        return tag;
    }

    @Override
    public Friend fromTag(NbtCompound tag) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Friend friend = (Friend) o;
        return Objects.equals(name, friend.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public int compareTo(@NotNull Friend friend) {
        return name.compareToIgnoreCase(friend.name);
    }

    private static class APIResponse {
        String name, id;
    }
}
