package dev.gumba21.villageeconomy.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DependencyMetadataTest {
    @Test
    void declaresEveryTradingModAsHardDependency() throws Exception {
        try (var input = getClass().getClassLoader()
                .getResourceAsStream("fabric.mod.json")) {
            assertNotNull(input);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(
                    input,
                    StandardCharsets.UTF_8
            )).getAsJsonObject();
            JsonObject depends = root.getAsJsonObject("depends");

            assertEquals(
                    "*",
                    depends.get(CompatibilityManager.NUMISMATIC_ID).getAsString()
            );
            assertEquals(
                    "*",
                    depends.get(CompatibilityManager.TRADE_OVERHAUL_ID).getAsString()
            );
            assertEquals(
                    "*",
                    depends.get(CompatibilityManager.DYNAMIC_TRADES_ID).getAsString()
            );
        }
    }
}
