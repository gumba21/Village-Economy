package dev.gumba21.villageeconomy.market.simulation;

import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import dev.gumba21.villageeconomy.market.MarketManager;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.trade.model.ItemIdentityKind;
import dev.gumba21.villageeconomy.trade.model.ItemSnapshot;
import dev.gumba21.villageeconomy.trade.model.MarketReference;
import dev.gumba21.villageeconomy.trade.model.ObservedTransaction;
import dev.gumba21.villageeconomy.trade.model.TradeExecutionSnapshot;
import dev.gumba21.villageeconomy.trade.model.TransactionSource;
import dev.gumba21.villageeconomy.trade.model.VillageReference;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import dev.gumba21.villageeconomy.village.data.VillagePersistentState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservedTradeMarketSimulationTest {
    private static final ResourceLocation WHEAT =
            new ResourceLocation("minecraft", "wheat");
    private static final ResourceLocation CARROT =
            new ResourceLocation("minecraft", "carrot");
    private static final ResourceLocation FARMER =
            new ResourceLocation("minecraft", "farmer");
    private static final SimulationParameters PARAMETERS =
            new SimulationParameters(0.5, 2.0, 0.15, 0.02);
    private static final SimulationParameters NO_RECOVERY =
            new SimulationParameters(0.5, 2.0, 0.15, 0.0);

    private final MarketSimulator simulator = new MarketSimulator();

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void repeatedBuyingRaisesPriceGradually() {
        Fixture fixture = fixture();
        MarketEntry wheat = fixture.entry(WHEAT);

        for (int tick = 1; tick <= 20; tick++) {
            wheat.recordTradeObservation(TradeDirection.PLAYER_BUYS, 8);
            simulate(fixture, PARAMETERS, tick);
        }

        assertTrue(wheat.getCurrentMultiplier() > 1.0);
        assertTrue(wheat.getCurrentMultiplier() < 1.3);
        assertTrue(wheat.getLastNetPressure() > 0.0);
    }

    @Test
    void repeatedSellingLowersPriceGradually() {
        Fixture fixture = fixture();
        MarketEntry wheat = fixture.entry(WHEAT);

        for (int tick = 1; tick <= 20; tick++) {
            wheat.recordTradeObservation(TradeDirection.PLAYER_SELLS, 8);
            simulate(fixture, PARAMETERS, tick);
        }

        assertTrue(wheat.getCurrentMultiplier() < 1.0);
        assertTrue(wheat.getCurrentMultiplier() > 0.7);
        assertTrue(wheat.getLastNetPressure() < 0.0);
    }

    @Test
    void inactivityRecoversTowardEquilibriumWithoutOvershoot() {
        Fixture fixture = fixture();
        MarketEntry wheat = fixture.entry(WHEAT);
        for (int tick = 1; tick <= 20; tick++) {
            wheat.recordTradeObservation(TradeDirection.PLAYER_BUYS, 16);
            simulate(fixture, PARAMETERS, tick);
        }
        double raised = wheat.getCurrentMultiplier();

        for (int tick = 21; tick <= 500; tick++) {
            double previous = wheat.getCurrentMultiplier();
            simulate(fixture, PARAMETERS, tick);
            assertTrue(wheat.getCurrentMultiplier() <= previous);
            assertTrue(wheat.getCurrentMultiplier() >= 1.0);
        }

        assertTrue(wheat.getCurrentMultiplier() < raised);
        assertEquals(1.0, wheat.getCurrentMultiplier(), 1.0E-4);
    }

    @Test
    void repeatedPressureClampsAtConfiguredBounds() {
        Fixture fixture = fixture();
        MarketEntry wheat = fixture.entry(WHEAT);
        SimulationParameters bounds =
                new SimulationParameters(0.9, 1.1, 1.0, 0.0);

        for (int tick = 1; tick <= 100; tick++) {
            wheat.recordTradeObservation(TradeDirection.PLAYER_BUYS, 64);
            simulate(fixture, bounds, tick);
        }
        assertEquals(1.1, wheat.getCurrentMultiplier(), 1.0E-12);

        for (int tick = 101; tick <= 300; tick++) {
            wheat.recordTradeObservation(TradeDirection.PLAYER_SELLS, 64);
            simulate(fixture, bounds, tick);
        }
        assertEquals(0.9, wheat.getCurrentMultiplier(), 1.0E-12);
    }

    @Test
    void identicalTradeReplayIsDeterministic() {
        Fixture first = fixture();
        Fixture second = fixture();
        TradeDirection[] directions = {
                TradeDirection.PLAYER_BUYS,
                TradeDirection.PLAYER_SELLS,
                TradeDirection.PLAYER_BUYS,
                TradeDirection.PLAYER_BUYS,
                TradeDirection.PLAYER_SELLS
        };
        int[] quantities = {3, 8, 13, 2, 5};

        for (int tick = 0; tick < 200; tick++) {
            int index = tick % directions.length;
            first.entry(WHEAT).recordTradeObservation(
                    directions[index],
                    quantities[index]
            );
            second.entry(WHEAT).recordTradeObservation(
                    directions[index],
                    quantities[index]
            );
            simulate(first, PARAMETERS, tick + 1L);
            simulate(second, PARAMETERS, tick + 1L);
        }

        assertEquals(
                first.entry(WHEAT).getCurrentPrice(),
                second.entry(WHEAT).getCurrentPrice()
        );
        assertEquals(
                first.entry(WHEAT).getLastNetPressure(),
                second.entry(WHEAT).getLastNetPressure()
        );
    }

    @Test
    void mixedBuyingAndSellingUsesNetPressure() {
        Fixture fixture = fixture();
        MarketEntry wheat = fixture.entry(WHEAT);
        wheat.recordTradeObservation(TradeDirection.PLAYER_BUYS, 20);
        wheat.recordTradeObservation(TradeDirection.PLAYER_SELLS, 12);

        MarketSimulator.VillageResult result =
                simulate(fixture, NO_RECOVERY, 1L);

        assertEquals(2L, result.observationsProcessed());
        assertEquals(20L, wheat.getLastDemandAccumulator());
        assertEquals(12L, wheat.getLastSupplyAccumulator());
        assertTrue(wheat.getLastNetPressure() > 0.0);
        assertTrue(wheat.getCurrentMultiplier() > 1.0);
    }

    @Test
    void itemPricesUpdateIndependently() {
        Fixture fixture = fixture();
        MarketEntry wheat = fixture.entry(WHEAT);
        MarketEntry carrot = fixture.entry(CARROT);
        wheat.recordTradeObservation(TradeDirection.PLAYER_BUYS, 32);

        simulate(fixture, NO_RECOVERY, 1L);

        assertTrue(wheat.getCurrentMultiplier() > 1.0);
        assertEquals(1.0, carrot.getCurrentMultiplier());
        assertEquals(0L, carrot.getLastDemandAccumulator());
        assertEquals(0.0, carrot.getLastNetPressure());
    }

    @Test
    void pendingObservationsAreProcessedExactlyOnce() {
        Fixture fixture = fixture();
        MarketEntry wheat = fixture.entry(WHEAT);
        wheat.recordTradeObservation(TradeDirection.PLAYER_BUYS, 8);

        MarketSimulator.VillageResult first =
                simulate(fixture, NO_RECOVERY, 1L);
        double afterFirst = wheat.getCurrentMultiplier();
        MarketSimulator.VillageResult second =
                simulate(fixture, NO_RECOVERY, 2L);

        assertEquals(1L, first.observationsProcessed());
        assertEquals(0L, second.observationsProcessed());
        assertEquals(afterFirst, wheat.getCurrentMultiplier());
        assertEquals(0L, wheat.getPendingObservationCount());
        assertEquals(0L, wheat.getLastDemandAccumulator());
        assertEquals(0.0, wheat.getLastNetPressure());
    }

    @Test
    void pendingAndProcessedStatePersistAcrossSaveLoad() {
        Fixture original = fixture();
        MarketEntry wheat = original.entry(WHEAT);
        wheat.recordTradeObservation(TradeDirection.PLAYER_BUYS, 11);
        wheat.recordTradeObservation(TradeDirection.PLAYER_SELLS, 3);

        VillagePersistentState restored = VillagePersistentState.load(
                original.state().save(new CompoundTag())
        );
        TrackedVillage restoredVillage =
                restored.getVillages().iterator().next();
        MarketState restoredMarket =
                restored.getMarket(restoredVillage.getId()).orElseThrow();
        Fixture reloaded = new Fixture(
                restored,
                restoredVillage,
                restoredMarket
        );
        assertEquals(2L, reloaded.entry(WHEAT).getPendingObservationCount());

        simulate(reloaded, PARAMETERS, 77L);
        double simulatedPrice = reloaded.entry(WHEAT).getCurrentPrice();
        VillagePersistentState savedAgain = VillagePersistentState.load(
                restored.save(new CompoundTag())
        );
        MarketEntry finalWheat = savedAgain.getMarket(restoredVillage.getId())
                .orElseThrow()
                .getEntry(WHEAT)
                .orElseThrow();

        assertEquals(simulatedPrice, finalWheat.getCurrentPrice());
        assertEquals(11L, finalWheat.getLastDemandAccumulator());
        assertEquals(3L, finalWheat.getLastSupplyAccumulator());
        assertEquals(77L, finalWheat.getLastSimulationTick());
        assertEquals(0L, finalWheat.getPendingObservationCount());
    }

    @Test
    void validObservedTransactionAccumulatesOnOwningMarketOnly() {
        Fixture fixture = fixture();
        MarketObservationAccumulator accumulator =
                new MarketObservationAccumulator(
                        new MarketManager(fixture.state())
                );

        assertTrue(accumulator.accumulate(transaction(
                fixture.village().getId(),
                WHEAT,
                TradeDirection.PLAYER_BUYS,
                6
        )));

        assertEquals(6L, fixture.entry(WHEAT).getPendingDemandAccumulator());
        assertEquals(1L, fixture.entry(WHEAT).getPendingObservationCount());
        assertEquals(0L, fixture.entry(CARROT).getPendingObservationCount());
        assertTrue(fixture.state().isDirty());
    }

    private MarketSimulator.VillageResult simulate(
            Fixture fixture,
            SimulationParameters parameters,
            long tick
    ) {
        return simulator.simulateVillage(
                fixture.village(),
                fixture.market(),
                parameters,
                10_000L + tick,
                tick
        );
    }

    private Fixture fixture() {
        VillagePersistentState state = new VillagePersistentState();
        TrackedVillage village = new TrackedVillage(
                UUID.randomUUID(),
                BlockPos.ZERO,
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                8,
                4,
                true
        );
        assertTrue(state.add(village));
        MarketState market = new MarketManager(state)
                .createMarket(village.getId());
        return new Fixture(state, village, market);
    }

    private static ObservedTransaction transaction(
            UUID villageId,
            ResourceLocation itemId,
            TradeDirection direction,
            int quantity
    ) {
        ItemSnapshot item = new ItemSnapshot(
                itemId,
                quantity,
                false,
                false,
                ItemIdentityKind.ORDINARY
        );
        return new ObservedTransaction(
                UUID.randomUUID(),
                100L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Level.OVERWORLD.location(),
                BlockPos.ZERO,
                FARMER,
                2,
                direction,
                itemId,
                quantity,
                MarketValue.ofBaseUnits(100L),
                new VillageReference(
                        villageId,
                        Level.OVERWORLD.location(),
                        BlockPos.ZERO
                ),
                new MarketReference(villageId),
                TransactionSource.TRADE_OVERHAUL,
                new TradeExecutionSnapshot(
                        item,
                        quantity,
                        0,
                        MarketValue.ofBaseUnits(1_000L),
                        MarketValue.ofBaseUnits(900L)
                )
        );
    }

    private record Fixture(
            VillagePersistentState state,
            TrackedVillage village,
            MarketState market
    ) {
        private MarketEntry entry(ResourceLocation itemId) {
            return market.getEntry(itemId).orElseThrow();
        }
    }
}
