package com.mwa.n0name.pathfinding;

import com.mwa.n0name.DebugLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Unified pathfinding service for all modules.
 * Replaces ad-hoc AStarPathfinder calls with consistent, validated paths.
 * 
 * Benefits:
 * - Unified fallback logic (no more "goes straight" by accident)
 * - Stricter walkability validation
 * - Path smoothing and optimization
 * - Automatic waypoint fallback for unreachable targets
 * - Consistent behavior across all modules
 */
public class PathfindingService {

    private static final String LOG_MODULE = "PathfindingService";
    
    /** Max total path attempts before giving up */
    private static final int MAX_ATTEMPTS = 4;
    
    /** Radius for finding nearby walkable blocks if exact target unreachable */
    private static final int FALLBACK_SEARCH_RADIUS = 8;
    
    /** How closely to validate the path for wall-clips and invalid sequences */
    private static final int PATH_VALIDATION_STRIDE = 1;  // Check every node
    
    private static final long CACHE_TTL_MS = 3000;  // Cache paths for 3 seconds
    private final Map<Long, CachedPath> pathCache = new LinkedHashMap<>();

    private static class CachedPath {
        List<PathNode> path;
        long timestamp;
        
        CachedPath(List<PathNode> path) {
            this.path = new ArrayList<>(path);
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isValid() {
            return System.currentTimeMillis() - timestamp < CACHE_TTL_MS;
        }
    }

    /**
     * Find a path from 'from' to 'to' with unified fallback strategy.
     * Returns an empty list if no path can be found.
     */
    public static List<PathNode> findPath(World world, BlockPos from, BlockPos to) {
        return findPath(world, from, to, false);
    }

    /**
     * Find a path from 'from' to 'to' with unified fallback strategy.
     * When verboseTrace is true, low-level A* tracing is enabled.
     */
    public static List<PathNode> findPath(World world, BlockPos from, BlockPos to, boolean verboseTrace) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return Collections.emptyList();

        // Validate start/end points are walkable
        if (!WalkabilityChecker.isWalkable(world, from)) {
            BlockPos snappedFrom = findNearestWalkable(world, from, 3);
            if (snappedFrom == null) {
                DebugLogger.log(LOG_MODULE, "Start position " + from.toShortString() + " is not walkable, no nearby fallback");
                return Collections.emptyList();
            }
            DebugLogger.log(LOG_MODULE, "Start snapped from " + from.toShortString() + " to " + snappedFrom.toShortString());
            from = snappedFrom;
        }

        if (!WalkabilityChecker.isWalkable(world, to)) {
            BlockPos snappedTo = findNearestWalkable(world, to, FALLBACK_SEARCH_RADIUS);
            if (snappedTo == null) {
                DebugLogger.log(LOG_MODULE, "Target " + to.toShortString() + " is not walkable, no nearby fallback");
                return Collections.emptyList();
            }
            DebugLogger.log(LOG_MODULE, "Target snapped from " + to.toShortString() + " to " + snappedTo.toShortString());
            to = snappedTo;
        }

        // First try strict block-grid A* (cardinal moves only) to avoid corner cutting in corridors.
        int dist = (int) Math.sqrt(from.getSquaredDistance(to));
        int padding = Math.max(6, Math.min(24, dist / 2 + 4));
        List<BlockPos> grid = scanWalkableGrid(world, from, to, padding, 4);
        List<BlockPos> blockPath = findBlockPath(world, grid, from, to);
        if (!blockPath.isEmpty() && blockPath.size() >= 2) {
            List<PathNode> strictPath = toPathNodes(blockPath);
            if (isValidPath(world, strictPath)) {
                DebugLogger.log(LOG_MODULE, "Found strict grid path (" + strictPath.size() + " nodes)");
                return strictPath;
            }
        }

        // Try direct path first
        List<PathNode> path = findAStarPath(world, from, to, verboseTrace);
        if (!path.isEmpty() && isValidPath(world, path)) {
            DebugLogger.log(LOG_MODULE, "Found direct path (" + path.size() + " nodes)");
            return path;
        }

        // If direct path failed or is invalid, try intermediate waypoints
        List<BlockPos> waypoints = generateWaypoints(world, from, to);
        for (BlockPos waypoint : waypoints) {
            List<PathNode> segment1 = findAStarPath(world, from, waypoint, verboseTrace);
            if (segment1.isEmpty() || !isValidPath(world, segment1)) continue;

            List<PathNode> segment2 = findAStarPath(world, waypoint, to, verboseTrace);
            if (segment2.isEmpty() || !isValidPath(world, segment2)) continue;

            // Merge segments, removing duplicate waypoint
            List<PathNode> merged = new ArrayList<>(segment1);
            merged.addAll(segment2.subList(1, segment2.size()));
            
            DebugLogger.log(LOG_MODULE, "Found waypoint path via " + waypoint.toShortString() 
                + " (" + merged.size() + " nodes)");
            return merged;
        }

