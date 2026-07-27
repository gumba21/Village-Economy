package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.VillageEconomy;
import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.trade.mapping.MarketItemMapper;
import dev.gumba21.villageeconomy.trade.mapping.MarketItemMappingResult;
import dev.gumba21.villageeconomy.trade.model.ItemSnapshot;
import dev.gumba21.villageeconomy.trade.model.MarketReference;
import dev.gumba21.villageeconomy.trade.model.ObservationStatus;
import dev.gumba21.villageeconomy.trade.model.ObservedTransaction;
import dev.gumba21.villageeconomy.trade.model.TradeCapture;
import dev.gumba21.villageeconomy.trade.model.VillageReference;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-thread, read-only observation pipeline. It never mutates persisted
 * villages, markets, offers, or currency.
 */
public final class TradeObservationService {
    private static final long WARNING_INTERVAL_MILLIS = 60_000L;
    private static final int MAX_WARNING_KEYS = 64;

    private final VillageOwnershipResolver villageResolver;
    private final MarketResolver marketResolver;
    private final MarketItemMapper itemMapper;
    private final TransactionDeduplicator deduplicator;
    private final TradeObservationDiagnostics diagnostics;
    private final List<ObservedTransactionListener> listeners = new ArrayList<>();
    private final Map<String, Long> warningTimes = new HashMap<>();
    private boolean hookInitialized;

    public TradeObservationService(
            VillageOwnershipResolver villageResolver,
            MarketResolver marketResolver
    ) {
        this(
                villageResolver,
                marketResolver,
                new MarketItemMapper(),
                new TransactionDeduplicator(),
                new TradeObservationDiagnostics()
        );
    }

