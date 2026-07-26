package dev.gumba21.villageeconomy.village.data;

import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagePersistentStateTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void roundTripsVillageDataAndReloadsRecordsAsUnloaded() {
        VillagePersistentState original = new VillagePersistentState();
        UUID id = UUID.randomUUID();
        original.add(village(
                id,
                new BlockPos(120, 68, -40),
                Level.NETHER,
                96,
                1_000L,
                2_000L,
                14,
                9
        ));

        VillagePersistentState restored =
                VillagePersistentState.load(original.save(new CompoundTag()));
        TrackedVillage village = restored.getVillages().iterator().next();

        assertEquals(1, restored.size());
        assertEquals(id, village.getId());
        assertEquals(new BlockPos(120, 68, -40), village.getCenter());
        assertEquals(Level.NETHER, village.getDimension());
        assertEquals(96, village.getDetectionRadius());
        assertEquals(1_000L, village.getFirstDiscoveredTimestamp());
        assertEquals(2_000L, village.getLastSeenTimestamp());
        assertEquals(14, village.getVillagerCount());
        assertEquals(9, village.getWorkstationCount());
        assertFalse(village.isLoaded());
    }

    @Test
    void repairsOlderEntriesWithMissingOptionalFields() {
        CompoundTag olderVillage = new CompoundTag();
        olderVillage.putInt("CenterX", 5);
        olderVillage.putInt("CenterY", 70);
        olderVillage.putInt("CenterZ", 8);
        olderVillage.putString("Dimension", "minecraft:overworld");

        VillagePersistentState restored = VillagePersistentState.load(rootWith(olderVillage));
        TrackedVillage village = restored.getVillages().iterator().next();

        assertEquals(1, restored.size());
        assertNotEquals(new UUID(0L, 0L), village.getId());
        assertEquals(64, village.getDetectionRadius());
        assertTrue(village.getFirstDiscoveredTimestamp() > 0L);
        assertEquals(
                village.getFirstDiscoveredTimestamp(),
                village.getLastSeenTimestamp()
        );
        assertFalse(village.isLoaded());
        assertTrue(restored.isDirty());
    }

    @Test
    void skipsEntriesWithoutAUsableCenter() {
        CompoundTag invalidVillage = new CompoundTag();
        invalidVillage.putUUID("Id", UUID.randomUUID());
        invalidVillage.putString("Dimension", "minecraft:overworld");

        VillagePersistentState restored = VillagePersistentState.load(rootWith(invalidVillage));

        assertEquals(0, restored.size());
        assertTrue(restored.isDirty());
    }

    @Test
    void rejectsDuplicateIdsAndNearbyDuplicateRecords() {
        VillagePersistentState state = new VillagePersistentState();
        UUID id = UUID.randomUUID();

        assertTrue(state.add(village(
                id,
                BlockPos.ZERO,
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                2,
                1
        )));
        assertFalse(state.add(village(
                id,
                new BlockPos(500, 64, 500),
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                2,
                1
        )));
        assertFalse(state.add(village(
                UUID.randomUUID(),
                new BlockPos(10, 64, 10),
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                2,
                1
        )));
        assertEquals(1, state.size());
    }

    @Test
    void queriesContainingAndNearestVillageByDimension() {
        VillagePersistentState state = new VillagePersistentState();
        TrackedVillage close = village(
                UUID.randomUUID(),
                BlockPos.ZERO,
                Level.OVERWORLD,
                32,
                1_000L,
                1_000L,
                2,
                1
        );
        TrackedVillage far = village(
                UUID.randomUUID(),
                new BlockPos(200, 64, 200),
                Level.OVERWORLD,
                32,
                1_000L,
                1_000L,
                2,
                1
        );
        TrackedVillage nether = village(
                UUID.randomUUID(),
                BlockPos.ZERO,
                Level.NETHER,
                32,
                1_000L,
                1_000L,
                2,
                1
        );
        state.add(close);
        state.add(far);
        state.add(nether);

        assertEquals(
                close.getId(),
                state.findContaining(Level.OVERWORLD, new BlockPos(10, 200, 10))
                        .orElseThrow()
                        .getId()
        );
        assertEquals(
                far.getId(),
                state.findNearest(Level.OVERWORLD, new BlockPos(180, 64, 180))
                        .orElseThrow()
                        .getId()
        );
        assertEquals(
                nether.getId(),
                state.findNearest(Level.NETHER, new BlockPos(1, 64, 1))
                        .orElseThrow()
                        .getId()
        );
    }

    private CompoundTag rootWith(CompoundTag village) {
        ListTag villages = new ListTag();
        villages.add(village);
        CompoundTag root = new CompoundTag();
        root.put("Villages", villages);
        return root;
    }

    private TrackedVillage village(
            UUID id,
            BlockPos center,
            net.minecraft.resources.ResourceKey<Level> dimension,
            int radius,
            long discovered,
            long lastSeen,
            int villagers,
            int workstations
    ) {
        return new TrackedVillage(
                id,
                center,
                dimension,
                radius,
                discovered,
                lastSeen,
                villagers,
                workstations,
                true
        );
    }
}
