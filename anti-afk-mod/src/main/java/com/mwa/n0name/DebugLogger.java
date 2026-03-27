package com.mwa.n0name;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("n0name");
    private static boolean enabled = false;

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void log(String module, String message) {
        if (enabled) LOGGER.info("[{}] {}", module, message);
    }

    public static void info(String message) {
        LOGGER.info("[n0name] {}", message);
    }
}
