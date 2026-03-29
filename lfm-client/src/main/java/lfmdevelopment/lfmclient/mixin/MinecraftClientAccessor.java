/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.resource.ResourceReloadLogger;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.ApiServices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("currentFps")
    static int lfm$getFps() {
        return 0;
    }

    @Mutable
    @Accessor("session")
    void lfm$setSession(Session session);

    @Accessor("resourceReloadLogger")
    ResourceReloadLogger lfm$getResourceReloadLogger();

    @Accessor("attackCooldown")
    int lfm$getAttackCooldown();

    @Accessor("attackCooldown")
    void lfm$setAttackCooldown(int attackCooldown);

    @Invoker("doAttack")
    boolean lfm$leftClick();

    @Mutable
    @Accessor("profileKeys")
    void lfm$setProfileKeys(ProfileKeys keys);

    @Mutable
    @Accessor("userApiService")
    void lfm$setUserApiService(UserApiService apiService);

    @Mutable
    @Accessor("skinProvider")
    void lfm$setSkinProvider(PlayerSkinProvider skinProvider);

    @Mutable
    @Accessor("socialInteractionsManager")
    void lfm$setSocialInteractionsManager(SocialInteractionsManager socialInteractionsManager);

    @Mutable
    @Accessor("abuseReportContext")
    void lfm$setAbuseReportContext(AbuseReportContext abuseReportContext);

    @Mutable
    @Accessor("gameProfileFuture")
    void lfm$setGameProfileFuture(CompletableFuture<ProfileResult> future);

    @Mutable
    @Accessor("apiServices")
    void lfm$setApiServices(ApiServices apiServices);

    @Invoker("handleInputEvents")
    void lfm$handleInputEvents();
}
