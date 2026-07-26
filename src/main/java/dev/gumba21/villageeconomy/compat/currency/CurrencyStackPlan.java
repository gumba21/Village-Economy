package dev.gumba21.villageeconomy.compat.currency;

/**
 * Exact structured representation used before materializing ItemStacks.
 */
public record CurrencyStackPlan(
        CurrencyBreakdown breakdown,
        long requiredStacks
) {
    public CurrencyStackPlan {
        if (breakdown == null) {
            throw new NullPointerException("breakdown");
        }
        if (requiredStacks < 0L) {
            throw new IllegalArgumentException("Required stack count cannot be negative");
        }
    }

    public static CurrencyStackPlan forValue(MarketValue value) {
        CurrencyBreakdown breakdown = CurrencyDenominations.decompose(value);
        long stacks = stackCount(breakdown.gold())
                + stackCount(breakdown.silver())
                + stackCount(breakdown.bronze());
        return new CurrencyStackPlan(breakdown, stacks);
    }

    private static long stackCount(long coinCount) {
        if (coinCount == 0L) {
            return 0L;
        }
        return 1L + (coinCount - 1L) / CurrencyDenominations.COIN_STACK_SIZE;
    }
}
