/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.systems.accounts;

public class UuidToProfileResponse {
    public Property[] properties;

    public String getPropertyValue(String name) {
        for (Property property : properties) {
            if (property.name.equals(name)) return property.value;
        }

        return null;
    }

    public static class Property {
        public String name;
        public String value;
    }
}
