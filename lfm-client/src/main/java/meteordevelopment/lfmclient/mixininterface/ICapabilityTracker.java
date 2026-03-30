/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixininterface;

public interface ICapabilityTracker {
    boolean lfm$get();

    void lfm$set(boolean state);
}
