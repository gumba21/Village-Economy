package dev.gumba21.villageeconomy.village.lifecycle;

import dev.gumba21.villageeconomy.village.data.TrackedVillage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Conservative loaded-state state machine. Active evidence restores a village
 * immediately, while unload requires consecutive scans with no active evidence.
 */
public final class VillageLoadReconciler {
    public static final int DEFAULT_MISSING_SCANS_BEFORE_UNLOAD = 2;

    private final int missingScansBeforeUnload;
    private final Map<UUID, Integer> consecutiveMissingScans = new HashMap<>();

    public VillageLoadReconciler() {
        this(DEFAULT_MISSING_SCANS_BEFORE_UNLOAD);
    }

    VillageLoadReconciler(int missingScansBeforeUnload) {
        if (missingScansBeforeUnload < 2) {
            throw new IllegalArgumentException(
                    "missingScansBeforeUnload must be at least two"
            );
        }
        this.missingScansBeforeUnload = missingScansBeforeUnload;
    }

    public VillageLoadReconciliation reconcile(
            TrackedVillage village,
            boolean detectedThisScan,
            VillageLoadEvidence evidence
    ) {
        Objects.requireNonNull(village, "village");
        Objects.requireNonNull(evidence, "evidence");

        boolean previousLoaded = village.isLoaded();
        if (detectedThisScan || evidence.demonstratesLoaded()) {
            consecutiveMissingScans.remove(village.getId());
            boolean changed = village.updateLoadedState(true);
            return result(
                    village,
                    previousLoaded,
                    changed,
                    false,
                    activeReason(detectedThisScan, evidence),
                    evidence
            );
        }

        int missingScans = consecutiveMissingScans.merge(
                village.getId(),
                1,
                (previous, ignored) -> Math.min(
                        missingScansBeforeUnload,
                        previous + 1
                )
        );
        if (previousLoaded && missingScans < missingScansBeforeUnload) {
            return result(
                    village,
                    true,
                    false,
                    true,
                    "transient_missing_evidence_"
                            + missingScans
                            + "_of_"
                            + missingScansBeforeUnload,
                    evidence
            );
        }

        boolean changed = village.updateLoadedState(false);
        return result(
                village,
                previousLoaded,
                changed,
                false,
                "confirmed_no_active_evidence",
                evidence
        );
    }

    public VillageLoadReconciliation forceUnloaded(
            TrackedVillage village,
            String reason
    ) {
        Objects.requireNonNull(village, "village");
        Objects.requireNonNull(reason, "reason");
        consecutiveMissingScans.remove(village.getId());
        boolean previousLoaded = village.isLoaded();
        boolean changed = village.updateLoadedState(false);
        return result(
                village,
                previousLoaded,
                changed,
                false,
                reason,
                VillageLoadEvidence.absent(village.getVillagerCount())
        );
    }

    public void forget(UUID villageId) {
        consecutiveMissingScans.remove(villageId);
    }

    public void clear() {
        consecutiveMissingScans.clear();
    }

    private static String activeReason(
            boolean detectedThisScan,
            VillageLoadEvidence evidence
    ) {
        if (detectedThisScan) {
            return "detected_village_cluster";
        }
        if (evidence.loadedTrackedVillagerCount() > 0) {
            return "loaded_tracked_villagers";
        }
        if (evidence.relevantLoadedChunkCount() > 0) {
            return "relevant_loaded_chunks";
        }
        return "nearby_players";
    }

    private static VillageLoadReconciliation result(
            TrackedVillage village,
            boolean previousLoaded,
            boolean changed,
            boolean rejectedUnload,
            String reason,
            VillageLoadEvidence evidence
    ) {
        return new VillageLoadReconciliation(
                village.getId(),
                previousLoaded,
                village.isLoaded(),
                changed,
                rejectedUnload,
                reason,
                evidence
        );
    }
}
