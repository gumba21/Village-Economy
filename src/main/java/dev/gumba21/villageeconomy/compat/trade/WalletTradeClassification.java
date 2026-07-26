package dev.gumba21.villageeconomy.compat.trade;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * Neutral classification for Trade Overhaul's wallet-backed transactions,
 * which do not expose coin ItemStacks as a MerchantOffer.
 */
public record WalletTradeClassification(
        TradeDirection direction,
        Optional<ResourceLocation> marketItemId,
        int quantity,
        Optional<MarketValue> monetaryValue
) {
    public WalletTradeClassification {
        Objects.requireNonNull(direction, "direction");
        marketItemId = Objects.requireNonNull(marketItemId, "marketItemId");
        monetaryValue = Objects.requireNonNull(monetaryValue, "monetaryValue");
    }

    public boolean isComplete() {
        return direction != TradeDirection.UNKNOWN
                && marketItemId.isPresent()
                && quantity > 0
                && monetaryValue.isPresent()
                && monetaryValue.get().baseUnits() > 0L;
    }
}
