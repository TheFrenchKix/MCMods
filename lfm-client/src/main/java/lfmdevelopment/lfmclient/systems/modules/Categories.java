/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.modules;

import lfmdevelopment.lfmclient.addons.AddonManager;
import lfmdevelopment.lfmclient.addons.lfmAddon;
import net.minecraft.item.Items;

public class Categories {
    public static final Category Player = new Category("Player", Items.ARMOR_STAND.getDefaultStack());
    public static final Category Movement = new Category("Movement", Items.DIAMOND_BOOTS.getDefaultStack());
    public static final Category Render = new Category("Render", Items.GLASS.getDefaultStack());
    public static final Category World = new Category("World", Items.GRASS_BLOCK.getDefaultStack());
    public static final Category Misc = new Category("Misc", Items.LAVA_BUCKET.getDefaultStack());
    public static final Category Skyblock = new Category("Skyblock", Items.NETHER_STAR.getDefaultStack());

    public static boolean REGISTERING;

    public static void init() {
        REGISTERING = true;

        // LFM
        Modules.registerCategory(Player);
        Modules.registerCategory(Movement);
        Modules.registerCategory(Render);
        Modules.registerCategory(World);
        Modules.registerCategory(Misc);
        Modules.registerCategory(Skyblock);

        // Addons
        AddonManager.ADDONS.forEach(lfmAddon::onRegisterCategories);

        REGISTERING = false;
    }
}
