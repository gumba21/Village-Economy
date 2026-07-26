package dev.gumba21.villageeconomy.market.simulation;

import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import dev.gumba21.villageeconomy.market.MarketManager;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.market.registry.DefaultTradeGoods;
import dev.gumba21.villageeconomy.market.registry.TradeGoodDefinition;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;

import java.util.Collection;

public final class MarketSimulator {
    private static final double MINIMUM_NORMALIZED_VALUE = 1.0E-6;
    private static final double PRICE_SIGNAL_LIMIT = 0.75;
    private static final double MAX_SUPPLY_SCALE = 3.0;
    private static final double MAX_DEMAND_SCALE = 3.0;

    public MarketSimulationResult simulateAll(
            Collection<TrackedVillage> villages,
            MarketManager marketManager,
            SimulationParameters parameters,
            long timestamp
    ) {
        long startedAt = System.nanoTime();
        int villagesUpdated = 0;
        int pricesChanged = 0;
        double largestIncrease = 0.0;
        double largestDecrease = 0.0;

        VillageEconomyDebugLogger.info(
                "Market update started: villages={}",
                villages.size()
        );

        for (TrackedVillage village : villages) {
            MarketState market = marketManager.getMarket(village.getId())
                    .orElseGet(() -> marketManager.createMarket(village.getId()));
            VillageResult result =
                    simulateVillage(village, market, parameters, timestamp);
            villagesUpdated++;
            pricesChanged += result.pricesChanged();
            largestIncrease = Math.max(largestIncrease, result.largestIncrease());
            largestDecrease = Math.min(largestDecrease, result.largestDecrease());

            VillageEconomyDebugLogger.info(
                    "Market village updated: village={}, loaded={}, trackedItems={}, "
                            + "pricesChanged={}",
                    village.getId(),
                    village.isLoaded(),
                    market.size(),
                    result.pricesChanged()
            );
        }

        if (villagesUpdated > 0) {
            marketManager.markDirty();
        }
        long durationNanos = System.nanoTime() - startedAt;
        VillageEconomyDebugLogger.info(
                "Market update finished: villages={}, pricesChanged={}, "
                        + "largestIncrease={}, largestDecrease={}, duration={} µs",
                villagesUpdated,
                pricesChanged,
                largestIncrease,
                largestDecrease,
                durationNanos / 1_000L
        );
        return new MarketSimulationResult(
                villagesUpdated,
                pricesChanged,
                largestIncrease,
                largestDecrease,
                durationNanos
        );
    }

    public VillageResult simulateVillage(
            TrackedVillage village,
            MarketState market,
            SimulationParameters parameters,
            long timestamp
    ) {
        int pricesChanged = 0;
        double largestIncrease = 0.0;
        double largestDecrease = 0.0;

        for (MarketEntry entry : market.getEntries()) {
            TradeGoodDefinition definition =
                    DefaultTradeGoods.getOrNull(entry.getItemId());
            if (definition == null) {
                continue;
            }

            double supplyTarget = applyRecovery(
                    calculateSupplyTarget(definition, village),
                    definition.initialSupply(),
                    parameters.recoveryRate()
            );
            double newSupply = approach(
                    entry.getSupply(),
                    supplyTarget,
                    responseRate(parameters.recoveryRate())
            );
            double demandTarget = applyRecovery(
                    calculateDemandTarget(
                            definition,
                            village,
                            newSupply
                    ),
                    definition.initialDemand(),
                    parameters.recoveryRate()
            );
            double newDemand = approach(
                    entry.getDemand(),
                    demandTarget,
                    responseRate(parameters.recoveryRate())
            );
            double newPrice = calculatePrice(
                    entry,
                    definition,
                    newSupply,
                    newDemand,
                    parameters
            );

            double priceDelta = newPrice - entry.getCurrentPrice();
            boolean changed = entry.updateSimulationValues(
                    newPrice,
                    parameters.minimumPriceMultiplier(),
                    parameters.maximumPriceMultiplier(),
                    sanitizeNonNegative(newSupply, definition.initialSupply()),
                    sanitizeNonNegative(newDemand, definition.initialDemand()),
                    timestamp
            );
            if (changed) {
                pricesChanged++;
                largestIncrease = Math.max(largestIncrease, priceDelta);
                largestDecrease = Math.min(largestDecrease, priceDelta);
            }
        }
        market.markUpdated(timestamp);
        return new VillageResult(
                pricesChanged,
                largestIncrease,
                largestDecrease
        );
    }

