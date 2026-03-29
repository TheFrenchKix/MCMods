/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixininterface;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.joml.Vector3d;

@SuppressWarnings("UnusedReturnValue")
public interface IVec3d {
    Vec3d lfm$set(double x, double y, double z);

    default Vec3d lfm$set(Vec3i vec) {
        return lfm$set(vec.getX(), vec.getY(), vec.getZ());
    }

    default Vec3d lfm$set(Vector3d vec) {
        return lfm$set(vec.x, vec.y, vec.z);
    }

    default Vec3d lfm$set(Vec3d pos) {
        return lfm$set(pos.x, pos.y, pos.z);
    }

    Vec3d lfm$setXZ(double x, double z);

    Vec3d lfm$setY(double y);
}
