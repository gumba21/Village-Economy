package dev.gumba21.villageeconomy.village.lifecycle;

import dev.gumba21.villageeconomy.village.data.TrackedVillage;

import java.util.UUID;

/**
 * A deterministic, server-side association between a loaded villager and an
 * inactive persisted village. This is lifecycle evidence, not normal trade
 * resolution.
 */
public record VillageReactivationMatch(
        TrackedVillage village,
        UUID villagerId,
        long distanceSquared,
        int candidateCount,
        boolean equalDistanceTie
) {
    public VillageReactivationMatch {
        if (distanceSquared < 0L) {
            throw new IllegalArgumentException("distanceSquared cannot be negative");
        }
        if (candidateCount < 1) {
            throw new IllegalArgumentException("candidateCount must be positive");
        }
    }
}
