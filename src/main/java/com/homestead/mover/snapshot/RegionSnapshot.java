package com.homestead.mover.snapshot;

/** One captured cuboid of blocks: palette + packed indices, no world objects. */
public final class RegionSnapshot {
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Palette palette;
    private final PackedStates states;

    public RegionSnapshot(int sizeX, int sizeY, int sizeZ, Palette palette, PackedStates states) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("size must be positive: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (palette.size() == 0) {
            throw new IllegalArgumentException("palette must not be empty");
        }
        if (states.size() != sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("states size " + states.size()
                    + " does not match volume " + (sizeX * sizeY * sizeZ));
        }
        if (states.bitsPerEntry() != PackedStates.bitsForPaletteSize(palette.size())) {
            throw new IllegalArgumentException("bitsPerEntry " + states.bitsPerEntry()
                    + " does not match palette size " + palette.size());
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.states = states;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    public Palette palette() {
        return palette;
    }

    public PackedStates states() {
        return states;
    }

    public int index(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + "," + z + ") outside "
                    + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        return BoxGeometry.index(x, y, z, sizeX, sizeZ);
    }

    public RawBlockState blockAt(int x, int y, int z) {
        return palette.stateAt(states.get(index(x, y, z)));
    }

    /** Blocks that are not minecraft:air — the number the confirm screen reports. */
    public int nonAirCount() {
        Integer airIndex = palette.indexByName(RawBlockState.AIR).orElse(null);
        int count = 0;
        for (int i = 0; i < states.size(); i++) {
            if (airIndex == null || states.get(i) != airIndex) {
                count++;
            }
        }
        return count;
    }
}
