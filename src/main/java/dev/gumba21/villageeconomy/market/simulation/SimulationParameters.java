package dev.gumba21.villageeconomy.market.simulation;

public record SimulationParameters(
        double minimumPriceMultiplier,
        double maximumPriceMultiplier,
        double priceChangeStrength,
        double recoveryRate
) {
    public SimulationParameters {
        if (!Double.isFinite(minimumPriceMultiplier)
                || minimumPriceMultiplier <= 0.0) {
            throw new IllegalArgumentException(
                    "minimumPriceMultiplier must be finite and positive"
            );
        }
        if (!Double.isFinite(maximumPriceMultiplier)
                || maximumPriceMultiplier < minimumPriceMultiplier) {
            throw new IllegalArgumentException(
                    "maximumPriceMultiplier must be finite and at least the minimum"
            );
        }
        if (!Double.isFinite(priceChangeStrength)
                || priceChangeStrength < 0.0
                || priceChangeStrength > 1.0) {
            throw new IllegalArgumentException(
                    "priceChangeStrength must be between 0 and 1"
            );
        }
        if (!Double.isFinite(recoveryRate)
                || recoveryRate < 0.0
                || recoveryRate > 1.0) {
            throw new IllegalArgumentException("recoveryRate must be between 0 and 1");
        }
    }
}
