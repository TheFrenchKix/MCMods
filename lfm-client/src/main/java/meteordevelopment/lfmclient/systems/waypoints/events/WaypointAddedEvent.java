/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.waypoints.events;

import lfmdevelopment.lfmclient.systems.waypoints.Waypoint;

public record WaypointAddedEvent(Waypoint waypoint) {
}
