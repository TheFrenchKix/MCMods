/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.events.entity;

import net.minecraft.entity.vehicle.AbstractBoatEntity;

public class BoatMoveEvent {
    private static final BoatMoveEvent INSTANCE = new BoatMoveEvent();

    public AbstractBoatEntity boat;

    public static BoatMoveEvent get(AbstractBoatEntity entity) {
        INSTANCE.boat = entity;
        return INSTANCE;
    }
}
