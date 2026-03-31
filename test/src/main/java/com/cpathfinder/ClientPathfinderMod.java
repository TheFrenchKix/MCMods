package com.cpathfinder;

import com.cpathfinder.command.GotoCommand;
import com.cpathfinder.movement.MovementHandler;
import com.cpathfinder.render.PathRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Client entry-point.
 * Registers all components:  GotoCommand, PathRenderer, MovementHandler.
 */
public class ClientPathfinderMod implements ClientModInitializer {

    public static final String MOD_ID = "client-pathfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final MovementHandler movementHandler = new MovementHandler();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ClientPathfinder] Initializing...");

        // 1. /goto command
        GotoCommand.register();

        // 2. 3D line renderer
        PathRenderer.register();

        // 3. Movement + pending-path consumer (runs every client tick)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // Drain path computed by the async A* thread
            List<BlockPos> pending = PathState.pollPendingPath();
            if (pending != null) {
                if (!pending.isEmpty()) {
                    PathState.setPath(pending);
                    movementHandler.reset();
                    LOGGER.info("[ClientPathfinder] Path found - {} nodes.", pending.size());
                    if (client.player != null) {
                        client.player.displayClientMessage(
                                Component.literal("[Goto] Chemin trouve ! " + pending.size() + " noeuds.")
                                        .withStyle(ChatFormatting.GREEN), false);
                    }
                } else {
                    PathState.clear();
                    LOGGER.info("[ClientPathfinder] No path found.");
                    if (client.player != null) {
                        client.player.displayClientMessage(
                                Component.literal("[Goto] Aucun chemin trouve vers la destination.")
                                        .withStyle(ChatFormatting.RED), false);
                    }
                }
            }

            // Advance movement each tick
            movementHandler.tick(client);
        });

        LOGGER.info("[ClientPathfinder] Ready. Use /goto <x> <y> <z> to navigate.");
    }
}
