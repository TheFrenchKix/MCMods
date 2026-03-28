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

    @Override
    public void onInitializeClient() {
        // Load saved routes
        ModConfig.getInstance().loadRoutesFromFile();

        // Pass module references to GUI
        n0nameScreen.setModuleReferences(patchCreatorModule, waypointManagerModule, timeLoggerModule,
            routeHeatmapModule, blockESP, entityESP, pathfinderDebugModule);

        // Single keybind: open ClickGUI with Right Shift.
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.n0name.menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyBinding.Category.MISC
        ));

        // Tick all modules
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new n0nameScreen());
                }
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
            autoFarmModule.frameUpdate();
            autoMineModule.frameUpdate();
            autoSlayModule.frameUpdate();
            autoCropModule.frameUpdate();
            pathfinderDebugModule.frameUpdate();

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

        DebugLogger.info("Ready");
    }
}
