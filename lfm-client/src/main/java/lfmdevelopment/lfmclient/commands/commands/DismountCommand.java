/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lfmdevelopment.lfmclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;

public class DismountCommand extends Command {
    public DismountCommand() {
        super("dismount", "Dismounts you from entity you are riding.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            PlayerInput sneak = new PlayerInput(false, false, false, false, false, true, false);
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(sneak));
            return SINGLE_SUCCESS;
        });
    }
}
