package dev.gumba21.villageeconomy.market.data;

import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class MarketEntry {
    private final ResourceLocation itemId;
    private final double basePrice;
    private double currentPrice;
    private double minimumMultiplier;
    private double maximumMultiplier;
    private double supply;
    private double demand;
    private long lastModifiedTimestamp;
    private long pendingDemandAccumulator;
    private long pendingSupplyAccumulator;
    private long pendingObservationCount;
    private long lastDemandAccumulator;
    private long lastSupplyAccumulator;
    private double lastNetPressure;
    private double lastRecoveryContribution;
    private long lastSimulationTick;

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
        this(
                itemId,
                basePrice,
                currentPrice,
                minimumMultiplier,
                maximumMultiplier,
                supply,
                demand,
                lastModifiedTimestamp,
                0L,
                0L,
                0L,
                0L,
                0L,
                0.0,
                0.0,
                0L
        );
    }

    public MarketEntry(
            ResourceLocation itemId,
            double basePrice,
            double currentPrice,
            double minimumMultiplier,
            double maximumMultiplier,
            double supply,
            double demand,
            long lastModifiedTimestamp,
            long pendingDemandAccumulator,
            long pendingSupplyAccumulator,
            long pendingObservationCount,
            long lastDemandAccumulator,
            long lastSupplyAccumulator,
            double lastNetPressure,
            double lastRecoveryContribution,
            long lastSimulationTick
    ) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        validateValues(
                basePrice,
                currentPrice,
                minimumMultiplier,
                maximumMultiplier,
                supply,
                demand,
                lastModifiedTimestamp
        );
        validateTradeActivity(
                pendingDemandAccumulator,
                pendingSupplyAccumulator,
                pendingObservationCount,
                lastDemandAccumulator,
                lastSupplyAccumulator,
                lastNetPressure,
                lastRecoveryContribution,
                lastSimulationTick
        );
        this.basePrice = basePrice;
        this.currentPrice = currentPrice;
        this.minimumMultiplier = minimumMultiplier;
        this.maximumMultiplier = maximumMultiplier;
        this.supply = supply;
        this.demand = demand;
        this.lastModifiedTimestamp = lastModifiedTimestamp;
        this.pendingDemandAccumulator = pendingDemandAccumulator;
        this.pendingSupplyAccumulator = pendingSupplyAccumulator;
        this.pendingObservationCount = pendingObservationCount;
        this.lastDemandAccumulator = lastDemandAccumulator;
        this.lastSupplyAccumulator = lastSupplyAccumulator;
        this.lastNetPressure = lastNetPressure;
        this.lastRecoveryContribution = lastRecoveryContribution;
        this.lastSimulationTick = lastSimulationTick;
    }

    public boolean updateSimulationValues(
            double newCurrentPrice,
            double newMinimumMultiplier,
            double newMaximumMultiplier,
            double newSupply,
            double newDemand,
            long modifiedTimestamp
    ) {
        validateValues(
                basePrice,
                newCurrentPrice,
                newMinimumMultiplier,
                newMaximumMultiplier,
                newSupply,
                newDemand,
                modifiedTimestamp
        );
        boolean priceChanged = Math.abs(newCurrentPrice - currentPrice) > 1.0E-9;
        currentPrice = newCurrentPrice;
        minimumMultiplier = newMinimumMultiplier;
        maximumMultiplier = newMaximumMultiplier;
        supply = newSupply;
        demand = newDemand;
        lastModifiedTimestamp = modifiedTimestamp;
        return priceChanged;
    }

    public boolean recordTradeObservation(
            TradeDirection direction,
            int itemQuantity
    ) {
        Objects.requireNonNull(direction, "direction");
        if (itemQuantity <= 0) {
            throw new IllegalArgumentException("itemQuantity must be positive");
        }
        if (direction == TradeDirection.PLAYER_BUYS) {
            pendingDemandAccumulator = saturatingAdd(
                    pendingDemandAccumulator,
                    itemQuantity
            );
        } else if (direction == TradeDirection.PLAYER_SELLS) {
            pendingSupplyAccumulator = saturatingAdd(
                    pendingSupplyAccumulator,
                    itemQuantity
            );
        } else {
            return false;
        }
        pendingObservationCount = saturatingAdd(
                pendingObservationCount,
                1L
        );
        return true;
    }

    public boolean applyTradeSimulation(
            double newCurrentPrice,
            double newMinimumMultiplier,
            double newMaximumMultiplier,
            double newSupply,
            double newDemand,
            long processedDemandAccumulator,
            long processedSupplyAccumulator,
            double netPressure,
            double recoveryContribution,
            long simulationTick,
            long modifiedTimestamp
    ) {
        validateValues(
                basePrice,
                newCurrentPrice,
                newMinimumMultiplier,
                newMaximumMultiplier,
                newSupply,
                newDemand,
                modifiedTimestamp
        );
        validateTradeActivity(
                0L,
                0L,
                0L,
                processedDemandAccumulator,
                processedSupplyAccumulator,
                netPressure,
                recoveryContribution,
                simulationTick
        );
        if (processedDemandAccumulator != pendingDemandAccumulator
                || processedSupplyAccumulator != pendingSupplyAccumulator) {
            throw new IllegalStateException(
                    "Pending trade activity changed during simulation"
            );
        }

        boolean priceChanged =
                Math.abs(newCurrentPrice - currentPrice) > 1.0E-9;
        currentPrice = newCurrentPrice;
        minimumMultiplier = newMinimumMultiplier;
        maximumMultiplier = newMaximumMultiplier;
        supply = newSupply;
        demand = newDemand;
        lastModifiedTimestamp = modifiedTimestamp;
        lastDemandAccumulator = processedDemandAccumulator;
        lastSupplyAccumulator = processedSupplyAccumulator;
        lastNetPressure = netPressure;
        lastRecoveryContribution = recoveryContribution;
        lastSimulationTick = simulationTick;
        pendingDemandAccumulator = 0L;
        pendingSupplyAccumulator = 0L;
        pendingObservationCount = 0L;
        return priceChanged;
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

    public double getCurrentMultiplier() {
        return currentPrice / basePrice;
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

    public long getPendingDemandAccumulator() {
        return pendingDemandAccumulator;
    }

    public long getPendingSupplyAccumulator() {
        return pendingSupplyAccumulator;
    }

    public long getPendingObservationCount() {
        return pendingObservationCount;
    }

    public long getLastDemandAccumulator() {
        return lastDemandAccumulator;
    }

    public long getLastSupplyAccumulator() {
        return lastSupplyAccumulator;
    }

    public double getLastNetPressure() {
        return lastNetPressure;
    }

    public double getLastRecoveryContribution() {
        return lastRecoveryContribution;
    }

    public long getLastSimulationTick() {
        return lastSimulationTick;
    }

    private static void validateValues(
            double basePrice,
            double currentPrice,
            double minimumMultiplier,
            double maximumMultiplier,
            double supply,
            double demand,
            long lastModifiedTimestamp
    ) {
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
    }

    private static void validateTradeActivity(
            long pendingDemandAccumulator,
            long pendingSupplyAccumulator,
            long pendingObservationCount,
            long lastDemandAccumulator,
            long lastSupplyAccumulator,
            double lastNetPressure,
            double lastRecoveryContribution,
            long lastSimulationTick
    ) {
        if (pendingDemandAccumulator < 0L
                || pendingSupplyAccumulator < 0L
                || pendingObservationCount < 0L
                || lastDemandAccumulator < 0L
                || lastSupplyAccumulator < 0L
                || lastSimulationTick < 0L) {
            throw new IllegalArgumentException(
                    "Trade simulation counters cannot be negative"
            );
        }
        if (!Double.isFinite(lastNetPressure)
                || lastNetPressure < -1.0
                || lastNetPressure > 1.0) {
            throw new IllegalArgumentException(
                    "lastNetPressure must be finite and between -1 and 1"
            );
        }
        if (!Double.isFinite(lastRecoveryContribution)) {
            throw new IllegalArgumentException(
                    "lastRecoveryContribution must be finite"
            );
        }
    }

    private static long saturatingAdd(long current, long increment) {
        if (increment < 0L) {
            throw new IllegalArgumentException("increment cannot be negative");
        }
        if (Long.MAX_VALUE - current < increment) {
            return Long.MAX_VALUE;
        }
        return current + increment;
    }
}
