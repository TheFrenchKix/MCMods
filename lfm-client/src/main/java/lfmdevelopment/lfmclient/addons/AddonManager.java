/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.addons;

import lfmdevelopment.lfmclient.lfmClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.util.ArrayList;
import java.util.List;

public class AddonManager {
    public static final List<lfmAddon> ADDONS = new ArrayList<>();

    public static void init() {
        // lfm pseudo addon
        {
            lfmClient.ADDON = new lfmAddon() {
                @Override
                public void onInitialize() {}

                @Override
                public String getPackage() {
                    return "lfmdevelopment.lfmclient";
                }

                @Override
                public String getWebsite() {
                    return "https://lfmclient.com";
                }

                @Override
                public GithubRepo getRepo() {
                    return new GithubRepo("lfmDevelopment", "lfm-client");
                }

                @Override
                public String getCommit() {
                    String commit = lfmClient.MOD_META.getCustomValue(lfmClient.MOD_ID + ":commit").getAsString();
                    return commit.isEmpty() ? null : commit;
                }
            };

            ModMetadata metadata = FabricLoader.getInstance().getModContainer(lfmClient.MOD_ID).get().getMetadata();

            lfmClient.ADDON.name = metadata.getName();
            lfmClient.ADDON.authors = new String[metadata.getAuthors().size()];
            if (metadata.containsCustomValue(lfmClient.MOD_ID + ":color")) {
                lfmClient.ADDON.color.parse(metadata.getCustomValue(lfmClient.MOD_ID + ":color").getAsString());
            }

            int i = 0;
            for (Person author : metadata.getAuthors()) {
                lfmClient.ADDON.authors[i++] = author.getName();
            }

            ADDONS.add(lfmClient.ADDON);
        }

        // Addons
        for (EntrypointContainer<lfmAddon> entrypoint : FabricLoader.getInstance().getEntrypointContainers("lfm", lfmAddon.class)) {
            ModMetadata metadata = entrypoint.getProvider().getMetadata();
            lfmAddon addon;
            try {
                addon = entrypoint.getEntrypoint();
            } catch (Throwable throwable) {
                throw new RuntimeException("Exception during addon init \"%s\".".formatted(metadata.getName()), throwable);
            }

            addon.name = metadata.getName();

            if (metadata.getAuthors().isEmpty()) throw new RuntimeException("Addon \"%s\" requires at least 1 author to be defined in it's fabric.mod.json. See https://fabricmc.net/wiki/documentation:fabric_mod_json_spec".formatted(addon.name));
            addon.authors = new String[metadata.getAuthors().size()];

            if (metadata.containsCustomValue(lfmClient.MOD_ID + ":color")) {
                addon.color.parse(metadata.getCustomValue(lfmClient.MOD_ID + ":color").getAsString());
            }

            int i = 0;
            for (Person author : metadata.getAuthors()) {
                addon.authors[i++] = author.getName();
            }

            ADDONS.add(addon);
        }
    }
}
