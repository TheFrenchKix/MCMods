package com.example.macromod.command;

import com.example.macromod.MacroModClient;
import com.example.macromod.manager.MacroExecutor;
import com.example.macromod.manager.MacroManager;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroState;
import com.example.macromod.model.MacroStep;
import com.example.macromod.util.JsonUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Registers all /macro client commands.
 */
@Environment(EnvType.CLIENT)
public class MacroCommands {

    private static final SuggestionProvider<FabricClientCommandSource> MACRO_NAME_SUGGESTIONS =
            (context, builder) -> {
                MacroManager manager = MacroModClient.getManager();
                for (String name : manager.getAllNames()) {
                    builder.suggest(name);
                }
                return builder.buildFuture();
            };

    /**
     * Registers all /macro subcommands.
     */
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(
                ClientCommandManager.literal("macro")
                        // /macro list
                        .then(ClientCommandManager.literal("list")
                                .executes(ctx -> executeList(ctx.getSource())))

                        // /macro run <name_or_id> [--loop]
                        .then(ClientCommandManager.literal("run")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests(MACRO_NAME_SUGGESTIONS)
                                        .executes(ctx -> {
                                            String input = StringArgumentType.getString(ctx, "name");
                                            boolean loop = false;
                                            String nameOrId = input;
                                            if (input.endsWith(" --loop")) {
                                                loop = true;
                                                nameOrId = input.substring(0, input.length() - " --loop".length());
                                            }
                                            return executeRun(ctx.getSource(), nameOrId, loop);
                                        })))

                        // /macro stop
                        .then(ClientCommandManager.literal("stop")
                                .executes(ctx -> executeStop(ctx.getSource())))

                        // /macro pause
                        .then(ClientCommandManager.literal("pause")
                                .executes(ctx -> executePause(ctx.getSource())))

                        // /macro resume
                        .then(ClientCommandManager.literal("resume")
                                .executes(ctx -> executeResume(ctx.getSource())))

                        // /macro status
                        .then(ClientCommandManager.literal("status")
                                .executes(ctx -> executeStatus(ctx.getSource())))

