package lfmdevelopment.lfmclient.systems.modules.macro;

import lfmdevelopment.lfmclient.events.render.Render3DEvent;
import lfmdevelopment.lfmclient.events.world.TickEvent;
import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.renderer.ShapeMode;
import lfmdevelopment.lfmclient.settings.*;
import lfmdevelopment.lfmclient.systems.modules.Categories;
import lfmdevelopment.lfmclient.systems.modules.Module;
import lfmdevelopment.lfmclient.systems.modules.macro.data.*;
import lfmdevelopment.lfmclient.systems.modules.macro.pathfinding.*;
import lfmdevelopment.lfmclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MacroPlayer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPathfinding = settings.createGroup("Pathfinding");
    private final SettingGroup sgAttack = settings.createGroup("Attack");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General settings
    private final Setting<String> macroName = sgGeneral.add(new StringSetting.Builder()
        .name("macro-name")
        .description("Name of the macro to play.")
        .defaultValue("my_macro")
        .build()
    );

    private final Setting<Boolean> debugLog = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-log")
        .description("Log detailed debug info.")
        .defaultValue(false)
        .build()
    );

    // Pathfinding settings
    private final Setting<Double> moveTolerance = sgPathfinding.add(new DoubleSetting.Builder()
        .name("move-tolerance")
        .description("Distance threshold to consider a node reached.")
        .defaultValue(0.3)
        .min(0.1)
        .max(2.0)
        .sliderMin(0.1)
        .sliderMax(2.0)
        .build()
    );

    private final Setting<Integer> maxRetries = sgPathfinding.add(new IntSetting.Builder()
        .name("max-retries")
        .description("Max repath attempts per movement action.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build()
    );

    // Attack settings
    private final Setting<Double> attackRadius = sgAttack.add(new DoubleSetting.Builder()
        .name("attack-radius")
        .description("Radius to scan for hostile mobs.")
        .defaultValue(4.0)
        .min(1.0)
        .max(8.0)
        .sliderMin(1.0)
        .sliderMax(8.0)
        .build()
    );

    private final Setting<Integer> attackCooldown = sgAttack.add(new IntSetting.Builder()
        .name("attack-cooldown")
        .description("Ticks between attacks.")
        .defaultValue(10)
        .min(1)
        .sliderMax(20)
        .build()
    );

    // Render settings
    private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder()
        .name("render-path")
        .description("Render the current pathfinding path.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderActions = sgRender.add(new BoolSetting.Builder()
        .name("render-actions")
        .description("Render action target positions.")
        .defaultValue(true)
        .build()
    );

    // State
    private MacroData macro;
    private int actionIndex;
    private int delayTicks;
    private PlayState state;
    private final PathFollower pathFollower = new PathFollower();
    private final ActionExecutor actionExecutor = new ActionExecutor();
    private CompletableFuture<Path> pendingPath;
    private int attackTimer;

    // Render colors
    private static final Color PATH_NODE_COLOR = new Color(255, 255, 0, 80);      // Yellow
    private static final Color PATH_LINE_COLOR = new Color(255, 255, 0, 150);
    private static final Color CURRENT_COLOR = new Color(0, 255, 0, 100);          // Green
    private static final Color MINE_COLOR = new Color(255, 50, 50, 100);           // Red
    private static final Color INTERACT_COLOR = new Color(50, 100, 255, 100);      // Blue
    private static final Color MOVE_COLOR = new Color(255, 200, 50, 60);           // Orange

    public MacroPlayer() {
        super(Categories.Misc, "macro-player", "Plays back recorded macros using pathfinding.");
    }

    @Override
    public void onActivate() {
        macro = MacroSerializer.load(macroName.get());
        if (macro == null) {
            error("Failed to load macro: (highlight)%s", macroName.get());
            toggle();
            return;
        }

        actionIndex = 0;
        delayTicks = 0;
        state = PlayState.WAITING_DELAY;
        attackTimer = 0;
        actionExecutor.reset();
        pendingPath = null;

        info("Playing macro: (highlight)%s(default) (%d actions, loop=%s, attack=%s)",
            macro.name, macro.actions.size(), macro.loop, macro.attack);
    }

    @Override
    public void onDeactivate() {
        pathFollower.stop();
        actionExecutor.reset();
        if (pendingPath != null) pendingPath.cancel(true);
        pendingPath = null;
        macro = null;
        state = null;
        info("Macro playback stopped.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || macro == null || state == null) return;

        // Attack mode
        if (macro.attack) tickAttack();

        switch (state) {
            case WAITING_DELAY -> tickDelay();
            case PATHING -> tickPathing();
            case COMPUTING_PATH -> tickComputingPath();
            case EXECUTING_ACTION -> tickAction();
            case COMPLETE -> tickComplete();
        }
    }

    private void tickDelay() {
        if (actionIndex >= macro.actions.size()) {
            state = PlayState.COMPLETE;
            return;
        }

        MacroAction action = macro.actions.get(actionIndex);
        if (delayTicks < action.delay) {
            delayTicks++;
            return;
        }
        delayTicks = 0;

        // Start executing this action
        switch (action.type) {
            case MOVE -> startMove(action);
            case MINE, INTERACT -> startAction(action);
        }
    }

    private void startMove(MacroAction action) {
        if (mc.player == null || action.position == null) {
            advanceAction();
            return;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos target = action.position;

        // Already there?
        double dist = Math.sqrt(playerPos.getSquaredDistance(target));
        if (dist < (action.tolerance > 0 ? action.tolerance : moveTolerance.get())) {
            if (debugLog.get()) lfmClient.LOG.info("MacroPlayer: already at target {}", target.toShortString());
            advanceAction();
            return;
        }

        if (action.usePathfinder) {
            // Compute path asynchronously
            state = PlayState.COMPUTING_PATH;
            pendingPath = PathfindingService.findPathAsync(playerPos, target);
        } else {
            // Direct walk (simple)
            Path directPath = new Path(List.of(playerPos, target));
            pathFollower.start(directPath, target,
                action.tolerance > 0 ? action.tolerance : moveTolerance.get(),
                action.retries > 0 ? action.retries : maxRetries.get());
            state = PlayState.PATHING;
        }
    }

    private void tickComputingPath() {
        if (pendingPath == null) {
            state = PlayState.WAITING_DELAY;
            return;
        }

        if (!pendingPath.isDone()) return;

        try {
            Path path = pendingPath.join();
            pendingPath = null;

            if (path == null) {
                error("No path found for action #%d", actionIndex + 1);
                advanceAction();
                return;
            }

            MacroAction action = macro.actions.get(actionIndex);
            pathFollower.start(path, action.position,
                action.tolerance > 0 ? action.tolerance : moveTolerance.get(),
                action.retries > 0 ? action.retries : maxRetries.get());
            state = PlayState.PATHING;
        } catch (Exception e) {
            lfmClient.LOG.error("MacroPlayer: pathfinding failed", e);
            pendingPath = null;
            advanceAction();
        }
    }

    private void tickPathing() {
        PathFollower.FollowResult result = pathFollower.tick();

        switch (result) {
            case COMPLETE -> {
                if (debugLog.get()) lfmClient.LOG.info("MacroPlayer: movement complete for action #{}", actionIndex + 1);
                advanceAction();
            }
            case FAILED -> {
                error("Path following failed for action #%d", actionIndex + 1);
                advanceAction();
            }
            case REPATHING -> {
                if (debugLog.get()) lfmClient.LOG.info("MacroPlayer: repathing for action #{}", actionIndex + 1);
            }
            case MOVING, IDLE -> {}
        }
    }

    private void startAction(MacroAction action) {
        // Check chunk is loaded
        if (action.position != null && !mc.world.isChunkLoaded(action.position.getX() >> 4, action.position.getZ() >> 4)) {
            if (debugLog.get()) lfmClient.LOG.info("MacroPlayer: waiting for chunk at {}", action.position.toShortString());
            return; // Will retry next tick
        }

        if (actionExecutor.startAction(action)) {
            state = PlayState.EXECUTING_ACTION;
        } else {
            warning("Failed to start action #%d (%s)", actionIndex + 1, action.type);
            advanceAction();
        }
    }

    private void tickAction() {
        ActionExecutor.ActionResult result = actionExecutor.tick();

        switch (result) {
            case COMPLETE -> {
                if (debugLog.get()) lfmClient.LOG.info("MacroPlayer: action #{} complete", actionIndex + 1);
                advanceAction();
            }
            case FAILED -> {
                warning("Action #%d failed", actionIndex + 1);
                advanceAction();
            }
            case IN_PROGRESS, IDLE -> {}
        }
    }

    private void tickComplete() {
        if (macro.loop) {
            info("Macro loop restarting...");
            actionIndex = 0;
            delayTicks = 0;
            state = PlayState.WAITING_DELAY;
        } else {
            info("Macro playback complete.");
            toggle();
        }
    }

    private void advanceAction() {
        actionIndex++;
        delayTicks = 0;
        actionExecutor.reset();
        state = PlayState.WAITING_DELAY;
    }

    // Attack mode
    private void tickAttack() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (attackTimer > 0) {
            attackTimer--;
            return;
        }

        double radius = attackRadius.get();
        ClientPlayerEntity player = mc.player;

        Entity target = mc.world.getOtherEntities(player,
                player.getBoundingBox().expand(radius), e -> e instanceof HostileEntity && e.isAlive())
            .stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
            .orElse(null);

        if (target != null) {
            // Look at entity
            Vec3d entityPos = new Vec3d(target.getX(), target.getY() + target.getHeight() / 2, target.getZ());
            Vec3d eyes = player.getEyePos();
            double dx = entityPos.x - eyes.x;
            double dy = entityPos.y - eyes.y;
            double dz = entityPos.z - eyes.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float pitch = (float) -(MathHelper.atan2(dy, dist) * (180.0 / Math.PI));
            player.setYaw(yaw);
            player.setPitch(pitch);

            mc.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            attackTimer = attackCooldown.get();
        }
    }

    // Rendering
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (macro == null) return;

        // Render action targets
        if (renderActions.get()) {
            for (int i = 0; i < macro.actions.size(); i++) {
                MacroAction action = macro.actions.get(i);
                if (action.position == null) continue;

                Color color;
                if (i == actionIndex) {
                    color = CURRENT_COLOR;
                } else {
                    color = switch (action.type) {
                        case MINE -> MINE_COLOR;
                        case INTERACT -> INTERACT_COLOR;
                        case MOVE -> MOVE_COLOR;
                    };
                }

                event.renderer.box(action.position, color, PATH_LINE_COLOR, ShapeMode.Both, 0);
            }
        }

        // Render current path
        if (renderPath.get()) {
            Path path = pathFollower.getPath();
            if (path != null) {
                List<BlockPos> nodes = path.getNodes();
                for (int i = 0; i < nodes.size(); i++) {
                    BlockPos node = nodes.get(i);

                    Color nodeColor = (i == path.currentIndex()) ? CURRENT_COLOR : PATH_NODE_COLOR;
                    Box box = new Box(
                        node.getX() + 0.3, node.getY(), node.getZ() + 0.3,
                        node.getX() + 0.7, node.getY() + 0.1, node.getZ() + 0.7
                    );
                    event.renderer.box(box, nodeColor, PATH_LINE_COLOR, ShapeMode.Both, 0);

                    // Draw line to next node
                    if (i < nodes.size() - 1) {
                        BlockPos next = nodes.get(i + 1);
                        event.renderer.line(
                            node.getX() + 0.5, node.getY() + 0.1, node.getZ() + 0.5,
                            next.getX() + 0.5, next.getY() + 0.1, next.getZ() + 0.5,
                            PATH_LINE_COLOR
                        );
                    }
                }
            }
        }
    }

    public MacroData getMacro() {
        return macro;
    }

    public int getActionIndex() {
        return actionIndex;
    }

    public PlayState getState() {
        return state;
    }

    public enum PlayState {
        WAITING_DELAY,
        COMPUTING_PATH,
        PATHING,
        EXECUTING_ACTION,
        COMPLETE
    }
}

