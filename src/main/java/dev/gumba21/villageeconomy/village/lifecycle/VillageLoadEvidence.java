package dev.gumba21.villageeconomy.village.lifecycle;

/**
 * A scan-local snapshot of non-forcing evidence that a tracked village's area
 * is active on the logical server.
 */
public record VillageLoadEvidence(
        int trackedVillagerCount,
        int loadedTrackedVillagerCount,
        int relevantLoadedChunkCount,
        int nearbyPlayerCount
) {
    public VillageLoadEvidence {
        if (trackedVillagerCount < 0
                || loadedTrackedVillagerCount < 0
                || relevantLoadedChunkCount < 0
                || nearbyPlayerCount < 0) {
            throw new IllegalArgumentException("Village load evidence cannot be negative");
        }
    }

    public boolean demonstratesLoaded() {
        return loadedTrackedVillagerCount > 0
                || relevantLoadedChunkCount > 0
                || nearbyPlayerCount > 0;
    }

    /**
     * Strong evidence required to wake a persisted inactive village. Loaded
     * chunks alone can keep an already-active village from being unloaded, but
     * reactivation additionally requires either a loaded villager or a nearby
     * player in those chunks.
     */
    public boolean demonstratesReactivation() {
        return loadedTrackedVillagerCount > 0
                || (relevantLoadedChunkCount > 0 && nearbyPlayerCount > 0);
    }

    public static VillageLoadEvidence absent(int trackedVillagerCount) {
        return new VillageLoadEvidence(trackedVillagerCount, 0, 0, 0);
    }
}
