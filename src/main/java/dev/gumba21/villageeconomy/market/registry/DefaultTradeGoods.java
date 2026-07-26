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

    public static TradeGoodDefinition getOrNull(ResourceLocation itemId) {
        return GOODS.get(itemId);
    }

    public static int size() {
        return GOODS.size();
    }

    private static Map<ResourceLocation, TradeGoodDefinition> createRegistry() {
        Map<ResourceLocation, TradeGoodDefinition> goods = new LinkedHashMap<>();

        register(goods, "wheat", 1.0, 96.0, 64.0, SupplyDriver.FARMER);
        register(goods, "bread", 3.0, 48.0, 64.0, SupplyDriver.FARMER);
        register(goods, "carrot", 1.0, 80.0, 64.0, SupplyDriver.FARMER);
        register(goods, "potato", 1.0, 80.0, 64.0, SupplyDriver.FARMER);
        register(goods, "beetroot", 1.0, 64.0, 48.0, SupplyDriver.FARMER);
        register(goods, "apple", 4.0, 32.0, 48.0, SupplyDriver.FARMER);
        register(goods, "pumpkin", 6.0, 24.0, 32.0, SupplyDriver.FARMER);
        register(goods, "melon", 3.0, 32.0, 40.0, SupplyDriver.FARMER);
        register(goods, "egg", 2.0, 48.0, 48.0, SupplyDriver.FARMER);
        register(goods, "milk_bucket", 5.0, 16.0, 24.0, SupplyDriver.FARMER);
        register(goods, "coal", 2.0, 64.0, 80.0, SupplyDriver.SMITH);
        register(goods, "iron_ingot", 8.0, 32.0, 64.0, SupplyDriver.SMITH);
        register(goods, "iron_shovel", 12.0, 12.0, 24.0, SupplyDriver.SMITH);
        register(goods, "iron_pickaxe", 24.0, 8.0, 32.0, SupplyDriver.SMITH);
        register(goods, "iron_axe", 24.0, 8.0, 24.0, SupplyDriver.SMITH);
        register(goods, "iron_hoe", 16.0, 8.0, 16.0, SupplyDriver.SMITH);
        register(goods, "gold_ingot", 12.0, 20.0, 40.0, SupplyDriver.SMITH);
        register(goods, "emerald", 24.0, 16.0, 64.0, SupplyDriver.SMITH);
        register(goods, "diamond", 64.0, 4.0, 32.0, SupplyDriver.SMITH);
        register(goods, "stick", 0.25, 128.0, 64.0, SupplyDriver.FLETCHER);
        register(goods, "oak_log", 2.0, 64.0, 64.0, SupplyDriver.FLETCHER);
        register(goods, "oak_planks", 0.5, 128.0, 80.0, SupplyDriver.FLETCHER);
        register(goods, "stone", 0.5, 128.0, 64.0, SupplyDriver.MASON);
        register(goods, "cobblestone", 0.25, 192.0, 64.0, SupplyDriver.MASON);
        register(goods, "cooked_beef", 6.0, 24.0, 48.0, SupplyDriver.BUTCHER);
        register(goods, "cooked_porkchop", 6.0, 24.0, 48.0, SupplyDriver.BUTCHER);
        register(goods, "cooked_chicken", 4.0, 32.0, 48.0, SupplyDriver.BUTCHER);
        register(goods, "cooked_mutton", 5.0, 24.0, 40.0, SupplyDriver.BUTCHER);
        register(goods, "leather", 4.0, 32.0, 48.0, SupplyDriver.LEATHERWORKER);
        register(goods, "paper", 1.5, 64.0, 48.0, SupplyDriver.LIBRARIAN);
        register(goods, "bookshelf", 12.0, 12.0, 32.0, SupplyDriver.LIBRARIAN);

        return goods;
    }

    private static void register(
            Map<ResourceLocation, TradeGoodDefinition> goods,
            String itemPath,
            double basePrice,
            double initialSupply,
            double initialDemand,
            SupplyDriver supplyDriver
    ) {
        ResourceLocation itemId = new ResourceLocation("minecraft", itemPath);
        TradeGoodDefinition definition = new TradeGoodDefinition(
                itemId,
                basePrice,
                initialSupply,
                initialDemand,
                supplyDriver
        );
        if (goods.putIfAbsent(itemId, definition) != null) {
            throw new IllegalStateException("Duplicate default trade good: " + itemId);
        }
    }
}
