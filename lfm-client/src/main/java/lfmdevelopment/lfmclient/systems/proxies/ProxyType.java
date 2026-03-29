/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.proxies;

import org.jetbrains.annotations.Nullable;

public enum ProxyType {
    Socks4,
    Socks5;

    @Nullable
    public static ProxyType parse(String group) {
        for (ProxyType type : values()) {
            if (type.name().equalsIgnoreCase(group)) {
                return type;
            }
        }
        return null;
    }
}
