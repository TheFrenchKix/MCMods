/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lfmdevelopment.lfmclient.commands.Command;
import lfmdevelopment.lfmclient.renderer.Fonts;
import lfmdevelopment.lfmclient.systems.Systems;
import lfmdevelopment.lfmclient.systems.friends.Friend;
import lfmdevelopment.lfmclient.systems.friends.Friends;
import lfmdevelopment.lfmclient.utils.network.Capes;
import lfmdevelopment.lfmclient.utils.network.lfmExecutor;
import net.minecraft.command.CommandSource;

public class ReloadCommand extends Command {
    public ReloadCommand() {
        super("reload", "Reloads many systems.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            warning("Reloading systems, this may take a while.");

            Systems.load();
            Capes.init();
            Fonts.refresh();
            lfmExecutor.execute(() -> Friends.get().forEach(Friend::updateInfo));

            return SINGLE_SUCCESS;
        });
    }
}
