package dev.gumba21.villageeconomy.compat.tradeoverhaul;

import dev.gumba21.villageeconomy.compat.currency.CurrencyBreakdown;
import dev.gumba21.villageeconomy.compat.trade.ObservedTrade;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Village Economy-owned read-only view of Trade Overhaul villager state.
 */
public record TradeOverhaulVillagerSnapshot(
        CurrencyBreakdown currency,
        ResourceLocation profession,
        int professionLevel,
        List<ObservedTrade> offers
) {
    public TradeOverhaulVillagerSnapshot {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(profession, "profession");
        offers = List.copyOf(Objects.requireNonNull(offers, "offers"));
    }
}