    public TradeObservationService(
            VillageOwnershipResolver villageResolver,
            MarketResolver marketResolver,
            MarketItemMapper itemMapper,
            TransactionDeduplicator deduplicator,
            TradeObservationDiagnostics diagnostics
    ) {
        this.villageResolver = Objects.requireNonNull(
                villageResolver,
                "villageResolver"
        );
        this.marketResolver = Objects.requireNonNull(
                marketResolver,
                "marketResolver"
        );
        this.itemMapper = Objects.requireNonNull(itemMapper, "itemMapper");
        this.deduplicator = Objects.requireNonNull(
                deduplicator,
                "deduplicator"
        );
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public void markHookInitialized() {
        hookInitialized = true;
    }

    public boolean isHookInitialized() {
        return hookInitialized;
    }

    public void registerListener(ObservedTransactionListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public Optional<ObservedTransaction> observe(TradeCapture capture) {
        Objects.requireNonNull(capture, "capture");
        try {
            return observeSafely(capture);
        } catch (RuntimeException failure) {
            record(capture, ObservationStatus.OBSERVER_ERROR, Optional.empty(),
                    Optional.empty(), failure.getMessage());
            warnRateLimited(
                    "observer:" + failure.getClass().getName(),
                    "Trade observation failed open for gameplay: execution={}, player={}, "
                            + "villager={}",
                    capture.executionId(),
                    capture.playerId(),
                    capture.villagerId(),
                    failure
            );
            return Optional.empty();
        }
    }

    private Optional<ObservedTransaction> observeSafely(TradeCapture capture) {
        if (capture.status() != ObservationStatus.VALID) {
            record(
                    capture,
                    capture.status(),
                    Optional.empty(),
                    Optional.empty(),
                    capture.detail()
            );
            debugFailure(capture, capture.status(), capture.detail());
            return Optional.empty();
        }

        if (capture.marketItem().isEmpty()) {
            return reject(capture, ObservationStatus.UNKNOWN_MARKET_ITEM,
                    "compatibility hook did not resolve an item");
        }
        if (capture.monetaryValue().isEmpty()
                || capture.monetaryValue().get().baseUnits() <= 0L) {
            return reject(capture, ObservationStatus.UNKNOWN_MONETARY_VALUE,
                    "compatibility hook did not resolve a positive value");
        }
        if (capture.itemQuantity() <= 0
                || capture.executionSnapshot().isEmpty()) {
            return reject(capture, ObservationStatus.UNKNOWN_TRADE_SHAPE,
                    "compatibility hook produced an incomplete execution snapshot");
        }

        ItemSnapshot item = capture.marketItem().get();
        MarketValue value = capture.monetaryValue().get();
        TransactionKey key = new TransactionKey(
                capture.executionId(),
                capture.playerId(),
                capture.villagerId(),
                capture.direction(),
                item.itemId(),
                capture.itemQuantity(),
                value.baseUnits()
        );
        if (deduplicator.isDuplicate(key, capture.gameTime())) {
            record(capture, ObservationStatus.DUPLICATE_SUPPRESSED,
                    Optional.empty(), Optional.empty(),
                    "duplicate callback for the same execution token");
            VillageEconomyDebugLogger.info(
                    "Suppressed duplicate trade observation: execution={}, player={}, "
                            + "villager={}",
                    capture.executionId(),
                    capture.playerId(),
                    capture.villagerId()
            );
            return Optional.empty();
        }

        Optional<TrackedVillage> resolvedVillage =
                villageResolver.resolve(capture);
        if (resolvedVillage.isEmpty()) {
            return reject(capture, ObservationStatus.NO_VILLAGE,
                    "no loaded village owns the villager position");
        }
        TrackedVillage village = resolvedVillage.get();

        Optional<MarketState> resolvedMarket =
                marketResolver.resolve(village.getId());
        if (resolvedMarket.isEmpty()) {
            record(capture, ObservationStatus.NO_MARKET,
                    Optional.of(village.getId()), Optional.empty(),
                    "the resolved village has no market");
            VillageEconomyDebugLogger.info(
                    "Trade observation has no market: villager={}, village={}",
                    capture.villagerId(),
                    village.getId()
            );
            return Optional.empty();
        }
        MarketState market = resolvedMarket.get();

        MarketItemMappingResult mapping = itemMapper.map(item, market);
        if (!(mapping instanceof MarketItemMappingResult.Mapped mapped)) {
            String detail = mapping instanceof MarketItemMappingResult.Unsupported unsupported
                    ? unsupported.reason()
                    : ((MarketItemMappingResult.Ambiguous) mapping).reason();
            record(capture, ObservationStatus.UNKNOWN_MARKET_ITEM,
                    Optional.of(village.getId()),
                    Optional.of(market.getVillageId()), detail);
            VillageEconomyDebugLogger.info(
                    "Unsupported market item observed: item={}, reason={}",
                    item.itemId(),
                    detail
            );
            return Optional.empty();
        }

        ObservedTransaction transaction = new ObservedTransaction(
                UUID.randomUUID(),
                capture.gameTime(),
                capture.playerId(),
                capture.villagerId(),
                capture.dimensionId(),
                capture.villagerPosition(),
                capture.professionId(),
                capture.villagerLevel(),
                capture.direction(),
                mapped.itemId(),
                capture.itemQuantity(),
                value,
                new VillageReference(
                        village.getId(),
                        village.getDimension().location(),
                        village.getCenter()
                ),
                new MarketReference(market.getVillageId()),
                capture.source(),
                capture.executionSnapshot().orElseThrow()
        );

        record(capture, ObservationStatus.VALID,
                Optional.of(village.getId()),
                Optional.of(market.getVillageId()), "valid observation");
        VillageEconomyDebugLogger.info(
                "Observed completed trade: direction={}, item={}, quantity={}, value={}, "
                        + "village={}",
                transaction.direction(),
                transaction.marketItemId(),
                transaction.itemQuantity(),
                transaction.monetaryValue().baseUnits(),
                village.getId()
        );
        publish(transaction);
        return Optional.of(transaction);
    }

    private Optional<ObservedTransaction> reject(
            TradeCapture capture,
            ObservationStatus status,
            String detail
    ) {
        record(capture, status, Optional.empty(), Optional.empty(), detail);
        debugFailure(capture, status, detail);
        return Optional.empty();
    }

    private void publish(ObservedTransaction transaction) {
        for (ObservedTransactionListener listener : List.copyOf(listeners)) {
            try {
                listener.onTransaction(transaction);
            } catch (RuntimeException listenerFailure) {
                warnRateLimited(
                        "listener:" + listener.getClass().getName(),
                        "Trade observation listener failed: transaction={}, listener={}",
                        transaction.transactionId(),
                        listener.getClass().getName(),
                        listenerFailure
                );
            }
        }
    }

    private void record(
            TradeCapture capture,
            ObservationStatus status,
            Optional<UUID> villageId,
            Optional<UUID> marketId,
            String detail
    ) {
        diagnostics.record(new DiagnosticObservation(
                capture.gameTime(),
                capture.playerId(),
                capture.villagerId(),
                capture.professionId(),
                capture.direction(),
                capture.marketItem().map(ItemSnapshot::itemId),
                capture.itemQuantity(),
                capture.monetaryValue(),
                villageId,
                marketId,
                status,
                capture.source(),
                Objects.requireNonNullElse(detail, "")
        ));
    }

    private static void debugFailure(
            TradeCapture capture,
            ObservationStatus status,
            String detail
    ) {
        VillageEconomyDebugLogger.info(
                "Trade observation rejected: execution={}, status={}, detail={}",
                capture.executionId(),
                status,
                detail
        );
    }

    private void warnRateLimited(
            String key,
            String message,
            Object... arguments
    ) {
        long now = System.currentTimeMillis();
        Long previous = warningTimes.get(key);
        if (previous != null && now - previous < WARNING_INTERVAL_MILLIS) {
            return;
        }
        if (warningTimes.size() >= MAX_WARNING_KEYS && !warningTimes.containsKey(key)) {
            warningTimes.clear();
        }
        warningTimes.put(key, now);
        VillageEconomy.LOGGER.warn(message, arguments);
    }

    public TradeObservationDiagnostics diagnostics() {
        return diagnostics;
    }

    public TransactionDeduplicator deduplicator() {
        return deduplicator;
    }

    public void clearDiagnostics() {
        diagnostics.clear();
        deduplicator.clear();
    }

    public void shutdown() {
        clearDiagnostics();
        listeners.clear();
        warningTimes.clear();
        hookInitialized = false;
    }
}
