package com.homestead.mover.placement;

import com.homestead.mover.snapshot.BoxGeometry;
import com.homestead.mover.snapshot.RawBlockState;
import com.homestead.mover.snapshot.RegionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the ghost would overwrite, recomputed by the client every time the preview moves.
 * Kept out of the client package on purpose: the harness can then compare it with the server's
 * own {@link ConflictScanner}, so the cells that turn red are the cells the server will refuse.
 *
 * <p>Only surface cells are collected — a fully enclosed cell can never be seen, and drawing it
 * would cost frames for nothing.
 */
public final class PreviewScan {
    private PreviewScan() {
    }

    /** The drawn cells, which of them are blocked, and the short list the HUD names. */
    public record Result(int[] cells, boolean[] conflicts, int conflictCount,
                         List<BlockPos> sampleConflicts, List<String> sampleNames, boolean truncated) {
    }

    public static Result scan(Level level, RegionSnapshot region, BlockPos origin, int yaw,
                              int cellLimit, int sampleLimit) {
        int[] found = new int[Math.min(region.volume(), Math.max(0, cellLimit))];
        boolean[] flags = new boolean[found.length];
        List<BlockPos> samples = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int count = 0;
        int conflicts = 0;
        boolean truncated = false;
        scan:
        for (int y = 0; y < region.sizeY(); y++) {
            for (int z = 0; z < region.sizeZ(); z++) {
                for (int x = 0; x < region.sizeX(); x++) {
                    if (count >= found.length) {
                        truncated = true;
                        break scan;
                    }
                    RawBlockState raw = region.blockAt(x, y, z);
                    BlockState planned = BlockStateResolve.resolveRotated(raw, yaw);
                    if (planned.isAir() || isInterior(region, x, y, z)) {
                        continue;
                    }
                    BlockPos world = worldOf(region, origin, yaw, x, y, z);
                    boolean conflict = level != null
                            && PlacementRules.isConflict(level.getBlockState(world), planned, false, false);
                    found[count] = BoxGeometry.index(x, y, z, region.sizeX(), region.sizeZ());
                    flags[count] = conflict;
                    count++;
                    if (conflict) {
                        conflicts++;
                        if (samples.size() < sampleLimit) {
                            samples.add(world.immutable());
                            names.add(level.getBlockState(world).getBlock().getName().getString());
                        }
                    }
                }
            }
        }
        return new Result(Arrays.copyOf(found, count), Arrays.copyOf(flags, count), conflicts,
                samples, names, truncated);
    }

    private static boolean isInterior(RegionSnapshot region, int x, int y, int z) {
        return solid(region, x - 1, y, z) && solid(region, x + 1, y, z) && solid(region, x, y - 1, z)
                && solid(region, x, y + 1, z) && solid(region, x, y, z - 1) && solid(region, x, y, z + 1);
    }

    private static boolean solid(RegionSnapshot region, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= region.sizeX() || y >= region.sizeY() || z >= region.sizeZ()) {
            return false;
        }
        return !BlockStateResolve.resolve(region.blockAt(x, y, z)).isAir();
    }

    /** Where a local cell ends up once the building is rotated onto the given origin. */
    public static BlockPos worldOf(RegionSnapshot region, BlockPos origin, int yaw,
                                   int localX, int localY, int localZ) {
        int rotatedX = SchematicYaw.offsetX(localX, localZ, region.sizeX(), region.sizeZ(), yaw);
        int rotatedZ = SchematicYaw.offsetZ(localX, localZ, region.sizeX(), region.sizeZ(), yaw);
        return origin.offset(rotatedX, localY, rotatedZ);
    }
}
