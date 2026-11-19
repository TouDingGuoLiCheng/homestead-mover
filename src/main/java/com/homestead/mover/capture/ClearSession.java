package com.homestead.mover.capture;

import com.homestead.mover.task.FrameScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Empties the source after a capture has been persisted: blocks become air without drops,
 * and only the entities that actually travelled are discarded.
 */
public final class ClearSession {
    private enum Phase {
        BLOCKS,
        ENTITIES,
        DONE
    }

    /**
     * UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS. Removing a container through setBlock can
     * eject its contents as items; those contents are already inside the snapshot, so any
     * drop would duplicate them. Measured: without the suppress flag the chest contents
     * reappeared as an item entity at the source position during the agent test.
     */
    private static final int CLEAR_FLAGS = 2 | Block.UPDATE_SUPPRESS_DROPS;

    private final ServerLevel level;
    private final Bounds bounds;
    private final List<UUID> entityIds = new ArrayList<>();
    private int cursor;
    private int entityCursor;
    private Phase phase = Phase.BLOCKS;

    public ClearSession(ServerLevel level, Bounds bounds, ListTag capturedEntities) {
        this.level = level;
        this.bounds = bounds;
        for (int i = 0; i < capturedEntities.size(); i++) {
            CompoundTag tag = capturedEntities.getCompound(i);
            if (tag.hasUUID("UUID")) {
                entityIds.add(tag.getUUID("UUID"));
            }
        }
    }

    public double progress() {
        int total = bounds.volume() + Math.max(1, entityIds.size());
        int done = cursor + (phase == Phase.ENTITIES || phase == Phase.DONE ? entityIds.size() : 0);
        return Math.min(1.0, (double) done / total);
    }

    public boolean step(FrameScheduler.TimeBudget budget) {
        if (phase == Phase.BLOCKS) {
            clearBlocks(budget);
            if (phase != Phase.DONE) {
                return false;
            }
            return true;
        }
        if (phase == Phase.ENTITIES) {
            discardEntities();
            phase = Phase.DONE;
            return true;
        }
        return true;
    }

    private void clearBlocks(FrameScheduler.TimeBudget budget) {
        int volume = bounds.volume();
        BlockState air = Blocks.AIR.defaultBlockState();
        while (budget.hasTimeLeft() && cursor < volume) {
            BlockPos pos = bounds.posAt(cursor);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Container container) {
                // The contents are already inside the snapshot; empty them first so removing
                // the block cannot eject a duplicate stack as a dropped item.
                container.clearContent();
            }
            level.setBlock(pos, air, CLEAR_FLAGS);
            cursor++;
        }
        if (cursor >= volume) {
            phase = Phase.ENTITIES;
        }
    }

    private void discardEntities() {
        if (entityIds.isEmpty()) {
            return;
        }
        for (Entity entity : level.getEntitiesOfClass(Entity.class, bounds.aabb())) {
            if (entityIds.contains(entity.getUUID())) {
                entity.discard();
                entityCursor++;
            }
        }
        entityIds.clear();
    }
}
