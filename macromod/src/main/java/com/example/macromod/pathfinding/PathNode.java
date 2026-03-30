package com.example.macromod.pathfinding;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

/**
 * Represents a node in the A* pathfinding graph.
 */
@Environment(EnvType.CLIENT)
public class PathNode implements Comparable<PathNode> {

    /** World position of this node. */
    private final BlockPos pos;

    /** Parent node in the path (null for start). */
    private PathNode parent;

    /** Cost from start to this node. */
    private double g;

    /** Heuristic cost from this node to the goal. */
    private double h;

    /** Total estimated cost (g + h). */
    private double f;

    public PathNode(BlockPos pos) {
        this.pos = pos;
        this.g = Double.MAX_VALUE;
        this.h = 0;
        this.f = Double.MAX_VALUE;
    }

    public BlockPos getPos() {
        return pos;
    }

    public PathNode getParent() {
        return parent;
    }

    public void setParent(PathNode parent) {
        this.parent = parent;
    }

    public double getG() {
        return g;
    }

    public void setG(double g) {
        this.g = g;
        this.f = g + h;
    }

    public double getH() {
        return h;
    }

    public void setH(double h) {
        this.h = h;
        this.f = g + h;
    }

    public double getF() {
        return f;
    }

    @Override
    public int compareTo(PathNode other) {
        return Double.compare(this.f, other.f);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PathNode other)) return false;
        return pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}
