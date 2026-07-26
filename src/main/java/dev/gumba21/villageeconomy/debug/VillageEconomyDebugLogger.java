package dev.gumba21.villageeconomy.debug;

import dev.gumba21.villageeconomy.config.VillageEconomyConfig;
import dev.gumba21.villageeconomy.config.VillageEconomyConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VillageEconomyDebugLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("villageeconomy/debug");

    private VillageEconomyDebugLogger() {
    }

    public static void logConfig(VillageEconomyConfig config) {
        if (!config.isDebugLogging()) {
            return;
        }
        LOGGER.info(
                "Config values loaded: enabled={}, villageDetectionRadius={}, "
                        + "marketUpdateIntervalTicks={}, priceChangeStrength={}, "
                        + "minimumPriceMultiplier={}, maximumPriceMultiplier={}, recoveryRate={}",
                config.isEnabled(),
                config.getVillageDetectionRadius(),
                config.getMarketUpdateIntervalTicks(),
                config.getPriceChangeStrength(),
                config.getMinimumPriceMultiplier(),
                config.getMaximumPriceMultiplier(),
                config.getRecoveryRate()
        );
    }

    public static void info(String message, Object... arguments) {
        if (isEnabled()) {
            LOGGER.info(message, arguments);
        }
    }

    private static boolean isEnabled() {
        try {
            return VillageEconomyConfigManager.getInstance().isDebugLoggingEnabled();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
