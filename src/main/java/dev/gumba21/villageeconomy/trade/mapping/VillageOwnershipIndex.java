package dev.gumba21.villageeconomy.trade.mapping;

import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounded, non-persistent villager-to-village membership cache with a spatial
 * fallback over the existing tracked-village index.
 */
public final class VillageOwnershipIndex {
    public static final int DEFAULT_CAPACITY = 4_096;

    private final int capacity;
    private final LinkedHashMap<UUID, UUID> memberships =
            new LinkedHashMap<>(128, 0.75F, true);

    public VillageOwnershipIndex() {
        this(DEFAULT_CAPACITY);
    }

    public VillageOwnershipIndex(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public Optional<TrackedVillage> resolve(
            UUID villagerId,
            ResourceLocation dimensionId,
            BlockPos position,
            Collection<TrackedVillage> villages
    ) {
        UUID rememberedVillageId = memberships.get(villagerId);
        if (rememberedVillageId != null) {
            Optional<TrackedVillage> remembered = villages.stream()
                    .filter(village -> village.getId().equals(rememberedVillageId))
                    .filter(village -> valid(village, dimensionId, position))
                    .findFirst();
            if (remembered.isPresent()) {
                return remembered;
            }
            memberships.remove(villagerId);
        }

        Optional<TrackedVillage> resolved = villages.stream()
                .filter(village -> valid(village, dimensionId, position))
                .min((first, second) -> Long.compare(
                        first.horizontalDistanceSquared(position),
                        second.horizontalDistanceSquared(position)
                ));
        resolved.ifPresent(village -> remember(villagerId, village.getId()));
        return resolved;
    }

    public int size() {
        return memberships.size();
    }

    public void clear() {
        memberships.clear();
    }

    private static boolean valid(
            TrackedVillage village,
            ResourceLocation dimensionId,
            BlockPos position
    ) {
        return village.isLoaded()
                && village.getDimension().location().equals(dimensionId)
                && village.contains(village.getDimension(), position);
    }

    private void remember(UUID villagerId, UUID villageId) {
        memberships.put(villagerId, villageId);
        while (memberships.size() > capacity) {
            UUID eldest = memberships.keySet().iterator().next();
            memberships.remove(eldest);
        }
    }
}
