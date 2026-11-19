package com.homestead.mover.capture;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityFilterPolicyTest {

    private static EntityFilterPolicy.Candidate candidate(String id, boolean player, boolean living,
            boolean transientEntity) {
        return new EntityFilterPolicy.Candidate(id, player, living, transientEntity);
    }

    @Test
    void vanillaAnimalsAndVillagersTravel() {
        assertTrue(EntityFilterPolicy.skipReason(candidate("minecraft:zombie", false, true, false)).isEmpty());
        assertTrue(EntityFilterPolicy.skipReason(candidate("minecraft:villager", false, true, false)).isEmpty());
        assertTrue(EntityFilterPolicy.skipReason(candidate("minecraft:armor_stand", false, true, false)).isEmpty());
    }

    @Test
    void playersNeverTravel() {
        Optional<EntityFilterPolicy.Skip> skip =
                EntityFilterPolicy.skipReason(candidate("minecraft:player", true, true, false));
        assertEquals(Optional.of(EntityFilterPolicy.Skip.PLAYER), skip);
    }

    @Test
    void modEntitiesAreLeftBehind() {
        Optional<EntityFilterPolicy.Skip> skip =
                EntityFilterPolicy.skipReason(candidate("touhou_little_maid:maid", false, true, false));
        assertEquals(Optional.of(EntityFilterPolicy.Skip.MOD_ENTITY), skip);
        assertEquals(Optional.of(EntityFilterPolicy.Skip.MOD_ENTITY),
                EntityFilterPolicy.skipReason(candidate("some_mod:boss", false, true, false)));
    }

    @Test
    void disposableVanillaEntitiesAreLeftBehind() {
        assertEquals(Optional.of(EntityFilterPolicy.Skip.NOT_LIVING),
                EntityFilterPolicy.skipReason(candidate("minecraft:item", false, false, false)));
        assertEquals(Optional.of(EntityFilterPolicy.Skip.NOT_LIVING),
                EntityFilterPolicy.skipReason(candidate("minecraft:arrow", false, false, false)));
        assertEquals(Optional.of(EntityFilterPolicy.Skip.NOT_LIVING),
                EntityFilterPolicy.skipReason(candidate("minecraft:experience_orb", false, false, false)));
        assertEquals(Optional.of(EntityFilterPolicy.Skip.NOT_LIVING),
                EntityFilterPolicy.skipReason(candidate("minecraft:falling_block", false, false, false)));
        assertEquals(Optional.of(EntityFilterPolicy.Skip.NOT_LIVING),
                EntityFilterPolicy.skipReason(candidate("minecraft:item_frame", false, false, false)));
    }

    @Test
    void mountedOrLeashedMoversCanBeFlaggedAsTransient() {
        assertEquals(Optional.of(EntityFilterPolicy.Skip.TRANSIENT),
                EntityFilterPolicy.skipReason(candidate("minecraft:chicken", false, true, true)));
    }

    @Test
    void playerWinsOverEveryOtherReason() {
        assertEquals(Optional.of(EntityFilterPolicy.Skip.PLAYER),
                EntityFilterPolicy.skipReason(candidate("minecraft:player", true, false, true)));
    }

    @Test
    void rejectsMalformedIds() {
        assertThrows(IllegalArgumentException.class, () -> candidate("zombie", false, true, false));
        assertThrows(IllegalArgumentException.class, () -> candidate(null, false, true, false));
    }
}
