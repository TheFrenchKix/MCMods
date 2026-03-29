/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.accounts.types;

import com.mojang.authlib.Environment;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import de.florianmichael.waybackauthlib.InvalidCredentialsException;
import de.florianmichael.waybackauthlib.WaybackAuthLib;
import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.systems.accounts.Account;
import lfmdevelopment.lfmclient.systems.accounts.AccountType;
import lfmdevelopment.lfmclient.systems.accounts.TokenAccount;
import lfmdevelopment.lfmclient.utils.misc.NbtException;
import net.minecraft.client.session.Session;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static lfmdevelopment.lfmclient.lfmClient.mc;

public class TheAlteningAccount extends Account<TheAlteningAccount> implements TokenAccount {
    private static final Environment ENVIRONMENT = new Environment("http://sessionserver.thealtening.com", "http://authserver.thealtening.com", "https://api.mojang.com", "The Altening");
    private static final YggdrasilAuthenticationService SERVICE = new YggdrasilAuthenticationService(mc.getNetworkProxy(), ENVIRONMENT);
    private String token;
    private @Nullable WaybackAuthLib auth;

    public TheAlteningAccount(String token) {
        super(AccountType.TheAltening, token);
        this.token = token;
    }

    @Override
    public boolean fetchInfo() {
        auth = getAuth();

        try {
            auth.logIn();

            cache.username = auth.getCurrentProfile().name();
            cache.uuid = auth.getCurrentProfile().id().toString();
            cache.loadHead();

            return true;
        } catch (InvalidCredentialsException e) {
            lfmClient.LOG.error("Invalid TheAltening credentials.");
            return false;
        } catch (Exception e) {
            lfmClient.LOG.error("Failed to fetch info for TheAltening account!");
            return false;
        }
    }

    @Override
    public boolean login() {
        if (auth == null) return false;
        applyLoginEnvironment(SERVICE);

        try {
            setSession(new Session(auth.getCurrentProfile().name(), auth.getCurrentProfile().id(), auth.getAccessToken(), Optional.empty(), Optional.empty()));
            return true;
        } catch (Exception e) {
            lfmClient.LOG.error("Failed to login with TheAltening.");
            return false;
        }
    }

    private WaybackAuthLib getAuth() {
        WaybackAuthLib auth = new WaybackAuthLib(ENVIRONMENT.servicesHost());

        auth.setUsername(name);
        auth.setPassword("lfm on Crack!");

        return auth;
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("type", type.name());
        tag.putString("name", name);
        tag.putString("token", token);
        tag.put("cache", cache.toTag());

        return tag;
    }

    @Override
    public TheAlteningAccount fromTag(NbtCompound tag) {
        if (tag.getString("name").isEmpty() || tag.getCompound("cache").isEmpty() || tag.getString("token").isEmpty()) throw new NbtException();

        name = tag.getString("name").get();
        token = tag.getString("token").get();
        cache.fromTag(tag.getCompound("cache").get());

        return this;
    }
}
