package com.mwa.n0name.modules;

import com.mwa.n0name.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Skyblock-focused automation helpers (visitor alert, inventory auto-actions).
 */
public class SkyblockAutomationModule {

    private final CommandMacroModule commandMacroModule;
    private long lastInventoryActionMs = 0L;

    public SkyblockAutomationModule(CommandMacroModule commandMacroModule) {
        this.commandMacroModule = commandMacroModule;
    }

    public void tick(PatchCreatorModule patchCreatorModule, WaypointManagerModule waypointManagerModule) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        ModConfig cfg = ModConfig.getInstance();

        if (cfg.isGardenLaneLoopEnabled() && !cfg.getGardenLaneRouteName().isEmpty() && !patchCreatorModule.isExecuting()) {
            patchCreatorModule.executeRoute(cfg.getGardenLaneRouteName());
        }

        if (cfg.isWaypointChainEnabled() && !waypointManagerModule.isChainActive()) {
            waypointManagerModule.startChain();
        }

        maybeRunInventoryAction(client, cfg);
    }

    public void onChatMessage(String message) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.isVisitorAlarmEnabled()) return;
        if (message == null || message.isBlank()) return;

        String lower = message.toLowerCase(java.util.Locale.ROOT);
        String keyword = cfg.getVisitorAlarmKeyword();
        if (keyword == null || keyword.isBlank()) keyword = "visitor";

        if (!lower.contains(keyword)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        client.player.sendMessage(Text.literal("[n0name] Visitor alert detected: " + message), false);

        if (cfg.isAutoPauseOnVisitor()) {
            cfg.setAutoFarmEnabled(false);
            cfg.setGardenLaneLoopEnabled(false);
            cfg.setWaypointChainEnabled(false);
            commandMacroModule.executeMacro(3); // optional safe fallback command
        }
    }

    private void maybeRunInventoryAction(MinecraftClient client, ModConfig cfg) {
        if (!cfg.isInventoryAutoActionEnabled()) return;
        if (client.player == null) return;

        int used = 0;
        int total = Math.max(1, client.player.getInventory().size());
        for (int i = 0; i < total; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s != null && !s.isEmpty()) used++;
        }

        int percent = total == 0 ? 0 : (used * 100 / total);
        if (percent < cfg.getInventoryActionThresholdPct()) return;

        long now = System.currentTimeMillis();
        long cooldownMs = cfg.getInventoryActionCooldownSec() * 1000L;
        if (now - lastInventoryActionMs < cooldownMs) return;

        lastInventoryActionMs = now;
        commandMacroModule.executeMacro(2);
        client.player.sendMessage(Text.literal("[n0name] Inventory threshold reached: " + percent + "%"), true);
    }
}
