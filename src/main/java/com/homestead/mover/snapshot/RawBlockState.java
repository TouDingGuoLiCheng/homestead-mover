package com.homestead.mover.snapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Block id + property map, compared by value so a palette can deduplicate states. */
public final class RawBlockState {
    public static final String AIR = "minecraft:air";

    private final String name;
    private final Map<String, String> properties;

    public RawBlockState(String name, Map<String, String> properties) {
        this.name = Objects.requireNonNull(name, "name");
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static RawBlockState of(String name) {
        return new RawBlockState(name, Map.of());
    }

    public String name() {
        return name;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public boolean isAir() {
        return AIR.equals(name);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RawBlockState state)) {
            return false;
        }
        return name.equals(state.name) && properties.equals(state.properties);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + properties.hashCode();
    }

    @Override
    public String toString() {
        return properties.isEmpty() ? name : name + properties;
    }
}
