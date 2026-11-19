package com.homestead.mover.capture;

import com.homestead.mover.snapshot.CapturedBuilding;
import com.homestead.mover.snapshot.MoveSnapshot;
import com.homestead.mover.snapshot.PackedStates;
import com.homestead.mover.snapshot.Palette;
import com.homestead.mover.snapshot.RawBlockState;
import com.homestead.mover.snapshot.RegionSnapshot;
import com.homestead.mover.task.FrameScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a box out of the world in slices. Never touches the source: clearing is
 * {@link ClearSession}'s job so a cancelled capture leaves the building intact.
 */
public final class CaptureSession {
    public enum Phase {
        BLOCKS,
        ENTITY_SCAN,
        ENTITY_SAVE,
        /**
         * Packing the palette indices into the bit array. Sliced on purpose: a 100k-cell box
         * used to do this in one go and cost a single tick about 34 ms.
         */
        BUILD,
        DONE
    }

    private final ServerLevel level;
    private final Bounds bounds;
    private final String name;
    private final Palette palette = Palette.empty();
    private final int[] paletteIndices;
    private final ListTag tileEntities = new ListTag();
    private final ListTag entities = new ListTag();
    private final Map<String, Integer> skipped = new LinkedHashMap<>();

    private final List<Entity> pendingEntities = new ArrayList<>();
    private int cursor;
    private int entityCursor;
    private int packedCursor;
    private PackedStates packed;
    private Phase phase = Phase.BLOCKS;
    private CapturedBuilding result;

    public CaptureSession(ServerLevel level, Bounds bounds, String name) {
        this.level = level;
        this.bounds = bounds;
        this.name = name;
        this.paletteIndices = new int[bounds.volume()];
    }

    public Phase phase() {
        return phase;
    }

    public Bounds bounds() {
        return bounds;
    }

    public Map<String, Integer> skipped() {
        return Map.copyOf(skipped);
    }

    /** 0..1 over both passes; the confirm screen and the HUD read this. */
    public double progress() {
        int total = bounds.volume() + 1;
        int done = phase == Phase.BLOCKS ? cursor : bounds.volume();
        if (phase == Phase.DONE) {
            done++;
        }
        return Math.min(1.0, (double) done / total);
    }

    public CapturedBuilding result() {
        if (result == null) {
            throw new IllegalStateException("capture not finished yet: phase=" + phase);
        }
        return result;
    }

    /** One slice. Returns true when the snapshot is complete. */
    public boolean step(FrameScheduler.TimeBudget budget) {
        switch (phase) {
            case BLOCKS -> readBlocks(budget);
            case ENTITY_SCAN -> scanEntities();
            case ENTITY_SAVE -> saveEntities(budget);
            case BUILD -> buildSlice(budget);
            case DONE -> {
                return true;
            }
            default -> throw new IllegalStateException("unexpected phase " + phase);
        }
        return phase == Phase.DONE;
    }

    private void readBlocks(FrameScheduler.TimeBudget budget) {
        int volume = bounds.volume();
        while (budget.hasTimeLeft() && cursor < volume) {
            BlockPos pos = bounds.posAt(cursor);
            BlockState state = level.getBlockState(pos);
            paletteIndices[cursor] = palette.indexOf(BlockStateMapper.rawOf(state));
            if (state.hasBlockEntity()) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    tileEntities.add(blockEntity.saveWithFullMetadata());
                }
            }
            cursor++;
        }
        if (cursor >= volume) {
            phase = Phase.ENTITY_SCAN;
        }
    }

    private void scanEntities() {
        pendingEntities.clear();
        skipped.clear();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, bounds.aabb())) {
            Optional<EntityFilterPolicy.Skip> skip = EntityFilterPolicy.skipReason(candidateOf(entity));
            if (skip.isPresent()) {
                skipped.merge(reasonKey(entity, skip.get()), 1, Integer::sum);
            } else {
                pendingEntities.add(entity);
            }
        }
        entityCursor = 0;
        phase = Phase.ENTITY_SAVE;
    }

    private void saveEntities(FrameScheduler.TimeBudget budget) {
        while (budget.hasTimeLeft() && entityCursor < pendingEntities.size()) {
            Entity entity = pendingEntities.get(entityCursor++);
            if (!entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            if (entity.save(tag)) {
                entities.add(tag);
            }
        }
        if (entityCursor >= pendingEntities.size()) {
            pendingEntities.clear();
            packed = PackedStates.empty(bounds.volume(), palette.size());
            packedCursor = 0;
            phase = Phase.BUILD;
        }
    }

    /** The one phase whose work scales with volume, so it is budget-checked cell by cell. */
    private void buildSlice(FrameScheduler.TimeBudget budget) {
        while (budget.hasTimeLeft() && packedCursor < paletteIndices.length) {
            packed.set(packedCursor, paletteIndices[packedCursor]);
            packedCursor++;
        }
        if (packedCursor >= paletteIndices.length) {
            result = finish();
            phase = Phase.DONE;
        }
    }

    private CapturedBuilding finish() {
        RegionSnapshot region = new RegionSnapshot(bounds.sizeX(), bounds.sizeY(), bounds.sizeZ(), palette, packed);
        MoveSnapshot snapshot = new MoveSnapshot(name, level.dimension().location().toString(),
                bounds.minX(), bounds.minY(), bounds.minZ(), region, skipped);
        return new CapturedBuilding(snapshot, tileEntities, entities);
    }

    private static EntityFilterPolicy.Candidate candidateOf(Entity entity) {
        String id = EntityType.getKey(entity.getType()).toString();
        boolean transientEntity = entity.isPassenger() || entity.isVehicle() || !entity.isAlive();
        return new EntityFilterPolicy.Candidate(id, entity instanceof Player,
                entity instanceof LivingEntity, transientEntity);
    }

    /** Report keys stay stable so the confirm screen can group by reason. */
    static String reasonKey(Entity entity, EntityFilterPolicy.Skip skip) {
        return switch (skip) {
            case PLAYER -> "player";
            case MOD_ENTITY -> EntityType.getKey(entity.getType()).getNamespace();
            case NOT_LIVING -> "not_living";
            case TRANSIENT -> "transient";
        };
    }
}
