package dev.gumba21.villageeconomy.market.simulation;

import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import dev.gumba21.villageeconomy.market.MarketManager;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.market.registry.DefaultTradeGoods;
import dev.gumba21.villageeconomy.market.registry.TradeGoodDefinition;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import dev.gumba21.villageeconomy.village.data.VillagePersistentState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSimulatorTest {
    private static final ResourceLocation WHEAT =
            new ResourceLocation("minecraft", "wheat");
    private static final ResourceLocation FARMER =
            new ResourceLocation("minecraft", "farmer");
    private static final SimulationParameters PARAMETERS =
            new SimulationParameters(0.5, 2.0, 0.15, 0.02);

    private final MarketSimulator simulator = new MarketSimulator();

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void farmersIncreaseAgriculturalSupply() {
        TradeGoodDefinition wheat = DefaultTradeGoods.get(WHEAT).orElseThrow();
        TrackedVillage noFarmers = village(8, 4, true, Map.of());
        TrackedVillage farmers = village(8, 4, true, Map.of(FARMER, 4));

        assertTrue(
                simulator.calculateSupplyTarget(wheat, farmers)
                        > simulator.calculateSupplyTarget(wheat, noFarmers)
        );
    }

    @Test
    void scarcityRaisesDemandTarget() {
        TradeGoodDefinition wheat = DefaultTradeGoods.get(WHEAT).orElseThrow();
        TrackedVillage village = village(10, 5, true, Map.of(FARMER, 2));

        double scarceDemand =
                simulator.calculateDemandTarget(wheat, village, 10.0);
        double abundantDemand =
                simulator.calculateDemandTarget(wheat, village, 200.0);

        assertTrue(scarceDemand > abundantDemand);
    }

    @Test
    void excessDemandMovesPriceUpSmoothly() {
        Fixture fixture = fixture(village(12, 6, true, Map.of(FARMER, 3)));
        MarketEntry wheat = fixture.market().getEntry(WHEAT).orElseThrow();
        TradeGoodDefinition definition = DefaultTradeGoods.get(WHEAT).orElseThrow();

        double changed = simulator.calculatePrice(
                wheat,
                definition,
                20.0,
                160.0,
                PARAMETERS
        );

        assertTrue(changed > wheat.getBasePrice());
        assertTrue(changed < wheat.getBasePrice() * 1.1);
    }

    @Test
    void unloadedVillageRecoversSupplyAndDemandTowardEquilibrium() {
        Fixture fixture = fixture(village(12, 6, false, Map.of(FARMER, 3)));
        MarketEntry wheat = fixture.market().getEntry(WHEAT).orElseThrow();
        long timestamp = System.currentTimeMillis() + 1_000L;
        wheat.updateSimulationValues(
                1.5,
                0.5,
                2.0,
                0.0,
                500.0,
                timestamp
        );

        simulator.simulateVillage(
                fixture.village(),
                fixture.market(),
                PARAMETERS,
                timestamp + 1L
        );

        assertTrue(wheat.getSupply() > 0.0);
        assertTrue(wheat.getDemand() < 500.0);
    }

    @Test
    void pricesRemainInsideConfiguredClampLimits() {
        Fixture fixture = fixture(village(100, 100, true, Map.of(FARMER, 20)));
        long timestamp = System.currentTimeMillis() + 1_000L;

        for (int update = 0; update < 2_000; update++) {
            simulator.simulateVillage(
                    fixture.village(),
                    fixture.market(),
                    new SimulationParameters(0.75, 1.25, 1.0, 0.0),
                    timestamp + update
            );
        }

        for (MarketEntry entry : fixture.market().getEntries()) {
            double multiplier = entry.getCurrentPrice() / entry.getBasePrice();
            assertTrue(multiplier >= 0.75);
            assertTrue(multiplier <= 1.25);
        }
    }

    @Test
    void longRunningSimulationConvergesWithoutInvalidNumbers() {
        Fixture fixture = fixture(village(30, 18, true, Map.of(FARMER, 8)));
        long timestamp = System.currentTimeMillis() + 1_000L;

        for (int update = 0; update < 5_000; update++) {
            simulator.simulateVillage(
                    fixture.village(),
                    fixture.market(),
                    PARAMETERS,
                    timestamp + update
            );
        }
        double settledPrice = fixture.market()
                .getEntry(WHEAT)
                .orElseThrow()
                .getCurrentPrice();
        for (int update = 5_000; update < 6_000; update++) {
            simulator.simulateVillage(
                    fixture.village(),
                    fixture.market(),
                    PARAMETERS,
                    timestamp + update
            );
        }

        for (MarketEntry entry : fixture.market().getEntries()) {
            assertTrue(Double.isFinite(entry.getCurrentPrice()));
            assertTrue(Double.isFinite(entry.getSupply()));
            assertTrue(Double.isFinite(entry.getDemand()));
            assertTrue(entry.getCurrentPrice() > 0.0);
            assertTrue(entry.getSupply() >= 0.0);
            assertTrue(entry.getDemand() >= 0.0);
        }
        assertEquals(
                settledPrice,
                fixture.market().getEntry(WHEAT).orElseThrow().getCurrentPrice(),
                1.0E-9
        );
    }

    @Test
    void emptyVillageStillProducesFiniteRecoveryTick() {
        Fixture fixture = fixture(village(0, 0, true, Map.of()));
        MarketSimulator.VillageResult result = simulator.simulateVillage(
                fixture.village(),
                fixture.market(),
                PARAMETERS,
                System.currentTimeMillis() + 1_000L
        );

        assertTrue(result.pricesChanged() > 0);
        assertTrue(fixture.market().getEntries().stream().allMatch(
                entry -> Double.isFinite(entry.getCurrentPrice())
                        && entry.getSupply() >= 0.0
                        && entry.getDemand() >= 0.0
        ));
    }

    @Test
    void largeVillageRemainsBounded() {
        Fixture fixture = fixture(village(10_000, 10_000, true, Map.of(FARMER, 5_000)));
        long timestamp = System.currentTimeMillis() + 1_000L;

        for (int update = 0; update < 1_000; update++) {
            simulator.simulateVillage(
                    fixture.village(),
                    fixture.market(),
                    PARAMETERS,
                    timestamp + update
            );
        }

        for (MarketEntry entry : fixture.market().getEntries()) {
            assertTrue(entry.getSupply() <= 3.0
                    * DefaultTradeGoods.get(entry.getItemId())
                    .orElseThrow()
                    .initialSupply());
            assertTrue(entry.getDemand() <= 3.0
                    * DefaultTradeGoods.get(entry.getItemId())
                    .orElseThrow()
                    .initialDemand());
        }
    }

    @Test
    void multipleUpdatesRefreshEveryTimestamp() {
        Fixture fixture = fixture(village(8, 4, true, Map.of(FARMER, 2)));
        long timestamp = System.currentTimeMillis() + 1_000L;

        simulator.simulateVillage(
                fixture.village(),
                fixture.market(),
                PARAMETERS,
                timestamp
        );
        simulator.simulateVillage(
                fixture.village(),
                fixture.market(),
                PARAMETERS,
                timestamp + 1L
        );

        assertEquals(timestamp + 1L, fixture.market().getLastUpdateTimestamp());
        assertTrue(fixture.market().getEntries().stream().allMatch(
                entry -> entry.getLastModifiedTimestamp() == timestamp + 1L
        ));
    }

    @Test
    void identicalInputsProduceIdenticalSimulation() {
        UUID villageId = UUID.randomUUID();
        TrackedVillage firstVillage =
                village(villageId, 12, 8, true, Map.of(FARMER, 4));
        TrackedVillage secondVillage =
                village(villageId, 12, 8, true, Map.of(FARMER, 4));
        Fixture first = fixture(firstVillage);
        Fixture second = fixture(secondVillage);
        long timestamp = System.currentTimeMillis() + 1_000L;

        for (int update = 0; update < 100; update++) {
            simulator.simulateVillage(
                    first.village(),
                    first.market(),
                    PARAMETERS,
                    timestamp + update
            );
            simulator.simulateVillage(
                    second.village(),
                    second.market(),
                    PARAMETERS,
                    timestamp + update
            );
        }

        for (MarketEntry firstEntry : first.market().getEntries()) {
            MarketEntry secondEntry = second.market()
                    .getEntry(firstEntry.getItemId())
                    .orElseThrow();
            assertEquals(firstEntry.getSupply(), secondEntry.getSupply());
            assertEquals(firstEntry.getDemand(), secondEntry.getDemand());
            assertEquals(firstEntry.getCurrentPrice(), secondEntry.getCurrentPrice());
        }
    }

    @Test
    void simulateAllReportsVillageAndPriceCounts() {
        VillagePersistentState state = new VillagePersistentState();
        MarketManager manager = new MarketManager(state);
        TrackedVillage first = village(5, 3, true, Map.of(FARMER, 1));
        TrackedVillage second = village(15, 8, true, Map.of(FARMER, 5));
        state.add(first);
        state.add(second);
        manager.ensureMarkets(state.getVillages());

        MarketSimulationResult result = simulator.simulateAll(
                state.getVillages(),
                manager,
                PARAMETERS,
                System.currentTimeMillis() + 1_000L
        );

        assertEquals(2, result.villagesUpdated());
        assertTrue(result.pricesChanged() > 0);
        assertTrue(state.isDirty());
    }

    @Test
    void tenThousandUpdatePerformanceSanity() {
        Fixture fixture = fixture(village(30, 18, true, Map.of(FARMER, 8)));
        long timestamp = System.currentTimeMillis() + 1_000L;

        assertTimeout(Duration.ofSeconds(5), () -> {
            long startedAt = System.nanoTime();
            for (int update = 0; update < 10_000; update++) {
                simulator.simulateVillage(
                        fixture.village(),
                        fixture.market(),
                        PARAMETERS,
                        timestamp + update
                );
            }
            long durationNanos = System.nanoTime() - startedAt;
            System.out.printf(
                    "MARKET_BENCHMARK updates=10000 entriesPerUpdate=%d "
                            + "entryUpdates=%d durationMs=%.3f%n",
                    fixture.market().size(),
                    10_000 * fixture.market().size(),
                    durationNanos / 1_000_000.0
            );
        });
    }

    private Fixture fixture(TrackedVillage village) {
        VillagePersistentState state = new VillagePersistentState();
        state.add(village);
        MarketManager manager = new MarketManager(state);
        return new Fixture(
                village,
                manager.createMarket(village.getId())
        );
    }

    private TrackedVillage village(
            int population,
            int workstations,
            boolean loaded,
            Map<ResourceLocation, Integer> professions
    ) {
        return village(
                UUID.randomUUID(),
                population,
                workstations,
                loaded,
                professions
        );
    }

    private TrackedVillage village(
            UUID id,
            int population,
            int workstations,
            boolean loaded,
            Map<ResourceLocation, Integer> professions
    ) {
        return new TrackedVillage(
                id,
                BlockPos.ZERO,
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                population,
                workstations,
                loaded,
                professions
        );
    }

    private record Fixture(TrackedVillage village, MarketState market) {
    }
}
