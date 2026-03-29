/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lfmdevelopment.lfmclient.commands.Command;
import lfmdevelopment.lfmclient.commands.arguments.ModuleArgumentType;
import lfmdevelopment.lfmclient.commands.arguments.SettingArgumentType;
import lfmdevelopment.lfmclient.commands.arguments.SettingValueArgumentType;
import lfmdevelopment.lfmclient.gui.GuiThemes;
import lfmdevelopment.lfmclient.gui.WidgetScreen;
import lfmdevelopment.lfmclient.gui.tabs.TabScreen;
import lfmdevelopment.lfmclient.gui.tabs.Tabs;
import lfmdevelopment.lfmclient.gui.tabs.builtin.ConfigTab;
import lfmdevelopment.lfmclient.gui.tabs.builtin.HudTab;
import lfmdevelopment.lfmclient.settings.Setting;
import lfmdevelopment.lfmclient.systems.config.Config;
import lfmdevelopment.lfmclient.systems.modules.Module;
import lfmdevelopment.lfmclient.utils.Utils;
import lfmdevelopment.lfmclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

public class SettingCommand extends Command {
    public SettingCommand() {
        super("settings", "Allows you to view and change module settings.", "s");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // Open hud screen
        builder.then(
            literal("hud")
                .executes(context -> {
                    TabScreen screen = Tabs.get(HudTab.class).createScreen(GuiThemes.get());
                    screen.parent = null;

                    Utils.screenToOpen = screen;
                    return SINGLE_SUCCESS;
                })
        );

        // Open config screen
        builder.then(
            literal("config")
                .executes(context -> {
                    TabScreen screen = Tabs.get(ConfigTab.class).createScreen(GuiThemes.get());
                    screen.parent = null;

                    Utils.screenToOpen = screen;
                    return SINGLE_SUCCESS;
                })
        );

        // View or change config settings
        builder.then(
            literal("config").then(
                argument("setting", SettingArgumentType.create())
                    .executes(context -> {
                        // Get setting value
                        Setting<?> setting = SettingArgumentType.get(context, Config.get().settings);

                        ChatUtils.infoPrefix("Config", "Setting (highlight)%s(default) is (highlight)%s(default).", setting.title, setting.get());

                        return SINGLE_SUCCESS;
                    }).suggests((ctx, suggestionsBuilder) ->
                        SettingArgumentType.listSuggestions(suggestionsBuilder, Config.get().settings)
                    )
                    .then(
                        argument("value", SettingValueArgumentType.create())
                            .executes(context -> {
                                // Set setting value
                                Setting<?> setting = SettingArgumentType.get(context, Config.get().settings);
                                String value = SettingValueArgumentType.get(context);

                                if (setting.parse(value)) {
                                    ChatUtils.infoPrefix("Config", "Setting (highlight)%s(default) changed to (highlight)%s(default).", setting.title, value);
                                }

                                return SINGLE_SUCCESS;
                            }).suggests((context, suggestionsBuilder) ->
                                SettingValueArgumentType.listSuggestions(context, suggestionsBuilder, Config.get().settings)
                            )
                    )
            )
        );

        // Open module screen
        builder.then(
            argument("module", ModuleArgumentType.create())
                .executes(context -> {
                    Module module = context.getArgument("module", Module.class);

                    WidgetScreen screen = GuiThemes.get().moduleScreen(module);
                    screen.parent = null;

                    Utils.screenToOpen = screen;
                    return SINGLE_SUCCESS;
                })
        );

        // View or change module settings
        builder.then(
            argument("module", ModuleArgumentType.create())
                .then(
                    argument("setting", SettingArgumentType.create())
                    .executes(context -> {
                        // Get setting value
                        Setting<?> setting = SettingArgumentType.get(context);

                        ModuleArgumentType.get(context).info("Setting (highlight)%s(default) is (highlight)%s(default).", setting.title, setting.get());

                        return SINGLE_SUCCESS;
                    })
                    .then(
                        argument("value", SettingValueArgumentType.create())
                            .executes(context -> {
                                // Set setting value
                                Setting<?> setting = SettingArgumentType.get(context);
                                String value = SettingValueArgumentType.get(context);

                                if (setting.parse(value)) {
                                    ModuleArgumentType.get(context).info("Setting (highlight)%s(default) changed to (highlight)%s(default).", setting.title, value);
                                }

                                return SINGLE_SUCCESS;
                            })
                    )
                )
        );
    }
}
