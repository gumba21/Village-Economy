package dev.gumba21.villageeconomy.market.simulation;

public record MarketSimulationResult(
        int villagesUpdated,
        int pricesChanged,
        double largestIncrease,
        double largestDecrease,
        long durationNanos
) {
}
