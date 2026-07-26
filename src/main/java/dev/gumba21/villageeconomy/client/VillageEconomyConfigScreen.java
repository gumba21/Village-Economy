package dev.gumba21.villageeconomy.client;

import dev.gumba21.villageeconomy.config.VillageEconomyConfig;
import dev.gumba21.villageeconomy.config.VillageEconomyConfigManager;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class VillageEconomyConfigScreen {
    private VillageEconomyConfigScreen() {
    }

    public static Screen create(Screen parent) {
        VillageEconomyConfigManager manager = VillageEconomyConfigManager.getInstance();
        VillageEconomyConfig working = manager.getConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(text("title"));
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(text("category.general"));

        general.addEntry(entries.startTextDescription(text("apply_behavior")).build());
        general.addEntry(entries.startBooleanToggle(
                        text("enabled"),
                        working.isEnabled()
                )
                .setDefaultValue(VillageEconomyConfig.DEFAULT_ENABLED)
                .setTooltip(text("enabled.description"))
                .setSaveConsumer(working::setEnabled)
                .build());
        general.addEntry(entries.startBooleanToggle(
                        text("debugLogging"),
                        working.isDebugLogging()
                )
                .setDefaultValue(VillageEconomyConfig.DEFAULT_DEBUG_LOGGING)
                .setTooltip(text("debugLogging.description"))
                .setSaveConsumer(working::setDebugLogging)
                .build());
        general.addEntry(entries.startIntField(
                        text("villageDetectionRadius"),
                        working.getVillageDetectionRadius()
                )
                .setDefaultValue(VillageEconomyConfig.DEFAULT_VILLAGE_DETECTION_RADIUS)
                .setMin(VillageEconomyConfig.MIN_VILLAGE_DETECTION_RADIUS)
                .setMax(VillageEconomyConfig.MAX_VILLAGE_DETECTION_RADIUS)
                .setTooltip(text("villageDetectionRadius.description"))
                .setSaveConsumer(working::setVillageDetectionRadius)
                .build());
        general.addEntry(entries.startIntField(
                        text("marketUpdateIntervalTicks"),
                        working.getMarketUpdateIntervalTicks()
                )
                .setDefaultValue(VillageEconomyConfig.DEFAULT_MARKET_UPDATE_INTERVAL_TICKS)
                .setMin(VillageEconomyConfig.MIN_MARKET_UPDATE_INTERVAL_TICKS)
                .setMax(VillageEconomyConfig.MAX_MARKET_UPDATE_INTERVAL_TICKS)
                .setTooltip(text("marketUpdateIntervalTicks.description"))
                .setSaveConsumer(working::setMarketUpdateIntervalTicks)
                .build());
        general.addEntry(entries.startDoubleField(
                        text("priceChangeStrength"),
                        working.getPriceChangeStrength()
                )
                .setDefaultValue(VillageEconomyConfig.DEFAULT_PRICE_CHANGE_STRENGTH)
                .setMin(VillageEconomyConfig.MIN_PRICE_CHANGE_STRENGTH)
                .setMax(VillageEconomyConfig.MAX_PRICE_CHANGE_STRENGTH)
                .setTooltip(text("priceChangeStrength.description"))
                .setSaveConsumer(working::setPriceChangeStrength)
                .build());
        general.addEntry(entries.startDoubleField(
                        text("minimumPriceMultiplier"),
                        working.getMinimumPriceMultiplier()
                )
                .setDefaultValue(VillageEconomyConfig.DEFAULT_MINIMUM_PRICE_MULTIPLIER)
                .setMin(VillageEconomyConfig.MIN_MINIMUM_PRICE_MULTIPLIER)
                .setMax(VillageEconomyConfig.MAX_MINIMUM_PRICE_MULTIPLIER)
                .setTooltip(text("minimumPriceMultiplier.description"))
                .setSaveConsumer(working::setMinimumPriceMultiplier)
                .build());
        general.addEntry(entries.startDoubleField(
                        text("maximumPriceMultiplier"),
                        working.getMaximumPriceMultiplier()
                )
                .setDefaultValue(VillageEconomyConfig.DEFAULT_MAXIMUM_PRICE_MULTIPLIER)
                .setMin(VillageEconomyConfig.MIN_MAXIMUM_PRICE_MULTIPLIER)
                .setMax(VillageEconomyConfig.MAX_MAXIMUM_PRICE_MULTIPLIER)
                .setTooltip(text("maximumPriceMultiplier.description"))
                .setSaveConsumer(working::setMaximumPriceMultiplier)
                .build());
        general.addEntry(entries.startDoubleField(
                        text("recoveryRate"),
                        working.getRecoveryRate()
                )
                .setDefaultValue(VillageEconomyConfig.DEFAULT_RECOVERY_RATE)
                .setMin(VillageEconomyConfig.MIN_RECOVERY_RATE)
                .setMax(VillageEconomyConfig.MAX_RECOVERY_RATE)
                .setTooltip(text("recoveryRate.description"))
                .setSaveConsumer(working::setRecoveryRate)
                .build());

        builder.setSavingRunnable(() -> manager.saveAndApply(working));
        return builder.build();
    }

    private static Component text(String key) {
        return Component.translatable("config.villageeconomy." + key);
    }
}
