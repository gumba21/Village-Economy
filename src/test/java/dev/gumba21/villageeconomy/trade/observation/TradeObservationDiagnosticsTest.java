package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import dev.gumba21.villageeconomy.trade.model.ObservationStatus;
import dev.gumba21.villageeconomy.trade.model.TransactionSource;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeObservationDiagnosticsTest {
    @Test
    void historyIsNewestFirstAndBounded() {
        TradeObservationDiagnostics diagnostics =
                new TradeObservationDiagnostics(3);
        for (long time = 1L; time <= 5L; time++) {
            diagnostics.record(observation(time, ObservationStatus.VALID));
        }

        assertEquals(3, diagnostics.historySize());
        assertEquals(5L, diagnostics.recent(3).get(0).gameTime());
        assertEquals(3L, diagnostics.recent(3).get(2).gameTime());
    }

    @Test
    void countersTrackStructuredFailures() {
        TradeObservationDiagnostics diagnostics =
                new TradeObservationDiagnostics(10);
        diagnostics.record(observation(1L, ObservationStatus.VALID));
        diagnostics.record(observation(2L, ObservationStatus.NO_VILLAGE));
        diagnostics.record(observation(3L, ObservationStatus.NO_MARKET));
        diagnostics.record(observation(
                4L,
                ObservationStatus.DUPLICATE_SUPPRESSED
        ));
        diagnostics.record(observation(
                5L,
                ObservationStatus.UNKNOWN_MARKET_ITEM
        ));

        TradeObservationSummary summary = diagnostics.summary();
        assertEquals(5L, summary.total());
        assertEquals(1L, summary.valid());
        assertEquals(1L, summary.noVillage());
        assertEquals(1L, summary.noMarket());
        assertEquals(1L, summary.duplicatesSuppressed());
        assertEquals(1L, summary.unknownOrUnsupported());
    }

    @Test
    void clearAffectsOnlyRuntimeState() {
        TradeObservationDiagnostics diagnostics =
                new TradeObservationDiagnostics(10);
        diagnostics.record(observation(1L, ObservationStatus.VALID));
        diagnostics.clear();

        assertEquals(0L, diagnostics.summary().total());
        assertTrue(diagnostics.recent(10).isEmpty());
    }

    @Test
    void emptyHistoryProducesAnEmptySummary() {
        TradeObservationSummary summary =
                new TradeObservationDiagnostics(10).summary();

        assertEquals(0L, summary.total());
        assertTrue(summary.mostRecent().isEmpty());
    }

    private static DiagnosticObservation observation(
            long time,
            ObservationStatus status
    ) {
        return new DiagnosticObservation(
                time,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ResourceLocation("minecraft:farmer"),
                TradeDirection.PLAYER_BUYS,
                Optional.of(new ResourceLocation("minecraft:bread")),
                4,
                Optional.of(MarketValue.ofBaseUnits(12L)),
                Optional.empty(),
                Optional.empty(),
                status,
                TransactionSource.TRADE_OVERHAUL,
                status.name()
        );
    }
}
