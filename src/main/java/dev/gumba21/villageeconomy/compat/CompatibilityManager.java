package dev.gumba21.villageeconomy.compat;

import dev.gumba21.villageeconomy.VillageEconomy;
import dev.gumba21.villageeconomy.compat.currency.CurrencyDenominations;
import dev.gumba21.villageeconomy.compat.currency.NumismaticCurrencyAdapter;
import dev.gumba21.villageeconomy.compat.dynamictrades.DynamicVillagerTradesAdapter;
import dev.gumba21.villageeconomy.compat.trade.TradeClassifier;
import dev.gumba21.villageeconomy.compat.tradeoverhaul.TradeOverhaulAdapter;
import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Single server-safe entry point for the required trading integrations.
 */
public final class CompatibilityManager {
    public static final String NUMISMATIC_ID = "numismatic-overhaul";
    public static final String TRADE_OVERHAUL_ID = "tradeoverhaul";
    public static final String DYNAMIC_TRADES_ID = "dynamicvillagertrades";

    private static volatile CompatibilityManager instance;

    private final CompatibilityVersions versions;
    private final NumismaticCurrencyAdapter numismatic;
    private final DynamicVillagerTradesAdapter dynamicTrades;
    private final TradeOverhaulAdapter tradeOverhaul;

    private CompatibilityManager(
            CompatibilityVersions versions,
            NumismaticCurrencyAdapter numismatic,
            DynamicVillagerTradesAdapter dynamicTrades,
            TradeOverhaulAdapter tradeOverhaul
    ) {
        this.versions = versions;
        this.numismatic = numismatic;
        this.dynamicTrades = dynamicTrades;
        this.tradeOverhaul = tradeOverhaul;
    }

    public static synchronized CompatibilityManager initialize() {
        if (instance != null) {
            return instance;
        }

        VillageEconomyDebugLogger.info(
                "Trading compatibility initialization started"
        );
        try {
            FabricLoader loader = FabricLoader.getInstance();
            CompatibilityVersions versions = new CompatibilityVersions(
                    version(loader, VillageEconomy.MOD_ID),
                    version(loader, NUMISMATIC_ID),
                    version(loader, TRADE_OVERHAUL_ID),
                    version(loader, DYNAMIC_TRADES_ID)
            );

            NumismaticCurrencyAdapter numismatic =
                    new NumismaticCurrencyAdapter();
            DynamicVillagerTradesAdapter dynamicTrades =
                    new DynamicVillagerTradesAdapter();
            TradeClassifier classifier = new TradeClassifier(numismatic);
            TradeOverhaulAdapter tradeOverhaul = new TradeOverhaulAdapter(
                    dynamicTrades,
                    classifier,
                    numismatic
            );

            CompatibilityManager manager = new CompatibilityManager(
                    versions,
                    numismatic,
                    dynamicTrades,
                    tradeOverhaul
            );
            instance = manager;
            VillageEconomyDebugLogger.info(
                    "Trading compatibility initialized: villageeconomy={}, "
                            + "numismatic-overhaul={}, tradeoverhaul={}, "
                            + "dynamicvillagertrades={}, ratios gold:silver:bronze={}:{}:{}",
                    versions.villageEconomy(),
                    versions.numismaticOverhaul(),
                    versions.tradeOverhaul(),
                    versions.dynamicVillagerTrades(),
                    CurrencyDenominations.GOLD_VALUE,
                    CurrencyDenominations.SILVER_VALUE,
                    CurrencyDenominations.BRONZE_VALUE
            );
            return manager;
        } catch (RuntimeException failure) {
            VillageEconomyDebugLogger.info(
                    "Trading compatibility initialization failed: {}",
                    failure.toString()
            );
            throw new IllegalStateException(
                    "Village Economy could not initialize its required trading compatibility",
                    failure
            );
        }
    }

    public static CompatibilityManager getInstance() {
        CompatibilityManager manager = instance;
        if (manager == null) {
            throw new IllegalStateException(
                    "Trading compatibility has not been initialized"
            );
        }
        return manager;
    }

    public CompatibilityVersions versions() {
        return versions;
    }

    public NumismaticCurrencyAdapter numismatic() {
        return numismatic;
    }

    public DynamicVillagerTradesAdapter dynamicTrades() {
        return dynamicTrades;
    }

    public TradeOverhaulAdapter tradeOverhaul() {
        return tradeOverhaul;
    }

    public boolean isHealthy() {
        return true;
    }

    private static String version(FabricLoader loader, String modId) {
        ModContainer container = loader.getModContainer(modId)
                .orElseThrow(() -> new IllegalStateException(
                        "Required mod is missing: " + modId
                ));
        return container.getMetadata().getVersion().getFriendlyString();
    }
}
