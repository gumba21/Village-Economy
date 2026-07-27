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

    public static VillageLoadEvidence absent(int trackedVillagerCount) {
        return new VillageLoadEvidence(trackedVillagerCount, 0, 0, 0);
    }
}
