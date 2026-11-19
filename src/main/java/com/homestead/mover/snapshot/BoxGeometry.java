package com.homestead.mover.snapshot;

/**
 * The one place that defines the snapshot packing order:
 * {@code index = x + z * sizeX + y * sizeX * sizeZ}.
 */
public final class BoxGeometry {
    private BoxGeometry() {
    }

    public static int index(int x, int y, int z, int sizeX, int sizeZ) {
        return x + z * sizeX + y * sizeX * sizeZ;
    }

    /** Offset of a packed index, as {@code {x, y, z}}. Caller validates the range. */
    public static int[] offsetOf(int index, int sizeX, int sizeZ) {
        int y = index / (sizeX * sizeZ);
        int rest = index - y * sizeX * sizeZ;
        int z = rest / sizeX;
        int x = rest - z * sizeX;
        return new int[] {x, y, z};
    }
}
