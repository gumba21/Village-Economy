package dev.gumba21.villageeconomy.compat.currency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketValueTest {
    @Test
    void zeroIsCanonical() {
        assertEquals(0L, MarketValue.zero().baseUnits());
        assertEquals(MarketValue.zero(), MarketValue.ofBaseUnits(0L));
    }

    @Test
    void acceptsPositiveValuesAndComparesThem() {
        MarketValue small = MarketValue.ofBaseUnits(12L);
        MarketValue large = MarketValue.ofBaseUnits(100L);

        assertTrue(small.compareTo(large) < 0);
        assertEquals(large, MarketValue.max(small, large));
        assertEquals(small, MarketValue.min(small, large));
    }

    @Test
    void rejectsNegativeInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketValue.ofBaseUnits(-1L)
        );
    }

    @Test
    void arithmeticIsExact() {
        MarketValue value = MarketValue.ofBaseUnits(150L)
                .add(MarketValue.ofBaseUnits(50L))
                .subtract(MarketValue.ofBaseUnits(25L));

        assertEquals(175L, value.baseUnits());
    }

    @Test
    void subtractionCannotCreateNegativeMoney() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketValue.ofBaseUnits(1L)
                        .subtract(MarketValue.ofBaseUnits(2L))
        );
    }

    @Test
    void additionReportsOverflow() {
        assertThrows(
                ArithmeticException.class,
                () -> MarketValue.ofBaseUnits(Long.MAX_VALUE)
                        .add(MarketValue.ofBaseUnits(1L))
        );
    }

    @Test
    void rationalMultiplicationRoundsHalfUpDeterministically() {
        assertEquals(
                3L,
                MarketValue.ofBaseUnits(5L).multiply(1L, 2L).baseUnits()
        );
        assertEquals(
                2L,
                MarketValue.ofBaseUnits(4L).multiply(1L, 2L).baseUnits()
        );
    }

    @Test
    void multiplicationIsBoundedAndOverflowSafe() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketValue.ofBaseUnits(10L).multiply(
                        MarketValue.MAX_MULTIPLIER_COMPONENT + 1L,
                        1L
                )
        );
        assertThrows(
                ArithmeticException.class,
                () -> MarketValue.ofBaseUnits(Long.MAX_VALUE)
                        .multiply(2L, 1L)
        );
    }

    @Test
    void clampUsesInclusiveBounds() {
        MarketValue minimum = MarketValue.ofBaseUnits(10L);
        MarketValue maximum = MarketValue.ofBaseUnits(20L);

        assertEquals(minimum, MarketValue.ofBaseUnits(1L).clamp(minimum, maximum));
        assertEquals(maximum, MarketValue.ofBaseUnits(30L).clamp(minimum, maximum));
        assertEquals(
                MarketValue.ofBaseUnits(15L),
                MarketValue.ofBaseUnits(15L).clamp(minimum, maximum)
        );
    }

    @Test
    void marketPriceConversionUsesCentralHalfUpScale() {
        assertEquals(100L, MarketValue.fromMarketPrice(1.0).baseUnits());
        assertEquals(101L, MarketValue.fromMarketPrice(1.005).baseUnits());
        assertEquals(0L, MarketValue.fromMarketPrice(0.0).baseUnits());
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketValue.fromMarketPrice(Double.NaN)
        );
    }

    @Test
    void supportsLargestNormalizedValue() {
        assertEquals(
                Long.MAX_VALUE,
                MarketValue.ofBaseUnits(Long.MAX_VALUE).baseUnits()
        );
    }
}
