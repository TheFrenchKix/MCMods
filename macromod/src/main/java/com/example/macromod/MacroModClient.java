package com.example.macromod;

import com.example.macromod.command.MacroCommands;
import com.example.macromod.config.ConfigManager;
import com.example.macromod.manager.MacroExecutor;
import com.example.macromod.manager.MacroManager;
import com.example.macromod.recording.MacroRecorder;
import com.example.macromod.recording.RecordingState;
import com.example.macromod.ui.HudOverlay;
import com.example.macromod.ui.MacroScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side mod initializer. Registers keybindings, events, commands, and HUD.
 */
@Environment(EnvType.CLIENT)
public class MacroModClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");

    // ─── Singletons ─────────────────────────────────────────────
    private static MacroManager macroManager;
    private static MacroExecutor macroExecutor;
    private static MacroRecorder macroRecorder;
    private static ConfigManager configManager;
    private static HudOverlay hudOverlay;

    // ─── Keybindings ────────────────────────────────────────────
    private static KeyBinding openGuiKey;
    private static KeyBinding addWaypointKey;
    private static KeyBinding toggleRecordingKey;
    private static KeyBinding stopMacroKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Macro Mod...");

        // Initialize singletons
        configManager = new ConfigManager();
        configManager.load();

        macroManager = new MacroManager();
        macroManager.loadAll();

        macroExecutor = new MacroExecutor();
        macroRecorder = new MacroRecorder();
        hudOverlay = new HudOverlay();

        // Register keybindings
        registerKeybindings();

        // Register events
        registerEvents();

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register(MacroCommands::register);

        // Register HUD
        HudRenderCallback.EVENT.register(hudOverlay::render);

        LOGGER.info("Macro Mod initialized successfully.");
    }

    private void registerKeybindings() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.macromod.general"
        ));

        addWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.add_waypoint",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.macromod.general"
        ));

        toggleRecordingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.toggle_recording",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.macromod.general"
        ));

        stopMacroKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.stop_macro",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PERIOD,
                "category.macromod.general"
        ));
    }

    private void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Macro executor tick
            macroExecutor.tick();

            // Keybinding checks
            handleKeybindings(client);
        });
    }

    private void handleKeybindings(MinecraftClient client) {
        // Open GUI
        while (openGuiKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new MacroScreen());
            }
        }

        // Add waypoint (only while recording)
        while (addWaypointKey.wasPressed()) {
            if (macroRecorder.getState() == RecordingState.RECORDING) {
                macroRecorder.addWaypoint(null);
            }
        }

        // Toggle recording
        while (toggleRecordingKey.wasPressed()) {
            if (macroRecorder.getState() == RecordingState.IDLE) {
                // Start a quick recording with auto-generated name
                String name = "Recording " + System.currentTimeMillis() % 10000;
                macroRecorder.startRecording(name);
            } else if (macroRecorder.getState() == RecordingState.RECORDING) {
                macroRecorder.stopRecording();
            }
        }

        // Stop macro
        while (stopMacroKey.wasPressed()) {
            if (macroExecutor.isRunning()) {
                macroExecutor.stop();
            }
        }
    }

    // ─── Static accessors ───────────────────────────────────────

    /**
     * Returns the macro manager singleton.
     */
    public static MacroManager getManager() {
        return macroManager;
    }

    /**
     * Returns the macro executor singleton.
     */
    public static MacroExecutor getExecutor() {
        return macroExecutor;
    }

    /**
     * Returns the macro recorder singleton.
     */
    public static MacroRecorder getRecorder() {
        return macroRecorder;
    }

    /**
     * Returns the config manager singleton.
     */
    public static ConfigManager getConfigManager() {
        return configManager;
    }
}
