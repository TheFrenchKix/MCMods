/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.pathing;

import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.utils.PreInit;

import java.lang.reflect.InvocationTargetException;

public class PathManagers {
    private static IPathManager INSTANCE = new NopPathManager();

    public static IPathManager get() {
        return INSTANCE;
    }

    @PreInit
    public static void init() {
        if (exists("lfmdevelopment.voyager.PathManager")) {
            try {
                INSTANCE = (IPathManager) Class.forName("lfmdevelopment.voyager.PathManager").getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        if (exists("baritone.api.BaritoneAPI")) {
            BaritoneUtils.IS_AVAILABLE = true;

            if (INSTANCE instanceof NopPathManager)
                INSTANCE = new BaritonePathManager();
        }

        lfmClient.LOG.info("Path Manager: {}", INSTANCE.getName());
    }

    private static boolean exists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
