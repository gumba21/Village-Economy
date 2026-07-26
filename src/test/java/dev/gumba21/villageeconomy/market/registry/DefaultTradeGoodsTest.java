package dev.gumba21.villageeconomy.market.registry;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTradeGoodsTest {
    @Test
    void containsEveryFoundationTradeGoodExactlyOnce() {
        Set<ResourceLocation> itemIds = DefaultTradeGoods.all().stream()
                .map(TradeGoodDefinition::itemId)
                .collect(Collectors.toSet());

        assertEquals(27, DefaultTradeGoods.size());
        assertEquals(DefaultTradeGoods.size(), itemIds.size());
        assertTrue(itemIds.contains(new ResourceLocation("minecraft", "wheat")));
        assertTrue(itemIds.contains(new ResourceLocation("minecraft", "milk_bucket")));
        assertTrue(itemIds.contains(new ResourceLocation("minecraft", "diamond")));
        assertTrue(itemIds.contains(new ResourceLocation("minecraft", "bookshelf")));
        assertTrue(DefaultTradeGoods.all().stream().allMatch(
                definition -> definition.basePrice() > 0.0
                        && definition.initialSupply() >= 0.0
                        && definition.initialDemand() >= 0.0
        ));
    }
}
