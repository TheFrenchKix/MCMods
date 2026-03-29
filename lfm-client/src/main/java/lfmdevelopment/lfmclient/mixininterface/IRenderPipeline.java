/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixininterface;

public interface IRenderPipeline {
    void lfm$setLineSmooth(boolean lineSmooth);

    boolean lfm$getLineSmooth();
}
