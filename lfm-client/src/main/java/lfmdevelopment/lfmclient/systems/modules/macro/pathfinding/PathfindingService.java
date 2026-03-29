package lfmdevelopment.lfmclient.systems.modules.macro.pathfinding;

import lfmdevelopment.lfmclient.lfmClient;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PathfindingService {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LFM-Pathfinder");
        t.setDaemon(true);
        return t;
    });

    private PathfindingService() {}

    public static CompletableFuture<Path> findPathAsync(BlockPos start, BlockPos goal) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            Path result = AStarPathfinder.findPath(start, goal);
            long elapsed = (System.nanoTime() - startTime) / 1_000_000;
            if (result != null) {
                lfmClient.LOG.info("Path found: {} nodes in {}ms", result.size(), elapsed);
            } else {
                lfmClient.LOG.warn("No path found from {} to {} ({}ms)", start.toShortString(), goal.toShortString(), elapsed);
            }
            return result;
        }, EXECUTOR);
    }

    public static Path findPathSync(BlockPos start, BlockPos goal) {
        return AStarPathfinder.findPath(start, goal);
    }
}
