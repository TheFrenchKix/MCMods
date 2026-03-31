package com.cpathfinder.command;

import com.cpathfinder.PathState;
import com.cpathfinder.pathfinding.ClientPathfinder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Registers the client-side command:  /goto <x> <y> <z>
 *
 * On execution:
 *  1. Cancels any ongoing navigation (PathState.clear).
 *  2. Captures the player's current block position as start.
 *  3. Launches the async A* search via ClientPathfinder.findPathAsync.
 *  4. Pipes the result to PathState.postPath — the main tick thread will
 *     consume it and start movement automatically.
 */
public final class GotoCommand {

    private GotoCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("goto")
                                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                    int z = IntegerArgumentType.getInteger(ctx, "z");

                                    FabricClientCommandSource source = ctx.getSource();

                                    if (source.getWorld() == null || source.getPlayer() == null) {
                                        source.sendFeedback(
                                                Component.literal("[Goto] Monde non disponible.")
                                                        .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }

                                    BlockPos startPos = source.getPlayer().blockPosition();
                                    BlockPos goalPos  = new BlockPos(x, y, z);

                                    // Stop any current navigation
                                    PathState.clear();

                                    source.sendFeedback(
                                            Component.literal(String.format(
                                                    "[Goto] Calcul du chemin de (%d,%d,%d) -> (%d,%d,%d)...",
                                                    startPos.getX(), startPos.getY(), startPos.getZ(),
                                                    x, y, z))
                                                    .withStyle(ChatFormatting.YELLOW));

                                    // Launch async A* — result is posted to PathState from the worker thread
                                    ClientPathfinder
                                            .findPathAsync(source.getWorld(), startPos, goalPos)
                                            .thenAccept(PathState::postPath);

                                    return 1;
                                }))))
        ));
    }
}
