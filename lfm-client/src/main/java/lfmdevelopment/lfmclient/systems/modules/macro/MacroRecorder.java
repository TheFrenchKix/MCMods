package lfmdevelopment.lfmclient.systems.modules.macro;

import lfmdevelopment.lfmclient.events.world.TickEvent;
import lfmdevelopment.lfmclient.settings.*;
import lfmdevelopment.lfmclient.systems.modules.Categories;
import lfmdevelopment.lfmclient.systems.modules.Module;
import lfmdevelopment.lfmclient.systems.modules.macro.data.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public class MacroRecorder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> macroName = sgGeneral.add(new StringSetting.Builder()
        .name("macro-name")
        .description("Name for the recorded macro.")
        .defaultValue("my_macro")
        .build()
    );

    private final Setting<Boolean> recordLoop = sgGeneral.add(new BoolSetting.Builder()
        .name("loop")
        .description("Mark the macro as looping.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> recordAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("attack-mode")
        .description("Enable attack mode in recorded macro.")
        .defaultValue(false)
        .build()
    );

    private MacroData currentMacro;
    private BlockPos lastRecordedPos;
    private int ticksSinceLastAction;
    private int leftClickCooldown;
    private int rightClickCooldown;

    public MacroRecorder() {
        super(Categories.Misc, "macro-recorder", "Records player actions into a replayable macro.");
    }

    @Override
    public void onActivate() {
        currentMacro = new MacroData();
        currentMacro.name = macroName.get();
        currentMacro.loop = recordLoop.get();
        currentMacro.attack = recordAttack.get();
        lastRecordedPos = null;
        ticksSinceLastAction = 0;
        leftClickCooldown = 0;
        rightClickCooldown = 0;
        info("Recording macro: (highlight)%s", macroName.get());
    }

    @Override
    public void onDeactivate() {
        if (currentMacro != null && !currentMacro.actions.isEmpty()) {
            MacroSerializer.save(currentMacro);
            info("Saved macro (highlight)%s(default) with (highlight)%d(default) actions.",
                currentMacro.name, currentMacro.actions.size());
        } else {
            info("No actions recorded.");
        }
        currentMacro = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || currentMacro == null) return;

        ClientPlayerEntity player = mc.player;
        ticksSinceLastAction++;

        // Decrement cooldowns every tick
        if (leftClickCooldown > 0) leftClickCooldown--;
        if (rightClickCooldown > 0) rightClickCooldown--;

        // Record movement when player has moved to a new block
        BlockPos currentBlock = player.getBlockPos();
        if (lastRecordedPos == null || !currentBlock.equals(lastRecordedPos)) {
            MoveMode mode = MoveMode.WALK;
            if (player.isSneaking()) mode = MoveMode.SNEAK;
            else if (!player.isOnGround()) mode = MoveMode.JUMP;

            MacroAction moveAction = MacroAction.move(currentBlock, mode, ticksSinceLastAction);
            currentMacro.actions.add(moveAction);
            lastRecordedPos = currentBlock;
            ticksSinceLastAction = 0;
        }

        // Record left click (mine)
        if (mc.options.attackKey.isPressed() && leftClickCooldown <= 0) {
            recordMine();
            leftClickCooldown = 5;
        }

        // Record right click (interact)
        if (mc.options.useKey.isPressed() && rightClickCooldown <= 0) {
            recordInteract();
            rightClickCooldown = 5;
        }
    }

    private void recordMine() {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        MacroAction action = MacroAction.mine(pos, blockId, ticksSinceLastAction);
        currentMacro.actions.add(action);
        ticksSinceLastAction = 0;
    }

    private void recordInteract() {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        MacroAction action = MacroAction.interact(pos, blockId, ticksSinceLastAction);
        currentMacro.actions.add(action);
        ticksSinceLastAction = 0;
    }

    public MacroData getCurrentMacro() {
        return currentMacro;
    }
}

