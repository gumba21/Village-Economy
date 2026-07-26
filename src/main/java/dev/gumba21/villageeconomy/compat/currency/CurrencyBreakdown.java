package dev.gumba21.villageeconomy.compat.currency;

import java.util.Objects;

/**
 * Exact gold, silver, and bronze representation of one normalized value.
 */
public record CurrencyBreakdown(
        long gold,
        long silver,
        long bronze,
        MarketValue total
) {
    public CurrencyBreakdown {
        Objects.requireNonNull(total, "total");
        MarketValue calculated = CurrencyDenominations.composeCounts(
                gold,
                silver,
                bronze
        );
        if (!calculated.equals(total)) {
            throw new IllegalArgumentException(
                    "Currency total does not match its denomination counts"
            );
        }
    }

    public static CurrencyBreakdown of(long gold, long silver, long bronze) {
        return new CurrencyBreakdown(
                gold,
                silver,
                bronze,
                CurrencyDenominations.composeCounts(gold, silver, bronze)
        );
    }
}
