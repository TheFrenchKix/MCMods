package com.example.macromod;

import com.example.macromod.command.MacroCommands;
import com.example.macromod.config.ConfigManager;
import com.example.macromod.manager.MacroExecutor;
import com.example.macromod.manager.MacroManager;
import com.example.macromod.pathfinding.PathHandler;
import com.example.macromod.pathfinding.SmoothAim;
import com.example.macromod.pathfinding.StevebotPathRuntime;
import com.example.macromod.recording.MacroRecorder;
import com.example.macromod.recording.RecordingState;
import com.example.macromod.ui.HudOverlay;
import com.example.macromod.ui.KeybindHudOverlay;
import com.example.macromod.ui.MacroScreen;
import com.example.macromod.ui.PathDebugRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side mod initializer. Registers keybindings, events, commands, and HUD.
 * Integrates Stevebot for pathfinding and bot control.
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
    private static KeybindHudOverlay keybindHudOverlay;
    private static PathHandler pathHandler;
    private static StevebotApi stevebotApi;
    private static SmoothAim smoothAim;

    // ─── Keybindings ────────────────────────────────────────────
    private static KeyBinding openGuiKey;
    private static KeyBinding addWaypointKey;
    private static KeyBinding addTeleportKey;
    private static KeyBinding addBlockBreakKey;
    private static KeyBinding toggleRecordingKey;
    private static KeyBinding stopMacroKey;
    private static KeyBinding freelookToggleKey;
    private static KeyBinding toggleKeybindHudKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Macro Mod...");

        // Initialize singletons
        configManager       = new ConfigManager();
        configManager.load();

        macroManager        = new MacroManager();
        macroManager.loadAll();

        smoothAim           = new SmoothAim();

        macroExecutor       = new MacroExecutor(smoothAim);
        macroRecorder       = new MacroRecorder();
        hudOverlay          = new HudOverlay();
        keybindHudOverlay   = new KeybindHudOverlay();

        // Register keybindings
        registerKeybindings();

        // Register events
        registerEvents();

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register(MacroCommands::register);

        // Register HUD
        HudRenderCallback.EVENT.register(hudOverlay::render);
        HudRenderCallback.EVENT.register(keybindHudOverlay::render);

        // Register 3D path debug renderer
        PathDebugRenderer pathDebugRenderer = new PathDebugRenderer();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(pathDebugRenderer::onWorldRender);

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

        addTeleportKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.add_teleport",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_T,
                "category.macromod.general"
        ));

        addBlockBreakKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.add_block_break",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
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

        freelookToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.toggle_freelook",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                "category.macromod.general"
        ));

        toggleKeybindHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.toggle_keybind_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.macromod.general"
        ));
    }

    private void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Initialize Stevebot path runtime lazily once client options/world/player are ready.
            ensureStevebotInitialized(client);

            // Smooth aim tick (once per client tick — no async)
            smoothAim.tick(client.player);

            // Macro executor tick
            macroExecutor.tick();

            // Auto-fishing tick
            com.example.macromod.manager.AutoFishingManager.getInstance().tick();

            // Keybinding checks
            handleKeybindings(client);
        });
    }

    private void ensureStevebotInitialized(MinecraftClient client) {
        if (pathHandler != null) {
            return;
        }
        if (client.options == null || client.world == null || client.player == null) {
            return;
        }

        try {
            StevebotPathRuntime.initialize();
            pathHandler = StevebotPathRuntime.getPathHandler();
            stevebotApi = new StevebotApi(pathHandler);
            LOGGER.info("Stevebot path runtime initialized");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Stevebot path runtime", e);
        }
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

        // Add teleport (only while recording)
        while (addTeleportKey.wasPressed()) {
            if (macroRecorder.getState() == RecordingState.RECORDING) {
                macroRecorder.addWaypoint(null);
            }
        }

        // Add block break (only while recording)
        while (addBlockBreakKey.wasPressed()) {
            if (macroRecorder.getState() == RecordingState.RECORDING) {
                macroRecorder.addBlockTarget();
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

        // Freelook toggle (while executing)
        while (freelookToggleKey.wasPressed()) {
            if (macroExecutor.isRunning()) {
                stevebotApi.toggleFreelook();
            }
        }

        // Toggle keybind HUD
        while (toggleKeybindHudKey.wasPressed()) {
            boolean current = configManager.getConfig().isKeybindHudVisible();
            configManager.getConfig().setKeybindHudVisible(!current);
            configManager.save();
        }
    }

    // ─── Static accessors ───────────────────────────────────────

    /**
     * Returns the smooth aim singleton.
     */
    public static SmoothAim getSmoothAim() {
        return smoothAim;
    }

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

    // ─── Keybinding accessors ───────────────────────────────────

    public static KeyBinding getOpenGuiKey() {
        return openGuiKey;
    }

    public static KeyBinding getAddWaypointKey() {
        return addWaypointKey;
    }

    public static KeyBinding getAddTeleportKey() {
        return addTeleportKey;
    }

    public static KeyBinding getAddBlockBreakKey() {
        return addBlockBreakKey;
    }

    public static KeyBinding getToggleRecordingKey() {
        return toggleRecordingKey;
    }

    public static KeyBinding getStopMacroKey() {
        return stopMacroKey;
    }

    public static KeyBinding getToggleKeybindHudKey() {
        return toggleKeybindHudKey;
    }

    /**
     * Returns the Stevebot PathHandler singleton.
     */
    public static PathHandler getPathHandler() {
        if (pathHandler == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null && client.world != null && client.player != null) {
                try {
                    StevebotPathRuntime.initialize();
                    pathHandler = StevebotPathRuntime.getPathHandler();
                    stevebotApi = new StevebotApi(pathHandler);
                } catch (Exception e) {
                    LOGGER.error("Failed to lazily initialize Stevebot path runtime", e);
                }
            }
        }
        return pathHandler;
    }

    /**
     * Returns the Stevebot API singleton.
     */
    public static StevebotApi getStevebotApi() {
        return stevebotApi;
    }
}
