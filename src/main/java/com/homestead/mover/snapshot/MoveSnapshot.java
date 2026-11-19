package com.homestead.mover.snapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A captured building plus where it came from. Entities and tile entities ride along as NBT. */
public final class MoveSnapshot {
    private String name;
    private final String dimensionId;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final RegionSnapshot region;
    private final Map<String, Integer> skipped;

    public MoveSnapshot(
            String name,
            String dimensionId,
            int originX,
            int originY,
            int originZ,
            RegionSnapshot region,
            Map<String, Integer> skipped) {
        this.name = Objects.requireNonNull(name, "name");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.region = Objects.requireNonNull(region, "region");
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.skipped = Collections.unmodifiableMap(new LinkedHashMap<>(skipped));
    }

    public String name() {
        return name;
    }

    /** The player names the build on the confirm screen, before the blob is written to the store. */
    public void rename(String newName) {
        this.name = Objects.requireNonNull(newName, "name");
    }

    public String dimensionId() {
        return dimensionId;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public RegionSnapshot region() {
        return region;
    }

    public Map<String, Integer> skipped() {
        return skipped;
    }
}
