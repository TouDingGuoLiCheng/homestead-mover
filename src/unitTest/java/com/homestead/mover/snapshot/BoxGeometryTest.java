package com.homestead.mover.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxGeometryTest {

    @Test
    void packsInXThenZThenY() {
        assertEquals(0, BoxGeometry.index(0, 0, 0, 4, 3));
        assertEquals(1, BoxGeometry.index(1, 0, 0, 4, 3));
        assertEquals(4, BoxGeometry.index(0, 0, 1, 4, 3));
        assertEquals(12, BoxGeometry.index(0, 1, 0, 4, 3));
        assertEquals(23, BoxGeometry.index(3, 1, 2, 4, 3));
    }

    @Test
    void offsetIsTheInverseOfIndexEverywhere() {
        int sizeX = 7;
        int sizeY = 5;
        int sizeZ = 3;
        int volume = sizeX * sizeY * sizeZ;
        for (int index = 0; index < volume; index++) {
            int[] offset = BoxGeometry.offsetOf(index, sizeX, sizeZ);
            assertTrue(offset[0] < sizeX && offset[1] < sizeY && offset[2] < sizeZ,
                    "offset out of range at " + index);
            assertEquals(index, BoxGeometry.index(offset[0], offset[1], offset[2], sizeX, sizeZ),
                    "round trip failed at " + index);
        }
    }
}
