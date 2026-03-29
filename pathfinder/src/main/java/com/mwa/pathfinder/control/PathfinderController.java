package com.mwa.pathfinder.control;

import com.mwa.pathfinder.pathing.IPathManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public class PathfinderController {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final IPathManager pathManager;

    private BlockPos lastTarget;
    private String lastEntityTarget = "none";
    private String followTarget = "none";
    private boolean ignoreY;
    private boolean cameraSync;

    public PathfinderController(IPathManager pathManager) {
        this.pathManager = pathManager;
        this.pathManager.configureDefaults();
    }

    public void tick() {
        pathManager.tick();

        if (!cameraSync || !pathManager.isPathing() || client.player == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        float yaw = pathManager.getTargetYaw();
        float pitch = pathManager.getTargetPitch();
        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
    }

    public boolean pathToLookTarget() {
        if (client.player == null || client.crosshairTarget == null) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            return pathToLookEntity();
        }

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            sendMessage("Look at a block or entity first.");
            return false;
        }

        BlockPos target = ((BlockHitResult) hitResult).getBlockPos();
        lastTarget = target;
        pathManager.moveTo(target, ignoreY);
        sendMessage("Pathing to " + target.toShortString() + (ignoreY ? " (XZ)" : ""));
        return true;
    }

    public boolean pathToLookEntity() {
        Entity entity = getLookedAtEntity();
        if (entity == null) {
            sendMessage("Look at an entity first.");
            return false;
        }

        lastEntityTarget = entity.getName().getString();
        lastTarget = entity.getBlockPos().toImmutable();
        pathManager.moveTo(lastTarget, ignoreY);
        sendMessage("Pathing to entity " + lastEntityTarget + " at " + lastTarget.toShortString());
        return true;
    }

    public boolean followLookEntity() {
        Entity entity = getLookedAtEntity();
        if (entity == null) {
            sendMessage("Look at an entity first.");
            return false;
        }

        followTarget = entity.getName().getString();
        lastEntityTarget = followTarget;
        lastTarget = entity.getBlockPos().toImmutable();
        pathManager.followEntity(entity.getUuid());
        sendMessage("Following entity " + followTarget);
        return true;
    }

    public void pathTo(BlockPos target, boolean ignoreY) {
        lastTarget = target;
        this.ignoreY = ignoreY;
        pathManager.moveTo(target, ignoreY);
        sendMessage("Pathing to " + target.toShortString() + (ignoreY ? " (XZ)" : ""));
    }

    public void pathForward() {
        if (client.player == null) {
            return;
        }

        pathManager.moveInDirection(client.player.getYaw());
        sendMessage("Pathing in facing direction.");
    }

    public void stop() {
        pathManager.stop();
        followTarget = "none";
        sendMessage("Pathing stopped.");
    }

    public boolean toggleIgnoreY() {
        ignoreY = !ignoreY;
        sendMessage("Ignore Y: " + (ignoreY ? "ON" : "OFF"));
        return ignoreY;
    }

    public boolean toggleCameraSync() {
        cameraSync = !cameraSync;
        sendMessage("Camera sync: " + (cameraSync ? "ON" : "OFF"));
        return cameraSync;
    }

    public boolean isCameraSync() {
        return cameraSync;
    }

    public boolean isIgnoreY() {
        return ignoreY;
    }

    public void setCameraSync(boolean value) {
        cameraSync = value;
    }

    public void setIgnoreY(boolean value) {
        ignoreY = value;
    }

    public BlockPos getLastTarget() {
        return lastTarget;
    }

    public String getFollowTargetName() {
        return followTarget;
    }

    public String getLastEntityTargetName() {
        return lastEntityTarget;
    }

    public void renderHud(DrawContext context) {
        int x = 6;
        int y = 6;
        int color = 0xFFFFFFFF;
        context.drawTextWithShadow(client.textRenderer, "Path Manager: " + pathManager.getName(), x, y, color);
        y += 10;
        context.drawTextWithShadow(client.textRenderer, "Pathing: " + (pathManager.isPathing() ? "YES" : "NO"), x, y, color);
        y += 10;
        context.drawTextWithShadow(client.textRenderer, "Goal: " + (lastTarget == null ? "none" : lastTarget.toShortString()), x, y, color);
        y += 10;
        context.drawTextWithShadow(client.textRenderer, "Entity Target: " + lastEntityTarget, x, y, color);
        y += 10;
        context.drawTextWithShadow(client.textRenderer, "Following: " + (pathManager.isFollowing() ? followTarget : "none"), x, y, color);
        y += 10;
        context.drawTextWithShadow(client.textRenderer, "Baritone Goal: " + pathManager.getCurrentGoalText(), x, y, color);
        y += 10;
        context.drawTextWithShadow(client.textRenderer, "Camera Sync: " + (cameraSync ? "ON" : "OFF"), x, y, color);
        y += 10;
        context.drawTextWithShadow(client.textRenderer, "Ignore Y: " + (ignoreY ? "ON" : "OFF"), x, y, color);
        y += 10;
        context.drawTextWithShadow(client.textRenderer, "Keys: P look target, J look entity, H follow entity, I forward, O stop, K camera, Y ignoreY, U settings", x, y, color);
    }

    private Entity getLookedAtEntity() {
        if (!(client.crosshairTarget instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        return entityHitResult.getEntity();
    }

    private void sendMessage(String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Pathfinder] " + message), false);
        }
    }
}
