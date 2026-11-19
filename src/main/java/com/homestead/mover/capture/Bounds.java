package com.homestead.mover.capture;

import com.homestead.mover.snapshot.BoxGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Inclusive block box selected by two corners. */
public record Bounds(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
    public Bounds {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("size must be positive: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
    }

    public static Bounds between(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        return new Bounds(minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    public BlockPos min() {
        return new BlockPos(minX, minY, minZ);
    }

    /** Position of a local index in the same order the snapshot packs: {@code x + z * sizeX + y * sizeX * sizeZ}. */
    public BlockPos posAt(int index) {
        if (index < 0 || index >= volume()) {
            throw new IndexOutOfBoundsException("index=" + index + " volume=" + volume());
        }
        int[] offset = BoxGeometry.offsetOf(index, sizeX, sizeZ);
        return new BlockPos(minX + offset[0], minY + offset[1], minZ + offset[2]);
    }

    public AABB aabb() {
        return new AABB(minX, minY, minZ, minX + sizeX, minY + sizeY, minZ + sizeZ);
    }
}
