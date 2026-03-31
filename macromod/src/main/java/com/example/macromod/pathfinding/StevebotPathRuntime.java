package com.example.macromod.pathfinding;

import com.example.macromod.data.blocks.BlockLibrary;
import com.example.macromod.data.blocks.BlockProvider;
import com.example.macromod.data.blocks.BlockUtils;
import com.example.macromod.data.items.ItemLibrary;
import com.example.macromod.data.items.ItemUtils;
import com.example.macromod.minecraft.FabricMinecraftAdapter;
import com.example.macromod.minecraft.MinecraftAdapter;
import com.example.macromod.pathfinding.actions.ActionUtils;
import com.example.macromod.player.PlayerCamera;
import com.example.macromod.player.PlayerInput;
import com.example.macromod.player.PlayerInventory;
import com.example.macromod.player.PlayerMovement;
import com.example.macromod.player.PlayerUtils;

/**
 * Initializes and exposes the Stevebot-core pathfinding runtime components.
 */
public final class StevebotPathRuntime {

    private static boolean initialized;
    private static PathHandler pathHandler;

    private StevebotPathRuntime() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        MinecraftAdapter minecraftAdapter = new FabricMinecraftAdapter();

        // Wire global static singletons used by Stevebot-core pathfinding.
        ActionUtils.initMinecraftAdapter(minecraftAdapter);

        ItemLibrary itemLibrary = new ItemLibrary(minecraftAdapter);
        itemLibrary.onEventInitialize();
        ItemUtils.initialize(minecraftAdapter, itemLibrary);

        BlockLibrary blockLibrary = new BlockLibrary(minecraftAdapter);
        blockLibrary.onEventInitialize();

        BlockProvider blockProvider = new BlockProvider(minecraftAdapter, blockLibrary);
        BlockUtils.initialize(minecraftAdapter, blockProvider, blockLibrary);

        // Cross-link block <-> item wrappers.
        itemLibrary.insertBlocks(blockLibrary.getAllBlocks());
        blockLibrary.insertItems(itemLibrary.getAllItems());

        PlayerInput playerInput = new PlayerInput(minecraftAdapter);
        PlayerCamera playerCamera = new PlayerCamera(minecraftAdapter);
        PlayerMovement playerMovement = new PlayerMovement(playerInput, playerCamera);
        PlayerInventory playerInventory = new PlayerInventory(minecraftAdapter);
        PlayerUtils.initialize(minecraftAdapter, playerInput, playerCamera, playerMovement, playerInventory);

        pathHandler = new PathHandler(minecraftAdapter, null);
        initialized = true;
    }

    public static PathHandler getPathHandler() {
        if (!initialized) {
            initialize();
        }
        return pathHandler;
    }
}
