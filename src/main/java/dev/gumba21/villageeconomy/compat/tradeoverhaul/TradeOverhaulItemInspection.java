package dev.gumba21.villageeconomy.compat.tradeoverhaul;

import dev.gumba21.villageeconomy.trade.model.ItemSnapshot;

import java.util.Objects;
import java.util.Optional;

public record TradeOverhaulItemInspection(
        ItemSnapshot item,
        Optional<TradePriceSnapshot> configuredPrices
) {
    public TradeOverhaulItemInspection {
        Objects.requireNonNull(item, "item");
        configuredPrices = Objects.requireNonNull(
                configuredPrices,
                "configuredPrices"
        );
    }
}
