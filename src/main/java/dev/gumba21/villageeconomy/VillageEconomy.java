package dev.gumba21.villageeconomy;

import dev.gumba21.villageeconomy.config.VillageEconomyConfigManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VillageEconomy implements ModInitializer {
    public static final String MOD_ID = "villageeconomy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        VillageEconomyConfigManager.initialize();
        LOGGER.info("Village Economy initialized.");
    }
}
