package com.homestead.mover.capture;

import com.homestead.mover.placement.BlockStateResolve;
import com.homestead.mover.placement.SchematicYaw;
import com.homestead.mover.snapshot.BoxGeometry;
import com.homestead.mover.snapshot.CapturedBuilding;
import com.homestead.mover.snapshot.MoveSnapshot;
import com.homestead.mover.snapshot.RawBlockState;
import com.homestead.mover.snapshot.RegionSnapshot;
import com.homestead.mover.task.FrameScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Writes a captured building back into the world in slices, at any origin and in any of the
 * four yaw steps. Block states are rotated through {@link BlockStateResolve}, entity and block
 * entity positions through {@link SchematicYaw}, so "restore in place" and "place elsewhere,
 * rotated" stay one code path.
 */
public final class RestoreSession {
    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Phase {
        BLOCKS,
        TILE_ENTITIES,
        ENTITIES,
        DONE
    }

    private final ServerLevel level;
    private final BlockPos targetOrigin;
    private final RegionSnapshot region;
    private final ListTag tileEntities;
    private final ListTag entities;
    private final int yaw;
    private final int sourceOriginX;
    private final int sourceOriginY;
    private final int sourceOriginZ;

    public RestoreSession(ServerLevel level, BlockPos targetOrigin, CapturedBuilding building) {
        this(level, targetOrigin, building, 0);
    }

    public RestoreSession(ServerLevel level, BlockPos targetOrigin, CapturedBuilding building, int yaw) {
        this.level = level;
        this.targetOrigin = targetOrigin;
        this.region = building.snapshot().region();
        this.tileEntities = building.tileEntities();
        this.entities = building.entities();
        this.yaw = SchematicYaw.normalize(yaw);
        MoveSnapshot snapshot = building.snapshot();
        this.sourceOriginX = snapshot.originX();
        this.sourceOriginY = snapshot.originY();
        this.sourceOriginZ = snapshot.originZ();
    }

    private int cursor;
    private Phase phase = Phase.BLOCKS;

    public int yaw() {
        return yaw;
    }

    public double progress() {
        int total = region.volume() + tileEntities.size() + entities.size();
        int done = switch (phase) {
            case BLOCKS -> cursor;
            case TILE_ENTITIES -> region.volume();
            case ENTITIES -> region.volume() + tileEntities.size();
            case DONE -> total;
        };
        return total <= 0 ? 1.0 : Math.min(1.0, (double) done / total);
    }

    public boolean step(FrameScheduler.TimeBudget budget) {
        switch (phase) {
            case BLOCKS -> placeBlocks(budget);
            case TILE_ENTITIES -> placeTileEntities(budget);
            case ENTITIES -> placeEntities();
            case DONE -> {
                return true;
            }
        }
        return phase == Phase.DONE;
    }

    private void placeBlocks(FrameScheduler.TimeBudget budget) {
        int volume = region.volume();
        while (budget.hasTimeLeft() && cursor < volume) {
            int[] offset = BoxGeometry.offsetOf(cursor, region.sizeX(), region.sizeZ());
            RawBlockState raw = region.blockAt(offset[0], offset[1], offset[2]);
            BlockState state = BlockStateResolve.resolveRotated(raw, yaw);
            level.setBlock(worldOf(offset[0], offset[1], offset[2]), state, 3);
            cursor++;
        }
        if (cursor >= volume) {
            cursor = 0;
            phase = Phase.TILE_ENTITIES;
        }
    }

    private void placeTileEntities(FrameScheduler.TimeBudget budget) {
        while (budget.hasTimeLeft() && cursor < tileEntities.size()) {
            CompoundTag tag = tileEntities.getCompound(cursor++).copy();
            BlockPos pos = worldOf(tag.getInt("x") - sourceOriginX, tag.getInt("y") - sourceOriginY,
                    tag.getInt("z") - sourceOriginZ);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
                blockEntity.load(tag);
                blockEntity.setChanged();
            }
        }
        if (cursor >= tileEntities.size()) {
            cursor = 0;
            phase = Phase.ENTITIES;
        }
    }

    private void placeEntities() {
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag tag = entities.getCompound(i).copy();
            shiftPosition(tag);
            rotateFacing(tag);
            Optional<Entity> created = EntityType.create(tag, level);
            if (created.isPresent()) {
                level.addFreshEntity(created.get());
            } else {
                // Losing a mob silently would be worse than a line in the log.
                LOGGER.warn("[homestead_mover] entity could not be recreated from the snapshot: id={} pos={}",
                        tag.getString("id"), tag.getList("Pos", Tag.TAG_DOUBLE));
            }
        }
        phase = Phase.DONE;
    }

    /** Local snapshot cell -> world position, honouring the placement yaw. */
    private BlockPos worldOf(int localX, int localY, int localZ) {
        int rotatedX = SchematicYaw.offsetX(localX, localZ, region.sizeX(), region.sizeZ(), yaw);
        int rotatedZ = SchematicYaw.offsetZ(localX, localZ, region.sizeX(), region.sizeZ(), yaw);
        return new BlockPos(targetOrigin.getX() + rotatedX, targetOrigin.getY() + localY,
                targetOrigin.getZ() + rotatedZ);
    }

    private void shiftPosition(CompoundTag tag) {
        ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
        if (pos.size() != 3) {
            return;
        }
        double localX = pos.getDouble(0) - sourceOriginX;
        double localY = pos.getDouble(1) - sourceOriginY;
        double localZ = pos.getDouble(2) - sourceOriginZ;
        int cellX = (int) Math.floor(localX);
        int cellZ = (int) Math.floor(localZ);
        double rotatedX = SchematicYaw.offsetX(cellX, cellZ, region.sizeX(), region.sizeZ(), yaw) + (localX - cellX);
        double rotatedZ = SchematicYaw.offsetZ(cellX, cellZ, region.sizeX(), region.sizeZ(), yaw) + (localZ - cellZ);
        ListTag shifted = new ListTag();
        shifted.add(DoubleTag.valueOf(targetOrigin.getX() + rotatedX));
        shifted.add(DoubleTag.valueOf(targetOrigin.getY() + localY));
        shifted.add(DoubleTag.valueOf(targetOrigin.getZ() + rotatedZ));
        tag.put("Pos", shifted);
    }

    /** Entities turn with the building, the same 90-degree step the blocks take. */
    private void rotateFacing(CompoundTag tag) {
        if (yaw == 0) {
            return;
        }
        ListTag rotation = tag.getList("Rotation", Tag.TAG_FLOAT);
        if (rotation.size() != 2) {
            return;
        }
        float bodyYaw = rotation.getFloat(0) + 90.0F * yaw;
        float headYaw = rotation.getFloat(1) + 90.0F * yaw;
        ListTag shifted = new ListTag();
        shifted.add(FloatTag.valueOf(wrapDegrees(bodyYaw)));
        shifted.add(FloatTag.valueOf(wrapDegrees(headYaw)));
        tag.put("Rotation", shifted);
    }

    private static float wrapDegrees(float value) {
        return ((value % 360.0F) + 360.0F) % 360.0F;
    }
}
