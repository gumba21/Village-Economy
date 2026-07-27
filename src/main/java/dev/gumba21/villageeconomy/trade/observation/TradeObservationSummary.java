package dev.gumba21.villageeconomy.trade.observation;

import java.util.Optional;

public record TradeObservationSummary(
        long total,
        long valid,
        long unknownOrUnsupported,
        long duplicatesSuppressed,
        long noVillage,
        long noMarket,
        Optional<DiagnosticObservation> mostRecent,
        int historySize
) {
}
