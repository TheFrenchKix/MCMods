package lfmdevelopment.lfmclient.systems.modules.macro.pathfinding;

import lfmdevelopment.lfmclient.lfmClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PathFollower {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private Path path;
    private final StuckDetector stuckDetector = new StuckDetector(40);
    private boolean finished;
    private boolean failed;
    private double tolerance = 0.3;
    private int repathAttempts = 0;
    private int maxRepaths = 3;
    private BlockPos destination;

    public void start(Path path, BlockPos destination, double tolerance, int maxRetries) {
        this.path = path;
        this.destination = destination;
        this.tolerance = tolerance;
        this.maxRepaths = maxRetries;
        this.repathAttempts = 0;
        this.finished = false;
        this.failed = false;
        stuckDetector.reset();
    }

    public void stop() {
        this.path = null;
        this.finished = true;
        stopMovement();
    }

    public FollowResult tick() {
        if (path == null || finished || failed) return FollowResult.IDLE;

        ClientPlayerEntity player = mc.player;
        if (player == null) return FollowResult.IDLE;

        BlockPos playerBlock = player.getBlockPos();
        BlockPos target = path.current();
        if (target == null) {
            finished = true;
            stopMovement();
            return FollowResult.COMPLETE;
        }

        // Check if stuck
        if (stuckDetector.update(playerBlock)) {
            stuckDetector.reset();
            return handleStuck(playerBlock);
        }

        double dist = horizontalDistance(new Vec3d(player.getX(), player.getY(), player.getZ()), Vec3d.ofCenter(target));

        // Check if we reached the current node
        if (dist < tolerance && Math.abs(player.getY() - target.getY()) < 1.5) {
            if (!path.advance()) {
                finished = true;
                stopMovement();
                return FollowResult.COMPLETE;
            }
            target = path.current();
            if (target == null) {
                finished = true;
                stopMovement();
                return FollowResult.COMPLETE;
            }
        }

        // Move toward target
        moveToward(player, target);

        return FollowResult.MOVING;
    }

    private void moveToward(ClientPlayerEntity player, BlockPos target) {
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double dx = targetCenter.x - player.getX();
        double dz = targetCenter.z - player.getZ();
        double dy = target.getY() - player.getY();

        // Smooth rotation
        float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float currentYaw = player.getYaw();
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float smoothYaw = currentYaw + MathHelper.clamp(yawDiff, -10.0f, 10.0f);
        player.setYaw(smoothYaw);

        // Movement
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        // Sprint if far enough
        player.setSprinting(horizontalDist > 4.0);

        // Apply movement input via key bindings for current client input API.
        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);

        // Jump if needed (target is higher)
        if (dy > 0.5 && player.isOnGround()) {
            player.jump();
        }
    }

    private FollowResult handleStuck(BlockPos playerPos) {
        repathAttempts++;
        if (repathAttempts > maxRepaths) {
            failed = true;
            stopMovement();
            lfmClient.LOG.warn("PathFollower: max repath attempts reached, failing");
            return FollowResult.FAILED;
        }

        lfmClient.LOG.info("PathFollower: stuck detected, repathing (attempt {})", repathAttempts);

        if (destination == null) {
            failed = true;
            stopMovement();
            return FollowResult.FAILED;
        }

        Path newPath = AStarPathfinder.findPath(playerPos, destination);
        if (newPath == null) {
            failed = true;
            stopMovement();
            lfmClient.LOG.warn("PathFollower: repath failed, no path found");
            return FollowResult.FAILED;
        }

        this.path = newPath;
        return FollowResult.REPATHING;
    }

    private void stopMovement() {
        if (mc.player != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            mc.player.setSprinting(false);
        }
    }

    private double horizontalDistance(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public boolean isFinished() { return finished; }
    public boolean isFailed() { return failed; }
    public Path getPath() { return path; }

    public enum FollowResult {
        IDLE,
        MOVING,
        REPATHING,
        COMPLETE,
        FAILED
    }
}
