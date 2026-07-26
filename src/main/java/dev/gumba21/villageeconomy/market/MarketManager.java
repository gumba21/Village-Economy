package dev.gumba21.villageeconomy.market;

import dev.gumba21.villageeconomy.config.VillageEconomyConfig;
import dev.gumba21.villageeconomy.config.VillageEconomyConfigManager;
import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.market.registry.DefaultTradeGoods;
import dev.gumba21.villageeconomy.market.registry.TradeGoodDefinition;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import dev.gumba21.villageeconomy.village.data.VillagePersistentState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

public final class MarketManager {
    private final VillagePersistentState state;

    public MarketManager(VillagePersistentState state) {
        this.state = state;
    }

    public Optional<MarketState> getMarket(UUID villageId) {
        return state.getMarket(villageId);
    }

    public MarketState createMarket(UUID villageId) {
        Optional<MarketState> existing = state.getMarket(villageId);
        if (existing.isPresent()) {
            return existing.get();
        }

        long now = System.currentTimeMillis();
        PriceBounds bounds = currentPriceBounds();
        MarketState market = createDefaultMarket(villageId, now, bounds);
        if (!state.addMarket(market)) {
            return state.getMarket(villageId).orElseThrow();
        }

        VillageEconomyDebugLogger.info(
                "Market created: village={}, trackedItems={}",
                villageId,
                market.size()
        );
        return market;
    }

    public boolean removeMarket(UUID villageId) {
        boolean removed = state.removeMarket(villageId);
        if (removed) {
            VillageEconomyDebugLogger.info("Market removed: village={}", villageId);
        }
        return removed;
    }

    public OptionalDouble getPrice(UUID villageId, Item item) {
        return state.getMarket(villageId)
                .flatMap(market -> market.getEntry(BuiltInRegistries.ITEM.getKey(item)))
                .map(entry -> OptionalDouble.of(entry.getCurrentPrice()))
                .orElseGet(OptionalDouble::empty);
    }

    public boolean hasMarket(UUID villageId) {
        return state.hasMarket(villageId);
    }

    public MarketState resetMarket(UUID villageId) {
        long now = System.currentTimeMillis();
        MarketState market = createDefaultMarket(villageId, now, currentPriceBounds());
        state.putMarket(market);
        VillageEconomyDebugLogger.info(
                "Market reset: village={}, trackedItems={}",
                villageId,
                market.size()
        );
        return market;
    }

    public Collection<MarketState> getMarkets() {
        return state.getMarkets();
    }

    public int ensureMarkets(Collection<TrackedVillage> villages) {
        int generated = 0;
        for (TrackedVillage village : villages) {
            if (!hasMarket(village.getId())) {
                createMarket(village.getId());
                generated++;
            }
        }
        if (generated > 0) {
            VillageEconomyDebugLogger.info(
                    "Generated {} missing markets for tracked villages",
                    generated
            );
        }
        VillageEconomyDebugLogger.info(
                "Market initialization complete: markets={}, trackedItems={}",
                state.marketSize(),
                state.marketEntryCount()
        );
        return generated;
    }

    private static MarketState createDefaultMarket(
            UUID villageId,
            long timestamp,
            PriceBounds bounds
    ) {
        List<MarketEntry> entries = new ArrayList<>(DefaultTradeGoods.size());
        for (TradeGoodDefinition definition : DefaultTradeGoods.all()) {
            entries.add(new MarketEntry(
                    definition.itemId(),
                    definition.basePrice(),
                    definition.basePrice(),
                    bounds.minimumMultiplier(),
                    bounds.maximumMultiplier(),
                    definition.initialSupply(),
                    definition.initialDemand(),
                    timestamp
            ));
        }
        return new MarketState(villageId, timestamp, timestamp, entries);
    }

    private static PriceBounds currentPriceBounds() {
        try {
            VillageEconomyConfig config =
                    VillageEconomyConfigManager.getInstance().getConfig();
            return new PriceBounds(
                    config.getMinimumPriceMultiplier(),
                    config.getMaximumPriceMultiplier()
            );
        } catch (IllegalStateException ignored) {
            return new PriceBounds(
                    VillageEconomyConfig.DEFAULT_MINIMUM_PRICE_MULTIPLIER,
                    VillageEconomyConfig.DEFAULT_MAXIMUM_PRICE_MULTIPLIER
            );
        }
    }

    private record PriceBounds(double minimumMultiplier, double maximumMultiplier) {
    }
}
