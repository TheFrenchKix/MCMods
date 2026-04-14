package com.example.macromod.pathfinding.astar;

import net.minecraft.util.math.BlockPos;

/**
 * A* search node using primitive int coordinates to avoid BlockPos allocation.
 * Stores intrusive heap position for O(log n) decrease-key in {@link BinaryHeapOpenSet}.
 */
public final class PathNode {

    public final int x, y, z;

    /** g(n): cost from start to this node */
    public double cost;

    /** h(n): estimated cost to goal */
    public double estimatedCostToGoal;

    /** f(n) = g(n) + h(n) — cached for heap comparisons */
    public double combinedCost;

    /** Back-pointer for path reconstruction */
    public PathNode previous;

    /**
     * Position in the binary heap array. -1 means not in the open set (closed).
     * Used by {@link BinaryHeapOpenSet} for O(log n) update.
     */
    public int heapPosition = -1;

    public PathNode(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.cost = Double.MAX_VALUE;
        this.estimatedCostToGoal = Double.MAX_VALUE;
        this.combinedCost = Double.MAX_VALUE;
    }

    public boolean isOpen() {
        return heapPosition != -1;
    }

    /** Packs x,y,z into a single long for use as a hash map key. */
    public static long posHash(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) y & 0xFFFL) << 26) | (((long) z & 0x3FFFFFFL) << 38);
    }

    public long posHash() {
        return posHash(x, y, z);
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    @Override
    public String toString() {
        return "PathNode(" + x + "," + y + "," + z + " g=" + String.format("%.2f", cost) + ")";
    }
}
