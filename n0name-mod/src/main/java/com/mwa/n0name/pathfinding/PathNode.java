package com.mwa.n0name.pathfinding;

import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record PathNode(int x, int y, int z) {

    public PathNode(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public Vec3d toVec3dCenter() {
        return new Vec3d(x + 0.5, y, z + 0.5);
    }

    public double distanceTo(PathNode other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceSquaredTo(PathNode other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        return obj;
    }

    public static PathNode fromJson(JsonObject obj) {
        return new PathNode(
            obj.get("x").getAsInt(),
            obj.get("y").getAsInt(),
            obj.get("z").getAsInt()
        );
    }
}
