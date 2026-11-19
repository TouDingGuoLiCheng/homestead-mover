package com.homestead.mover.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackedStatesTest {

    @Test
    void bitsPerEntryIsAtLeastTwo() {
        assertEquals(2, PackedStates.bitsForPaletteSize(1));
        assertEquals(2, PackedStates.bitsForPaletteSize(4));
        assertEquals(3, PackedStates.bitsForPaletteSize(5));
        assertEquals(4, PackedStates.bitsForPaletteSize(16));
        assertEquals(5, PackedStates.bitsForPaletteSize(17));
        assertThrows(IllegalArgumentException.class, () -> PackedStates.bitsForPaletteSize(0));
    }

    @Test
    void wordsForRoundsUp() {
        assertEquals(1, PackedStates.wordsFor(32, 2));
        assertEquals(0, PackedStates.wordsFor(0, 32));
        assertEquals(2, PackedStates.wordsFor(33, 2));
        assertEquals(25, PackedStates.wordsFor(100, 16));
    }

    @Test
    void roundTripsEveryValueAcrossLongBoundaries() {
        for (int bits : new int[] {2, 3, 4, 5, 8, 16}) {
            int size = 200;
            PackedStates states = new PackedStates(new long[PackedStates.wordsFor(size, bits)], bits, size);
            for (int i = 0; i < size; i++) {
                states.set(i, i % (1 << bits));
            }
            for (int i = 0; i < size; i++) {
                assertEquals(i % (1 << bits), states.get(i), "bits=" + bits + " index=" + i);
            }
        }
    }

    @Test
    void overwriteKeepsNeighboursIntact() {
        int size = 40;
        PackedStates states = new PackedStates(new long[PackedStates.wordsFor(size, 5)], 5, size);
        for (int i = 0; i < size; i++) {
            states.set(i, 31);
        }
        for (int i = 0; i < size; i++) {
            states.set(i, i % 32);
        }
        for (int i = 0; i < size; i++) {
            assertEquals(i % 32, states.get(i), "index=" + i);
        }
    }

    @Test
    void rejectsOutOfRangeValueAndShortBuffer() {
        PackedStates states = new PackedStates(new long[1], 2, 10);
        assertThrows(IllegalArgumentException.class, () -> states.set(0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> states.get(10));
        assertThrows(IllegalArgumentException.class, () -> new PackedStates(new long[0], 2, 100));
    }
}
