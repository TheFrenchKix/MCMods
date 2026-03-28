package com.mwa.n0name;

import com.mwa.n0name.gui.n0nameScreen;
import com.mwa.n0name.modules.AntiAfkModule;
import com.mwa.n0name.modules.AutoCropModule;
import com.mwa.n0name.modules.AutoFarmModule;
import com.mwa.n0name.modules.AutoFishModule;
import com.mwa.n0name.modules.AutoMineModule;
import com.mwa.n0name.modules.AutoSlayModule;
import com.mwa.n0name.modules.BlockNukerModule;
import com.mwa.n0name.modules.CommandMacroModule;
import com.mwa.n0name.modules.DebugToolsModule;
import com.mwa.n0name.modules.FairySoulFinderModule;
import com.mwa.n0name.modules.HypixelStatsHudModule;
import com.mwa.n0name.modules.InventoryHudModule;
import com.mwa.n0name.modules.PatchCreatorModule;
import com.mwa.n0name.modules.PathfinderDebugModule;
import com.mwa.n0name.modules.PlayerEspModule;
import com.mwa.n0name.modules.RouteHeatmapModule;
import com.mwa.n0name.modules.SkyblockAutomationModule;
import com.mwa.n0name.modules.TimeLoggerModule;
import com.mwa.n0name.modules.WaypointManagerModule;
import com.mwa.n0name.render.BlockESP;
import com.mwa.n0name.render.EntityESP;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class n0nameMod implements ClientModInitializer {

    private final BlockESP          blockESP          = new BlockESP();
    private final EntityESP         entityESP         = new EntityESP();
    private final AntiAfkModule     antiAfkModule     = new AntiAfkModule();
    private final PatchCreatorModule patchCreatorModule = new PatchCreatorModule();
    private final AutoFarmModule    autoFarmModule    = new AutoFarmModule();
    private final AutoMineModule    autoMineModule    = new AutoMineModule();
    private final AutoSlayModule    autoSlayModule    = new AutoSlayModule();
    private final AutoCropModule    autoCropModule    = new AutoCropModule();
    private final BlockNukerModule  blockNukerModule  = new BlockNukerModule();
    private final CommandMacroModule commandMacroModule = new CommandMacroModule();
    private final WaypointManagerModule waypointManagerModule = new WaypointManagerModule();
    private final TimeLoggerModule timeLoggerModule = new TimeLoggerModule();
    private final SkyblockAutomationModule skyblockAutomationModule = new SkyblockAutomationModule(commandMacroModule);
    private final AutoFishModule autoFishModule = new AutoFishModule();
    private final FairySoulFinderModule fairySoulFinderModule = new FairySoulFinderModule();
    private final PlayerEspModule playerEspModule = new PlayerEspModule();
    private final DebugToolsModule debugToolsModule = new DebugToolsModule();
    private final PathfinderDebugModule pathfinderDebugModule = new PathfinderDebugModule();
    private final RouteHeatmapModule routeHeatmapModule = new RouteHeatmapModule();
    private final InventoryHudModule inventoryHudModule = new InventoryHudModule();
    private final HypixelStatsHudModule hypixelStatsHudModule = new HypixelStatsHudModule(timeLoggerModule);

    private KeyBinding menuKey;
    private KeyBinding macro1Key;
    private KeyBinding macro2Key;
    private KeyBinding macro3Key;
    private KeyBinding saveWaypointKey;
    private KeyBinding recallWaypointKey;
    private KeyBinding cropModeKey;
    private KeyBinding cropTypeKey;
    private KeyBinding cropAutoToolKey;
    private KeyBinding cropSilentAimKey;
    private KeyBinding laneLoopKey;
    private KeyBinding waypointChainKey;
    private KeyBinding jacobPresetKey;

    @Override
    public void onInitializeClient() {
        // Load saved routes
        ModConfig.getInstance().loadRoutesFromFile();

        // Pass module references to GUI
        n0nameScreen.setModuleReferences(patchCreatorModule, waypointManagerModule, timeLoggerModule,
            routeHeatmapModule, blockESP, entityESP, pathfinderDebugModule);

        // Register keybinding
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyBinding.Category.MISC
        ));

        macro1Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.macro1", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, KeyBinding.Category.MISC));
        macro2Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.macro2", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, KeyBinding.Category.MISC));
        macro3Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.macro3", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, KeyBinding.Category.MISC));

        saveWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.waypoint.save", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, KeyBinding.Category.MISC));
        recallWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.waypoint.recall", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F10, KeyBinding.Category.MISC));

        cropModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.crop.mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F3, KeyBinding.Category.MISC));
        cropTypeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.crop.type", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F4, KeyBinding.Category.MISC));
        cropAutoToolKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.crop.autotool", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F2, KeyBinding.Category.MISC));
        cropSilentAimKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.crop.silentaim", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F1, KeyBinding.Category.MISC));
        laneLoopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.lane.loop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F11, KeyBinding.Category.MISC));
        waypointChainKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.waypoint.chain", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F12, KeyBinding.Category.MISC));
        jacobPresetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.jacob.preset", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F5, KeyBinding.Category.MISC));

        // Tick all modules
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new n0nameScreen());
                }
            }

            while (macro1Key.wasPressed()) commandMacroModule.executeMacro(1);
            while (macro2Key.wasPressed()) commandMacroModule.executeMacro(2);
            while (macro3Key.wasPressed()) commandMacroModule.executeMacro(3);

            while (saveWaypointKey.wasPressed()) {
                waypointManagerModule.saveCurrentToSlot(resolveWaypointSlot(client));
            }
            while (recallWaypointKey.wasPressed()) {
                waypointManagerModule.recallSlot(resolveWaypointSlot(client));
            }

            while (cropModeKey.wasPressed()) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.setAutoFarmMode(cfg.getAutoFarmMode() == ModConfig.AutoFarmMode.MOBS
                    ? ModConfig.AutoFarmMode.CROPS
                    : ModConfig.AutoFarmMode.MOBS);
                sendStatus(client, "AutoFarm mode: " + cfg.getAutoFarmMode().name());
            }
            while (cropTypeKey.wasPressed()) {
                ModConfig cfg = ModConfig.getInstance();
                ModConfig.CropType[] values = ModConfig.CropType.values();
                int next = (cfg.getCropType().ordinal() + 1) % values.length;
                cfg.setCropType(values[next]);
                sendStatus(client, "Crop type: " + cfg.getCropType().name());
            }
            while (cropAutoToolKey.wasPressed()) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.setAutoFarmAutoTool(!cfg.isAutoFarmAutoTool());
                sendStatus(client, "Crop Auto Tool: " + (cfg.isAutoFarmAutoTool() ? "ON" : "OFF"));
            }
            while (cropSilentAimKey.wasPressed()) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.setAutoFarmSilentCropAim(!cfg.isAutoFarmSilentCropAim());
                sendStatus(client, "Crop Silent Aim: " + (cfg.isAutoFarmSilentCropAim() ? "ON" : "OFF"));
            }
            while (laneLoopKey.wasPressed()) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.setGardenLaneLoopEnabled(!cfg.isGardenLaneLoopEnabled());
                sendStatus(client, "Lane Loop: " + (cfg.isGardenLaneLoopEnabled() ? "ON" : "OFF"));
            }
            while (waypointChainKey.wasPressed()) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.setWaypointChainEnabled(!cfg.isWaypointChainEnabled());
                if (!cfg.isWaypointChainEnabled()) {
                    waypointManagerModule.stopChain();
                }
                sendStatus(client, "Waypoint Chain: " + (cfg.isWaypointChainEnabled() ? "ON" : "OFF"));
            }
            while (jacobPresetKey.wasPressed()) {
                ModConfig cfg = ModConfig.getInstance();
                ModConfig.JacobPreset[] presets = ModConfig.JacobPreset.values();
                int next = (cfg.getJacobPreset().ordinal() + 1) % presets.length;
                cfg.applyJacobPreset(presets[next]);
                sendStatus(client, "Jacob preset: " + cfg.getJacobPreset().name());
            }

            antiAfkModule.tick();
            patchCreatorModule.tick();
            autoFarmModule.tick();
            autoMineModule.tick();
            autoSlayModule.tick();
            autoCropModule.tick();
            blockNukerModule.tick();
            autoFishModule.tick();
            fairySoulFinderModule.tick();
            playerEspModule.tick();
            waypointManagerModule.tick();
            timeLoggerModule.tick();
            skyblockAutomationModule.tick(patchCreatorModule, waypointManagerModule);
            debugToolsModule.tick();
            pathfinderDebugModule.tick();
            routeHeatmapModule.tick();
            hypixelStatsHudModule.tick();
            entityESP.tick();
            blockESP.tick();
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            skyblockAutomationModule.onChatMessage(message.getString());
        });

        // Render all ESP and paths
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            blockESP.render(context);
            entityESP.render(context);
            antiAfkModule.render(context);
            patchCreatorModule.render(context);
            autoFarmModule.render(context);
            autoMineModule.render(context);
            autoSlayModule.render(context);
            blockNukerModule.render(context);
            fairySoulFinderModule.render(context);
            playerEspModule.render(context);
            pathfinderDebugModule.render(context);
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            inventoryHudModule.renderHud(drawContext);
            routeHeatmapModule.renderHud(drawContext);
            hypixelStatsHudModule.renderHud(drawContext);
            pathfinderDebugModule.renderHud(drawContext);
        });

        DebugLogger.info("Ready - RSHIFT to open menu");
    }

    private int resolveWaypointSlot(net.minecraft.client.MinecraftClient client) {
        var window = client.getWindow();
        boolean shift = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
            || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean alt = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_ALT)
            || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_ALT);
        if (alt) return 3;
        if (shift) return 2;
        return 1;
    }

    private void sendStatus(net.minecraft.client.MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[n0name] " + message), true);
        }
    }
}
