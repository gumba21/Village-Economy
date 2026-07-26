package dev.gumba21.villageeconomy.compat.currency;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable monetary value measured in Numismatic bronze-equivalent base units.
 *
 * <p>Negative values are rejected. Arithmetic is exact and reports overflow
 * instead of wrapping. One market price unit currently converts to one silver
 * coin (100 base units); this is only a conversion boundary and does not change
 * the market simulator's existing double-valued prices.</p>
 */
public record MarketValue(long baseUnits) implements Comparable<MarketValue> {
    public static final long BASE_UNITS_PER_MARKET_PRICE = 100L;
    public static final long MAX_MULTIPLIER_COMPONENT = 1_000_000L;
    private static final MarketValue ZERO = new MarketValue(0L);

    public MarketValue {
        if (baseUnits < 0L) {
            throw new IllegalArgumentException("Market values cannot be negative");
        }
    }

    public static MarketValue ofBaseUnits(long baseUnits) {
        return baseUnits == 0L ? ZERO : new MarketValue(baseUnits);
    }

    public static MarketValue zero() {
        return ZERO;
    }

    public static MarketValue fromMarketPrice(double price) {
        if (!Double.isFinite(price) || price < 0.0) {
            throw new IllegalArgumentException(
                    "Market price must be finite and non-negative"
            );
        }
        long converted = BigDecimal.valueOf(price)
                .multiply(BigDecimal.valueOf(BASE_UNITS_PER_MARKET_PRICE))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return ofBaseUnits(converted);
    }

    public MarketValue add(MarketValue other) {
        Objects.requireNonNull(other, "other");
        return ofBaseUnits(Math.addExact(baseUnits, other.baseUnits));
    }

    /**
     * Subtracts exactly. Results below zero are rejected rather than clamped.
     */
    public MarketValue subtract(MarketValue other) {
        Objects.requireNonNull(other, "other");
        return ofBaseUnits(Math.subtractExact(baseUnits, other.baseUnits));
    }

    /**
     * Multiplies by a bounded non-negative rational scalar using HALF_UP
     * rounding. Integer components are used so calculation behavior is
     * deterministic and no floating-point money is introduced.
     */
    public MarketValue multiply(long numerator, long denominator) {
        if (numerator < 0L || numerator > MAX_MULTIPLIER_COMPONENT) {
            throw new IllegalArgumentException("Multiplier numerator is out of range");
        }
        if (denominator <= 0L || denominator > MAX_MULTIPLIER_COMPONENT) {
            throw new IllegalArgumentException("Multiplier denominator is out of range");
        }

        BigInteger product = BigInteger.valueOf(baseUnits)
                .multiply(BigInteger.valueOf(numerator));
        BigInteger divisor = BigInteger.valueOf(denominator);
        BigInteger[] division = product.divideAndRemainder(divisor);
        if (division[1].shiftLeft(1).compareTo(divisor) >= 0) {
            division[0] = division[0].add(BigInteger.ONE);
        }
        return ofBaseUnits(division[0].longValueExact());
    }

    public MarketValue clamp(MarketValue minimum, MarketValue maximum) {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Minimum cannot exceed maximum");
        }
        if (compareTo(minimum) < 0) {
            return minimum;
        }
        if (compareTo(maximum) > 0) {
            return maximum;
        }
        return this;
    }

    public static MarketValue min(MarketValue first, MarketValue second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    public static MarketValue max(MarketValue first, MarketValue second) {
        return first.compareTo(second) >= 0 ? first : second;
    }
}
