package dev.gumba21.villageeconomy.village.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackedVillageTest {
    @Test
    void updatesMutableFieldsWithoutChangingIdentityOrDiscoveryTime() {
        UUID id = UUID.randomUUID();
        TrackedVillage village = village(id, new BlockPos(10, 64, 10), 1_000L);

        assertTrue(village.updateSeen(
                new BlockPos(20, 70, 20),
                96,
                2_000L,
                12,
                8
        ));

        assertEquals(id, village.getId());
        assertEquals(1_000L, village.getFirstDiscoveredTimestamp());
        assertEquals(2_000L, village.getLastSeenTimestamp());
        assertEquals(new BlockPos(20, 70, 20), village.getCenter());
        assertEquals(96, village.getDetectionRadius());
        assertEquals(12, village.getVillagerCount());
        assertEquals(8, village.getWorkstationCount());
        assertTrue(village.isLoaded());
    }

    @Test
    void containmentUsesDimensionAndHorizontalDetectionRadius() {
        TrackedVillage village = village(
                UUID.randomUUID(),
                new BlockPos(0, 64, 0),
                1_000L
        );

        assertTrue(village.contains(Level.OVERWORLD, new BlockPos(32, 200, 32)));
        assertFalse(village.contains(Level.OVERWORLD, new BlockPos(65, 64, 0)));
        assertFalse(village.contains(Level.NETHER, new BlockPos(0, 64, 0)));
    }

    @Test
    void loadedStateOnlyReportsChangesWhenValueChanges() {
        TrackedVillage village = village(
                UUID.randomUUID(),
                BlockPos.ZERO,
                1_000L
        );

        assertFalse(village.updateLoadedState(true));
        assertTrue(village.updateLoadedState(false));
        assertFalse(village.updateLoadedState(false));
    }

    private TrackedVillage village(UUID id, BlockPos center, long discovered) {
        return new TrackedVillage(
                id,
                center,
                Level.OVERWORLD,
                64,
                discovered,
                discovered,
                5,
                3,
                true
        );
    }
}
