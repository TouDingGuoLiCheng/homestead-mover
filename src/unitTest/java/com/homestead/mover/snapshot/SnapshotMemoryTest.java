package com.homestead.mover.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotMemoryTest {

    /** 100k blocks, a modest palette: the packed block data must stay far below 1 MB. */
    @Test
    void hundredThousandBlocksStayUnderOneMegabyte() {
        int sizeX = 100;
        int sizeY = 20;
        int sizeZ = 50;
        int volume = sizeX * sizeY * sizeZ;
        assertEquals(100_000, volume);

        for (int paletteSize : new int[] {4, 8, 32, 256}) {
            PackedStates states = PackedStates.empty(volume, paletteSize);
            long bytes = (long) states.copyData().length * Long.BYTES;
            assertTrue(bytes < 1_000_000L,
                    "palette=" + paletteSize + " packed bytes=" + bytes);
        }
    }
}
