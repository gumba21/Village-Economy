package dev.gumba21.villageeconomy.market.registry;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DefaultTradeGoods {
    private static final Map<ResourceLocation, TradeGoodDefinition> GOODS =
            createRegistry();

    private DefaultTradeGoods() {
    }

    public static Collection<TradeGoodDefinition> all() {
        return Collections.unmodifiableCollection(GOODS.values());
    }

    public static Optional<TradeGoodDefinition> get(ResourceLocation itemId) {
        return Optional.ofNullable(GOODS.get(itemId));
    }

    public static int size() {
        return GOODS.size();
    }

    private static Map<ResourceLocation, TradeGoodDefinition> createRegistry() {
        Map<ResourceLocation, TradeGoodDefinition> goods = new LinkedHashMap<>();

        register(goods, "wheat", 1.0, 96.0, 64.0);
        register(goods, "bread", 3.0, 48.0, 64.0);
        register(goods, "carrot", 1.0, 80.0, 64.0);
        register(goods, "potato", 1.0, 80.0, 64.0);
        register(goods, "beetroot", 1.0, 64.0, 48.0);
        register(goods, "apple", 4.0, 32.0, 48.0);
        register(goods, "pumpkin", 6.0, 24.0, 32.0);
        register(goods, "melon", 3.0, 32.0, 40.0);
        register(goods, "egg", 2.0, 48.0, 48.0);
        register(goods, "milk_bucket", 5.0, 16.0, 24.0);
        register(goods, "coal", 2.0, 64.0, 80.0);
        register(goods, "iron_ingot", 8.0, 32.0, 64.0);
        register(goods, "gold_ingot", 12.0, 20.0, 40.0);
        register(goods, "emerald", 24.0, 16.0, 64.0);
        register(goods, "diamond", 64.0, 4.0, 32.0);
        register(goods, "stick", 0.25, 128.0, 64.0);
        register(goods, "oak_log", 2.0, 64.0, 64.0);
        register(goods, "oak_planks", 0.5, 128.0, 80.0);
        register(goods, "stone", 0.5, 128.0, 64.0);
        register(goods, "cobblestone", 0.25, 192.0, 64.0);
        register(goods, "cooked_beef", 6.0, 24.0, 48.0);
        register(goods, "cooked_porkchop", 6.0, 24.0, 48.0);
        register(goods, "cooked_chicken", 4.0, 32.0, 48.0);
        register(goods, "cooked_mutton", 5.0, 24.0, 40.0);
        register(goods, "leather", 4.0, 32.0, 48.0);
        register(goods, "paper", 1.5, 64.0, 48.0);
        register(goods, "bookshelf", 12.0, 12.0, 32.0);

        return goods;
    }

    private static void register(
            Map<ResourceLocation, TradeGoodDefinition> goods,
            String itemPath,
            double basePrice,
            double initialSupply,
            double initialDemand
    ) {
        ResourceLocation itemId = new ResourceLocation("minecraft", itemPath);
        TradeGoodDefinition definition = new TradeGoodDefinition(
                itemId,
                basePrice,
                initialSupply,
                initialDemand
        );
        if (goods.putIfAbsent(itemId, definition) != null) {
            throw new IllegalStateException("Duplicate default trade good: " + itemId);
        }
    }
}
