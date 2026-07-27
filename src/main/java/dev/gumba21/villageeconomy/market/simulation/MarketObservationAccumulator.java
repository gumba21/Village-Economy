package dev.gumba21.villageeconomy.market.simulation;

import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import dev.gumba21.villageeconomy.market.MarketManager;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.trade.model.ObservedTransaction;
import dev.gumba21.villageeconomy.trade.observation.ObservedTransactionListener;

import java.util.Objects;
import java.util.Optional;

/**
 * Reduces valid observed transactions into persistent, per-entry integer
 * counters. The diagnostics history remains independent and can stay bounded.
 */
public final class MarketObservationAccumulator
        implements ObservedTransactionListener {
    private final MarketManager marketManager;

    public MarketObservationAccumulator(MarketManager marketManager) {
        this.marketManager = Objects.requireNonNull(
                marketManager,
                "marketManager"
        );
    }

    @Override
    public void onTransaction(ObservedTransaction transaction) {
        accumulate(transaction);
    }

    public boolean accumulate(ObservedTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (!transaction.village().villageId().equals(
                transaction.market().villageId()
        )) {
            VillageEconomyDebugLogger.info(
                    "Ignored market observation with mismatched ownership: "
                            + "transaction={}, village={}, market={}",
                    transaction.transactionId(),
                    transaction.village().villageId(),
                    transaction.market().villageId()
            );
            return false;
        }

        Optional<MarketState> market = marketManager.getMarket(
                transaction.market().villageId()
        );
        if (market.isEmpty()) {
            return false;
        }
        Optional<MarketEntry> entry = market.get().getEntry(
                transaction.marketItemId()
        );
        if (entry.isEmpty()
                || !entry.get().recordTradeObservation(
                        transaction.direction(),
                        transaction.itemQuantity()
                )) {
            return false;
        }

        marketManager.markDirty();
        VillageEconomyDebugLogger.info(
                "Accumulated market observation: village={}, item={}, "
                        + "direction={}, quantity={}, pendingObservations={}",
                market.get().getVillageId(),
                entry.get().getItemId(),
                transaction.direction(),
                transaction.itemQuantity(),
                entry.get().getPendingObservationCount()
        );
        return true;
    }
}
