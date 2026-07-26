package dev.gumba21.villageeconomy;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VillageEconomy implements ModInitializer {
    public static final String MOD_ID = "villageeconomy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Village Economy initialized.");
    }
}
