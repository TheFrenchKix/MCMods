package lfmdevelopment.lfmclient.systems.modules.macro;

import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.systems.modules.macro.data.ActionType;
import lfmdevelopment.lfmclient.systems.modules.macro.data.MacroAction;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ActionExecutor {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private boolean executing;
    private MacroAction currentAction;
    private int miningTicks;
    private boolean miningStarted;

    public void reset() {
        executing = false;
        currentAction = null;
        miningTicks = 0;
        miningStarted = false;
    }

    public boolean startAction(MacroAction action) {
        if (mc.player == null || mc.interactionManager == null) return false;
        this.currentAction = action;
        this.executing = true;
        this.miningTicks = 0;
        this.miningStarted = false;

        switch (action.type) {
            case MINE -> {
                return startMine(action);
            }
            case INTERACT -> {
                return executeInteract(action);
            }
            default -> {
                executing = false;
                return false;
            }
        }
    }

    public ActionResult tick() {
        if (!executing || currentAction == null) return ActionResult.IDLE;
        if (mc.player == null) return ActionResult.FAILED;

        if (currentAction.type == ActionType.MINE) {
            return tickMine();
        }

        // INTERACT completes immediately
        executing = false;
        return ActionResult.COMPLETE;
    }

    private boolean startMine(MacroAction action) {
        BlockPos pos = action.position;
        if (pos == null) {
            executing = false;
            return false;
        }

        // Validate block type
        if (action.block != null && !validateBlock(pos, action.block)) {
            lfmClient.LOG.warn("ActionExecutor: block mismatch at {}, expected {}", pos.toShortString(), action.block);
            executing = false;
            return false;
        }

        // Look at the block
        lookAt(pos);
        return true;
    }

    private ActionResult tickMine() {
        BlockPos pos = currentAction.position;
        if (pos == null) {
            executing = false;
            return ActionResult.FAILED;
        }

        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;
        if (player == null || im == null) {
            executing = false;
            return ActionResult.FAILED;
        }

        // Check if block is already air
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            executing = false;
            return ActionResult.COMPLETE;
        }

        // Look at block each tick
        lookAt(pos);

        if (!miningStarted) {
            // Start mining
            Direction side = getClosestFace(player, pos);
            im.attackBlock(pos, side);
            player.swingHand(Hand.MAIN_HAND);
            miningStarted = true;
            miningTicks = 0;
        }

        miningTicks++;

        // Continue mining tick
        Direction side = getClosestFace(player, pos);
        if (im.updateBlockBreakingProgress(pos, side)) {
            player.swingHand(Hand.MAIN_HAND);
        }

        // Check if block is broken
        state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            executing = false;
            return ActionResult.COMPLETE;
        }

        // Safety: timeout after 200 ticks (10 seconds)
        if (miningTicks > 200) {
            lfmClient.LOG.warn("ActionExecutor: mining timeout at {}", pos.toShortString());
            executing = false;
            return ActionResult.FAILED;
        }

        return ActionResult.IN_PROGRESS;
    }

    private boolean executeInteract(MacroAction action) {
        BlockPos pos = action.position;
        if (pos == null) {
            executing = false;
            return false;
        }

        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;
        if (player == null || im == null) {
            executing = false;
            return false;
        }

        // Validate block if specified
        if (action.block != null && !validateBlock(pos, action.block)) {
            lfmClient.LOG.warn("ActionExecutor: interact block mismatch at {}, expected {}", pos.toShortString(), action.block);
            executing = false;
            return false;
        }

        // Look at the block
        lookAt(pos);

        // Interact
        Direction side = getClosestFace(player, pos);
        Vec3d hitVec = Vec3d.ofCenter(pos);
        BlockHitResult hitResult = new BlockHitResult(hitVec, side, pos, false);
        im.interactBlock(player, Hand.MAIN_HAND, hitResult);
        player.swingHand(Hand.MAIN_HAND);

        executing = false;
        return true;
    }

    private boolean validateBlock(BlockPos pos, String expectedBlock) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        String actualId = Registries.BLOCK.getId(state.getBlock()).toString();
        return actualId.equals(expectedBlock);
    }

    private void lookAt(BlockPos pos) {
        if (mc.player == null) return;
        Vec3d target = Vec3d.ofCenter(pos);
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(MathHelper.atan2(dy, dist) * (180.0 / Math.PI));

        // Smooth rotation
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float yawDiff = MathHelper.wrapDegrees(yaw - currentYaw);
        float pitchDiff = pitch - currentPitch;
        mc.player.setYaw(currentYaw + MathHelper.clamp(yawDiff, -15.0f, 15.0f));
        mc.player.setPitch(currentPitch + MathHelper.clamp(pitchDiff, -15.0f, 15.0f));
    }

    private Direction getClosestFace(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyePos = player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d diff = eyePos.subtract(blockCenter);

        double absX = Math.abs(diff.x);
        double absY = Math.abs(diff.y);
        double absZ = Math.abs(diff.z);

        if (absY >= absX && absY >= absZ) {
            return diff.y > 0 ? Direction.UP : Direction.DOWN;
        } else if (absX >= absZ) {
            return diff.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    public boolean isExecuting() {
        return executing;
    }

    public enum ActionResult {
        IDLE,
        IN_PROGRESS,
        COMPLETE,
        FAILED
    }
}
