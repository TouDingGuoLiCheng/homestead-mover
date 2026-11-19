package com.homestead.mover.flow;

import com.homestead.mover.placement.SchematicYaw;
import com.homestead.mover.snapshot.CapturedBuilding;
import com.homestead.mover.snapshot.RegionSnapshot;
import net.minecraft.core.BlockPos;

/**
 * Server-side state of one in-flight placement: which building, where, which way round.
 * The client only ever sees a copy of this through the preview packet.
 */
public final class PlacementSession {
    private static final int MIN_Y_OFFSET = -64;
    private static final int MAX_Y_OFFSET = 320;

    private final String blueprintId;
    private final CapturedBuilding building;
    private BlockPos baseOrigin;
    private final int slot;
    private int yaw;
    private int yOffset;

    public PlacementSession(String blueprintId, CapturedBuilding building, BlockPos baseOrigin, int slot) {
        this.blueprintId = blueprintId;
        this.building = building;
        this.baseOrigin = baseOrigin;
        this.slot = slot;
    }

    public String blueprintId() {
        return blueprintId;
    }

    public CapturedBuilding building() {
        return building;
    }

    /** Hotbar slot the case sits in, so the callback can reach the live stack. */
    public int slot() {
        return slot;
    }

    public int yaw() {
        return yaw;
    }

    public int yOffset() {
        return yOffset;
    }

    public void rotate(boolean clockwise) {
        yaw = clockwise ? SchematicYaw.rotateRight(yaw) : SchematicYaw.rotateLeft(yaw);
    }

    /** Slides the whole preview along the ground; the client freezes it so the crosshair cannot pull it back. */
    public void shift(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return;
        }
        this.baseOrigin = baseOrigin.offset(dx, 0, dz);
    }

    public void adjustY(int delta) {
        yOffset = Math.max(MIN_Y_OFFSET, Math.min(MAX_Y_OFFSET, yOffset + delta));
    }

    /** The client owns where its preview sits; the server still re-checks before landing. */
    public void setBaseOrigin(BlockPos origin) {
        this.baseOrigin = origin.immutable();
    }

    public void setYaw(int yaw) {
        this.yaw = SchematicYaw.normalize(yaw);
    }

    public void setYOffset(int yOffset) {
        this.yOffset = Math.max(MIN_Y_OFFSET, Math.min(MAX_Y_OFFSET, yOffset));
    }

    /** Where the building would go right now, including the vertical nudge. */
    public BlockPos origin() {
        return baseOrigin.offset(0, yOffset, 0);
    }

    public BlockPos baseOrigin() {
        return baseOrigin;
    }

    public int placedSizeX() {
        RegionSnapshot region = region();
        return SchematicYaw.placedSizeX(region.sizeX(), region.sizeZ(), yaw);
    }

    public int placedSizeZ() {
        RegionSnapshot region = region();
        return SchematicYaw.placedSizeZ(region.sizeX(), region.sizeZ(), yaw);
    }

    public int sizeY() {
        return region().sizeY();
    }

    public int nonAirCount() {
        return region().nonAirCount();
    }

    public int totalVolume() {
        return region().volume();
    }

    private RegionSnapshot region() {
        return building.snapshot().region();
    }
}
