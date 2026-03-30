/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixininterface;

import net.minecraft.util.math.BlockPos;

public interface IBox {
    void lfm$expand(double v);

    void lfm$set(double x1, double y1, double z1, double x2, double y2, double z2);

    default void lfm$set(BlockPos pos) {
        lfm$set(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }
}
