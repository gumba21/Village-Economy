package dev.gumba21.villageeconomy.village.lifecycle;

import dev.gumba21.villageeconomy.trade.mapping.VillageOwnershipIndex;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageReactivationMatcherTest {
    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
    private static final ResourceKey<Level> NETHER = Level.NETHER;
    private static final ResourceLocation OVERWORLD_ID =
            new ResourceLocation("minecraft:overworld");

    private final VillageReactivationMatcher matcher =
            new VillageReactivationMatcher();

    @Test
    void persistedVillageStartsUnloadedAndLoadedVillagerReactivatesIt() {
        UUID villageId = new UUID(0L, 20L);
        UUID villagerId = new UUID(0L, 200L);
        TrackedVillage village = village(
                villageId,
                BlockPos.ZERO,
                OVERWORLD,
                false
        );
        VillageOwnershipIndex resolver = new VillageOwnershipIndex(8);

        assertTrue(resolver.resolve(
                villagerId,
                OVERWORLD_ID,
                new BlockPos(4, 64, 4),
                List.of(village)
        ).isEmpty());

        VillageReactivationMatch match = matcher.matchLoadedVillager(
                villagerId,
                OVERWORLD,
                new BlockPos(4, 64, 4),
                List.of(village),
                64
        ).orElseThrow();
        new VillageLoadReconciler().reconcile(
                match.village(),
                false,
                new VillageLoadEvidence(7, 1, 1, 1)
        );
        resolver.associateLoadedVillager(villagerId, match.village());

        assertTrue(village.isLoaded());
        assertSame(village, match.village());
        assertEquals(villageId, match.village().getId());
        assertEquals(
                villageId,
                resolver.resolve(
                        villagerId,
                        OVERWORLD_ID,
                        new BlockPos(4, 64, 4),
                        List.of(village)
                ).orElseThrow().getId()
        );
    }

    @Test
    void nearestOverlappingPersistedVillageWinsDeterministically() {
        UUID firstId = new UUID(0L, 1L);
        UUID secondId = new UUID(0L, 2L);
        UUID villagerId = new UUID(0L, 100L);
        TrackedVillage first = village(
                firstId,
                new BlockPos(-10, 64, 0),
                OVERWORLD,
                false
        );
        TrackedVillage second = village(
                secondId,
                new BlockPos(10, 64, 0),
                OVERWORLD,
                false
        );

        VillageReactivationMatch match = matcher.matchLoadedVillager(
                villagerId,
                OVERWORLD,
                BlockPos.ZERO,
                List.of(second, first),
                64
        ).orElseThrow();

        assertEquals(firstId, match.village().getId());
        assertEquals(2, match.candidateCount());
        assertTrue(match.equalDistanceTie());
    }

    @Test
    void activeOverlappingOwnerPreventsOwnershipSteal() {
        UUID villagerId = UUID.randomUUID();
        TrackedVillage active = village(
                new UUID(0L, 2L),
                new BlockPos(4, 64, 0),
                OVERWORLD,
                true
        );
        TrackedVillage persisted = village(
                new UUID(0L, 1L),
                BlockPos.ZERO,
                OVERWORLD,
                false
        );

        assertTrue(matcher.matchLoadedVillager(
                villagerId,
                OVERWORLD,
                new BlockPos(2, 64, 0),
                List.of(persisted, active),
                64
        ).isEmpty());
        assertFalse(persisted.isLoaded());
    }

    @Test
    void distantAndWrongDimensionVillagersDoNotReactivateVillage() {
        TrackedVillage village = village(
                UUID.randomUUID(),
                BlockPos.ZERO,
                OVERWORLD,
                false
        );

        assertTrue(matcher.matchLoadedVillager(
                UUID.randomUUID(),
                OVERWORLD,
                new BlockPos(65, 64, 0),
                List.of(village),
                64
        ).isEmpty());
        assertTrue(matcher.matchLoadedVillager(
                UUID.randomUUID(),
                NETHER,
                BlockPos.ZERO,
                List.of(village),
                64
        ).isEmpty());
        assertFalse(village.isLoaded());
    }

    @Test
    void currentConfiguredRadiusLimitsPersistedReactivationRadius() {
        TrackedVillage village = new TrackedVillage(
                UUID.randomUUID(),
                BlockPos.ZERO,
                OVERWORLD,
                96,
                1_000L,
                1_000L,
                4,
                2,
                false
        );

        assertTrue(matcher.matchLoadedVillager(
                UUID.randomUUID(),
                OVERWORLD,
                new BlockPos(70, 64, 0),
                List.of(village),
                64
        ).isEmpty());
    }

    private static TrackedVillage village(
            UUID id,
            BlockPos center,
            ResourceKey<Level> dimension,
            boolean loaded
    ) {
        return new TrackedVillage(
                id,
                center,
                dimension,
                64,
                1_000L,
                1_000L,
                7,
                4,
                loaded
        );
    }
}
