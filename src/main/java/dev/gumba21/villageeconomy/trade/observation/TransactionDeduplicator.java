package dev.gumba21.villageeconomy.trade.observation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Short-lived execution-token cache. Repeated identical trades receive
 * different execution IDs and therefore remain distinct even in one tick.
 */
public final class TransactionDeduplicator {
    public static final int DEFAULT_CAPACITY = 2_048;
    public static final long DEFAULT_TTL_TICKS = 40L;

    private final int capacity;
    private final long ttlTicks;
    private final LinkedHashMap<TransactionKey, Long> expirations =
            new LinkedHashMap<>();

    public TransactionDeduplicator() {
        this(DEFAULT_CAPACITY, DEFAULT_TTL_TICKS);
    }

    public TransactionDeduplicator(int capacity, long ttlTicks) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (ttlTicks <= 0L) {
            throw new IllegalArgumentException("ttlTicks must be positive");
        }
        this.capacity = capacity;
        this.ttlTicks = ttlTicks;
    }

    public boolean isDuplicate(TransactionKey key, long gameTime) {
        Objects.requireNonNull(key, "key");
        expire(gameTime);
        if (expirations.containsKey(key)) {
            return true;
        }
        while (expirations.size() >= capacity) {
            Iterator<TransactionKey> keys = expirations.keySet().iterator();
            keys.next();
            keys.remove();
        }
        expirations.put(key, Math.addExact(gameTime, ttlTicks));
        return false;
    }

    public void expire(long gameTime) {
        Iterator<Map.Entry<TransactionKey, Long>> iterator =
                expirations.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() >= gameTime) {
                continue;
            }
            iterator.remove();
        }
    }

    public int size() {
        return expirations.size();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        expirations.clear();
    }
}
