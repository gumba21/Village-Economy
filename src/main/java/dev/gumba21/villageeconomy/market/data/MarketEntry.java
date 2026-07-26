package dev.gumba21.villageeconomy.market.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class MarketEntry {
    private final ResourceLocation itemId;
    private final double basePrice;
    private final double currentPrice;
    private final double minimumMultiplier;
    private final double maximumMultiplier;
    private final double supply;
    private final double demand;
    private final long lastModifiedTimestamp;

    public MarketEntry(
            ResourceLocation itemId,
            double basePrice,
            double currentPrice,
            double minimumMultiplier,
            double maximumMultiplier,
            double supply,
            double demand,
            long lastModifiedTimestamp
    ) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        if (!Double.isFinite(basePrice) || basePrice <= 0.0) {
            throw new IllegalArgumentException("basePrice must be finite and positive");
        }
        if (!Double.isFinite(minimumMultiplier) || minimumMultiplier <= 0.0) {
            throw new IllegalArgumentException(
                    "minimumMultiplier must be finite and positive"
            );
        }
        if (!Double.isFinite(maximumMultiplier)
                || maximumMultiplier < minimumMultiplier) {
            throw new IllegalArgumentException(
                    "maximumMultiplier must be finite and at least the minimum"
            );
        }
        if (!Double.isFinite(currentPrice)
                || currentPrice < basePrice * minimumMultiplier
                || currentPrice > basePrice * maximumMultiplier) {
            throw new IllegalArgumentException(
                    "currentPrice must be within the configured price bounds"
            );
        }
        if (!Double.isFinite(supply) || supply < 0.0) {
            throw new IllegalArgumentException("supply must be finite and non-negative");
        }
        if (!Double.isFinite(demand) || demand < 0.0) {
            throw new IllegalArgumentException("demand must be finite and non-negative");
        }
        if (lastModifiedTimestamp <= 0L) {
            throw new IllegalArgumentException("lastModifiedTimestamp must be positive");
        }
        this.basePrice = basePrice;
        this.currentPrice = currentPrice;
        this.minimumMultiplier = minimumMultiplier;
        this.maximumMultiplier = maximumMultiplier;
        this.supply = supply;
        this.demand = demand;
        this.lastModifiedTimestamp = lastModifiedTimestamp;
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getMinimumMultiplier() {
        return minimumMultiplier;
    }

    public double getMaximumMultiplier() {
        return maximumMultiplier;
    }

    public double getSupply() {
        return supply;
    }

    public double getDemand() {
        return demand;
    }

    public long getLastModifiedTimestamp() {
        return lastModifiedTimestamp;
    }
}
