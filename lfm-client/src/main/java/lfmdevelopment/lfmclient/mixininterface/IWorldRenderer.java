/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixininterface;

import net.minecraft.client.gl.Framebuffer;

public interface IWorldRenderer {
    void lfm$pushEntityOutlineFramebuffer(Framebuffer framebuffer);

    void lfm$popEntityOutlineFramebuffer();
}
