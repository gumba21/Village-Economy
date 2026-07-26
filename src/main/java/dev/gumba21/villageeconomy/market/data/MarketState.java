package dev.gumba21.villageeconomy.market.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketState {
    private final UUID villageId;
    private final long creationTimestamp;
    private long lastUpdateTimestamp;
    private final Map<ResourceLocation, MarketEntry> entries;

    public MarketState(
            UUID villageId,
            long creationTimestamp,
            long lastUpdateTimestamp,
            Collection<MarketEntry> entries
    ) {
        this.villageId = Objects.requireNonNull(villageId, "villageId");
        if (creationTimestamp <= 0L) {
            throw new IllegalArgumentException("creationTimestamp must be positive");
        }
        if (lastUpdateTimestamp < creationTimestamp) {
            throw new IllegalArgumentException(
                    "lastUpdateTimestamp cannot precede creationTimestamp"
            );
        }
        this.creationTimestamp = creationTimestamp;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.entries = new LinkedHashMap<>();
        for (MarketEntry entry : entries) {
            this.entries.putIfAbsent(entry.getItemId(), entry);
        }
    }

    public UUID getVillageId() {
        return villageId;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    public Collection<MarketEntry> getEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public Optional<MarketEntry> getEntry(ResourceLocation itemId) {
        return Optional.ofNullable(entries.get(itemId));
    }

    public int size() {
        return entries.size();
    }

    public void markUpdated(long timestamp) {
        if (timestamp <= 0L) {
            throw new IllegalArgumentException("timestamp must be positive");
        }
        lastUpdateTimestamp = Math.max(creationTimestamp, timestamp);
    }
}
