package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import com.mwa.n0name.movement.AimController;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * AutoCrop module: scans for ripe crops, breaks them, optionally replants.
 * Separated from mob combat (AutoSlay).
 */
public class AutoCropModule {

    private final AimController aimController = new AimController();
    private boolean wasActive = false;
    private int cropPauseTicks = 0;
    private int cropBreakFailures = 0;
    private int jitterCooldown = 0;
    private final Random random = new Random();

    public void frameUpdate() {
        if (aimController.isActive()) {
            aimController.tick();
        }
    }

    public void tick() {
        boolean active = ModConfig.getInstance().isAutoCropEnabled();

        if (wasActive && !active) {
            DebugLogger.log("AutoCrop", "Disabled");
        }
        wasActive = active;
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) return;

        ModConfig config = ModConfig.getInstance();

        if (cropPauseTicks > 0) {
            cropPauseTicks--;
            return;
        }

        if (config.isFailsafeRandomPauseEnabled() && random.nextInt(80) == 0) {
            int min = config.getFailsafePauseMinTicks();
            int max = Math.max(min, config.getFailsafePauseMaxTicks());
            cropPauseTicks = min + random.nextInt(max - min + 1);
            return;
        }

        if (config.isFailsafeYawJitterEnabled() && --jitterCooldown <= 0) {
            float jitter = (random.nextFloat() - 0.5f) * 1.2f;
            aimController.applyYawJitter(jitter);
            jitterCooldown = 8 + random.nextInt(8);
        }

        double cropRange = Math.min(8.0, config.getAutoFarmRange());
        List<BlockPos> targets = findRipeCropTargets(player, cropRange, config.getCropType());
        if (targets.isEmpty()) return;

        if (config.isAutoFarmAutoTool()) {
            equipBestFarmingTool(player, config.getCropType());
        }

        int maxBreaks = config.getAutoFarmCropBlocksPerTick();
        int wheat = 0, carrot = 0, potato = 0;

        for (BlockPos pos : targets) {
            if (wheat + carrot + potato >= maxBreaks) break;

            BlockState cropState = client.world.getBlockState(pos);
            if (!isMatchingRipeCrop(cropState, config.getCropType())) continue;

            boolean didBreak;
            if (config.isAutoFarmSilentCropAim()) {
                didBreak = breakCrop(client, player, pos);
            } else {
                Vec3d center = Vec3d.ofCenter(pos);
                aimController.setTarget(center);
                aimController.setFastTracking(true);
                aimController.tick();
                didBreak = aimController.isOnTarget(8.0f) && breakCrop(client, player, pos);
            }

            if (!didBreak) {
                cropBreakFailures++;
                continue;
            }

            if (cropState.isOf(Blocks.WHEAT)) wheat++;
            else if (cropState.isOf(Blocks.CARROTS)) carrot++;
            else if (cropState.isOf(Blocks.POTATOES)) potato++;
            cropBreakFailures = 0;

            if (config.isAutoFarmReplant()) {
                tryReplant(client, player, pos, cropState);
            }
        }

        if (wheat > 0) FarmStats.addWheatBreaks(wheat);
        if (carrot > 0) FarmStats.addCarrotBreaks(carrot);
        if (potato > 0) FarmStats.addPotatoBreaks(potato);

        if (config.isDesyncWatchdogEnabled() && cropBreakFailures >= config.getDesyncFailLimit()) {
            cropBreakFailures = 0;
            config.setAutoFarmCropBlocksPerTick(Math.max(1, config.getAutoFarmCropBlocksPerTick() - 1));
            cropPauseTicks = 10;
            DebugLogger.log("AutoCrop", "Desync watchdog: lowering crop speed");
        }
    }

    private List<BlockPos> findRipeCropTargets(ClientPlayerEntity player, double range, ModConfig.CropType cropType) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return Collections.emptyList();

        BlockPos center = player.getBlockPos();
        int r = Math.max(1, (int) Math.ceil(range));
        List<BlockPos> out = new java.util.ArrayList<>();

        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > range * range) continue;
                    BlockState state = client.world.getBlockState(pos);
                    if (!isMatchingRipeCrop(state, cropType)) continue;
                    out.add(pos);
                }
            }
        }

        out.sort((a, b) -> Double.compare(
            player.squaredDistanceTo(Vec3d.ofCenter(a)),
            player.squaredDistanceTo(Vec3d.ofCenter(b))));
        return out;
    }

    private boolean isMatchingRipeCrop(BlockState state, ModConfig.CropType cropType) {
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) return false;
        return switch (cropType) {
            case WHEAT -> state.isOf(Blocks.WHEAT);
            case CARROT -> state.isOf(Blocks.CARROTS);
            case POTATO -> state.isOf(Blocks.POTATOES);
            case ALL -> state.isOf(Blocks.WHEAT) || state.isOf(Blocks.CARROTS) || state.isOf(Blocks.POTATOES);
        };
    }

    private boolean breakCrop(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
        if (client.world == null || client.interactionManager == null) return false;
        if (client.world.isAir(pos)) return false;
        if (!(client.world.getBlockState(pos).getBlock() instanceof CropBlock)) return false;

        boolean attacked = client.interactionManager.attackBlock(pos, Direction.UP);
        if (attacked) player.swingHand(Hand.MAIN_HAND);
        return attacked;
    }

    private void equipBestFarmingTool(ClientPlayerEntity player, ModConfig.CropType cropType) {
        int current = player.getInventory().getSelectedSlot();
        ItemStack selected = player.getInventory().getStack(current);
        if (isGoodFarmingTool(selected, cropType)) return;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (isGoodFarmingTool(stack, cropType)) {
                player.getInventory().setSelectedSlot(slot);
                return;
            }
        }
    }

    private boolean isGoodFarmingTool(ItemStack stack, ModConfig.CropType cropType) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof HoeItem) return true;
        String name = stack.getName().getString().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("hoe") || name.contains("dicer") || name.contains("chopper")) return true;
        return switch (cropType) {
            case WHEAT -> name.contains("wheat");
            case CARROT -> name.contains("carrot");
            case POTATO -> name.contains("potato");
            case ALL -> false;
        };
    }

    private void tryReplant(MinecraftClient client, ClientPlayerEntity player, BlockPos cropPos, BlockState oldCropState) {
        if (client.world == null || client.interactionManager == null) return;
        if (!client.world.isAir(cropPos)) return;

        BlockPos farmland = cropPos.down();
        if (!client.world.getBlockState(farmland).isOf(Blocks.FARMLAND)) return;

        int seedSlot = findSeedSlot(player, oldCropState);
        if (seedSlot < 0) return;

        int prevSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(seedSlot);

        Vec3d hitPos = new Vec3d(farmland.getX() + 0.5, farmland.getY() + 1.0, farmland.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, farmland, false);
        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) player.swingHand(Hand.MAIN_HAND);

        player.getInventory().setSelectedSlot(prevSlot);
    }

    private int findSeedSlot(ClientPlayerEntity player, BlockState oldCropState) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s == null || s.isEmpty()) continue;
            if (oldCropState.isOf(Blocks.WHEAT) && s.isOf(Items.WHEAT_SEEDS)) return i;
            if (oldCropState.isOf(Blocks.CARROTS) && s.isOf(Items.CARROT)) return i;
            if (oldCropState.isOf(Blocks.POTATOES) && s.isOf(Items.POTATO)) return i;
        }
        return -1;
    }
}
