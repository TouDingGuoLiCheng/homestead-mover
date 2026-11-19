package com.homestead.mover.snapshot;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionSnapshotTest {

    private static RegionSnapshot build(int sizeX, int sizeY, int sizeZ) {
        Palette palette = Palette.empty();
        PackedStates states = PackedStates.empty(sizeX * sizeY * sizeZ, 2);
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int index = x + z * sizeX + y * sizeX * sizeZ;
                    if (x == 0 && y == 0 && z == 0) {
                        states.set(index, palette.indexOf(RawBlockState.of(RawBlockState.AIR)));
                    } else if (y % 2 == 0) {
                        states.set(index, palette.indexOf(RawBlockState.of("minecraft:stone")));
                    } else {
                        states.set(index, palette.indexOf(new RawBlockState(
                                "minecraft:oak_stairs", Map.of("facing", "north"))));
                    }
                }
            }
        }
        return new RegionSnapshot(sizeX, sizeY, sizeZ, palette, states);
    }

    @Test
    void capturesTheBlockAtEveryCoordinate() {
        RegionSnapshot region = build(3, 4, 2);
        assertEquals(24, region.volume());
        assertEquals(RawBlockState.AIR, region.blockAt(0, 0, 0).name());
        assertEquals("minecraft:stone", region.blockAt(1, 2, 1).name());
        assertEquals("minecraft:oak_stairs", region.blockAt(2, 1, 0).name());
        assertEquals("north", region.blockAt(1, 3, 0).properties().get("facing"));
    }

    @Test
    void countsOnlyNonAirBlocks() {
        RegionSnapshot region = build(3, 4, 2);
        assertEquals(23, region.nonAirCount());
    }

    @Test
    void refusesInconsistentShapes() {
        Palette palette = Palette.empty();
        palette.indexOf(RawBlockState.of(RawBlockState.AIR));
        PackedStates states = PackedStates.empty(8, 2);
        assertThrows(IllegalArgumentException.class,
                () -> new RegionSnapshot(3, 3, 3, palette, states));
        assertThrows(IndexOutOfBoundsException.class, () -> build(3, 4, 2).blockAt(3, 0, 0));
    }
}
