package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.trade.model.ItemIdentityKind;
import dev.gumba21.villageeconomy.trade.model.ItemSnapshot;
import dev.gumba21.villageeconomy.trade.model.ObservationStatus;
import dev.gumba21.villageeconomy.trade.model.ObservedTransaction;
import dev.gumba21.villageeconomy.trade.model.TradeCapture;
import dev.gumba21.villageeconomy.trade.model.TradeExecutionSnapshot;
import dev.gumba21.villageeconomy.trade.model.TransactionSource;
import dev.gumba21.villageeconomy.trade.mapping.VillageOwnershipIndex;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadEvidence;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadReconciler;
import dev.gumba21.villageeconomy.village.lifecycle.VillageReactivationMatch;
import dev.gumba21.villageeconomy.village.lifecycle.VillageReactivationMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeObservationServiceTest {
    private static final ResourceLocation OVERWORLD =
            new ResourceLocation("minecraft:overworld");
    private static final ResourceLocation BREAD =
            new ResourceLocation("minecraft:bread");
    private static final long NOW = 1_000L;

    @Test
    void validCompletedTransactionReachesListener() {
        Fixture fixture = fixture();
        List<ObservedTransaction> received = new ArrayList<>();
        fixture.service.registerListener(received::add);

        Optional<ObservedTransaction> observed =
                fixture.service.observe(validCapture(1L));

        assertTrue(observed.isPresent());
        assertEquals(1, received.size());
        assertEquals(BREAD, received.get(0).marketItemId());
        assertEquals(4, received.get(0).itemQuantity());
        assertEquals(12L, received.get(0).monetaryValue().baseUnits());
    }

    @Test
    void invalidCaptureDoesNotReachNormalListener() {
        Fixture fixture = fixture();
        AtomicInteger received = new AtomicInteger();
        fixture.service.registerListener(value -> received.incrementAndGet());

        fixture.service.observe(invalidCapture(1L));

        assertEquals(0, received.get());
        assertEquals(1L, fixture.service.diagnostics()
                .count(ObservationStatus.UNKNOWN_MONETARY_VALUE));
    }

    @Test
    void duplicateCallbackEmitsExactlyOnce() {
        Fixture fixture = fixture();
        AtomicInteger received = new AtomicInteger();
        fixture.service.registerListener(value -> received.incrementAndGet());
        TradeCapture capture = validCapture(1L);

        fixture.service.observe(capture);
        fixture.service.observe(capture);

        assertEquals(1, received.get());
        assertEquals(1L, fixture.service.diagnostics()
                .count(ObservationStatus.DUPLICATE_SUPPRESSED));
    }

    @Test
    void repeatedIdenticalTradesWithNewExecutionIdsRemainDistinct() {
        Fixture fixture = fixture();
        AtomicInteger received = new AtomicInteger();
        fixture.service.registerListener(value -> received.incrementAndGet());

        fixture.service.observe(validCapture(1L));
        fixture.service.observe(validCapture(2L));

        assertEquals(2, received.get());
    }

    @Test
    void missingVillageAndMarketFailClosedForAnalytics() {
        TradeObservationService noVillage = new TradeObservationService(
                capture -> Optional.empty(),
                villageId -> Optional.empty()
        );
        noVillage.observe(validCapture(1L));
        assertEquals(1L, noVillage.diagnostics()
                .count(ObservationStatus.NO_VILLAGE));

        Fixture fixture = fixture();
        TradeObservationService noMarket = new TradeObservationService(
                capture -> Optional.of(fixture.village),
                villageId -> Optional.empty()
        );
        noMarket.observe(validCapture(2L));
        assertEquals(1L, noMarket.diagnostics()
                .count(ObservationStatus.NO_MARKET));
    }

    @Test
    void unsupportedItemDoesNotPublish() {
        Fixture fixture = fixture();
        AtomicInteger received = new AtomicInteger();
        fixture.service.registerListener(value -> received.incrementAndGet());
        TradeCapture apple = capture(
                1L,
                new ResourceLocation("minecraft:apple"),
                ObservationStatus.VALID
        );

        fixture.service.observe(apple);

        assertEquals(0, received.get());
        assertEquals(1L, fixture.service.diagnostics()
                .count(ObservationStatus.UNKNOWN_MARKET_ITEM));
    }

    @Test
    void listenerFailureIsolatedAndOrderingDeterministic() {
        Fixture fixture = fixture();
        List<Long> order = new ArrayList<>();
        fixture.service.registerListener(value -> {
            throw new IllegalStateException("expected listener failure");
        });
        fixture.service.registerListener(value -> order.add(value.gameTime()));

        fixture.service.observe(validCapture(1L));
        fixture.service.observe(capture(2L, BREAD, ObservationStatus.VALID));

        assertEquals(List.of(100L, 100L), order);
        assertEquals(2L, fixture.service.diagnostics()
                .count(ObservationStatus.VALID));
    }

    @Test
    void observationDoesNotMutateMarketState() {
        Fixture fixture = fixture();
        MarketEntry entry = fixture.market.getEntry(BREAD).orElseThrow();
        double supply = entry.getSupply();
        double demand = entry.getDemand();
        double price = entry.getCurrentPrice();
        long updated = fixture.market.getLastUpdateTimestamp();

        fixture.service.observe(validCapture(1L));

        assertEquals(supply, entry.getSupply());
        assertEquals(demand, entry.getDemand());
        assertEquals(price, entry.getCurrentPrice());
        assertEquals(updated, fixture.market.getLastUpdateTimestamp());
    }

    @Test
    void tradeObservationResolvesVillageAndMarketAfterAreaReloads() {
        Fixture fixture = fixture();
        fixture.village.updateLoadedState(false);
        TradeCapture completedTrade = validCapture(1L);
        VillageOwnershipIndex index = new VillageOwnershipIndex(8);

        assertTrue(index.resolve(
                completedTrade.villagerId(),
                completedTrade.dimensionId(),
                completedTrade.villagerPosition(),
                List.of(fixture.village)
        ).isEmpty());

        VillageReactivationMatch match =
                new VillageReactivationMatcher().matchLoadedVillager(
                        completedTrade.villagerId(),
                        fixture.village.getDimension(),
                        completedTrade.villagerPosition(),
                        List.of(fixture.village),
                        64
                ).orElseThrow();
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        reconciler.reconcile(
                match.village(),
                false,
                new VillageLoadEvidence(4, 1, 1, 0)
        );
        index.associateLoadedVillager(
                completedTrade.villagerId(),
                match.village()
        );
        TradeObservationService service = new TradeObservationService(
                capture -> index.resolve(
                        capture.villagerId(),
                        capture.dimensionId(),
                        capture.villagerPosition(),
                        List.of(fixture.village)
                ),
                villageId -> villageId.equals(fixture.village.getId())
                        ? Optional.of(fixture.market)
                        : Optional.empty()
        );

        Optional<ObservedTransaction> observed = service.observe(completedTrade);

        assertTrue(observed.isPresent());
        assertEquals(
                fixture.village.getId(),
                observed.orElseThrow().village().villageId()
        );
        assertEquals(
                fixture.market.getVillageId(),
                observed.orElseThrow().market().villageId()
        );
        assertEquals(
                0L,
                service.diagnostics().count(ObservationStatus.NO_VILLAGE)
        );
    }

    @Test
    void syntheticObservationBenchmarkKeepsCachesBounded() {
        Fixture fixture = fixture();
        AtomicInteger observed = new AtomicInteger();
        fixture.service.registerListener(value -> observed.incrementAndGet());
        long started = System.nanoTime();
        int count = 100_000;
        for (int index = 1; index <= count; index++) {
            fixture.service.observe(validCapture(index));
        }
        double durationMillis = (System.nanoTime() - started) / 1_000_000.0;

        assertEquals(count, observed.get());
        assertTrue(fixture.service.diagnostics().historySize()
                <= fixture.service.diagnostics().capacity());
        assertTrue(fixture.service.deduplicator().size()
                <= fixture.service.deduplicator().capacity());
        System.out.printf(
                "TRADE_OBSERVATION_BENCHMARK observations=%d durationMs=%.3f%n",
                count,
                durationMillis
        );
    }

    private static Fixture fixture() {
        UUID villageId = UUID.randomUUID();
        TrackedVillage village = new TrackedVillage(
                villageId,
                new BlockPos(0, 64, 0),
                ResourceKey.create(Registries.DIMENSION, OVERWORLD),
                64,
                NOW,
                NOW,
                4,
                2,
                true
        );
        MarketState market = new MarketState(
                villageId,
                NOW,
                NOW,
                List.of(new MarketEntry(
                        BREAD,
                        1.0,
                        1.0,
                        0.5,
                        2.0,
                        10.0,
                        10.0,
                        NOW
                ))
        );
        return new Fixture(
                village,
                market,
                new TradeObservationService(
                        capture -> Optional.of(village),
                        ignored -> Optional.of(market)
                )
        );
    }

    private static TradeCapture validCapture(long executionId) {
        return capture(executionId, BREAD, ObservationStatus.VALID);
    }

    private static TradeCapture invalidCapture(long executionId) {
        return new TradeCapture(
                executionId,
                100L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                OVERWORLD,
                new BlockPos(0, 64, 0),
                new ResourceLocation("minecraft:farmer"),
                2,
                TradeDirection.PLAYER_BUYS,
                Optional.of(item(BREAD, 4)),
                4,
                Optional.empty(),
                TransactionSource.TRADE_OVERHAUL,
                ObservationStatus.UNKNOWN_MONETARY_VALUE,
                "unknown exact value",
                Optional.empty()
        );
    }

    private static TradeCapture capture(
            long executionId,
            ResourceLocation itemId,
            ObservationStatus status
    ) {
        ItemSnapshot item = item(itemId, 4);
        return new TradeCapture(
                executionId,
                100L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                OVERWORLD,
                new BlockPos(0, 64, 0),
                new ResourceLocation("minecraft:farmer"),
                2,
                TradeDirection.PLAYER_BUYS,
                Optional.of(item),
                4,
                Optional.of(MarketValue.ofBaseUnits(12L)),
                TransactionSource.TRADE_OVERHAUL,
                status,
                status.name(),
                Optional.of(new TradeExecutionSnapshot(
                        item(itemId, 16),
                        16,
                        12,
                        MarketValue.ofBaseUnits(100L),
                        MarketValue.ofBaseUnits(88L)
                ))
        );
    }

    private static ItemSnapshot item(ResourceLocation id, int count) {
        return new ItemSnapshot(
                id,
                count,
                false,
                false,
                ItemIdentityKind.ORDINARY
        );
    }

    private record Fixture(
            TrackedVillage village,
            MarketState market,
            TradeObservationService service
    ) {
    }
}
