package com.mwa.n0name;

import com.mwa.n0name.gui.n0nameScreen;
import com.mwa.n0name.modules.AntiAfkModule;
import com.mwa.n0name.modules.AutoFarmModule;
import com.mwa.n0name.modules.AutoWalkModule;
import com.mwa.n0name.modules.PatchCreatorModule;
import com.mwa.n0name.render.BlockESP;
import com.mwa.n0name.render.EntityESP;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class n0nameMod implements ClientModInitializer {

    private final BlockESP          blockESP          = new BlockESP();
    private final EntityESP         entityESP         = new EntityESP();
    private final AntiAfkModule     antiAfkModule     = new AntiAfkModule();
    private final AutoWalkModule    autoWalkModule    = new AutoWalkModule();
    private final PatchCreatorModule patchCreatorModule = new PatchCreatorModule();
    private final AutoFarmModule    autoFarmModule    = new AutoFarmModule();

    private KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        // Load saved routes
        ModConfig.getInstance().loadRoutesFromFile();

        // Pass module references to GUI
        n0nameScreen.setModuleReferences(autoWalkModule, patchCreatorModule);

        // Register keybinding
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
            autoWalkModule.tick();
            patchCreatorModule.tick();
            autoFarmModule.tick();
            entityESP.tick();
            blockESP.tick();
        });

        // Render all ESP and paths
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            blockESP.render(context);
            entityESP.render(context);
            antiAfkModule.render(context);
            autoWalkModule.render(context);
            patchCreatorModule.render(context);
        });

        DebugLogger.info("Ready - RSHIFT to open menu");
    }
}
