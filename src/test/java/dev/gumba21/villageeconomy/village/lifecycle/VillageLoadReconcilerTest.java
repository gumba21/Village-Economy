package dev.gumba21.villageeconomy.village.lifecycle;

import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageLoadReconcilerTest {
    @Test
    void activeVillageRemainsLoadedAcrossRepeatedReconciliationTicks() {
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        TrackedVillage village = village(true);
        VillageLoadEvidence evidence = new VillageLoadEvidence(4, 4, 12, 1);

        for (int tick = 0; tick < 20; tick++) {
            VillageLoadReconciliation result =
                    reconciler.reconcile(village, true, evidence);
            assertTrue(village.isLoaded());
            assertFalse(result.changed());
        }
    }

    @Test
    void oneTransientEmptyScanDoesNotUnloadVillage() {
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        TrackedVillage village = village(true);

        VillageLoadReconciliation result = reconciler.reconcile(
                village,
                false,
                VillageLoadEvidence.absent(4)
        );

        assertTrue(village.isLoaded());
        assertTrue(result.rejectedUnload());
    }

    @Test
    void loadedVillagersPreventFalseUnload() {
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        TrackedVillage village = village(true);

        for (int tick = 0; tick < 20; tick++) {
            reconciler.reconcile(
                    village,
                    false,
                    new VillageLoadEvidence(4, 1, 0, 0)
            );
        }

        assertTrue(village.isLoaded());
    }

    @Test
    void loadedRelevantChunksPreventFalseUnload() {
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        TrackedVillage village = village(true);

        for (int tick = 0; tick < 20; tick++) {
            reconciler.reconcile(
                    village,
                    false,
                    new VillageLoadEvidence(4, 0, 1, 0)
            );
        }

        assertTrue(village.isLoaded());
    }

    @Test
    void nearbyPlayerPreventsFalseUnload() {
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        TrackedVillage village = village(true);

        reconciler.reconcile(
                village,
                false,
                new VillageLoadEvidence(4, 0, 0, 1)
        );
        reconciler.reconcile(
                village,
                false,
                new VillageLoadEvidence(4, 0, 0, 1)
        );

        assertTrue(village.isLoaded());
    }

    @Test
    void genuineChunkAndEntityUnloadEventuallyMarksVillageUnloaded() {
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        TrackedVillage village = village(true);
        VillageLoadEvidence absent = VillageLoadEvidence.absent(4);

        reconciler.reconcile(village, false, absent);
        assertTrue(village.isLoaded());

        VillageLoadReconciliation result =
                reconciler.reconcile(village, false, absent);
        assertFalse(village.isLoaded());
        assertTrue(result.changed());
    }

    @Test
    void reloadingAreaRestoresLoadedImmediately() {
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        TrackedVillage village = village(true);
        VillageLoadEvidence absent = VillageLoadEvidence.absent(4);
        reconciler.reconcile(village, false, absent);
        reconciler.reconcile(village, false, absent);
        assertFalse(village.isLoaded());

        VillageLoadReconciliation result = reconciler.reconcile(
                village,
                false,
                new VillageLoadEvidence(4, 1, 1, 0)
        );

        assertTrue(village.isLoaded());
        assertTrue(result.changed());
    }

    private static TrackedVillage village(boolean loaded) {
        return new TrackedVillage(
                UUID.randomUUID(),
                BlockPos.ZERO,
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                4,
                2,
                loaded
        );
    }
}
