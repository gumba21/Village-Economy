package dev.gumba21.villageeconomy.config;

import java.nio.file.Path;
import java.util.List;

public record ConfigLoadResult(
        ConfigLoadStatus status,
        Path backupPath,
        List<String> repairedFields
) {
    public ConfigLoadResult {
        repairedFields = List.copyOf(repairedFields);
    }
}
