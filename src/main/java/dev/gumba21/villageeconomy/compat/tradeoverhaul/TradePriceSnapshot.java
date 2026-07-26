package dev.gumba21.villageeconomy.compat.tradeoverhaul;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Read-only prices reported by Trade Overhaul's own configuration API.
 */
public record TradePriceSnapshot(
        ResourceLocation itemId,
        int playerBuyPrice,
        int playerSellPrice
) {
    public TradePriceSnapshot {
        Objects.requireNonNull(itemId, "itemId");
    }
}
