package com.example.macromod;

import com.example.macromod.command.MacroCommands;
import com.example.macromod.config.ConfigManager;
import com.example.macromod.manager.AutoAttackManager;
import com.example.macromod.manager.AutoFarmerManager;
import com.example.macromod.manager.FreelookManager;
import com.example.macromod.manager.MacroExecutor;
import com.example.macromod.manager.MacroManager;
import com.example.macromod.pathfinding.PathHandler;
import com.example.macromod.pathfinding.SmoothAim;
import com.example.macromod.pathfinding.StevebotPathRuntime;
import com.example.macromod.recording.MacroRecorder;
import com.example.macromod.recording.RecordingState;
import com.example.macromod.ui.HudOverlay;
import com.example.macromod.ui.KeybindHudOverlay;
import com.example.macromod.ui.oringo.OringoClickGui;
import com.example.macromod.ui.PathDebugRenderer;
import com.example.macromod.ui.ESPRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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

    // ─── Keybinding category ───────────────────────────────────────
    private static final KeyBinding.Category MACRO_MOD_CATEGORY =
            KeyBinding.Category.create(Identifier.of("macromod", "general"));

    // ─── Keybindings ────────────────────────────────────────────
    private static KeyBinding openGuiKey;
    private static KeyBinding addWaypointKey;
    private static KeyBinding addTeleportKey;
    private static KeyBinding addBlockBreakKey;
    private static KeyBinding toggleRecordingKey;
    private static KeyBinding stopMacroKey;
    private static KeyBinding toggleKeybindHudKey;
    private static KeyBinding debugInfoKey;
    private static KeyBinding debugEntitiesKey;
    private static KeyBinding toggleAutoFishKey;
    private static KeyBinding toggleFreelookKey;
    private static KeyBinding toggleAutoFarmerKey;
    private static KeyBinding toggleAutoAttackKey;

    // ─── Initialization ───────────────────────────────────────────

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Macro Mod...");

        // Initialize singletons
        configManager       = new ConfigManager();
        configManager.load();

        // Load proxy config
        com.example.macromod.proxy.ProxyManager.getInstance().load();

        macroManager        = new MacroManager();
        macroManager.loadAll();

        smoothAim           = new SmoothAim();

        // Apply saved Smooth Aim tuning from config
        com.example.macromod.config.ModConfig smoothCfg = configManager.getConfig();
        smoothAim.setBaseLerp(smoothCfg.getSmoothAimBaseLerp());
        smoothAim.setSpeedAtZero(smoothCfg.getSmoothAimSpeedZero());
        smoothAim.setSpeedAtSlow(smoothCfg.getSmoothAimSpeedSlow());
        smoothAim.setSpeedAtFast(smoothCfg.getSmoothAimSpeedFast());
        smoothAim.setSlowZoneDeg(smoothCfg.getSmoothAimSlowZone());
        smoothAim.setFastZoneDeg(smoothCfg.getSmoothAimFastZone());

        macroExecutor       = new MacroExecutor(smoothAim);
        macroRecorder       = new MacroRecorder();
        hudOverlay          = new HudOverlay();
        keybindHudOverlay   = new KeybindHudOverlay();

        // Load freelook FOV from config
        FreelookManager.getInstance().setFreelookFov(configManager.getConfig().getFreelookFov());

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
        WorldRenderEvents.END_MAIN.register(ctx -> pathDebugRenderer.onWorldRender(ctx));

        // Register ESP renderer (target, entities, blocks)
        ESPRenderer espRenderer = new ESPRenderer();
        WorldRenderEvents.END_MAIN.register(ctx -> espRenderer.onWorldRender(ctx));

        LOGGER.info("========================================");
        LOGGER.info("Macro Mod initialized successfully.");
        LOGGER.info("========================================");
    }

    private void registerKeybindings() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                MACRO_MOD_CATEGORY
        ));

        addWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.add_waypoint",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                MACRO_MOD_CATEGORY
        ));

        addTeleportKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.add_teleport",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_T,
                MACRO_MOD_CATEGORY
        ));

        addBlockBreakKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.add_block_break",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                MACRO_MOD_CATEGORY
        ));

        toggleRecordingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.toggle_recording",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                MACRO_MOD_CATEGORY
        ));

        stopMacroKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.stop_macro",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PERIOD,
                MACRO_MOD_CATEGORY
        ));

        toggleKeybindHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.toggle_keybind_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                MACRO_MOD_CATEGORY
        ));

        debugInfoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.debug_info",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                MACRO_MOD_CATEGORY
        ));

        debugEntitiesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.debug_entities",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                MACRO_MOD_CATEGORY
        ));

        toggleAutoFishKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.toggle_auto_fish",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                MACRO_MOD_CATEGORY
        ));

        toggleFreelookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.toggle_freelook",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                MACRO_MOD_CATEGORY
        ));

        toggleAutoFarmerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.toggle_auto_farmer",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                MACRO_MOD_CATEGORY
        ));

        toggleAutoAttackKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macromod.toggle_auto_attack",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                MACRO_MOD_CATEGORY
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

            // Auto-farmer tick
            AutoFarmerManager.getInstance().tick();

            // Auto-attack tick
            AutoAttackManager.getInstance().tick();

            // Hotspot ESP tick (detect hotspot ArmorStands)
            com.example.macromod.manager.HotspotManager.getInstance().tick();

            // Death / teleport detection (Hypixel die check)
            com.example.macromod.manager.DeathDetector.getInstance().tick();

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
                client.setScreen(new OringoClickGui());
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

        // Toggle keybind HUD
        while (toggleKeybindHudKey.wasPressed()) {
            boolean current = configManager.getConfig().isKeybindHudVisible();
            configManager.getConfig().setKeybindHudVisible(!current);
            configManager.save();
        }

        // Debug info - show entity or block data
        while (debugInfoKey.wasPressed()) {
            debugTargetInfo(client);
        }

        // Debug entities around player (within 10m)
        while (debugEntitiesKey.wasPressed()) {
            debugEntitiesAroundPlayer(client);
        }

        // Toggle auto fishing
        while (toggleAutoFishKey.wasPressed()) {
            com.example.macromod.manager.AutoFishingManager afm = 
                    com.example.macromod.manager.AutoFishingManager.getInstance();
            boolean newState = !afm.isEnabled();
            afm.setEnabled(newState);
            
            if (client.player != null) {
                String status = newState ? "enabled" : "disabled";
                client.player.sendMessage(
                        Text.literal("Auto Fishing " + status).formatted(newState ? Formatting.GREEN : Formatting.RED),
                        false
                );
            }
        }

        // Toggle freelook (decouple camera rotation from player body rotation)
        while (toggleFreelookKey.wasPressed()) {
            FreelookManager.getInstance().toggle();
            if (client.player != null) {
                boolean flEnabled = FreelookManager.getInstance().isEnabled();
            }
        }

        // Toggle auto farmer (zig-zag crop harvesting)
        while (toggleAutoFarmerKey.wasPressed()) {
            AutoFarmerManager afmr = AutoFarmerManager.getInstance();
            afmr.toggle();
            if (client.player != null) {
                boolean afEnabled = afmr.isEnabled();
                client.player.sendMessage(
                        Text.literal("Auto Farmer " + (afEnabled ? "enabled" : "disabled"))
                                .formatted(afEnabled ? Formatting.GREEN : Formatting.RED),
                        false
                );
            }
        }

        // Toggle auto attack (single-target entity lock)
        while (toggleAutoAttackKey.wasPressed()) {
            AutoAttackManager aam = AutoAttackManager.getInstance();
            aam.toggle();
            if (client.player != null) {
                boolean aaEnabled = aam.isEnabled();
                client.player.sendMessage(
                        Text.literal("Auto Attack " + (aaEnabled ? "enabled" : "disabled"))
                                .formatted(aaEnabled ? Formatting.GREEN : Formatting.RED),
                        false
                );
            }
        }
    }

    /**
     * Displays information about the entity or block the player is currently targeting.
     * Shows data in chat and logs it.
     */
    private void debugTargetInfo(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        HitResult hitResult = client.crosshairTarget;
        if (hitResult == null) {
            sendDebugMessage(client.player, "No target");
            return;
        }

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            // Entity info
            EntityHitResult entityHit = (EntityHitResult) hitResult;
            net.minecraft.entity.Entity entity = entityHit.getEntity();
            String extra = "";
            if (entity instanceof net.minecraft.entity.decoration.ArmorStandEntity ase) {
                String customName = ase.getCustomName() == null ? "null" : ase.getCustomName().getString();
                ItemStack headStack = ase.getEquippedStack(EquipmentSlot.HEAD);
                String headItem = Registries.ITEM.getId(headStack.getItem()).toString();
                String skullOwner = extractSkullOwnerInfo(headStack);
                extra = String.format(
                    " | ArmorStand[inv=%s, marker=%s, small=%s, noGravity=%s, nameVisible=%s, customName=%s, arms=%s, basePlate=%s, headItem=%s, owner=%s, width=%.3f, height=%.3f]",
                    ase.isInvisible(),
                    ase.isMarker(),
                    ase.isSmall(),
                    ase.hasNoGravity(),
                    ase.isCustomNameVisible(),
                    customName,
                    ase.shouldShowArms(),
                    ase.shouldShowBasePlate(),
                    headItem,
                    skullOwner,
                    ase.getWidth(),
                    ase.getHeight()
                );
            }

            String info = String.format(
                "ENTITY: %s | Pos: (%.1f, %.1f, %.1f) | Health: %.1f | Vel: (%.2f, %.2f, %.2f) | Invisible: %s | UUID: %s%s",
                entity.getType().getName().getString(),
                entity.getX(), entity.getY(), entity.getZ(),
                entity instanceof LivingEntity le ? le.getHealth() : 0.0,
                entity.getVelocity().x, entity.getVelocity().y, entity.getVelocity().z,
                entity.isInvisible(),
                entity.getUuid(),
                extra
            );
            
            sendDebugMessage(client.player, info);
            LOGGER.info("[DEBUG] {}", info);
        } else if (hitResult.getType() == HitResult.Type.BLOCK) {
            // Block info
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            
            String info = String.format(
                "BLOCK: %s | Pos: (%d, %d, %d) | Face: %s | Properties: %s",
                blockId,
                pos.getX(), pos.getY(), pos.getZ(),
                blockHit.getSide(),
                state.getEntries()
            );
            
            sendDebugMessage(client.player, info);
            LOGGER.info("[DEBUG] {}", info);
        }
    }

    /**
     * Displays information about entities around the player, in 10m radius. Useful for debugging mob farms, entity detection, and ESP rendering.
     * Shows data in chat and logs it.
     */
    private void debugEntitiesAroundPlayer(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());

        StringBuilder sb = new StringBuilder();
        sb.append("Nearby entities within 10m:\n");
        int count = 0;
        for (net.minecraft.entity.Entity entity : world.getEntities()) {
            try {
                double distSq = entity.squaredDistanceTo(playerPos);
                if (distSq > 10 * 10) continue; // Only show entities within 10m

                String extra = "";
                if (entity instanceof net.minecraft.entity.decoration.ArmorStandEntity ase) {
                    String customName = ase.getCustomName() == null ? "null" : ase.getCustomName().getString();
                    ItemStack headStack = ase.getEquippedStack(EquipmentSlot.HEAD);
                    String headItem = Registries.ITEM.getId(headStack.getItem()).toString();
                    String skullOwner = extractSkullOwnerInfo(headStack);
                    extra = String.format(
                        " | ArmorStand[inv=%s, marker=%s, small=%s, noGravity=%s, nameVisible=%s, customName=%s, arms=%s, basePlate=%s, headItem=%s, owner=%s, width=%.3f, height=%.3f]",
                        ase.isInvisible(),
                        ase.isMarker(),
                        ase.isSmall(),
                        ase.hasNoGravity(),
                        ase.isCustomNameVisible(),
                        customName,
                        ase.shouldShowArms(),
                        ase.shouldShowBasePlate(),
                        headItem,
                        skullOwner,
                        ase.getWidth(),
                        ase.getHeight()
                    );
                }

                String line = String.format(
                    "ENTITY: %s | Pos: (%.1f, %.1f, %.1f) | Health: %.1f | Vel: (%.2f, %.2f, %.2f) | Invisible: %s | UUID: %s%s",
                    entity.getType().getName().getString(),
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity instanceof LivingEntity le ? le.getHealth() : 0.0,
                    entity.getVelocity().x, entity.getVelocity().y, entity.getVelocity().z,
                    entity.isInvisible(),
                    entity.getUuid(),
                    extra
                );
                sb.append(line).append("\n");
                LOGGER.info("[DEBUG] {}", line);
                count++;
            } catch (Exception e) {
                LOGGER.warn("[DEBUG] Failed to read entity: {}", e.getMessage());
            }
        }

        LOGGER.info("[DEBUG] Found {} entities within 10m", count);
        String info = sb.toString();
        sendDebugMessage(client.player, info);
    }

    /**
     * Extract skull owner name and texture info from a player head ItemStack.
     * Returns a compact string representation or "none" if not a textured head.
     */
    private static String extractSkullOwnerInfo(ItemStack headStack) {
        try {
            // Convert ItemStack to debug string - shows full NBT structure
            String stackStr = headStack.toString();
            
            // Look for SkullOwner in the string representation
            if (!stackStr.contains("SkullOwner")) {
                return "none";
            }
            
            // Return the full stack info for deep inspection
            // This includes NBT data in the toString() output
            if (stackStr.length() > 200) {
                return stackStr.substring(0, 200) + "...";
            }
            return stackStr;
        } catch (Exception e) {
            LOGGER.debug("Failed to extract skull owner info", e);
            return "error";
        }
    }

    /**
     * Sends a debug message to the player in chat (client-side only).
     */
    private void sendDebugMessage(ClientPlayerEntity player, String message) {
        if (player != null) {
            player.sendMessage(Text.literal("[DEBUG] " + message).formatted(Formatting.GRAY), false);
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
