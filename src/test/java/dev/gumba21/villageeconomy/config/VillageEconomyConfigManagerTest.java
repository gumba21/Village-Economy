package dev.gumba21.villageeconomy.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageEconomyConfigManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsDefaultConfigWhenMissing() throws IOException {
        Path path = configPath();
        VillageEconomyConfigManager manager = new VillageEconomyConfigManager(path);

        ConfigLoadResult result = manager.load();

        assertEquals(ConfigLoadStatus.CREATED, result.status());
        assertTrue(Files.exists(path));
        assertDefaults(manager.getConfig());
        assertDefaults(readConfig(path));
    }

    @Test
    void loadsValidConfigWithoutRepairingIt() throws IOException {
        Path path = configPath();
        Files.writeString(path, """
                {
                  "enabled": false,
                  "debugLogging": true,
                  "villageDetectionRadius": 96,
                  "marketUpdateIntervalTicks": 2400,
                  "priceChangeStrength": 0.25,
                  "minimumPriceMultiplier": 0.6,
                  "maximumPriceMultiplier": 3.0,
                  "recoveryRate": 0.04
                }
                """, StandardCharsets.UTF_8);
        VillageEconomyConfigManager manager = new VillageEconomyConfigManager(path);

        ConfigLoadResult result = manager.load();
        VillageEconomyConfig config = manager.getConfig();

        assertEquals(ConfigLoadStatus.LOADED, result.status());
        assertFalse(config.isEnabled());
        assertTrue(config.isDebugLogging());
        assertEquals(96, config.getVillageDetectionRadius());
        assertEquals(2400, config.getMarketUpdateIntervalTicks());
        assertEquals(0.25, config.getPriceChangeStrength(), 0.000001);
        assertEquals(0.6, config.getMinimumPriceMultiplier(), 0.000001);
        assertEquals(3.0, config.getMaximumPriceMultiplier(), 0.000001);
        assertEquals(0.04, config.getRecoveryRate(), 0.000001);
    }

    @Test
    void repairsEveryInvalidSettingAndPreservesValidSettings() throws IOException {
        Path path = configPath();
        Files.writeString(path, """
                {
                  "enabled": "yes",
                  "debugLogging": true,
                  "villageDetectionRadius": 0,
                  "marketUpdateIntervalTicks": 1.5,
                  "priceChangeStrength": 2.0,
                  "minimumPriceMultiplier": -0.5,
                  "maximumPriceMultiplier": 0.5,
                  "recoveryRate": "NaN"
                }
                """, StandardCharsets.UTF_8);
        VillageEconomyConfigManager manager = new VillageEconomyConfigManager(path);

        ConfigLoadResult result = manager.load();
        VillageEconomyConfig config = manager.getConfig();

        assertEquals(ConfigLoadStatus.REPAIRED, result.status());
        assertEquals(List.of(
                "enabled",
                "villageDetectionRadius",
                "marketUpdateIntervalTicks",
                "priceChangeStrength",
                "minimumPriceMultiplier",
                "maximumPriceMultiplier",
                "recoveryRate"
        ), result.repairedFields());
        assertTrue(config.isDebugLogging());
        assertTrue(config.isEnabled());
        assertEquals(
                VillageEconomyConfig.DEFAULT_VILLAGE_DETECTION_RADIUS,
                config.getVillageDetectionRadius()
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_MARKET_UPDATE_INTERVAL_TICKS,
                config.getMarketUpdateIntervalTicks()
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_PRICE_CHANGE_STRENGTH,
                config.getPriceChangeStrength(),
                0.000001
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_MINIMUM_PRICE_MULTIPLIER,
                config.getMinimumPriceMultiplier(),
                0.000001
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_MAXIMUM_PRICE_MULTIPLIER,
                config.getMaximumPriceMultiplier(),
                0.000001
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_RECOVERY_RATE,
                config.getRecoveryRate(),
                0.000001
        );
        assertTrue(readConfig(path).get("debugLogging").getAsBoolean());
    }

    @Test
    void backsUpMalformedConfigAndRegeneratesDefaults() throws IOException {
        Path path = configPath();
        String malformed = "{\"enabled\": tru";
        Files.writeString(path, malformed, StandardCharsets.UTF_8);
        VillageEconomyConfigManager manager = new VillageEconomyConfigManager(path);

        ConfigLoadResult result = manager.load();

        assertEquals(ConfigLoadStatus.RESET, result.status());
        assertNotNull(result.backupPath());
        assertTrue(Files.exists(result.backupPath()));
        assertEquals(malformed, Files.readString(result.backupPath(), StandardCharsets.UTF_8));
        assertDefaults(manager.getConfig());
        assertDefaults(readConfig(path));
    }

    @Test
    void fillsMissingSettingsAndWritesACompleteConfig() throws IOException {
        Path path = configPath();
        Files.writeString(path, "{\"enabled\": false}", StandardCharsets.UTF_8);
        VillageEconomyConfigManager manager = new VillageEconomyConfigManager(path);

        ConfigLoadResult result = manager.load();
        JsonObject saved = readConfig(path);

        assertEquals(ConfigLoadStatus.REPAIRED, result.status());
        assertFalse(manager.getConfig().isEnabled());
        assertEquals(8, saved.size());
        assertEquals(1200, saved.get("marketUpdateIntervalTicks").getAsInt());
    }

    @Test
    void savesAndAppliesChangesImmediately() {
        VillageEconomyConfigManager manager =
                new VillageEconomyConfigManager(configPath());
        manager.load();
        VillageEconomyConfig changed = manager.getConfig();
        changed.setDebugLogging(true);
        changed.setVillageDetectionRadius(128);

        assertTrue(manager.saveAndApply(changed));
        assertTrue(manager.getConfig().isDebugLogging());
        assertEquals(128, manager.getConfig().getVillageDetectionRadius());
    }

    private Path configPath() {
        return temporaryDirectory.resolve(VillageEconomyConfigManager.CONFIG_FILE_NAME);
    }

    private JsonObject readConfig(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private void assertDefaults(VillageEconomyConfig config) {
        assertEquals(VillageEconomyConfig.DEFAULT_ENABLED, config.isEnabled());
        assertEquals(VillageEconomyConfig.DEFAULT_DEBUG_LOGGING, config.isDebugLogging());
        assertEquals(
                VillageEconomyConfig.DEFAULT_VILLAGE_DETECTION_RADIUS,
                config.getVillageDetectionRadius()
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_MARKET_UPDATE_INTERVAL_TICKS,
                config.getMarketUpdateIntervalTicks()
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_PRICE_CHANGE_STRENGTH,
                config.getPriceChangeStrength(),
                0.000001
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_MINIMUM_PRICE_MULTIPLIER,
                config.getMinimumPriceMultiplier(),
                0.000001
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_MAXIMUM_PRICE_MULTIPLIER,
                config.getMaximumPriceMultiplier(),
                0.000001
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_RECOVERY_RATE,
                config.getRecoveryRate(),
                0.000001
        );
    }

    private void assertDefaults(JsonObject config) {
        assertEquals(VillageEconomyConfig.DEFAULT_ENABLED, config.get("enabled").getAsBoolean());
        assertEquals(
                VillageEconomyConfig.DEFAULT_DEBUG_LOGGING,
                config.get("debugLogging").getAsBoolean()
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_VILLAGE_DETECTION_RADIUS,
                config.get("villageDetectionRadius").getAsInt()
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_MARKET_UPDATE_INTERVAL_TICKS,
                config.get("marketUpdateIntervalTicks").getAsInt()
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_PRICE_CHANGE_STRENGTH,
                config.get("priceChangeStrength").getAsDouble(),
                0.000001
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_MINIMUM_PRICE_MULTIPLIER,
                config.get("minimumPriceMultiplier").getAsDouble(),
                0.000001
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_MAXIMUM_PRICE_MULTIPLIER,
                config.get("maximumPriceMultiplier").getAsDouble(),
                0.000001
        );
        assertEquals(
                VillageEconomyConfig.DEFAULT_RECOVERY_RATE,
                config.get("recoveryRate").getAsDouble(),
                0.000001
        );
    }
}
