package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record TransactionKey(
        long executionId,
        UUID playerId,
        UUID villagerId,
        TradeDirection direction,
        ResourceLocation itemId,
        int quantity,
        long baseUnits
) {
    public TransactionKey {
        if (executionId <= 0L) {
            throw new IllegalArgumentException("executionId must be positive");
        }
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(villagerId, "villagerId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(itemId, "itemId");
    }
}
