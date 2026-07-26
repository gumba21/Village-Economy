package dev.gumba21.villageeconomy.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class VillageEconomyConfigManager {
    public static final String CONFIG_FILE_NAME = "villageeconomy.json";

    private static final Logger LOGGER = LoggerFactory.getLogger("villageeconomy/config");
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static volatile VillageEconomyConfigManager instance;

    private final Path configPath;
    private volatile VillageEconomyConfig currentConfig = VillageEconomyConfig.defaults();

    VillageEconomyConfigManager(Path configPath) {
        this.configPath = configPath;
    }

    public static synchronized VillageEconomyConfigManager initialize() {
        VillageEconomyConfigManager manager = new VillageEconomyConfigManager(
                FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME)
        );
        manager.load();
        instance = manager;
        return manager;
    }

    public static VillageEconomyConfigManager getInstance() {
        VillageEconomyConfigManager manager = instance;
        if (manager == null) {
            throw new IllegalStateException("Village Economy configuration has not been initialized");
        }
        return manager;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public VillageEconomyConfig getConfig() {
        return currentConfig.copy();
    }

    public boolean isVillageTrackingEnabled() {
        return currentConfig.isEnabled();
    }

    public boolean isDebugLoggingEnabled() {
        return currentConfig.isDebugLogging();
    }

    public int getVillageDetectionRadius() {
        return currentConfig.getVillageDetectionRadius();
    }

    public int getMarketUpdateIntervalTicks() {
        return currentConfig.getMarketUpdateIntervalTicks();
    }

    public double getPriceChangeStrength() {
        return currentConfig.getPriceChangeStrength();
    }

    public double getMinimumPriceMultiplier() {
        return currentConfig.getMinimumPriceMultiplier();
    }

    public double getMaximumPriceMultiplier() {
        return currentConfig.getMaximumPriceMultiplier();
    }

    public double getRecoveryRate() {
        return currentConfig.getRecoveryRate();
    }

    public synchronized ConfigLoadResult load() {
        try {
            createParentDirectory();
            if (Files.notExists(configPath)) {
                VillageEconomyConfig defaults = VillageEconomyConfig.defaults();
                write(defaults);
                currentConfig = defaults;
                LOGGER.info("Created Village Economy config at {}", configPath);
                return result(ConfigLoadStatus.CREATED, null, List.of());
            }

            JsonElement root;
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader);
            }
            if (!root.isJsonObject()) {
                throw new JsonParseException("Configuration root must be a JSON object");
            }

            VillageEconomyConfigCodec.DecodeResult decoded =
                    VillageEconomyConfigCodec.decode(root.getAsJsonObject());
            currentConfig = decoded.config();

            if (decoded.wasRepaired()) {
                write(decoded.config());
                LOGGER.warn(
                        "Repaired Village Economy config at {}; restored defaults for {}",
                        configPath,
                        decoded.repairedFields()
                );
                return result(ConfigLoadStatus.REPAIRED, null, decoded.repairedFields());
            }

            LOGGER.info("Loaded Village Economy config from {}", configPath);
            return result(ConfigLoadStatus.LOADED, null, List.of());
        } catch (JsonParseException | IllegalStateException malformed) {
            return resetMalformedConfig(malformed);
        } catch (IOException ioException) {
            currentConfig = VillageEconomyConfig.defaults();
            LOGGER.error(
                    "Could not load Village Economy config at {}; using safe defaults in memory",
                    configPath,
                    ioException
            );
            return result(ConfigLoadStatus.FALLBACK, null, List.of());
        }
    }

    public synchronized boolean saveAndApply(VillageEconomyConfig requestedConfig) {
        VillageEconomyConfigCodec.DecodeResult sanitized =
                VillageEconomyConfigCodec.sanitize(requestedConfig);
        try {
            createParentDirectory();
            write(sanitized.config());
            currentConfig = sanitized.config();
            if (sanitized.wasRepaired()) {
                LOGGER.warn(
                        "Saved and applied Village Economy config after repairing {}",
                        sanitized.repairedFields()
                );
            } else {
                LOGGER.info("Saved and applied Village Economy config at {}", configPath);
            }
            return true;
        } catch (IOException ioException) {
            LOGGER.error(
                    "Could not save Village Economy config at {}; current settings were not changed",
                    configPath,
                    ioException
            );
            return false;
        }
    }

    private ConfigLoadResult resetMalformedConfig(Exception malformed) {
        VillageEconomyConfig defaults = VillageEconomyConfig.defaults();
        try {
            Path backupPath = nextBackupPath();
            Files.move(configPath, backupPath);
            write(defaults);
            currentConfig = defaults;
            LOGGER.warn(
                    "Reset malformed Village Economy config at {}; backup saved to {}",
                    configPath,
                    backupPath,
                    malformed
            );
            return result(ConfigLoadStatus.RESET, backupPath, List.of());
        } catch (IOException backupOrWriteFailure) {
            currentConfig = defaults;
            LOGGER.error(
                    "Village Economy config at {} is malformed, but it could not be backed up and reset; "
                            + "using safe defaults in memory",
                    configPath,
                    backupOrWriteFailure
            );
            return result(ConfigLoadStatus.FALLBACK, null, List.of());
        }
    }

    private void createParentDirectory() throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void write(VillageEconomyConfig config) throws IOException {
        Path parent = configPath.getParent();
        Path temporary = Files.createTempFile(
                parent == null ? Path.of(".") : parent,
                "villageeconomy-",
                ".tmp"
        );
        try {
            Files.writeString(
                    temporary,
                    VillageEconomyConfigCodec.encode(config),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            try {
                Files.move(
                        temporary,
                        configPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path nextBackupPath() {
        String timestamp = BACKUP_TIMESTAMP.format(LocalDateTime.now());
        Path candidate = configPath.resolveSibling(
                CONFIG_FILE_NAME + ".malformed-" + timestamp + ".bak"
        );
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = configPath.resolveSibling(
                    CONFIG_FILE_NAME + ".malformed-" + timestamp + "-" + suffix + ".bak"
            );
            suffix++;
        }
        return candidate;
    }

    private ConfigLoadResult result(
            ConfigLoadStatus status,
            Path backupPath,
            List<String> repairedFields
    ) {
        return new ConfigLoadResult(status, backupPath, repairedFields);
    }
}
