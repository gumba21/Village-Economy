package dev.gumba21.villageeconomy.trade.mapping;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public sealed interface MarketItemMappingResult {
    record Mapped(ResourceLocation itemId) implements MarketItemMappingResult {
        public Mapped {
            Objects.requireNonNull(itemId, "itemId");
        }
    }

    record Unsupported(String reason) implements MarketItemMappingResult {
        public Unsupported {
            reason = Objects.requireNonNullElse(reason, "unsupported item");
        }
    }

    record Ambiguous(String reason) implements MarketItemMappingResult {
        public Ambiguous {
            reason = Objects.requireNonNullElse(reason, "ambiguous item");
        }
    }
}
