package dev.gumba21.villageeconomy.village.data;

import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import dev.gumba21.villageeconomy.market.MarketManager;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.market.registry.DefaultTradeGoods;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadEvidence;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadReconciler;
import dev.gumba21.villageeconomy.village.lifecycle.VillageReactivationMatch;
import dev.gumba21.villageeconomy.village.lifecycle.VillageReactivationMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void roundTripsMarketDataAlongsideVillageData() {
        VillagePersistentState original = new VillagePersistentState();
        UUID villageId = UUID.randomUUID();
        original.add(village(
                villageId,
                BlockPos.ZERO,
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                3,
                2
        ));
        MarketState created = new MarketManager(original).createMarket(villageId);

        VillagePersistentState restored =
                VillagePersistentState.load(original.save(new CompoundTag()));
        MarketState loaded = restored.getMarket(villageId).orElseThrow();

        assertEquals(1, restored.marketSize());
        assertEquals(created.getVillageId(), loaded.getVillageId());
        assertEquals(created.getCreationTimestamp(), loaded.getCreationTimestamp());
        assertEquals(created.getLastUpdateTimestamp(), loaded.getLastUpdateTimestamp());
        assertEquals(DefaultTradeGoods.size(), loaded.size());
        assertEquals(
                1.0,
                loaded.getEntry(new net.minecraft.resources.ResourceLocation(
                        "minecraft",
                        "wheat"
                )).orElseThrow().getCurrentPrice()
        );
    }

    @Test
    void persistedReactivationRetainsVillageAndMarketOwnershipWithoutDuplicates() {
        VillagePersistentState original = new VillagePersistentState();
        UUID villageId = UUID.randomUUID();
        original.add(village(
                villageId,
                BlockPos.ZERO,
                Level.OVERWORLD,
                64,
                1_000L,
                2_000L,
                7,
                4
        ));
        MarketState originalMarket =
                new MarketManager(original).createMarket(villageId);
        MarketEntry originalWheat = originalMarket.getEntry(
                new ResourceLocation("minecraft", "wheat")
        ).orElseThrow();
        originalWheat.updateSimulationValues(
                1.25,
                0.5,
                2.0,
                73.0,
                81.0,
                3_000L
        );

        VillagePersistentState restored =
                VillagePersistentState.load(original.save(new CompoundTag()));
        TrackedVillage persistedVillage =
                restored.getVillages().iterator().next();
        MarketState persistedMarket =
                restored.getMarket(villageId).orElseThrow();
        MarketManager marketManager = new MarketManager(restored);

        assertFalse(persistedVillage.isLoaded());
        VillageReactivationMatch match =
                new VillageReactivationMatcher().matchLoadedVillager(
                        UUID.randomUUID(),
                        Level.OVERWORLD,
                        new BlockPos(4, 64, 4),
                        restored.getVillages(),
                        64
                ).orElseThrow();
        new VillageLoadReconciler().reconcile(
                match.village(),
                false,
                new VillageLoadEvidence(7, 1, 4, 1)
        );

        assertTrue(persistedVillage.isLoaded());
        assertEquals(villageId, persistedVillage.getId());
        assertEquals(1, restored.size());
        assertEquals(1, restored.marketSize());
        assertEquals(0, marketManager.ensureMarkets(restored.getVillages()));
        assertSame(persistedMarket, marketManager.getMarket(villageId).orElseThrow());
        MarketEntry persistedWheat = persistedMarket.getEntry(
                new ResourceLocation("minecraft", "wheat")
        ).orElseThrow();
        assertEquals(1.25, persistedWheat.getCurrentPrice());
        assertEquals(73.0, persistedWheat.getSupply());
        assertEquals(81.0, persistedWheat.getDemand());
    }

    @Test
    void generatesMarketDataForVersionOneVillageSaves() {
        VillagePersistentState original = new VillagePersistentState();
        UUID villageId = UUID.randomUUID();
        original.add(village(
                villageId,
                BlockPos.ZERO,
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                3,
                2
        ));
        CompoundTag versionOneRoot = original.save(new CompoundTag());
        versionOneRoot.putInt("DataVersion", 1);
        versionOneRoot.remove("Markets");

        VillagePersistentState restored = VillagePersistentState.load(versionOneRoot);
        MarketManager manager = new MarketManager(restored);

        assertFalse(manager.hasMarket(villageId));
        assertEquals(1, manager.ensureMarkets(restored.getVillages()));
        assertTrue(manager.hasMarket(villageId));
        assertTrue(restored.isDirty());
    }

    @Test
    void repairsInvalidMarketValuesAndRestoresMissingGoods() {
        VillagePersistentState original = new VillagePersistentState();
        UUID villageId = UUID.randomUUID();
        original.add(village(
                villageId,
                BlockPos.ZERO,
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                3,
                2
        ));
        new MarketManager(original).createMarket(villageId);
        CompoundTag root = original.save(new CompoundTag());
        CompoundTag market = root.getList("Markets", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0);
        ListTag entries = new ListTag();
        CompoundTag invalidWheat = new CompoundTag();
        invalidWheat.putString("Item", "minecraft:wheat");
        invalidWheat.putDouble("BasePrice", -1.0);
        invalidWheat.putDouble("CurrentPrice", Double.POSITIVE_INFINITY);
        invalidWheat.putDouble("MinimumMultiplier", -1.0);
        invalidWheat.putDouble("MaximumMultiplier", -1.0);
        invalidWheat.putDouble("Supply", -5.0);
        invalidWheat.putDouble("Demand", Double.NaN);
        invalidWheat.putLong("LastModified", -1L);
        entries.add(invalidWheat);
        market.put("Entries", entries);

        VillagePersistentState restored = VillagePersistentState.load(root);
        MarketState repaired = restored.getMarket(villageId).orElseThrow();
        MarketEntry wheat = repaired.getEntry(
                new net.minecraft.resources.ResourceLocation("minecraft", "wheat")
        ).orElseThrow();

        assertEquals(DefaultTradeGoods.size(), repaired.size());
        assertEquals(1.0, wheat.getBasePrice());
        assertEquals(1.0, wheat.getCurrentPrice());
        assertEquals(0.5, wheat.getMinimumMultiplier());
        assertEquals(2.0, wheat.getMaximumMultiplier());
        assertEquals(96.0, wheat.getSupply());
        assertEquals(64.0, wheat.getDemand());
        assertTrue(wheat.getLastModifiedTimestamp() > 0L);
        assertTrue(restored.isDirty());
    }

    @Test
    void savingMarketsPreservesUnrelatedRootData() {
        VillagePersistentState state = new VillagePersistentState();
        CompoundTag root = new CompoundTag();
        root.putString("UnrelatedData", "keep-me");

        CompoundTag saved = state.save(root);

        assertEquals("keep-me", saved.getString("UnrelatedData"));
    }

    @Test
    void roundTripsCachedProfessionCounts() {
        VillagePersistentState state = new VillagePersistentState();
        UUID villageId = UUID.randomUUID();
        ResourceLocation farmer =
                new ResourceLocation("minecraft", "farmer");
        state.add(new TrackedVillage(
                villageId,
                BlockPos.ZERO,
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                5,
                3,
                true,
                Map.of(farmer, 4)
        ));

        VillagePersistentState restored =
                VillagePersistentState.load(state.save(new CompoundTag()));
        TrackedVillage village = restored.getVillages().iterator().next();

        assertEquals(4, village.getProfessionCount(farmer));
        assertFalse(restored.isDirty());
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
