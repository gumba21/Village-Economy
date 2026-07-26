package dev.gumba21.villageeconomy.market.registry;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record TradeGoodDefinition(
        ResourceLocation itemId,
        double basePrice,
        double initialSupply,
        double initialDemand,
        SupplyDriver supplyDriver
) {
    public TradeGoodDefinition {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(supplyDriver, "supplyDriver");
        if (!Double.isFinite(basePrice) || basePrice <= 0.0) {
            throw new IllegalArgumentException("basePrice must be finite and positive");
        }
        if (!Double.isFinite(initialSupply) || initialSupply < 0.0) {
            throw new IllegalArgumentException("initialSupply must be finite and non-negative");
        }
        if (!Double.isFinite(initialDemand) || initialDemand < 0.0) {
            throw new IllegalArgumentException("initialDemand must be finite and non-negative");
        }
    }
}
