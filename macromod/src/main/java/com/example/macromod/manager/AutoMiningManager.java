package com.example.macromod.manager;

import com.example.macromod.MacroModClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Auto Mining Manager - async 3-layer pipeline (Scanner -> Planner -> Executor).
 *
 * Scanner  - background thread, scans nearby blocks every SCAN_INTERVAL_MS ms.
 * Planner  - background thread, validates and creates MiningTask objects.
 * Executor - main thread (game tick), pops tasks, raycasts, mines.
 *
 * The main thread is NEVER blocked by scanning or planning.
 */
@Environment(EnvType.CLIENT)
public class AutoMiningManager {

    // Singleton
    private static AutoMiningManager INSTANCE;

    public static AutoMiningManager getInstance() {
        if (INSTANCE == null) INSTANCE = new AutoMiningManager();
        return INSTANCE;
    }

    private AutoMiningManager() {}

    // Task definition
    private record MiningTask(BlockPos target, Block blockType) {}

    // Pipeline queues
    private final ConcurrentLinkedDeque<MiningTask> taskQueue  = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedQueue<BlockPos>   scanBuffer = new ConcurrentLinkedQueue<>();
    private final Set<BlockPos> queuedBlocks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Background threads
    private ScheduledExecutorService scanScheduler  = null;
    private ExecutorService          plannerExecutor = null;
    private final AtomicBoolean      plannerBusy    = new AtomicBoolean(false);

    // Volatile shared state (written on main thread, read by bg threads)
    private volatile double      playerX, playerY, playerZ;
    private volatile ClientWorld worldRef;

    // Executor state (main thread only)
    private boolean    enabled        = false;
    private MiningTask currentTask    = null;
    private int        miningTicks    = 0;
    private boolean    moving         = false;
    private int        idleTicks      = 0;
    /** True only when WE programmatically pressed the forward key — never release it unless we pressed it. */
    private boolean    pressingForward = false;
    /** Monotonic tick counter shared (volatile) with scanner thread for blacklist expiry. */
    private volatile long currentTick = 0L;
    /** Positions that hit the mining failsafe — scanner skips them until expiry tick. */
    private final java.util.concurrent.ConcurrentHashMap<BlockPos, Long> miningBlacklist
            = new java.util.concurrent.ConcurrentHashMap<>();

    // Constants
    private static final double MAX_DIST_SQ      = 4.5 * 4.5;
    private static final int    SCAN_RADIUS       = 8;
    private static final int    MIN_QUEUE_SIZE    = 5;
    private static final int    MAX_QUEUE_SIZE    = 40;
    private static final int    MAX_MINING_TICKS  = 15;
    private static final int    MAX_MOVE_TICKS    = 120;
    private static final long   SCAN_INTERVAL_MS  = 500L;

    // Public API

    public boolean isEnabled() { return enabled; }

    public void toggle() { if (enabled) disable(); else enable(); }

    public void enable() {
        if (enabled) return;
        enabled        = true;
        currentTask    = null;
        miningTicks    = 0;
        moving         = false;
        idleTicks      = 0;
        pressingForward = false;
        taskQueue.clear();
        scanBuffer.clear();
        queuedBlocks.clear();
        miningBlacklist.clear();
        startPipeline();
    }

