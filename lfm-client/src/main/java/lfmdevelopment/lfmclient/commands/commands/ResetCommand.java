/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lfmdevelopment.lfmclient.commands.Command;
import lfmdevelopment.lfmclient.commands.arguments.ModuleArgumentType;
import lfmdevelopment.lfmclient.gui.GuiThemes;
import lfmdevelopment.lfmclient.settings.Setting;
import lfmdevelopment.lfmclient.systems.hud.Hud;
import lfmdevelopment.lfmclient.systems.modules.Module;
import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

public class ResetCommand extends Command {

    public ResetCommand() {
        super("reset", "Resets specified settings.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("settings")
            .then(argument("module", ModuleArgumentType.create()).executes(context -> {
                Module module = context.getArgument("module", Module.class);
                module.settings.forEach(group -> group.forEach(Setting::reset));
                module.info("Reset all settings.");
                return SINGLE_SUCCESS;
            }))
            .then(literal("all").executes(context -> {
                Modules.get().getAll().forEach(module -> module.settings.forEach(group -> group.forEach(Setting::reset)));
                ChatUtils.infoPrefix("Modules", "Reset all module settings");
                return SINGLE_SUCCESS;
            }))
        ).then(literal("gui").executes(context -> {
            GuiThemes.get().clearWindowConfigs();
            GuiThemes.get().settings.reset();
            ChatUtils.info("Reset all GUI settings.");
            return SINGLE_SUCCESS;
        })).then(literal("bind")
            .then(argument("module", ModuleArgumentType.create()).executes(context -> {
                Module module = context.getArgument("module", Module.class);

                module.keybind.reset();
                module.info("Reset bind.");

                return SINGLE_SUCCESS;
            }))
            .then(literal("all").executes(context -> {
                Modules.get().getAll().forEach(module -> module.keybind.reset());
                ChatUtils.infoPrefix("Modules", "Reset all binds.");
                return SINGLE_SUCCESS;
            }))
        ).then(literal("hud").executes(context -> {
            Hud.get().resetToDefaultElements();
            ChatUtils.infoPrefix("HUD", "Reset all elements.");
            return SINGLE_SUCCESS;
        }));
    }
}
