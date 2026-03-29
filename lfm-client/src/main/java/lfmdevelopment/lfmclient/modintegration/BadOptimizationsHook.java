/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.modintegration;

import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.systems.modules.render.Fullbright;

import java.util.function.BooleanSupplier;

/*
 * Hook for BadOptimizations mod compatibility.
 * Signals when the lightmap needs to be updated due to Fullbright or Xray state changes.
 */
public class BadOptimizationsHook implements BooleanSupplier {
    private int lastState;

    @Override
    public boolean getAsBoolean() {
        Modules m = Modules.get();
        if (m == null) return false;

        int state = (m.get(Fullbright.class).getGamma() ? 1 : 0);
        boolean changed = state != lastState;
        lastState = state;
        return changed;
    }
}