    public double calculateSupplyTarget(
            TradeGoodDefinition definition,
            TrackedVillage village
    ) {
        if (!village.isLoaded()) {
            return definition.initialSupply();
        }

        int population = Math.max(0, village.getVillagerCount());
        int workstations = Math.max(0, village.getWorkstationCount());
        int relevantProfessionals = definition.supplyDriver()
                .count(village.getProfessionCounts());
        double sizeAdjustment = (
                clamp(village.getDetectionRadius() / 64.0, 0.5, 2.0) - 1.0
        ) * 0.15;
        double scale = 0.55
                + Math.min(population, 100) * 0.025
                + Math.min(workstations, 100) * 0.01
                + Math.min(relevantProfessionals, 20) * 0.12
                + sizeAdjustment;
        return definition.initialSupply() * clamp(
                scale,
                0.35,
                MAX_SUPPLY_SCALE
        );
    }

    public double calculateDemandTarget(
            TradeGoodDefinition definition,
            TrackedVillage village,
            double supply
    ) {
        if (!village.isLoaded()) {
            return definition.initialDemand();
        }

        int population = Math.max(0, village.getVillagerCount());
        int relevantProfessionals = definition.supplyDriver()
                .count(village.getProfessionCounts());
        double normalizedSupply = Math.max(
                supply / definition.initialSupply(),
                MINIMUM_NORMALIZED_VALUE
        );
        double scarcity = clamp(1.0 / Math.sqrt(normalizedSupply), 0.6, 1.8);
        double populationScale = clamp(0.6 + population * 0.05, 0.5, 2.5);
        double professionUse =
                1.0 + Math.min(relevantProfessionals, 20) * 0.025;
        double scale = populationScale
                * (0.7 + scarcity * 0.3)
                * professionUse;
        return definition.initialDemand() * clamp(
                scale,
                0.35,
                MAX_DEMAND_SCALE
        );
    }

    public double calculatePrice(
            MarketEntry entry,
            TradeGoodDefinition definition,
            double supply,
            double demand,
            SimulationParameters parameters
    ) {
        double normalizedSupply = Math.max(
                supply / definition.initialSupply(),
                MINIMUM_NORMALIZED_VALUE
        );
        double normalizedDemand = Math.max(
                demand / definition.initialDemand(),
                MINIMUM_NORMALIZED_VALUE
        );
        double imbalance = clamp(
                Math.log(normalizedDemand / normalizedSupply),
                -4.0,
                4.0
        );
        double desiredMultiplier = 1.0
                + Math.tanh(imbalance) * PRICE_SIGNAL_LIMIT;
        desiredMultiplier = 1.0
                + (desiredMultiplier - 1.0) * (1.0 - parameters.recoveryRate());

        double currentMultiplier = entry.getCurrentPrice() / entry.getBasePrice();
        double priceResponse = parameters.priceChangeStrength() * 0.25;
        double nextMultiplier = approach(
                currentMultiplier,
                desiredMultiplier,
                priceResponse
        );
        nextMultiplier = approach(
                nextMultiplier,
                1.0,
                parameters.recoveryRate() * 0.1
        );
        nextMultiplier = clamp(
                sanitizeFinite(nextMultiplier, 1.0),
                parameters.minimumPriceMultiplier(),
                parameters.maximumPriceMultiplier()
        );

        double newPrice = entry.getBasePrice() * nextMultiplier;
        return clamp(
                sanitizeFinite(newPrice, entry.getBasePrice()),
                entry.getBasePrice() * parameters.minimumPriceMultiplier(),
                entry.getBasePrice() * parameters.maximumPriceMultiplier()
        );
    }

    private static double responseRate(double recoveryRate) {
        return clamp(0.04 + recoveryRate * 0.8, 0.01, 0.25);
    }

    private static double applyRecovery(
            double target,
            double equilibrium,
            double recoveryRate
    ) {
        return approach(target, equilibrium, recoveryRate);
    }

    private static double approach(double current, double target, double rate) {
        double safeCurrent = sanitizeFinite(current, target);
        double safeTarget = sanitizeFinite(target, safeCurrent);
        return safeCurrent + (safeTarget - safeCurrent) * clamp(rate, 0.0, 1.0);
    }

    private static double sanitizeNonNegative(double value, double fallback) {
        return Math.max(0.0, sanitizeFinite(value, fallback));
    }

    private static double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record VillageResult(
            int pricesChanged,
            double largestIncrease,
            double largestDecrease
    ) {
    }
}
