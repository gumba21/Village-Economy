package dev.gumba21.villageeconomy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class VillageEconomyConfigCodec {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private VillageEconomyConfigCodec() {
    }

    static DecodeResult decode(JsonObject root) {
        List<String> repairedFields = new ArrayList<>();
        VillageEconomyConfig config = new VillageEconomyConfig();

        config.setEnabled(readBoolean(
                root,
                "enabled",
                VillageEconomyConfig.DEFAULT_ENABLED,
                repairedFields
        ));
        config.setDebugLogging(readBoolean(
                root,
                "debugLogging",
                VillageEconomyConfig.DEFAULT_DEBUG_LOGGING,
                repairedFields
        ));
        config.setVillageDetectionRadius(readInt(
                root,
                "villageDetectionRadius",
                VillageEconomyConfig.DEFAULT_VILLAGE_DETECTION_RADIUS,
                VillageEconomyConfig.MIN_VILLAGE_DETECTION_RADIUS,
                VillageEconomyConfig.MAX_VILLAGE_DETECTION_RADIUS,
                repairedFields
        ));
        config.setMarketUpdateIntervalTicks(readInt(
                root,
                "marketUpdateIntervalTicks",
                VillageEconomyConfig.DEFAULT_MARKET_UPDATE_INTERVAL_TICKS,
                VillageEconomyConfig.MIN_MARKET_UPDATE_INTERVAL_TICKS,
                VillageEconomyConfig.MAX_MARKET_UPDATE_INTERVAL_TICKS,
                repairedFields
        ));
        config.setPriceChangeStrength(readDouble(
                root,
                "priceChangeStrength",
                VillageEconomyConfig.DEFAULT_PRICE_CHANGE_STRENGTH,
                VillageEconomyConfig.MIN_PRICE_CHANGE_STRENGTH,
                VillageEconomyConfig.MAX_PRICE_CHANGE_STRENGTH,
                repairedFields
        ));
        config.setMinimumPriceMultiplier(readDouble(
                root,
                "minimumPriceMultiplier",
                VillageEconomyConfig.DEFAULT_MINIMUM_PRICE_MULTIPLIER,
                VillageEconomyConfig.MIN_MINIMUM_PRICE_MULTIPLIER,
                VillageEconomyConfig.MAX_MINIMUM_PRICE_MULTIPLIER,
                repairedFields
        ));
        config.setMaximumPriceMultiplier(readDouble(
                root,
                "maximumPriceMultiplier",
                VillageEconomyConfig.DEFAULT_MAXIMUM_PRICE_MULTIPLIER,
                VillageEconomyConfig.MIN_MAXIMUM_PRICE_MULTIPLIER,
                VillageEconomyConfig.MAX_MAXIMUM_PRICE_MULTIPLIER,
                repairedFields
        ));
        config.setRecoveryRate(readDouble(
                root,
                "recoveryRate",
                VillageEconomyConfig.DEFAULT_RECOVERY_RATE,
                VillageEconomyConfig.MIN_RECOVERY_RATE,
                VillageEconomyConfig.MAX_RECOVERY_RATE,
                repairedFields
        ));

        return new DecodeResult(config, repairedFields);
    }

    static DecodeResult sanitize(VillageEconomyConfig config) {
        return decode(toJsonObject(config));
    }

    static String encode(VillageEconomyConfig config) {
        return GSON.toJson(toJsonObject(config)) + System.lineSeparator();
    }

    private static JsonObject toJsonObject(VillageEconomyConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", config.isEnabled());
        root.addProperty("debugLogging", config.isDebugLogging());
        root.addProperty("villageDetectionRadius", config.getVillageDetectionRadius());
        root.addProperty("marketUpdateIntervalTicks", config.getMarketUpdateIntervalTicks());
        root.addProperty("priceChangeStrength", config.getPriceChangeStrength());
        root.addProperty("minimumPriceMultiplier", config.getMinimumPriceMultiplier());
        root.addProperty("maximumPriceMultiplier", config.getMaximumPriceMultiplier());
        root.addProperty("recoveryRate", config.getRecoveryRate());
        return root;
    }

    private static boolean readBoolean(
            JsonObject root,
            String name,
            boolean defaultValue,
            List<String> repairedFields
    ) {
        JsonPrimitive value = primitive(root.get(name));
        if (value == null || !value.isBoolean()) {
            repairedFields.add(name);
            return defaultValue;
        }
        return value.getAsBoolean();
    }

    private static int readInt(
            JsonObject root,
            String name,
            int defaultValue,
            int minimum,
            int maximum,
            List<String> repairedFields
    ) {
        JsonPrimitive value = primitive(root.get(name));
        if (value != null && value.isNumber()) {
            try {
                int parsed = new BigDecimal(value.getAsString()).intValueExact();
                if (parsed >= minimum && parsed <= maximum) {
                    return parsed;
                }
            } catch (ArithmeticException | NumberFormatException ignored) {
                // The default below is safer than truncating a fractional or oversized number.
            }
        }
        repairedFields.add(name);
        return defaultValue;
    }

    private static double readDouble(
            JsonObject root,
            String name,
            double defaultValue,
            double minimum,
            double maximum,
            List<String> repairedFields
    ) {
        JsonPrimitive value = primitive(root.get(name));
        if (value != null && value.isNumber()) {
            double parsed = value.getAsDouble();
            if (Double.isFinite(parsed) && parsed >= minimum && parsed <= maximum) {
                return parsed;
            }
        }
        repairedFields.add(name);
        return defaultValue;
    }

    private static JsonPrimitive primitive(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsJsonPrimitive();
    }

    record DecodeResult(VillageEconomyConfig config, List<String> repairedFields) {
        DecodeResult {
            repairedFields = List.copyOf(repairedFields);
        }

        boolean wasRepaired() {
            return !repairedFields.isEmpty();
        }
    }
}
