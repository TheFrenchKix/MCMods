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
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DebugToolsModule {

    private static final int MAX_BLOCK_LOGS = 96;
    private static final int MAX_ENTITY_LOGS = 64;

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
            Vec3d hitPos = bhr.getPos();
            double dist = client.player == null ? 0.0 : Math.sqrt(client.player.squaredDistanceTo(hitPos));
            String snapshot = "[Debug] BlockData"
                + " position=" + pos.toShortString()
                + " distance=" + formatDouble(dist)
                + " side=" + bhr.getSide().asString()
                + " type=" + Registries.BLOCK.getId(state.getBlock());
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
        List<BlockDataEntry> blocks = new ArrayList<>();

        for (int x = -range; x <= range; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Vec3d blockCenter = Vec3d.ofCenter(pos);
                    if (client.player.squaredDistanceTo(blockCenter) > rangeSq) continue;

                    BlockState state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;

                    double distSq = client.player.squaredDistanceTo(blockCenter);
                    blocks.add(new BlockDataEntry(
                        pos.toImmutable(),
                        Math.sqrt(distSq),
                        estimateSide(new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ()), blockCenter),
                        Registries.BLOCK.getId(state.getBlock()).toString()
                    ));
                }
            }
        }

        blocks.sort(Comparator.comparingDouble(BlockDataEntry::distance));

        List<String> lines = new ArrayList<>();
        lines.add("[Debug] Block scan range=" + range + " count=" + blocks.size());
        int limit = Math.min(blocks.size(), MAX_BLOCK_LOGS);
        for (int i = 0; i < limit; i++) {
            BlockDataEntry block = blocks.get(i);
            lines.add("[Debug] BlockData"
                + " position=" + block.position().toShortString()
                + " distance=" + formatDouble(block.distance())
                + " side=" + block.side()
                + " type=" + block.type());
        }
        if (blocks.isEmpty()) {
            lines.add("[Debug] Block scan found no non-air blocks in range");
        } else if (blocks.size() > limit) {
            lines.add("[Debug] Block scan truncated " + (blocks.size() - limit) + " entries");
        }
        emitSnapshot(lines, true);
    }

    private void logAroundEntities(MinecraftClient client, int range) {
        if (client.player == null || client.world == null) return;
        double rangeSq = (double) range * range;
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (client.player.squaredDistanceTo(entity) > rangeSq) continue;
            entities.add(entity);
        }

        if (client.player.squaredDistanceTo(client.player) <= rangeSq) {
            entities.add(client.player);
        }

        entities.sort(Comparator.comparingDouble(client.player::squaredDistanceTo));
        List<String> lines = new ArrayList<>();
        lines.add("[Debug] Entity scan range=" + range + " count=" + entities.size());
        int limit = Math.min(entities.size(), MAX_ENTITY_LOGS);
        for (int i = 0; i < limit; i++) {
            Entity entity = entities.get(i);
            boolean local = entity == client.player;
            @Nullable Float health = entity instanceof LivingEntity living ? living.getHealth() : null;

            StringBuilder line = new StringBuilder("[Debug] Entity id=")
                .append(entity.getId())
                .append(" EntityData")
                .append(" name=\"").append(entity.getDisplayName().getString()).append("\"")
                .append(" type=").append(Registries.ENTITY_TYPE.getId(entity.getType()))
                .append(" uuid=").append(entity.getUuidAsString())
                .append(" position=").append(formatVec3(new Vec3d(entity.getX(), entity.getY(), entity.getZ())))
                .append(" yaw=").append(formatDouble(entity.getYaw()))
                .append(" pitch=").append(formatDouble(entity.getPitch()))
                .append(" velocity=").append(formatVec3(entity.getVelocity()))
                .append(" health=").append(formatNullableFloat(health))
                .append(" local=").append(local)
                .append(" passengers=").append(formatPassengerUuids(entity))
                .append(" nbt=").append(getEntityNbtString(entity));
            lines.add(line.toString());
        }
        if (entities.isEmpty()) {
            lines.add("[Debug] Entity scan found no entities in range");
        } else if (entities.size() > limit) {
            lines.add("[Debug] Entity scan truncated " + (entities.size() - limit) + " entries");
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

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatVec3(Vec3d vec) {
        return "(" + formatDouble(vec.x) + "," + formatDouble(vec.y) + "," + formatDouble(vec.z) + ")";
    }

    private static String formatNullableFloat(@Nullable Float value) {
        if (value == null) {
            return "null";
        }
        return formatDouble(value);
    }

    private static String formatPassengerUuids(Entity entity) {
        if (entity.getPassengerList().isEmpty()) {
            return "[]";
        }
        List<String> uuids = new ArrayList<>();
        for (Entity passenger : entity.getPassengerList()) {
            uuids.add(passenger.getUuidAsString());
        }
        return uuids.toString();
    }

    private static String getEntityNbtString(Entity entity) {
        try {
            Class<?> nbtClass = Class.forName("net.minecraft.nbt.NbtCompound");
            Object nbt = nbtClass.getDeclaredConstructor().newInstance();

            Method writeNbt = null;
            try {
                writeNbt = entity.getClass().getMethod("writeNbt", nbtClass);
            } catch (NoSuchMethodException ignored) {
                try {
                    writeNbt = entity.getClass().getMethod("saveNbt", nbtClass);
                } catch (NoSuchMethodException ignored2) {
                    try {
                        writeNbt = entity.getClass().getMethod("writeNbt", nbtClass, boolean.class);
                    } catch (NoSuchMethodException ignored3) {
                        // No compatible NBT writer found for this runtime mapping.
                    }
                }
            }

            if (writeNbt == null) {
                return "{}";
            }

            Object result;
            if (writeNbt.getParameterCount() == 2) {
                result = writeNbt.invoke(entity, nbt, Boolean.TRUE);
            } else {
                result = writeNbt.invoke(entity, nbt);
            }

            Object nbtOut = result != null ? result : nbt;
            String serialized = String.valueOf(nbtOut);
            if (serialized.length() > 1800) {
                return serialized.substring(0, 1800) + "...";
            }
            return serialized;
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static String estimateSide(Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double ax = Math.abs(delta.x);
        double ay = Math.abs(delta.y);
        double az = Math.abs(delta.z);

        if (ay > ax && ay > az) {
            return delta.y >= 0.0 ? "up" : "down";
        }
        if (ax > az) {
            return delta.x >= 0.0 ? "east" : "west";
        }
        return delta.z >= 0.0 ? "south" : "north";
    }

    private record BlockDataEntry(BlockPos position, double distance, String side, String type) {
    }
}
