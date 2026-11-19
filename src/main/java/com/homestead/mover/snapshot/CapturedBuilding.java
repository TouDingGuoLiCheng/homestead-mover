package com.homestead.mover.snapshot;

import net.minecraft.nbt.ListTag;

/**
 * A captured building plus the raw NBT of its tile entities and entities.
 * Kept apart from {@link MoveSnapshot} so the pure block model stays testable without Forge.
 */
public final class CapturedBuilding {
    private final MoveSnapshot snapshot;
    private final ListTag tileEntities;
    private final ListTag entities;

    public CapturedBuilding(MoveSnapshot snapshot, ListTag tileEntities, ListTag entities) {
        this.snapshot = snapshot;
        this.tileEntities = tileEntities == null ? new ListTag() : tileEntities.copy();
        this.entities = entities == null ? new ListTag() : entities.copy();
    }

    public MoveSnapshot snapshot() {
        return snapshot;
    }

    /** Block entity NBT (chest contents, sign text, …), one compound per position. */
    public ListTag tileEntities() {
        return tileEntities.copy();
    }

    /** Entity NBT captured inside the box, already filtered and with UUIDs kept. */
    public ListTag entities() {
        return entities.copy();
    }

    public int entityCount() {
        return entities.size();
    }

    public int tileEntityCount() {
        return tileEntities.size();
    }
}
