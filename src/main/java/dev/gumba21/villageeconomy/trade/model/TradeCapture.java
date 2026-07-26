package dev.gumba21.villageeconomy.trade.model;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Third-party-free result of the immediate compatibility hook.
 */
public record TradeCapture(
        long executionId,
        long gameTime,
        UUID playerId,
        UUID villagerId,
        ResourceLocation dimensionId,
        BlockPos villagerPosition,
        ResourceLocation professionId,
        int villagerLevel,
        TradeDirection direction,
        Optional<ItemSnapshot> marketItem,
        int itemQuantity,
        Optional<MarketValue> monetaryValue,
        TransactionSource source,
        ObservationStatus status,
        String detail,
        Optional<TradeExecutionSnapshot> executionSnapshot
) {
    public TradeCapture {
        if (executionId <= 0L) {
            throw new IllegalArgumentException("executionId must be positive");
        }
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(villagerId, "villagerId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        villagerPosition = Objects.requireNonNull(
                villagerPosition,
                "villagerPosition"
        ).immutable();
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(direction, "direction");
        marketItem = Objects.requireNonNull(marketItem, "marketItem");
        monetaryValue = Objects.requireNonNull(monetaryValue, "monetaryValue");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNullElse(detail, "");
        executionSnapshot = Objects.requireNonNull(
                executionSnapshot,
                "executionSnapshot"
        );
    }

    @Override
    public BlockPos villagerPosition() {
        return villagerPosition.immutable();
    }
}