                        // /macro record start <name>
                        // /macro record waypoint [label]
                        // /macro record block
                        // /macro record stop
                        // /macro record cancel
                        .then(ClientCommandManager.literal("record")
                                .then(ClientCommandManager.literal("start")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> executeRecordStart(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(ClientCommandManager.literal("waypoint")
                                        .executes(ctx -> executeRecordWaypoint(ctx.getSource(), null))
                                        .then(ClientCommandManager.argument("label", StringArgumentType.greedyString())
                                                .executes(ctx -> executeRecordWaypoint(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "label")))))
                                .then(ClientCommandManager.literal("block")
                                        .executes(ctx -> executeRecordBlock(ctx.getSource())))
                                .then(ClientCommandManager.literal("stop")
                                        .executes(ctx -> executeRecordStop(ctx.getSource())))
                                .then(ClientCommandManager.literal("cancel")
                                        .executes(ctx -> executeRecordCancel(ctx.getSource()))))

                        // /macro delete <name_or_id>
                        .then(ClientCommandManager.literal("delete")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests(MACRO_NAME_SUGGESTIONS)
                                        .executes(ctx -> executeDelete(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))

                        // /macro info <name_or_id>
                        .then(ClientCommandManager.literal("info")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests(MACRO_NAME_SUGGESTIONS)
                                        .executes(ctx -> executeInfo(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))

                        // /macro export <name_or_id>
                        .then(ClientCommandManager.literal("export")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests(MACRO_NAME_SUGGESTIONS)
                                        .executes(ctx -> executeExport(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Command implementations
    // ═══════════════════════════════════════════════════════════════

    private static int executeList(FabricClientCommandSource source) {
        MacroManager manager = MacroModClient.getManager();
        var macros = manager.getAll();

        if (macros.isEmpty()) {
            source.sendFeedback(Text.translatable("macromod.chat.no_macros").formatted(Formatting.YELLOW));
            return 0;
        }

        source.sendFeedback(Text.translatable("macromod.chat.macro_list").formatted(Formatting.GOLD));
        for (Macro macro : macros) {
            source.sendFeedback(Text.translatable("macromod.chat.macro_entry",
                    macro.getName(), macro.getId(), macro.getSteps().size()).formatted(Formatting.WHITE));
        }
        return macros.size();
    }

    private static int executeRun(FabricClientCommandSource source, String nameOrId, boolean loop) {
        MacroExecutor executor = MacroModClient.getExecutor();
        executor.start(nameOrId, loop ? true : null);
        return 1;
    }

    private static int executeStop(FabricClientCommandSource source) {
        MacroModClient.getExecutor().stop();
        return 1;
    }

    private static int executePause(FabricClientCommandSource source) {
        MacroModClient.getExecutor().pause();
        return 1;
    }

    private static int executeResume(FabricClientCommandSource source) {
        MacroModClient.getExecutor().resume();
        return 1;
    }

    private static int executeStatus(FabricClientCommandSource source) {
        MacroExecutor executor = MacroModClient.getExecutor();

        if (!executor.isRunning() && executor.getState() != MacroState.PAUSED) {
            source.sendFeedback(Text.translatable("macromod.chat.no_active_macro").formatted(Formatting.YELLOW));
            return 0;
        }

        Macro macro = executor.getCurrentMacro();
        source.sendFeedback(Text.translatable("macromod.chat.status",
                executor.getState().name(),
                macro != null ? macro.getName() : "?",
                executor.getCurrentStepIndex() + 1,
                executor.getTotalSteps(),
                executor.getBlocksProcessedInStep(),
                executor.getTotalBlocksInStep()
        ).formatted(Formatting.AQUA));
        return 1;
    }

    private static int executeRecordStart(FabricClientCommandSource source, String name) {
        MacroModClient.getRecorder().startRecording(name);
        return 1;
    }

    private static int executeRecordWaypoint(FabricClientCommandSource source, String label) {
        MacroModClient.getRecorder().addWaypoint(label);
        return 1;
    }

    private static int executeRecordBlock(FabricClientCommandSource source) {
        MacroModClient.getRecorder().addBlockTarget();
        return 1;
    }

    private static int executeRecordStop(FabricClientCommandSource source) {
        MacroModClient.getRecorder().stopRecording();
        return 1;
    }

    private static int executeRecordCancel(FabricClientCommandSource source) {
        MacroModClient.getRecorder().cancelRecording();
        return 1;
    }

    private static int executeDelete(FabricClientCommandSource source, String nameOrId) {
        MacroManager manager = MacroModClient.getManager();
        Macro macro = manager.getByNameOrId(nameOrId);

        if (macro == null) {
            source.sendFeedback(Text.translatable("macromod.chat.macro_not_found", nameOrId).formatted(Formatting.RED));
            return 0;
        }

        String name = macro.getName();
        manager.delete(macro.getId());
        source.sendFeedback(Text.translatable("macromod.chat.macro_deleted", name).formatted(Formatting.GREEN));
        return 1;
    }

    private static int executeInfo(FabricClientCommandSource source, String nameOrId) {
        MacroManager manager = MacroModClient.getManager();
        Macro macro = manager.getByNameOrId(nameOrId);

        if (macro == null) {
            source.sendFeedback(Text.translatable("macromod.chat.macro_not_found", nameOrId).formatted(Formatting.RED));
            return 0;
        }

        source.sendFeedback(Text.translatable("macromod.chat.macro_info", macro.getName()).formatted(Formatting.GOLD));
        source.sendFeedback(Text.translatable("macromod.chat.macro_info_id", macro.getId()).formatted(Formatting.GRAY));
        source.sendFeedback(Text.translatable("macromod.chat.macro_info_steps", macro.getSteps().size()).formatted(Formatting.WHITE));

        if (macro.getDescription() != null && !macro.getDescription().isEmpty()) {
            source.sendFeedback(Text.translatable("macromod.chat.macro_info_desc", macro.getDescription()).formatted(Formatting.WHITE));
        }

        source.sendFeedback(Text.translatable("macromod.chat.macro_info_config",
                macro.getConfig().isLoop() ? "ON" : "OFF",
                macro.getConfig().isSkipMismatch() ? "ON" : "OFF",
                macro.getConfig().isStopOnDanger() ? "ON" : "OFF"
        ).formatted(Formatting.GRAY));

        for (int i = 0; i < macro.getSteps().size(); i++) {
            MacroStep step = macro.getSteps().get(i);
            source.sendFeedback(Text.translatable("macromod.label.step_entry",
                    i + 1, step.getLabel(),
                    step.getDestination().getX(), step.getDestination().getY(), step.getDestination().getZ(),
                    step.getTargets().size()
            ).formatted(Formatting.AQUA));
        }
        return 1;
    }

    private static int executeExport(FabricClientCommandSource source, String nameOrId) {
        MacroManager manager = MacroModClient.getManager();
        Macro macro = manager.getByNameOrId(nameOrId);

        if (macro == null) {
            source.sendFeedback(Text.translatable("macromod.chat.macro_not_found", nameOrId).formatted(Formatting.RED));
            return 0;
        }

        String json = JsonUtils.toJson(macro);
        source.sendFeedback(Text.translatable("macromod.chat.export_header", macro.getName()).formatted(Formatting.GOLD));
        // Split JSON into lines to avoid chat overflow
        for (String line : json.split("\n")) {
            source.sendFeedback(Text.literal(line).formatted(Formatting.WHITE));
        }
        return 1;
    }
}
