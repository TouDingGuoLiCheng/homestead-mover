package com.homestead.mover.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The copied yaw math must stay consistent: footprint swap and offset mirroring. */
class SchematicYawTest {
    @Test
    void normalizeWrapsBothWays() {
        assertEquals(0, SchematicYaw.normalize(4));
        assertEquals(3, SchematicYaw.normalize(-1));
        assertEquals(1, SchematicYaw.normalize(5));
    }

    @Test
    void rotateLeftAndRightAreInverse() {
        for (int yaw = 0; yaw < 4; yaw++) {
            assertEquals(yaw, SchematicYaw.rotateRight(SchematicYaw.rotateLeft(yaw)));
            assertEquals(yaw, SchematicYaw.rotateLeft(SchematicYaw.rotateRight(yaw)));
        }
    }

    @Test
    void oddYawSwapsFootprint() {
        assertEquals(3, SchematicYaw.placedSizeX(3, 5, 0));
        assertEquals(5, SchematicYaw.placedSizeZ(3, 5, 0));
        assertEquals(5, SchematicYaw.placedSizeX(3, 5, 1));
        assertEquals(3, SchematicYaw.placedSizeZ(3, 5, 1));
        assertEquals(3, SchematicYaw.placedSizeX(3, 5, 2));
        assertEquals(5, SchematicYaw.placedSizeX(3, 5, 3));
    }

    @Test
    void offsetsAreASingleRotation() {
        int sizeX = 3;
        int sizeZ = 5;
        // every (x,z) cell must land on a unique (x,z) cell of the rotated footprint
        for (int yaw = 0; yaw < 4; yaw++) {
            boolean[] seen = new boolean[sizeX * sizeZ];
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int rx = SchematicYaw.offsetX(x, z, sizeX, sizeZ, yaw);
                    int rz = SchematicYaw.offsetZ(x, z, sizeX, sizeZ, yaw);
                    int width = SchematicYaw.placedSizeX(sizeX, sizeZ, yaw);
                    if (rx < 0 || rx >= width || rz < 0 || rz >= SchematicYaw.placedSizeZ(sizeX, sizeZ, yaw)) {
                        throw new AssertionError("yaw " + yaw + " pushed (" + x + "," + z + ") to (" + rx + "," + rz + ")");
                    }
                    int index = rx + rz * width;
                    if (seen[index]) {
                        throw new AssertionError("yaw " + yaw + " is not injective at (" + x + "," + z + ")");
                    }
                    seen[index] = true;
                }
            }
        }
    }
}
