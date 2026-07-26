package dev.gumba21.villageeconomy.config;

public final class VillageEconomyConfig {
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_DEBUG_LOGGING = false;
    public static final int DEFAULT_VILLAGE_DETECTION_RADIUS = 64;
    public static final int DEFAULT_MARKET_UPDATE_INTERVAL_TICKS = 1200;
    public static final double DEFAULT_PRICE_CHANGE_STRENGTH = 0.15;
    public static final double DEFAULT_MINIMUM_PRICE_MULTIPLIER = 0.5;
    public static final double DEFAULT_MAXIMUM_PRICE_MULTIPLIER = 2.0;
    public static final double DEFAULT_RECOVERY_RATE = 0.02;

    public static final int MIN_VILLAGE_DETECTION_RADIUS = 1;
    public static final int MAX_VILLAGE_DETECTION_RADIUS = 512;
    public static final int MIN_MARKET_UPDATE_INTERVAL_TICKS = 20;
    public static final int MAX_MARKET_UPDATE_INTERVAL_TICKS = 1_728_000;
    public static final double MIN_PRICE_CHANGE_STRENGTH = 0.0;
    public static final double MAX_PRICE_CHANGE_STRENGTH = 1.0;
    public static final double MIN_MINIMUM_PRICE_MULTIPLIER = 0.01;
    public static final double MAX_MINIMUM_PRICE_MULTIPLIER = 1.0;
    public static final double MIN_MAXIMUM_PRICE_MULTIPLIER = 1.0;
    public static final double MAX_MAXIMUM_PRICE_MULTIPLIER = 10.0;
    public static final double MIN_RECOVERY_RATE = 0.0;
    public static final double MAX_RECOVERY_RATE = 1.0;

    private boolean enabled = DEFAULT_ENABLED;
    private boolean debugLogging = DEFAULT_DEBUG_LOGGING;
    private int villageDetectionRadius = DEFAULT_VILLAGE_DETECTION_RADIUS;
    private int marketUpdateIntervalTicks = DEFAULT_MARKET_UPDATE_INTERVAL_TICKS;
    private double priceChangeStrength = DEFAULT_PRICE_CHANGE_STRENGTH;
    private double minimumPriceMultiplier = DEFAULT_MINIMUM_PRICE_MULTIPLIER;
    private double maximumPriceMultiplier = DEFAULT_MAXIMUM_PRICE_MULTIPLIER;
    private double recoveryRate = DEFAULT_RECOVERY_RATE;

    public static VillageEconomyConfig defaults() {
        return new VillageEconomyConfig();
    }

    public VillageEconomyConfig copy() {
        VillageEconomyConfig copy = new VillageEconomyConfig();
        copy.enabled = enabled;
        copy.debugLogging = debugLogging;
        copy.villageDetectionRadius = villageDetectionRadius;
        copy.marketUpdateIntervalTicks = marketUpdateIntervalTicks;
        copy.priceChangeStrength = priceChangeStrength;
        copy.minimumPriceMultiplier = minimumPriceMultiplier;
        copy.maximumPriceMultiplier = maximumPriceMultiplier;
        copy.recoveryRate = recoveryRate;
        return copy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public int getVillageDetectionRadius() {
        return villageDetectionRadius;
    }

    public void setVillageDetectionRadius(int villageDetectionRadius) {
        this.villageDetectionRadius = villageDetectionRadius;
    }

    public int getMarketUpdateIntervalTicks() {
        return marketUpdateIntervalTicks;
    }

    public void setMarketUpdateIntervalTicks(int marketUpdateIntervalTicks) {
        this.marketUpdateIntervalTicks = marketUpdateIntervalTicks;
    }

    public double getPriceChangeStrength() {
        return priceChangeStrength;
    }

    public void setPriceChangeStrength(double priceChangeStrength) {
        this.priceChangeStrength = priceChangeStrength;
    }

    public double getMinimumPriceMultiplier() {
        return minimumPriceMultiplier;
    }

    public void setMinimumPriceMultiplier(double minimumPriceMultiplier) {
        this.minimumPriceMultiplier = minimumPriceMultiplier;
    }

    public double getMaximumPriceMultiplier() {
        return maximumPriceMultiplier;
    }

    public void setMaximumPriceMultiplier(double maximumPriceMultiplier) {
        this.maximumPriceMultiplier = maximumPriceMultiplier;
    }

    public double getRecoveryRate() {
        return recoveryRate;
    }

    public void setRecoveryRate(double recoveryRate) {
        this.recoveryRate = recoveryRate;
    }
}
