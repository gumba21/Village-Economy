package dev.gumba21.villageeconomy.trade.mapping;

import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadEvidence;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadReconciler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageOwnershipIndexTest {
    private static final ResourceLocation OVERWORLD =
            new ResourceLocation("minecraft:overworld");
    private static final ResourceLocation NETHER =
            new ResourceLocation("minecraft:the_nether");

    @Test
    void spatialFallbackResolvesAndCachesLoadedVillage() {
        VillageOwnershipIndex index = new VillageOwnershipIndex(8);
        TrackedVillage village = village(OVERWORLD, new BlockPos(0, 64, 0), true);
        UUID villager = UUID.randomUUID();

        assertEquals(
                village.getId(),
                index.resolve(
                        villager,
                        OVERWORLD,
                        new BlockPos(4, 64, 4),
                        List.of(village)
                ).orElseThrow().getId()
        );
        assertEquals(1, index.size());
    }

    @Test
    void wrongDimensionNeverResolves() {
        VillageOwnershipIndex index = new VillageOwnershipIndex(8);
        TrackedVillage village = village(OVERWORLD, new BlockPos(0, 64, 0), true);

        assertTrue(index.resolve(
                UUID.randomUUID(),
                NETHER,
                new BlockPos(0, 64, 0),
                List.of(village)
        ).isEmpty());
    }

    @Test
    void unloadedVillageNeverResolves() {
        VillageOwnershipIndex index = new VillageOwnershipIndex(8);
        TrackedVillage village = village(
                OVERWORLD,
                new BlockPos(0, 64, 0),
                false
        );

        assertTrue(index.resolve(
                UUID.randomUUID(),
                OVERWORLD,
                new BlockPos(0, 64, 0),
                List.of(village)
        ).isEmpty());
    }

    @Test
    void outsideDetectionRadiusDoesNotResolve() {
        VillageOwnershipIndex index = new VillageOwnershipIndex(8);
        TrackedVillage village = village(OVERWORLD, new BlockPos(0, 64, 0), true);

        assertTrue(index.resolve(
                UUID.randomUUID(),
                OVERWORLD,
                new BlockPos(100, 64, 100),
                List.of(village)
        ).isEmpty());
    }

    @Test
    void staleMembershipFallsBackWithoutCrossDimensionAssociation() {
        VillageOwnershipIndex index = new VillageOwnershipIndex(8);
        UUID villager = UUID.randomUUID();
        TrackedVillage first = village(
                OVERWORLD,
                new BlockPos(0, 64, 0),
                true
        );
        TrackedVillage second = village(
                OVERWORLD,
                new BlockPos(200, 64, 200),
                true
        );
        index.resolve(
                villager,
                OVERWORLD,
                new BlockPos(0, 64, 0),
                List.of(first, second)
        );

        assertEquals(
                second.getId(),
                index.resolve(
                        villager,
                        OVERWORLD,
                        new BlockPos(200, 64, 200),
                        List.of(first, second)
                ).orElseThrow().getId()
        );
    }

    @Test
    void membershipCacheRemainsBounded() {
        VillageOwnershipIndex index = new VillageOwnershipIndex(2);
        TrackedVillage village = village(OVERWORLD, new BlockPos(0, 64, 0), true);
        for (int count = 0; count < 10; count++) {
            index.resolve(
                    UUID.randomUUID(),
                    OVERWORLD,
                    new BlockPos(0, 64, 0),
                    List.of(village)
            );
        }
        assertEquals(2, index.size());
    }

    @Test
    void villageResolutionSucceedsImmediatelyAfterAreaReloads() {
        VillageOwnershipIndex index = new VillageOwnershipIndex(8);
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        TrackedVillage village = village(
                OVERWORLD,
                new BlockPos(0, 64, 0),
                true
        );
        VillageLoadEvidence absent = VillageLoadEvidence.absent(4);
        reconciler.reconcile(village, false, absent);
        reconciler.reconcile(village, false, absent);
        assertTrue(index.resolve(
                UUID.randomUUID(),
                OVERWORLD,
                BlockPos.ZERO,
                List.of(village)
        ).isEmpty());

        reconciler.reconcile(
                village,
                false,
                new VillageLoadEvidence(4, 1, 1, 0)
        );

        assertEquals(
                village.getId(),
                index.resolve(
                        UUID.randomUUID(),
                        OVERWORLD,
                        BlockPos.ZERO,
                        List.of(village)
                ).orElseThrow().getId()
        );
    }

    private static TrackedVillage village(
            ResourceLocation dimension,
            BlockPos center,
            boolean loaded
    ) {
        return new TrackedVillage(
                UUID.randomUUID(),
                center,
                ResourceKey.create(Registries.DIMENSION, dimension),
                64,
                1_000L,
                1_000L,
                4,
                2,
                loaded
        );
    }
}
