/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.events.render;

import lfmdevelopment.lfmclient.events.Cancellable;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;

public class RenderBlockEntityEvent extends Cancellable {
    private static final RenderBlockEntityEvent INSTANCE = new RenderBlockEntityEvent();

    public BlockEntityRenderState blockEntityState;

    public static RenderBlockEntityEvent get(BlockEntityRenderState blockEntityState) {
        INSTANCE.setCancelled(false);
        INSTANCE.blockEntityState = blockEntityState;
        return INSTANCE;
    }
}
