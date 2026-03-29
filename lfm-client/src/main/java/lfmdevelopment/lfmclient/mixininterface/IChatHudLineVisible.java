/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixininterface;

public interface IChatHudLineVisible extends IChatHudLine {
    boolean lfm$isStartOfEntry();
    void lfm$setStartOfEntry(boolean start);
}
