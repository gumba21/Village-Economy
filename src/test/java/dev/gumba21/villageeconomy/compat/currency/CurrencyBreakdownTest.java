package dev.gumba21.villageeconomy.compat.currency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyBreakdownTest {
    @Test
    void decomposesBronzeOnly() {
        assertEquals(
                CurrencyBreakdown.of(0L, 0L, 99L),
                CurrencyDenominations.decompose(MarketValue.ofBaseUnits(99L))
        );
    }

    @Test
    void observesSilverBoundary() {
        assertEquals(
                CurrencyBreakdown.of(0L, 1L, 0L),
                CurrencyDenominations.decompose(MarketValue.ofBaseUnits(100L))
        );
    }

    @Test
    void observesGoldBoundary() {
        assertEquals(
                CurrencyBreakdown.of(1L, 0L, 0L),
                CurrencyDenominations.decompose(
                        MarketValue.ofBaseUnits(10_000L)
                )
        );
    }

    @Test
    void mixedDenominationsRoundTrip() {
        MarketValue value = MarketValue.ofBaseUnits(120_304L);
        CurrencyBreakdown breakdown = CurrencyDenominations.decompose(value);

        assertEquals(12L, breakdown.gold());
        assertEquals(3L, breakdown.silver());
        assertEquals(4L, breakdown.bronze());
        assertEquals(value, CurrencyDenominations.compose(breakdown));
    }

    @Test
    void zeroRoundTrips() {
        CurrencyBreakdown breakdown = CurrencyDenominations.decompose(
                MarketValue.zero()
        );

        assertEquals(CurrencyBreakdown.of(0L, 0L, 0L), breakdown);
        assertEquals(MarketValue.zero(), breakdown.total());
    }

    @Test
    void maximumLongValueRoundTrips() {
        MarketValue maximum = MarketValue.ofBaseUnits(Long.MAX_VALUE);
        assertEquals(
                maximum,
                CurrencyDenominations.compose(
                        CurrencyDenominations.decompose(maximum)
                )
        );
    }

    @Test
    void rejectsNegativeDenominationCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CurrencyBreakdown.of(-1L, 0L, 0L)
        );
    }

    @Test
    void rejectsMismatchedDeclaredTotal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrencyBreakdown(
                        1L,
                        0L,
                        0L,
                        MarketValue.ofBaseUnits(1L)
                )
        );
    }

    @Test
    void malformedBreakdownReportsOverflow() {
        assertThrows(
                ArithmeticException.class,
                () -> CurrencyBreakdown.of(Long.MAX_VALUE, 0L, 0L)
        );
    }

    @Test
    void plansValuesLargerThanOneStackWithoutTruncation() {
        CurrencyStackPlan plan = CurrencyStackPlan.forValue(
                MarketValue.ofBaseUnits(
                        100L * CurrencyDenominations.GOLD_VALUE
                )
        );

        assertEquals(100L, plan.breakdown().gold());
        assertEquals(2L, plan.requiredStacks());
    }
}
