package com.homestead.mover.flow;

import com.homestead.mover.placement.ConflictScanner;
import com.homestead.mover.placement.SchematicYaw;
import com.homestead.mover.snapshot.CapturedBuilding;
import com.homestead.mover.snapshot.RegionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Conflict and chunk-readiness checks that gate a placement confirm. */
public final class MoverPlacer {
    private MoverPlacer() {
    }

    public static ConflictScanner.Result conflicts(ServerLevel level, CapturedBuilding building, BlockPos origin, int yaw) {
        return ConflictScanner.scan(level, building.snapshot().region(), origin, yaw);
    }

    /**
     * The plan forbids force-loading chunks, so a placement is only allowed when every corner
     * of the rotated footprint is already loaded. The corners are enough: a box that has all
     * four corners loaded has its whole chunk rectangle loaded too.
     */
    public static boolean areaLoaded(ServerLevel level, CapturedBuilding building, BlockPos origin, int yaw) {
        RegionSnapshot region = building.snapshot().region();
        int sizeX = SchematicYaw.placedSizeX(region.sizeX(), region.sizeZ(), yaw);
        int sizeZ = SchematicYaw.placedSizeZ(region.sizeX(), region.sizeZ(), yaw);
        int lastX = sizeX - 1;
        int lastZ = sizeZ - 1;
        for (int dx : new int[] {0, lastX}) {
            for (int dz : new int[] {0, lastZ}) {
                if (!level.isLoaded(origin.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return true;
    }
}
