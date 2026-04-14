package com.example.macromod.pathfinding;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-processes an A* path ({@code List<BlockPos>}) in three passes:
 * <ol>
 *   <li>{@link #smooth} — greedy collinearity pass: collapses zig-zag runs where
 *       intermediate nodes deviate less than 0.75 blocks from the straight
 *       anchor-to-candidate line.</li>
 *   <li>{@link #losSmooth} — greedy Bresenham LOS pass: skips nodes when the
 *       full 3D voxel corridor from anchor to candidate is walkable.</li>
 *   <li>{@link #capNodes} — uniform sampling pass: when the path still exceeds
 *       a maximum node count, evenly samples it down for smoother movement.</li>
 * </ol>
 * Usage: {@code PathSmoother.fullSmooth(raw, world)} or individual passes.
 */
public final class PathSmoother {

    private PathSmoother() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod-pathfinder");

    /** Squared perpendicular distance threshold for the collinearity check (0.75² = 0.5625). */
    private static final double COLLINEAR_THRESHOLD_SQ = 0.75 * 0.75;

    /** Maximum waypoints per step. Paths longer than this are uniformly sampled down. */
    private static final int MAX_WAYPOINTS_PER_STEP = 20;

    /**
     * Runs all three passes in sequence: collinearity → LOS → cap.
     * This is the preferred entry point for path smoothing.
     *
     * @param raw   raw A* path
     * @param world block access for LOS checks
     * @return optimally reduced waypoint list
     */
    public static List<BlockPos> fullSmooth(List<BlockPos> raw, BlockView world) {
        int rawSize = raw.size();
        List<BlockPos> pass1 = smooth(raw);
        List<BlockPos> pass2 = losSmooth(pass1, world);
        List<BlockPos> pass3 = capNodes(pass2);
        LOGGER.info("PathSmoother: {} raw → {} collinear → {} LOS → {} capped",
                rawSize, pass1.size(), pass2.size(), pass3.size());
        return pass3;
    }

    /**
     * Collinearity-based greedy pass.  Starting from each anchor, extends the
     * current segment as far as possible while all intermediate nodes stay within
     * {@link #COLLINEAR_THRESHOLD_SQ} of the straight XZ line from anchor to
     * candidate.
     *
     * <p>Segment extension stops when deltaY &gt; 1 or the threshold is exceeded.
     * All intermediate nodes must share the same Y level as the anchor.</p>
     *
     * @param path raw A* path
     * @return reduced waypoint list
     */
    public static List<BlockPos> smooth(List<BlockPos> path) {
        if (path.size() < 3) return path;

        List<BlockPos> result = new ArrayList<>();
        result.add(path.get(0));

        int anchor = 0;
        while (anchor < path.size() - 1) {
            int farthest = anchor + 1;

            for (int candidate = anchor + 2; candidate < path.size(); candidate++) {
                BlockPos a = path.get(anchor);
                BlockPos c = path.get(candidate);

                if (Math.abs(c.getY() - a.getY()) > 1) break;

                if (allNearLine(path, anchor, candidate)) {
                    farthest = candidate;
                } else {
                    break;
                }
            }

            result.add(path.get(farthest));
            anchor = farthest;
        }

        return result;
    }

    /**
     * Returns true when every intermediate node (strictly between anchorIdx and
     * candidateIdx) lies within {@link #COLLINEAR_THRESHOLD_SQ} of the XZ line
     * from anchor to candidate, and shares the anchor's Y level.
     */
    private static boolean allNearLine(List<BlockPos> path, int anchorIdx, int candidateIdx) {
        if (candidateIdx <= anchorIdx + 1) return true;

        BlockPos a = path.get(anchorIdx);
        BlockPos c = path.get(candidateIdx);
        double dx    = c.getX() - a.getX();
        double dz    = c.getZ() - a.getZ();
        double lenSq = dx * dx + dz * dz;

        if (lenSq < 0.001) return true;

        for (int k = anchorIdx + 1; k < candidateIdx; k++) {
            BlockPos p = path.get(k);

            if (p.getY() != a.getY()) return false;

            double px    = p.getX() - a.getX();
            double pz    = p.getZ() - a.getZ();
            double cross = px * dz - pz * dx;
            double devSq = (cross * cross) / lenSq;

            if (devSq > COLLINEAR_THRESHOLD_SQ) return false;
        }
        return true;
    }

    /**
     * Greedy Bresenham LOS pass.  For each anchor, skips as many subsequent nodes
     * as possible while the 3D integer-voxel line is fully walkable
     * (feet + head passable, solid ground below every voxel).
     *
     * <p>Forces a required waypoint when {@code abs(deltaY) &gt; 2} or when any
     * voxel along the line is blocked.</p>
     *
     * @param path  collinearity-smoothed path
     * @param world block access — safe off-thread via {@link BlockView}
     * @return further reduced waypoint list
     */
    public static List<BlockPos> losSmooth(List<BlockPos> path, BlockView world) {
        if (path.size() < 3) return path;

        List<BlockPos> result = new ArrayList<>();
        result.add(path.get(0));

        int anchor = 0;
        while (anchor < path.size() - 1) {
            int farthest = anchor + 1;

            for (int candidate = anchor + 2; candidate < path.size(); candidate++) {
                BlockPos a = path.get(anchor);
                BlockPos c = path.get(candidate);

                if (Math.abs(c.getY() - a.getY()) > 2) break;

                if (hasLOS(world, a, c)) {
                    farthest = candidate;
                } else {
                    break;
                }
            }

            result.add(path.get(farthest));
            anchor = farthest;
        }

        return result;
    }

    /**
     * 3D Bresenham LOS: walks every integer voxel on the line from {@code from}
     * to {@code to}, verifying feet+head passable and solid ground below each.
     */
    private static boolean hasLOS(BlockView world, BlockPos from, BlockPos to) {
        int x = from.getX(), y = from.getY(), z = from.getZ();
        int dx = to.getX() - x, dy = to.getY() - y, dz = to.getZ() - z;
        int sx = Integer.signum(dx), sy = Integer.signum(dy), sz = Integer.signum(dz);
        int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);

        int max = Math.max(ax, Math.max(ay, az));
        if (max == 0) return true;

        int errX = 2 * ax - max;
        int errY = 2 * ay - max;
        int errZ = 2 * az - max;

        for (int i = 0; i < max; i++) {
            if (errX > 0) { x += sx; errX -= 2 * max; }
            if (errY > 0) { y += sy; errY -= 2 * max; }
            if (errZ > 0) { z += sz; errZ -= 2 * max; }
            errX += 2 * ax;
            errY += 2 * ay;
            errZ += 2 * az;

            BlockPos pos = new BlockPos(x, y, z);
            if (!isPassable(world, pos) || !isPassable(world, pos.up())) return false;
            if (!isSolid(world, pos.down())) return false;
        }
        return true;
    }

    private static boolean isPassable(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isSolidBlock(world, pos) && state.getFluidState().isEmpty();
    }

    private static boolean isSolid(BlockView world, BlockPos pos) {
        return world.getBlockState(pos).isSolidBlock(world, pos);
    }

    /**
     * If the path has more than {@link #MAX_WAYPOINTS_PER_STEP} nodes, uniformly
     * samples it down while always keeping the first and last node.
     * This prevents excessively fine-grained paths that cause jittery, robotic movement.
     *
     * @param path smoothed path
     * @return path with at most MAX_WAYPOINTS_PER_STEP nodes
     */
    public static List<BlockPos> capNodes(List<BlockPos> path) {
        if (path.size() <= MAX_WAYPOINTS_PER_STEP) return path;

        List<BlockPos> result = new ArrayList<>(MAX_WAYPOINTS_PER_STEP);
        result.add(path.get(0)); // always keep start

        // Evenly distribute (MAX_WAYPOINTS_PER_STEP - 2) intermediate waypoints
        int interior = MAX_WAYPOINTS_PER_STEP - 2;
        for (int i = 1; i <= interior; i++) {
            // Map i ∈ [1..interior] to evenly spaced indices in path
            int idx = (int) Math.round((double) i * (path.size() - 1) / (interior + 1));
            result.add(path.get(idx));
        }

        result.add(path.get(path.size() - 1)); // always keep end
        return result;
    }
}
