package dev.gumba21.villageeconomy.compat.currency;

/**
 * Numismatic Overhaul 0.2.18 denomination ratios, verified against its
 * CurrencyResolver implementation.
 */
public final class CurrencyDenominations {
    public static final long BRONZE_VALUE = 1L;
    public static final long SILVER_VALUE = 100L;
    public static final long GOLD_VALUE = 10_000L;
    public static final int COIN_STACK_SIZE = 99;

    private CurrencyDenominations() {
    }

    public static CurrencyBreakdown decompose(MarketValue value) {
        long remaining = value.baseUnits();
        long gold = remaining / GOLD_VALUE;
        remaining %= GOLD_VALUE;
        long silver = remaining / SILVER_VALUE;
        long bronze = remaining % SILVER_VALUE;
        return CurrencyBreakdown.of(gold, silver, bronze);
    }

    public static MarketValue compose(CurrencyBreakdown breakdown) {
        return breakdown.total();
    }

    static MarketValue composeCounts(long gold, long silver, long bronze) {
        if (gold < 0L || silver < 0L || bronze < 0L) {
            throw new IllegalArgumentException(
                    "Currency denomination counts cannot be negative"
            );
        }
        long goldValue = Math.multiplyExact(gold, GOLD_VALUE);
        long silverValue = Math.multiplyExact(silver, SILVER_VALUE);
        return MarketValue.ofBaseUnits(
                Math.addExact(Math.addExact(goldValue, silverValue), bronze)
        );
    }
}
