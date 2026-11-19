package com.homestead.mover.capture;

import java.util.Optional;

/**
 * Decides what the box may swallow. Pure: ids and flags in, verdict out.
 * Only vanilla living entities travel; mod entities and everything disposable stay behind.
 */
public final class EntityFilterPolicy {
    /** Why an entity is left in place. Reported in the confirm screen. */
    public enum Skip {
        PLAYER,
        MOD_ENTITY,
        NOT_LIVING,
        TRANSIENT
    }

    /** What the capture side knows about one entity, with no Minecraft types attached. */
    public record Candidate(String typeId, boolean player, boolean living, boolean transientEntity) {
        public Candidate {
            if (typeId == null || !typeId.contains(":")) {
                throw new IllegalArgumentException("malformed entity id: " + typeId);
            }
        }
    }

    private EntityFilterPolicy() {
    }

    /** Empty when the entity travels with the building; otherwise why it does not. */
    public static Optional<Skip> skipReason(Candidate candidate) {
        if (candidate.player()) {
            return Optional.of(Skip.PLAYER);
        }
        if (!candidate.typeId().startsWith("minecraft:")) {
            return Optional.of(Skip.MOD_ENTITY);
        }
        if (!candidate.living()) {
            return Optional.of(Skip.NOT_LIVING);
        }
        if (candidate.transientEntity()) {
            return Optional.of(Skip.TRANSIENT);
        }
        return Optional.empty();
    }
}
