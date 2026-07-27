package dev.gumba21.villageeconomy.market.data;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketStateTest {
    @Test
    void keepsOneEntryPerItemIdentifier() {
        ResourceLocation wheat = new ResourceLocation("minecraft", "wheat");
        MarketEntry first = entry(wheat, 1.0);
        MarketEntry duplicate = entry(wheat, 2.0);

        MarketState market = new MarketState(
                UUID.randomUUID(),
                1_000L,
                1_000L,
                List.of(first, duplicate)
        );

        assertEquals(1, market.size());
        assertEquals(1.0, market.getEntry(wheat).orElseThrow().getBasePrice());
    }

    @Test
    void rejectsPricesOutsideEntryBounds() {
        ResourceLocation wheat = new ResourceLocation("minecraft", "wheat");

        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketEntry(
                        wheat,
                        10.0,
                        30.0,
                        0.5,
                        2.0,
                        10.0,
                        10.0,
                        1_000L
                )
        );
    }

    @Test
    void exposesEntriesInStableIdentifierOrder() {
        ResourceLocation wheat =
                new ResourceLocation("minecraft", "wheat");
        ResourceLocation carrot =
                new ResourceLocation("minecraft", "carrot");
        MarketState market = new MarketState(
                UUID.randomUUID(),
                1_000L,
                1_000L,
                List.of(entry(wheat, 1.0), entry(carrot, 1.0))
        );

        assertEquals(
                List.of(carrot, wheat),
                market.getEntries().stream()
                        .map(MarketEntry::getItemId)
                        .toList()
        );
    }

    private MarketEntry entry(ResourceLocation itemId, double basePrice) {
        return new MarketEntry(
                itemId,
                basePrice,
                basePrice,
                0.5,
                2.0,
                64.0,
                64.0,
                1_000L
        );
    }
}
