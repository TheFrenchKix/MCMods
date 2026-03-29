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
 * Supports standard (nearest) mode and row farming mode.
 * Separated from mob combat (AutoSlay).
 */
public class AutoCropModule {

    private final AimController aimController = new AimController();
    private boolean wasActive = false;
    private int cropPauseTicks = 0;
    private int cropBreakFailures = 0;
    private int jitterCooldown = 0;
    private final Random random = new Random();

    // Row farming state
    private enum RowState { IDLE, DETECTING, WALKING_TO_ROW, FARMING_ROW }
    private RowState rowState = RowState.IDLE;
    private List<BlockPos> currentRow = new java.util.ArrayList<>();
    private int currentRowIndex = 0;
    private Direction rowDirection = null;   // auto-detected or manual
    private BlockPos rowOrigin = null;       // where the current row starts
    private int rowsCompleted = 0;
    private int rowDetectCooldown = 0;

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

        // Row farming mode: systematic row-by-row harvesting
        if (config.isRowFarmingEnabled()) {
            tickRowFarming(client, player, config);
            return;
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

    /**
     * Row farming: detect crop rows, walk along each row harvesting, step to next row.
     */
    private void tickRowFarming(MinecraftClient client, ClientPlayerEntity player, ModConfig config) {
        double cropRange = Math.min(8.0, config.getAutoFarmRange());

        switch (rowState) {
            case IDLE -> {
                if (--rowDetectCooldown <= 0) {
                    rowDetectCooldown = 10;
                    // Auto-detect row direction from nearby crops
                    rowDirection = detectRowDirection(player, cropRange, config.getCropType());
                    if (rowDirection != null) {
                        currentRow = findCropRow(player, rowDirection, cropRange, config.getCropType());
                        if (currentRow.size() >= 3) {
                            rowState = RowState.FARMING_ROW;
                            currentRowIndex = 0;
                            rowOrigin = currentRow.get(0);
                            DebugLogger.log("AutoCrop", "Row detected: " + currentRow.size()
                                + " crops, dir=" + rowDirection.asString());
                        }
                    }
                }
            }
            case FARMING_ROW -> {
                if (currentRowIndex >= currentRow.size()) {
                    // Row complete, find next parallel row
                    rowsCompleted++;
                    rowState = RowState.IDLE;
                    rowDetectCooldown = 5;
                    DebugLogger.log("AutoCrop", "Row complete (" + rowsCompleted + " total)");
                    return;
                }

                BlockPos target = currentRow.get(currentRowIndex);
                double dist = player.squaredDistanceTo(Vec3d.ofCenter(target));

                // If we're close enough, harvest
                if (dist < 4.0) {
                    BlockState cropState = client.world.getBlockState(target);
                    if (isMatchingRipeCrop(cropState, config.getCropType())) {
                        if (config.isAutoFarmAutoTool()) {
                            equipBestFarmingTool(player, config.getCropType());
                        }
                        boolean didBreak = breakCrop(client, player, target);
                        if (didBreak) {
                            if (cropState.isOf(Blocks.WHEAT)) FarmStats.addWheatBreaks(1);
                            else if (cropState.isOf(Blocks.CARROTS)) FarmStats.addCarrotBreaks(1);
                            else if (cropState.isOf(Blocks.POTATOES)) FarmStats.addPotatoBreaks(1);
                            if (config.isAutoFarmReplant()) {
                                tryReplant(client, player, target, cropState);
                            }
                        }
                    }
                    currentRowIndex++;
                } else {
                    // Walk toward next crop in row
                    Vec3d targetVec = Vec3d.ofCenter(target);
                    aimController.setTarget(targetVec);
                    aimController.setFastTracking(false);

                    // Simple forward movement toward target
                    double dx = targetVec.x - player.getX();
                    double dz = targetVec.z - player.getZ();
                    float targetYaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
                    player.setYaw(targetYaw);
                    client.options.forwardKey.setPressed(true);

                    // If stuck, advance to next crop
                    if (dist > 2.0 && player.getVelocity().horizontalLengthSquared() < 0.001) {
                        cropBreakFailures++;
                        if (cropBreakFailures > 20) {
                            currentRowIndex++;
                            cropBreakFailures = 0;
                        }
                    } else {
                        cropBreakFailures = 0;
                    }
                }
            }
            default -> rowState = RowState.IDLE;
        }
    }

    /**
     * Auto-detect row direction by finding the longest aligned crop sequence near the player.
     */
    private Direction detectRowDirection(ClientPlayerEntity player, double range, ModConfig.CropType cropType) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;

        BlockPos center = player.getBlockPos();
        int r = Math.max(1, (int) Math.ceil(range));

        Direction bestDir = null;
        int bestLength = 0;

        // Check N/S and E/W directions
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            // Scan from player position in this direction
            int length = 0;
            for (int i = 0; i <= r * 2; i++) {
                BlockPos check = center.offset(dir, i);
                // Check at player Y and Y-1 (in case crops are slightly below)
                boolean found = false;
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos checkY = check.up(dy);
                    if (isMatchingRipeCrop(client.world.getBlockState(checkY), cropType)) {
                        found = true;
                        break;
                    }
                }
                if (found) length++;
                else if (length > 0) break; // end of contiguous row
            }
            if (length > bestLength) {
                bestLength = length;
                bestDir = dir;
            }
        }

        return bestLength >= 3 ? bestDir : null;
    }

    /**
     * Find all ripe crops in a line from the player in the given direction.
     */
    private List<BlockPos> findCropRow(ClientPlayerEntity player, Direction dir, double range, ModConfig.CropType cropType) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return Collections.emptyList();

        BlockPos center = player.getBlockPos();
        int r = Math.max(1, (int) Math.ceil(range));
        List<BlockPos> row = new java.util.ArrayList<>();

        // Scan forward along the direction
        for (int i = -r; i <= r; i++) {
            BlockPos check = center.offset(dir, i);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos checkY = check.up(dy);
                if (isMatchingRipeCrop(client.world.getBlockState(checkY), cropType)) {
                    row.add(checkY);
                    break;
                }
            }
        }

        // Sort by distance along the direction so we walk in order
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        row.sort((a, b) -> {
            double da = Vec3d.ofCenter(a).squaredDistanceTo(playerPos);
            double db = Vec3d.ofCenter(b).squaredDistanceTo(playerPos);
            return Double.compare(da, db);
        });

        return row;
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
