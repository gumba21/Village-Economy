package dev.gumba21.villageeconomy.village.lifecycle;

import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Matches runtime evidence against inactive persisted villages without using
 * the normal ownership resolver. This breaks the reload dependency where the
 * resolver requires a village to be loaded before lifecycle code can see it.
 */
public final class VillageReactivationMatcher {
    private static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.comparingLong(Candidate::distanceSquared)
                    .thenComparing(candidate -> candidate.village().getId());

    public Optional<VillageReactivationMatch> matchLoadedVillager(
            UUID villagerId,
            ResourceKey<Level> dimension,
            BlockPos position,
            Collection<TrackedVillage> villages,
            int configuredRadius
    ) {
        Objects.requireNonNull(villagerId, "villagerId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(villages, "villages");
        if (configuredRadius <= 0) {
            throw new IllegalArgumentException("configuredRadius must be positive");
        }

        // A currently active owner wins. Never wake a second overlapping
        // persisted record and steal a villager from an active village.
        boolean hasActiveOwner = villages.stream().anyMatch(village ->
                village.isLoaded()
                        && contains(village, dimension, position, configuredRadius)
        );
        if (hasActiveOwner) {
            return Optional.empty();
        }

        Candidate best = null;
        int candidateCount = 0;
        int bestDistanceCount = 0;
        for (TrackedVillage village : villages) {
            if (village.isLoaded()
                    || !contains(village, dimension, position, configuredRadius)) {
                continue;
            }

            Candidate candidate = new Candidate(
                    village,
                    village.horizontalDistanceSquared(position)
            );
            candidateCount++;
            if (best == null) {
                best = candidate;
                bestDistanceCount = 1;
                continue;
            }

            int comparison = CANDIDATE_ORDER.compare(candidate, best);
            if (candidate.distanceSquared() == best.distanceSquared()) {
                bestDistanceCount++;
            } else if (candidate.distanceSquared() < best.distanceSquared()) {
                bestDistanceCount = 1;
            }
            if (comparison < 0) {
                best = candidate;
            }
        }

        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(new VillageReactivationMatch(
                best.village(),
                villagerId,
                best.distanceSquared(),
                candidateCount,
                bestDistanceCount > 1
        ));
    }

    private static boolean contains(
            TrackedVillage village,
            ResourceKey<Level> dimension,
            BlockPos position,
            int configuredRadius
    ) {
        if (!village.getDimension().equals(dimension)) {
            return false;
        }

        // Use the conservative intersection of the radius persisted with the
        // village and the currently configured radius. This guarantees that a
        // villager accepted for reactivation is also immediately resolvable by
        // the normal ownership index.
        int radius = Math.min(village.getDetectionRadius(), configuredRadius);
        return village.horizontalDistanceSquared(position)
                <= (long) radius * radius;
    }

    private record Candidate(TrackedVillage village, long distanceSquared) {
    }
}