        // Last resort: step-by-step with subgoals
        path = findStepByStepPath(world, from, to, verboseTrace);
        if (!path.isEmpty()) {
            DebugLogger.log(LOG_MODULE, "Found step-by-step path (" + path.size() + " nodes)");
            return path;
        }

        DebugLogger.log(LOG_MODULE, "Failed to find any path from " + from.toShortString() 
            + " to " + to.toShortString());
        return Collections.emptyList();
    }

    /**
     * Find a block-by-block path on a pre-scanned grid of walkable blocks.
     */
    public static List<BlockPos> findBlockPath(World world, Collection<BlockPos> scannedBlocks, BlockPos from, BlockPos to) {
        return BlockPosPathfinder.findPath(world, scannedBlocks, from, to);
    }

    /**
     * Unified random walkable target selection for roaming modules.
     */
    public static BlockPos findRandomWalkableTarget(World world, BlockPos origin, int radius, Random random) {
        return AStarPathfinder.findRandomWalkableTarget(world, origin, radius, random);
    }

    /**
     * Convert BlockPos path to PathNode path for compatibility with existing modules/renderers.
     */
    public static List<PathNode> toPathNodes(List<BlockPos> blockPath) {
        if (blockPath == null || blockPath.isEmpty()) {
            return Collections.emptyList();
        }

        List<PathNode> out = new ArrayList<>(blockPath.size());
        for (BlockPos pos : blockPath) {
            out.add(new PathNode(pos));
        }
        return out;
    }

