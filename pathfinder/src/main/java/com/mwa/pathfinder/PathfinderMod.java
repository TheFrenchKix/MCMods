package com.mwa.pathfinder;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mwa.pathfinder.config.PathfinderClientConfig;
import com.mwa.pathfinder.control.PathfinderController;
import com.mwa.pathfinder.pathing.IPathManager;
import com.mwa.pathfinder.pathing.PathManagers;
import com.mwa.pathfinder.render.PathfinderWorldOverlayRenderer;
import com.mwa.pathfinder.ui.PathfinderConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class PathfinderMod implements ClientModInitializer {
    private final IPathManager pathManager = PathManagers.get();
    private final PathfinderController controller = new PathfinderController(pathManager);
    private final PathfinderClientConfig config = PathfinderClientConfig.load();
    private final PathfinderWorldOverlayRenderer overlayRenderer = new PathfinderWorldOverlayRenderer(controller, pathManager, config);

    private KeyBinding pathLookKey;
    private KeyBinding pathEntityKey;
    private KeyBinding followEntityKey;
    private KeyBinding pathForwardKey;
    private KeyBinding stopKey;
    private KeyBinding cameraSyncKey;
    private KeyBinding ignoreYKey;
    private KeyBinding settingsKey;

    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        applyLoadedConfig();

        pathLookKey = registerKey("key.pathfinder.look_target", GLFW.GLFW_KEY_P);
        pathEntityKey = registerKey("key.pathfinder.look_entity", GLFW.GLFW_KEY_J);
        followEntityKey = registerKey("key.pathfinder.follow_entity", GLFW.GLFW_KEY_H);
        pathForwardKey = registerKey("key.pathfinder.forward", GLFW.GLFW_KEY_I);
        stopKey = registerKey("key.pathfinder.stop", GLFW.GLFW_KEY_O);
        cameraSyncKey = registerKey("key.pathfinder.camera_sync", GLFW.GLFW_KEY_K);
        ignoreYKey = registerKey("key.pathfinder.ignore_y", GLFW.GLFW_KEY_Y);
        settingsKey = registerKey("key.pathfinder.settings", GLFW.GLFW_KEY_U);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (pathLookKey.wasPressed()) {
                controller.pathToLookTarget();
            }
            while (pathEntityKey.wasPressed()) {
                controller.pathToLookEntity();
            }
            while (followEntityKey.wasPressed()) {
                controller.followLookEntity();
            }
            while (pathForwardKey.wasPressed()) {
                controller.pathForward();
            }
            while (stopKey.wasPressed()) {
                controller.stop();
            }
            while (cameraSyncKey.wasPressed()) {
                controller.toggleCameraSync();
                config.cameraSync = controller.isCameraSync();
                config.save();
            }
            while (ignoreYKey.wasPressed()) {
                controller.toggleIgnoreY();
                config.ignoreY = controller.isIgnoreY();
                config.save();
            }
            while (settingsKey.wasPressed()) {
                openSettingsScreen();
            }

            controller.tick();
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            controller.renderHud(context);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.textRenderer != null) {
                int x = 6;
                int y = 96;
                int color = 0xFFE6EEF8;
                context.drawTextWithShadow(client.textRenderer, "Overlay: " + (config.overlayEnabled ? "ON" : "OFF"), x, y, color);
                y += 10;
                context.drawTextWithShadow(client.textRenderer, "Settings Tab: " + tabName(config.selectedTab), x, y, color);
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(overlayRenderer::render);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            literal("pathfinder")
                .then(literal("goto")
                    .then(argument("x", IntegerArgumentType.integer())
                        .then(argument("y", IntegerArgumentType.integer())
                            .then(argument("z", IntegerArgumentType.integer())
                                .executes(context -> {
                                    int x = IntegerArgumentType.getInteger(context, "x");
                                    int y = IntegerArgumentType.getInteger(context, "y");
                                    int z = IntegerArgumentType.getInteger(context, "z");
                                    controller.pathTo(new BlockPos(x, y, z), false);
                                    return 1;
                                })
                                .then(argument("ignoreY", BoolArgumentType.bool())
                                    .executes(context -> {
                                        int x = IntegerArgumentType.getInteger(context, "x");
                                        int y = IntegerArgumentType.getInteger(context, "y");
                                        int z = IntegerArgumentType.getInteger(context, "z");
                                        boolean ignoreY = BoolArgumentType.getBool(context, "ignoreY");
                                        controller.pathTo(new BlockPos(x, y, z), ignoreY);
                                        return 1;
                                    }))))))
                .then(literal("look")
                    .executes(context -> controller.pathToLookTarget() ? 1 : 0))
                .then(literal("entity")
                    .executes(context -> controller.pathToLookEntity() ? 1 : 0))
                .then(literal("follow")
                    .executes(context -> controller.followLookEntity() ? 1 : 0))
                .then(literal("forward")
                    .executes(context -> {
                        controller.pathForward();
                        return 1;
                    }))
                .then(literal("stop")
                    .executes(context -> {
                        controller.stop();
                        return 1;
                    }))
                .then(literal("camera")
                    .executes(context -> {
                        boolean enabled = controller.toggleCameraSync();
                        context.getSource().sendFeedback(Text.literal("Camera sync: " + enabled));
                        return 1;
                    }))
                .then(literal("ignorey")
                    .executes(context -> {
                        boolean enabled = controller.toggleIgnoreY();
                        context.getSource().sendFeedback(Text.literal("Ignore Y: " + enabled));
                        return 1;
                    }))
                .then(literal("screen")
                    .executes(context -> {
                        openSettingsScreen();
                        return 1;
                    }))
        ));
    }

    private void openSettingsScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new PathfinderConfigScreen(client.currentScreen, controller, pathManager, config, this::syncAndSaveConfig));
    }

    private void applyLoadedConfig() {
        controller.setCameraSync(config.cameraSync);
        controller.setIgnoreY(config.ignoreY);

        pathManager.setAllowSprint(config.allowSprint);
        pathManager.setAllowParkour(config.allowParkour);
        pathManager.setFreeLook(config.freeLook);
        pathManager.setRenderPath(config.renderPath);
        pathManager.setRenderGoal(config.renderGoal);
        pathManager.setAllowBreak(config.allowBreak);
        pathManager.setAllowPlace(config.allowPlace);
        pathManager.setAntiCheatCompatibility(config.antiCheatCompatibility);
        pathManager.setPrimaryTimeoutMs(config.primaryTimeoutMs);
        pathManager.setRandomLooking(config.randomLooking);
    }

    private void syncAndSaveConfig() {
        config.cameraSync = controller.isCameraSync();
        config.ignoreY = controller.isIgnoreY();

        if (pathManager.isAvailable()) {
            config.allowSprint = pathManager.isAllowSprint();
            config.allowParkour = pathManager.isAllowParkour();
            config.freeLook = pathManager.isFreeLook();
            config.renderPath = pathManager.isRenderPath();
            config.renderGoal = pathManager.isRenderGoal();
            config.allowBreak = pathManager.isAllowBreak();
            config.allowPlace = pathManager.isAllowPlace();
            config.antiCheatCompatibility = pathManager.isAntiCheatCompatibility();
            config.primaryTimeoutMs = pathManager.getPrimaryTimeoutMs();
            config.randomLooking = pathManager.getRandomLooking();
        }
        config.save();
    }

    private KeyBinding registerKey(String translationKey, int glfwKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
            translationKey,
            InputUtil.Type.KEYSYM,
            glfwKey,
            "category.pathfinder"
        ));
    }

    private String tabName(int index) {
        return switch (index) {
            case 0 -> "Control";
            case 1 -> "Movement";
            case 2 -> "Safety";
            case 3 -> "Render";
            default -> "Unknown";
        };
    }
}