    public void disable() {
        if (!enabled) return;
        enabled = false;
        stopPipeline();
        taskQueue.clear();
        scanBuffer.clear();
        queuedBlocks.clear();
        miningBlacklist.clear();
        currentTask     = null;
        pressingForward = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) releaseAll(client);
    }

    // Pipeline management

    private void startPipeline() {
        stopPipeline();

        scanScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutoMining-Scanner");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        plannerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AutoMining-Planner");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        scanScheduler.scheduleAtFixedRate(this::runScanner, 0L, SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopPipeline() {
        if (scanScheduler  != null) { scanScheduler.shutdownNow();  scanScheduler  = null; }
        if (plannerExecutor != null) { plannerExecutor.shutdownNow(); plannerExecutor = null; }
    }

    // SCANNER (background thread)

    private void runScanner() {
        if (!enabled) return;
        if (taskQueue.size() >= MAX_QUEUE_SIZE) return;

        ClientWorld world = worldRef;
        if (world == null) return;

        double px = playerX, py = playerY, pz = playerZ;
        BlockPos origin = BlockPos.ofFloored(px, py, pz);

        List<String> whitelist = getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) return;
        Set<String> whitelistSet = new HashSet<>(whitelist);

        List<BlockPos> candidates = new ArrayList<>();
        try {
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                    for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                        if (!enabled) return;
                        BlockPos p = origin.add(dx, dy, dz);
                        if (queuedBlocks.contains(p)) continue;
                        Long blExpiry = miningBlacklist.get(p);
                        if (blExpiry != null && blExpiry > currentTick) continue;
                        BlockState bs = world.getBlockState(p);
                        if (bs.isAir()) continue;
                        String id = Registries.BLOCK.getId(bs.getBlock()).toString();
                        if (!whitelistSet.contains(id)) continue;
                        candidates.add(p);
                    }
                }
            }
        } catch (Exception ignored) {
            return;
        }

        if (candidates.isEmpty()) return;

        candidates.sort(Comparator.comparingDouble(p ->
                (p.getX() + 0.5 - px) * (p.getX() + 0.5 - px)
              + (p.getY() + 0.5 - py) * (p.getY() + 0.5 - py)
              + (p.getZ() + 0.5 - pz) * (p.getZ() + 0.5 - pz)));

        int headroom = MAX_QUEUE_SIZE - taskQueue.size();
        int limit    = Math.min(candidates.size(), headroom + 10);
        for (int i = 0; i < limit; i++) {
            scanBuffer.offer(candidates.get(i));
        }

        triggerPlanner();
    }

    // PLANNER (background thread)

    private void triggerPlanner() {
        if (plannerExecutor == null || plannerExecutor.isShutdown()) return;
        if (!plannerBusy.compareAndSet(false, true)) return;
        plannerExecutor.submit(() -> {
            try { runPlanner(); } finally { plannerBusy.set(false); }
        });
    }

    private void runPlanner() {
        ClientWorld world = worldRef;
        if (world == null) return;

        List<String> whitelist = getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) return;
        Set<String> whitelistSet = new HashSet<>(whitelist);

        BlockPos candidate;
        while ((candidate = scanBuffer.poll()) != null) {
            if (!enabled) break;
            if (taskQueue.size() >= MAX_QUEUE_SIZE) break;
            if (queuedBlocks.contains(candidate)) continue;

            try {
                BlockState bs = world.getBlockState(candidate);
                if (bs.isAir()) continue;
                String id = Registries.BLOCK.getId(bs.getBlock()).toString();
                if (!whitelistSet.contains(id)) continue;

                taskQueue.offer(new MiningTask(candidate, bs.getBlock()));
                queuedBlocks.add(candidate);
            } catch (Exception ignored) {}
        }
    }

    // EXECUTOR (main thread, called each tick)

    public void tick() {
        if (!enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld        world  = client.world;
        if (player == null || world == null || client.currentScreen != null) return;

        // Update volatile snapshot for background threads
        playerX  = player.getX();
        playerY  = player.getY();
        playerZ  = player.getZ();
        worldRef = world;
        currentTick++;

        // Retrigger planner if queue is low and there is work buffered
        if (taskQueue.size() < MIN_QUEUE_SIZE && !scanBuffer.isEmpty()) {
            triggerPlanner();
        }

        // Fetch next task when idle
        if (currentTask == null) {
            currentTask = taskQueue.poll();
            if (currentTask != null) {
                // Keep pos in queuedBlocks until execution finishes — prevents scanner
                // from re-queuing the same position while we are still mining it.
                miningTicks = 0;
                moving      = false;
                idleTicks   = 0;
            } else {
                idleTicks++;
                // Do NOT call releaseAll here — that would cancel the player's own W key.
                // Only release the attack key we may have pressed.
                releaseAttack(client);
                return;
            }
        }

        idleTicks = 0;
        executeTask(client, player, world);
    }

    private void executeTask(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        MiningTask task   = currentTask;
        BlockPos   target = task.target();

        // 1. Block type check — instant discard on desync (Hypixel fix)
        BlockState bs = world.getBlockState(target);
        if (bs.isAir() || bs.getBlock() != task.blockType()) {
            discardCurrent(client, false);
            return;
        }

        double distSq = player.squaredDistanceTo(Vec3d.ofCenter(target));

        // 2. Too far — move forward only, never strafe
        if (distSq > MAX_DIST_SQ) {
            releaseAttack(client);
            moving = true;
            rotateTo(player, Vec3d.ofCenter(target));
            if (!pressingForward) {
                client.options.forwardKey.setPressed(true);
                pressingForward = true;
            }
            miningTicks++;
            if (miningTicks > MAX_MOVE_TICKS) {
                discardCurrent(client, false);
            }
            return;
        }

        // 3. In reach — stop any movement WE started
        if (moving) {
            if (pressingForward) {
                client.options.forwardKey.setPressed(false);
                pressingForward = false;
            }
            player.setSprinting(false);
            moving      = false;
            miningTicks = 0;
        }

        // 4. Raycast for visible face (fast, <=6 rays)
        FaceHit face = findVisibleFace(player, world, target);
        if (face == null) {
            discardCurrent(client, false);
            return;
        }

        // 5. Failsafe — block not breaking; blacklist position so scanner skips it
        miningTicks++;
        if (miningTicks > MAX_MINING_TICKS) {
            discardCurrent(client, true);
            return;
        }

        // 6. Look and mine
        player.setSprinting(false);
        rotateTo(player, face.point());
        client.interactionManager.attackBlock(target, face.direction());
    }

    /**
     * Abandons the current task.
     * @param blacklist  if true, the block position is blacklisted for 15 s so the
     *                   scanner will not re-queue it immediately (used on failsafe only).
     */
    private void discardCurrent(MinecraftClient client, boolean blacklist) {
        if (currentTask != null) {
            if (blacklist) {
                miningBlacklist.put(currentTask.target(), currentTick + 300L);
            }
            queuedBlocks.remove(currentTask.target());
        }
        if (pressingForward) {
            if (client.options != null) client.options.forwardKey.setPressed(false);
            pressingForward = false;
        }
        releaseAttack(client);
        if (client.player != null) client.player.setSprinting(false);
        currentTask = null;
        miningTicks = 0;
        moving      = false;
    }

    // Visible face detection (main thread)

    /**
     * Raycasts from the player's eye to the block center to verify direct line-of-sight
     * (no obstacles like trees blocking it). If the block center is visible, returns
     * the hit face direction; otherwise returns null.
     *
     * This prevents mining blocks that are hidden behind other blocks/trees.
     */
    private FaceHit findVisibleFace(ClientPlayerEntity player, ClientWorld world, BlockPos pos) {
        Vec3d eyes = player.getCameraPosVec(1.0f);
        Vec3d blockCenter = Vec3d.ofCenter(pos);

        // Raycast to block center to verify nothing blocks the line of sight.
        // Use COLLIDER to detect solid blocks in the way (trees, etc).
        BlockHitResult centralHit = world.raycast(new RaycastContext(
                eyes, blockCenter,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player));

        // If we didn't hit our target block at the center, it's obstructed by something in front.
        if (centralHit.getType() != HitResult.Type.BLOCK
                || !centralHit.getBlockPos().equals(pos)) {
            return null;  // Block is behind an obstacle; skip it.
        }

        // Block center is visible — use the hit direction as the mining face.
        // This ensures we're always mining from a direction that has a clear line of sight.
        return new FaceHit(centralHit.getSide(), blockCenter);
    }

    // Helpers

    private List<String> getWhitelist() {
        try {
            return MacroModClient.getConfigManager().getConfig().getBlockWhitelist();
        } catch (Exception e) {
            return null;
        }
    }

    private void rotateTo(ClientPlayerEntity player, Vec3d target) {
        Vec3d  eyes = player.getCameraPosVec(1.0f);
        double dx   = target.x - eyes.x;
        double dy   = target.y - eyes.y;
        double dz   = target.z - eyes.z;
        double hd   = Math.sqrt(dx * dx + dz * dz);
        player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        player.setPitch((float) -Math.toDegrees(Math.atan2(dy, hd)));
    }

    private void releaseAttack(MinecraftClient client) {
        if (client.options != null) client.options.attackKey.setPressed(false);
    }

    private void releaseAll(MinecraftClient client) {
        if (client.options != null) {
            client.options.attackKey.setPressed(false);
            // Only cancel forward if WE pressed it — never cancel the player's own input.
            if (pressingForward) {
                client.options.forwardKey.setPressed(false);
                pressingForward = false;
            }
        }
        if (client.player != null) client.player.setSprinting(false);
    }

    // Data carriers
    private record FaceHit(Direction direction, Vec3d point) {}
}