    /**
     * Scan a bounded walkable grid around a source->destination corridor.
     */
    public static List<BlockPos> scanWalkableGrid(World world, BlockPos from, BlockPos to, int padding, int verticalRange) {
        if (world == null || from == null || to == null) {
            return Collections.emptyList();
        }

        int minX = Math.min(from.getX(), to.getX()) - Math.max(0, padding);
        int maxX = Math.max(from.getX(), to.getX()) + Math.max(0, padding);
        int minY = Math.min(from.getY(), to.getY()) - Math.max(0, verticalRange);
        int maxY = Math.max(from.getY(), to.getY()) + Math.max(0, verticalRange);
        int minZ = Math.min(from.getZ(), to.getZ()) - Math.max(0, padding);
        int maxZ = Math.max(from.getZ(), to.getZ()) + Math.max(0, padding);

        List<BlockPos> out = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (WalkabilityChecker.isWalkable(world, pos)) {
                        out.add(pos.toImmutable());
                    }
                }
            }
        }
        return out;
    }

    /**
     * Validate that a path doesn't clip through walls or have invalid transitions.
     * Uses stricter validation to prevent "walking into walls" behavior.
     */
    private static boolean isValidPath(World world, List<PathNode> path) {
        if (path.size() < 2) return true;

        // Check consecutive node transitions
        for (int i = 0; i < path.size() - 1; i++) {
            BlockPos from = path.get(i).toBlockPos();
            BlockPos to = path.get(i + 1).toBlockPos();

            // Validate target is walkable
            if (!WalkabilityChecker.isWalkable(world, to)) {
                DebugLogger.log(LOG_MODULE, "Path validation failed: destination " 
                    + to.toShortString() + " is not walkable");
                return false;
            }

            // Check if transition is valid
            if (!WalkabilityChecker.canTraverse(world, from, to)) {
                DebugLogger.log(LOG_MODULE, "Path validation failed: invalid transition from " 
                    + from.toShortString() + " to " + to.toShortString());
                return false;
            }
            
            // Additional check: verify no intermediate blocks between from/to are solid walls
            int dx = Integer.compare(to.getX(), from.getX());
            int dy = Integer.compare(to.getY(), from.getY());
            int dz = Integer.compare(to.getZ(), from.getZ());
            
            // Only check if diagonal movement would clip through corners
            if (dx != 0 && dz != 0) {
                BlockPos diagCheck1 = from.add(dx, 0, 0);
                BlockPos diagCheck2 = from.add(0, 0, dz);
                if (!WalkabilityChecker.isWalkable(world, diagCheck1) || !WalkabilityChecker.isWalkable(world, diagCheck2)) {
                    DebugLogger.log(LOG_MODULE, "Path validation failed: diagonal movement blocks at " 
                        + diagCheck1.toShortString() + " or " + diagCheck2.toShortString());
                    return false;
                }
            }
        }

        // Validate final destination
        BlockPos destination = path.get(path.size() - 1).toBlockPos();
        if (!WalkabilityChecker.isWalkable(world, destination)) {
            DebugLogger.log(LOG_MODULE, "Path validation failed: final destination " 
                + destination.toShortString() + " is not walkable");
            return false;
        }

        return true;
    }

    /**
     * Generate intermediate waypoints to navigate around obstacles.
     */
    private static List<BlockPos> generateWaypoints(World world, BlockPos from, BlockPos to) {
        List<BlockPos> waypoints = new ArrayList<>();
        
        // Get the direction vector
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        if (dist < 1.0) return waypoints;
        
        // Try waypoints at different distances
        for (double frac = 0.25; frac < 1.0; frac += 0.25) {
            BlockPos waypoint = BlockPos.ofFloored(
                from.getX() + dx * frac,
                from.getY(),
                from.getZ() + dz * frac
            );
            
            BlockPos snapped = findNearestWalkable(world, waypoint, 3);
            if (snapped != null && !snapped.equals(from) && !snapped.equals(to)) {
                waypoints.add(snapped);
            }
        }
        
        // Also try perpendicular offsets
        double perpX = -dz / dist;
        double perpZ = dx / dist;
        for (int offset = 2; offset <= 4; offset += 2) {
            BlockPos perpWaypoint = BlockPos.ofFloored(
                from.getX() + dx * 0.5 + perpX * offset,
                from.getY(),
                from.getZ() + dz * 0.5 + perpZ * offset
            );
            BlockPos snapped = findNearestWalkable(world, perpWaypoint, 3);
            if (snapped != null && !snapped.equals(from) && !snapped.equals(to)) {
                waypoints.add(snapped);
            }
        }
        
        return waypoints;
    }

    /**
     * Step-by-step path finding for complex terrain.
     * Creates intermediate waypoints all the way to destination (not partial paths).
     */
    private static List<PathNode> findStepByStepPath(World world, BlockPos from, BlockPos to, boolean verboseTrace) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        if (dist < 1.0) return Collections.emptyList();
        
        double dirX = dx / dist;
        double dirZ = dz / dist;
        
        // Try different step sizes to create intermediate waypoints
        for (int stepSize = 12; stepSize >= 4; stepSize -= 2) {
            List<PathNode> fullPath = new ArrayList<>();
            BlockPos current = from;
            
            // Build complete path using intermediate waypoints
            while (!current.equals(to)) {
                BlockPos projected = BlockPos.ofFloored(
                    current.getX() + dirX * stepSize,
                    current.getY() + dy * Math.min(1.0, stepSize / dist),
                    current.getZ() + dirZ * stepSize
                );
                
                // Clamp to destination if too close
                if (projected.getSquaredDistance(from) >= to.getSquaredDistance(from)) {
                    projected = to;
                }
                
                BlockPos stepTarget = findNearestWalkable(world, projected, 4);
                if (stepTarget == null || stepTarget.equals(current)) break;
                
                List<PathNode> segment = findAStarPath(world, current, stepTarget, verboseTrace);
                if (segment.isEmpty() || !isValidPath(world, segment)) break;
                
                // Add segment (skip first node if not first iteration)
                if (fullPath.isEmpty()) {
                    fullPath.addAll(segment);
                } else {
                    fullPath.addAll(segment.subList(1, segment.size()));
                }
                
                current = stepTarget;
                
                if (current.equals(to)) {
                    DebugLogger.log(LOG_MODULE, "Found step-by-step complete path with stepSize=" + stepSize);
                    return fullPath;
                }
            }
        }
        
        return Collections.emptyList();
    }

    private static List<PathNode> findAStarPath(World world, BlockPos from, BlockPos to, boolean verboseTrace) {
        return AStarPathfinder.findPath(world, from, to, false, verboseTrace);
    }

    /**
     * Find the nearest walkable block within radius from position.
     */
    private static BlockPos findNearestWalkable(World world, BlockPos around, int radius) {
        List<BlockPos> candidates = new ArrayList<>();
        
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // border only
                    
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos candidate = around.add(dx, dy, dz);
                        if (WalkabilityChecker.isWalkable(world, candidate)) {
                            candidates.add(candidate);
                        }
                    }
                }
            }
            
            if (!candidates.isEmpty()) {
                // Sort by distance
                final BlockPos center = around;
                candidates.sort((a, b) -> Double.compare(a.getSquaredDistance(center), b.getSquaredDistance(center)));
                return candidates.get(0);
            }
        }
        
        return null;
    }
}
