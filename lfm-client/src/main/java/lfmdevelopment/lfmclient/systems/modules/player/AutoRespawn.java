/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.modules.player;

import lfmdevelopment.lfmclient.events.game.OpenScreenEvent;
import lfmdevelopment.lfmclient.systems.modules.Categories;
import lfmdevelopment.lfmclient.systems.modules.Module;
import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.systems.modules.render.WaypointsModule;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawn extends Module {
    public AutoRespawn() {
        super(Categories.Player, "auto-respawn", "Automatically respawns after death.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onOpenScreenEvent(OpenScreenEvent event) {
        if (!(event.screen instanceof DeathScreen)) return;

        Modules.get().get(WaypointsModule.class).addDeath(mc.player.getEntityPos());
        mc.player.requestRespawn();
        event.cancel();
    }
}
