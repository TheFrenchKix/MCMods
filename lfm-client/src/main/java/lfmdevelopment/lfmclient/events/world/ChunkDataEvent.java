/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.events.world;

import net.minecraft.world.chunk.WorldChunk;

/**
 * @implNote Shouldn't be put in a {@link lfmdevelopment.lfmclient.utils.misc.Pool} to avoid a race-condition, or in a {@link ThreadLocal} as it is shared between threads.
 * @author Crosby
 */
public record ChunkDataEvent(WorldChunk chunk) {}
