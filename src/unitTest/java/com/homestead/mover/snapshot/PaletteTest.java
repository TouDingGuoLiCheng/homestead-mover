package com.homestead.mover.snapshot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaletteTest {

    @Test
    void appendsOnlyUnseenStates() {
        Palette palette = Palette.empty();
        assertEquals(0, palette.indexOf(RawBlockState.of(RawBlockState.AIR)));
        assertEquals(1, palette.indexOf(RawBlockState.of("minecraft:stone")));
        assertEquals(0, palette.indexOf(RawBlockState.of(RawBlockState.AIR)));
        assertEquals(1, palette.indexOf(RawBlockState.of("minecraft:stone")));
        assertEquals(2, palette.size());
    }

    @Test
    void propertiesTakePartInEquality() {
        RawBlockState stairsNorth = new RawBlockState("minecraft:oak_stairs", Map.of("facing", "north"));
        RawBlockState stairsSouth = new RawBlockState("minecraft:oak_stairs", Map.of("facing", "south"));
        assertNotEquals(stairsNorth, stairsSouth);
        Palette palette = Palette.empty();
        assertEquals(0, palette.indexOf(stairsNorth));
        assertEquals(1, palette.indexOf(stairsSouth));
        assertEquals(stairsNorth, palette.stateAt(0));
    }

    @Test
    void orderedRestoreKeepsFileOrderAndRejectsDuplicates() {
        Palette palette = Palette.ofOrdered(List.of(
                RawBlockState.of("minecraft:stone"),
                RawBlockState.of(RawBlockState.AIR)));
        assertEquals("minecraft:stone", palette.stateAt(0).name());
        assertEquals(RawBlockState.AIR, palette.stateAt(1).name());
        assertThrows(IllegalArgumentException.class, () -> Palette.ofOrdered(List.of(
                RawBlockState.of(RawBlockState.AIR),
                RawBlockState.of(RawBlockState.AIR))));
    }
}
