package com.mwa.n0name.modules;

import com.mwa.n0name.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Executes user-defined chat command macros from hotkeys.
 */
public class CommandMacroModule {

    public void executeMacro(int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) {
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        String raw = switch (index) {
            case 1 -> cfg.getCommandMacro1();
            case 2 -> cfg.getCommandMacro2();
            case 3 -> cfg.getCommandMacro3();
            default -> "";
        };

        if (raw == null || raw.isBlank()) {
            player.sendMessage(Text.literal("[n0name] Macro " + index + " is empty"), true);
            return;
        }

        String cmd = raw.trim();
        if (cmd.startsWith("/")) {
            client.getNetworkHandler().sendChatCommand(cmd.substring(1));
        } else {
            client.getNetworkHandler().sendChatMessage(cmd);
        }

        player.sendMessage(Text.literal("[n0name] Macro " + index + ": " + cmd), true);
    }
}
