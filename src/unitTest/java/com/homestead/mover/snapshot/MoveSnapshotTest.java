package com.homestead.mover.snapshot;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoveSnapshotTest {

    private static RegionSnapshot region() {
        Palette palette = Palette.empty();
        PackedStates states = PackedStates.empty(2, 2);
        states.set(0, palette.indexOf(RawBlockState.of(RawBlockState.AIR)));
        states.set(1, palette.indexOf(RawBlockState.of("minecraft:chest")));
        return new RegionSnapshot(2, 1, 1, palette, states);
    }

    @Test
    void keepsSourcePositionAndStats() {
        Map<String, Integer> skipped = new LinkedHashMap<>();
        skipped.put("touhou_little_maid:maid", 3);
        MoveSnapshot snapshot = new MoveSnapshot("黑顶砂岩小屋", "minecraft:overworld",
                100, 64, -200, region(), skipped);
        assertEquals("黑顶砂岩小屋", snapshot.name());
        assertEquals("minecraft:overworld", snapshot.dimensionId());
        assertEquals(100, snapshot.originX());
        assertEquals(64, snapshot.originY());
        assertEquals(-200, snapshot.originZ());
        assertEquals(1, snapshot.region().nonAirCount());
        assertEquals(3, snapshot.skipped().get("touhou_little_maid:maid"));
    }

    @Test
    void skippedReportIsImmutable() {
        MoveSnapshot snapshot = new MoveSnapshot("小屋", "minecraft:overworld", 0, 0, 0, region(), Map.of());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.skipped().put("minecraft:item", 1));
    }
}
