package com.mwa.antiafk;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée principal du mod Anti-AFK (client-side uniquement).
 * Enregistre le keybind, les événements tick et rendu.
 */
public class AntiAfkMod implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("AntiAFK");
    private static final String KEYBIND_CATEGORY = "Anti-AFK";

    private final BlockHighlighter highlighter = new BlockHighlighter();
    private final AntiAfkModule module = new AntiAfkModule(highlighter);

    private KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[AntiAFK] Initialisation du mod...");

        // Enregistrement du keybind toggle (touche R par défaut)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.antiafk.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KEYBIND_CATEGORY
        ));

        // Événement tick client
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Vérifier le keybind toggle
            while (toggleKey.wasPressed()) {
                module.toggle();
                if (client.player != null) {
                    String status = module.isActive() ? "§aActivé" : "§cDésactivé";
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("[AntiAFK] " + status),
                            true // action bar
                    );
                }
            }

            // Tick du module
            module.tick();
        });

        // Événement rendu monde (outlines)
        WorldRenderEvents.LAST.register(highlighter::render);

        LOGGER.info("[AntiAFK] Mod initialisé avec succès !");
    }
}
