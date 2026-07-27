package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.trade.model.ObservationStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TradeObservationDiagnostics {
    public static final int DEFAULT_CAPACITY = 100;

    private final int capacity;
    private final Deque<DiagnosticObservation> history;
    private final Map<ObservationStatus, Long> statusCounts =
            new EnumMap<>(ObservationStatus.class);
    private long total;

    public TradeObservationDiagnostics() {
        this(DEFAULT_CAPACITY);
    }

    public TradeObservationDiagnostics(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.history = new ArrayDeque<>(capacity);
    }

    public void record(DiagnosticObservation observation) {
        total++;
        statusCounts.merge(observation.status(), 1L, Long::sum);
        history.addFirst(observation);
        while (history.size() > capacity) {
            history.removeLast();
        }
    }

    public List<DiagnosticObservation> recent(int requestedCount) {
        int count = Math.max(0, Math.min(requestedCount, capacity));
        List<DiagnosticObservation> result = new ArrayList<>(
                Math.min(count, history.size())
        );
        int copied = 0;
        for (DiagnosticObservation observation : history) {
            if (copied++ >= count) {
                break;
            }
            result.add(observation);
        }
        return List.copyOf(result);
    }

    public TradeObservationSummary summary() {
        long valid = count(ObservationStatus.VALID);
        long duplicates = count(ObservationStatus.DUPLICATE_SUPPRESSED);
        long noVillage = count(ObservationStatus.NO_VILLAGE);
        long noMarket = count(ObservationStatus.NO_MARKET);
        long unknown = total - valid - duplicates - noVillage - noMarket;
        return new TradeObservationSummary(
                total,
                valid,
                Math.max(0L, unknown),
                duplicates,
                noVillage,
                noMarket,
                Optional.ofNullable(history.peekFirst()),
                history.size()
        );
    }

    public long count(ObservationStatus status) {
        return statusCounts.getOrDefault(status, 0L);
    }

    public int historySize() {
        return history.size();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        total = 0L;
        statusCounts.clear();
        history.clear();
    }
}
