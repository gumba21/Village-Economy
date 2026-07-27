package dev.gumba21.villageeconomy.trade.mapping;

import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.trade.model.ItemIdentityKind;
import dev.gumba21.villageeconomy.trade.model.ItemSnapshot;

import java.util.Objects;

/**
 * Conservative bridge from runtime item identity to the existing market's
 * canonical registry identifiers.
 */
public final class MarketItemMapper {
    public MarketItemMappingResult map(
            ItemSnapshot item,
            MarketState market
    ) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(market, "market");

        if (item.count() <= 0) {
            return new MarketItemMappingResult.Unsupported(
                    "item quantity is not positive"
            );
        }

        ItemIdentityKind kind = item.identityKind();
        if (kind == ItemIdentityKind.ENCHANTED_BOOK) {
            return unsupported("enchanted-book identity depends on enchantment NBT");
        }
        if (kind == ItemIdentityKind.FILLED_MAP) {
            return unsupported("filled-map identity depends on map data");
        }
        if (kind == ItemIdentityKind.POTION) {
            return unsupported("potion identity depends on potion NBT");
        }
        if (kind == ItemIdentityKind.ENCHANTED_ITEM) {
            return unsupported("enchanted item value depends on enchantment NBT");
        }
        if (kind == ItemIdentityKind.CUSTOM_DATA) {
            return unsupported("item contains identity-bearing custom NBT");
        }

        if (market.getEntry(item.itemId()).isEmpty()) {
            return unsupported(
                    "item is not tracked by this village market: " + item.itemId()
            );
        }

        // Damage and custom names do not change the canonical registry item
        // identity. They remain visible in the diagnostic ItemSnapshot.
        return new MarketItemMappingResult.Mapped(item.itemId());
    }

    private static MarketItemMappingResult.Unsupported unsupported(
            String reason
    ) {
        return new MarketItemMappingResult.Unsupported(reason);
    }
}
