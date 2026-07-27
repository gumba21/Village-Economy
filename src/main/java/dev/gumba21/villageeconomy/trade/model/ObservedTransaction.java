package dev.gumba21.villageeconomy.trade.model;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record ObservedTransaction(
        UUID transactionId,
        long gameTime,
        UUID playerId,
        UUID villagerId,
        ResourceLocation dimensionId,
        BlockPos villagerPosition,
        ResourceLocation professionId,
        int villagerLevel,
        TradeDirection direction,
        ResourceLocation marketItemId,
        int itemQuantity,
        MarketValue monetaryValue,
        VillageReference village,
        MarketReference market,
        TransactionSource source,
        TradeExecutionSnapshot executionSnapshot
) {
    public ObservedTransaction {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(villagerId, "villagerId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        villagerPosition = Objects.requireNonNull(
                villagerPosition,
                "villagerPosition"
        ).immutable();
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(marketItemId, "marketItemId");
        if (itemQuantity <= 0) {
            throw new IllegalArgumentException("itemQuantity must be positive");
        }
        Objects.requireNonNull(monetaryValue, "monetaryValue");
        if (monetaryValue.baseUnits() == 0L) {
            throw new IllegalArgumentException("monetaryValue must be positive");
        }
        Objects.requireNonNull(village, "village");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(executionSnapshot, "executionSnapshot");
    }

    @Override
    public BlockPos villagerPosition() {
        return villagerPosition.immutable();
    }
}
