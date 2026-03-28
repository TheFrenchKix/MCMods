package com.mwa.n0name.modules;

import com.mwa.n0name.DebugLogger;
import com.mwa.n0name.ModConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebugToolsModule {

    private int cooldown = 0;
    private String lastTargetBlockSnapshot = "";
    private String lastBlockScanSnapshot = "";
    private String lastEntityScanSnapshot = "";

    public void tick() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.isDebugToolsEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        if (--cooldown > 0) return;
        cooldown = 20;

        if (cfg.isDebugTargetBlockEnabled()) {
            logTargetBlock(client);
        }
        if (cfg.isDebugScanBlocksEnabled()) {
            logAroundBlocks(client, cfg.getDebugScanRange());
        }
        if (cfg.isDebugScanEntitiesEnabled()) {
            logAroundEntities(client, cfg.getDebugScanRange());
        }
    }

    private void logTargetBlock(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult bhr && client.world != null) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            String snapshot = "[Debug] TargetBlock id="
                + Registries.BLOCK.getId(state.getBlock())
                + " pos=" + pos.toShortString()
                + " state=" + state
                + " hardness=" + state.getHardness(client.world, pos)
                + " hasBlockEntity=" + (client.world.getBlockEntity(pos) != null);
            if (!snapshot.equals(lastTargetBlockSnapshot)) {
                lastTargetBlockSnapshot = snapshot;
                DebugLogger.info(snapshot);
            }
        } else {
            lastTargetBlockSnapshot = "";
        }
    }

    private void logAroundBlocks(MinecraftClient client, int range) {
        if (client.player == null || client.world == null) return;
        BlockPos center = client.player.getBlockPos();
        double rangeSq = (double) range * range;
        Map<String, BlockInfo> blocks = new HashMap<>();

        for (int x = -range; x <= range; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Vec3d blockCenter = Vec3d.ofCenter(pos);
                    if (client.player.squaredDistanceTo(blockCenter) > rangeSq) continue;

                    BlockState state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;

                    String id = Registries.BLOCK.getId(state.getBlock()).toString();
                    BlockInfo info = blocks.computeIfAbsent(id,
                        key -> new BlockInfo(state.toString(), state.getHardness(client.world, pos)));
                    info.count++;

                    double distSq = client.player.squaredDistanceTo(blockCenter);
                    if (distSq < info.nearestDistanceSq) {
                        info.nearestDistanceSq = distSq;
                        info.nearestPos = pos.toImmutable();
                    }
                }
            }
        }

        List<String> lines = new ArrayList<>();
        List<Map.Entry<String, BlockInfo>> sorted = new ArrayList<>(blocks.entrySet());
        sorted.sort(Map.Entry.comparingByKey());

        int total = 0;
        lines.add("[Debug] Block scan range=" + range + " unique=" + blocks.size());
        for (Map.Entry<String, BlockInfo> entry : sorted) {
            BlockInfo info = entry.getValue();
            total += info.count;
            lines.add("[Debug] Block id=" + entry.getKey()
                + " count=" + info.count
                + " nearest=" + (info.nearestPos == null ? "-" : info.nearestPos.toShortString())
                + " hardness=" + info.hardness
                + " state=" + info.state);
        }
        if (blocks.isEmpty()) {
            lines.add("[Debug] Block scan found no non-air blocks in range");
        } else {
            lines.set(0, lines.get(0) + " total=" + total);
        }
        emitSnapshot(lines, true);
    }

    private void logAroundEntities(MinecraftClient client, int range) {
        if (client.player == null || client.world == null) return;
        double rangeSq = (double) range * range;
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            if (client.player.squaredDistanceTo(entity) > rangeSq) continue;
            entities.add(entity);
        }

        entities.sort(Comparator.comparingDouble(client.player::squaredDistanceTo));
        List<String> lines = new ArrayList<>();
        lines.add("[Debug] Entity scan range=" + range + " count=" + entities.size());
        for (Entity entity : entities) {
            StringBuilder line = new StringBuilder("[Debug] Entity id=")
                .append(Registries.ENTITY_TYPE.getId(entity.getType()))
                .append(" name=").append(entity.getDisplayName().getString())
                .append(" pos=").append(entity.getBlockPos().toShortString())
                .append(" dist=")
                .append(String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(client.player.squaredDistanceTo(entity))))
                .append(" alive=").append(entity.isAlive());
            if (entity instanceof LivingEntity living) {
                line.append(" hp=")
                    .append(String.format(java.util.Locale.ROOT, "%.1f", living.getHealth()))
                    .append("/")
                    .append(String.format(java.util.Locale.ROOT, "%.1f", living.getMaxHealth()));
            }
            lines.add(line.toString());
        }
        if (entities.isEmpty()) {
            lines.add("[Debug] Entity scan found no entities in range");
        }
        emitSnapshot(lines, false);
    }

    private void emitSnapshot(List<String> lines, boolean blockSnapshot) {
        String snapshot = String.join("\n", lines);
        if (blockSnapshot) {
            if (snapshot.equals(lastBlockScanSnapshot)) return;
            lastBlockScanSnapshot = snapshot;
        } else {
            if (snapshot.equals(lastEntityScanSnapshot)) return;
            lastEntityScanSnapshot = snapshot;
        }

        for (String line : lines) {
            DebugLogger.info(line);
        }
    }

    private static final class BlockInfo {
        private final String state;
        private final float hardness;
        private int count;
        private double nearestDistanceSq = Double.MAX_VALUE;
        private BlockPos nearestPos;

        private BlockInfo(String state, float hardness) {
            this.state = state;
            this.hardness = hardness;
        }
    }
}
