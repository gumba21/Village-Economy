package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import dev.gumba21.villageeconomy.trade.model.ObservationStatus;
import dev.gumba21.villageeconomy.trade.model.TransactionSource;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DiagnosticObservation(
        long gameTime,
        UUID playerId,
        UUID villagerId,
        ResourceLocation professionId,
        TradeDirection direction,
        Optional<ResourceLocation> itemId,
        int itemQuantity,
        Optional<MarketValue> monetaryValue,
        Optional<UUID> villageId,
        Optional<UUID> marketId,
        ObservationStatus status,
        TransactionSource source,
        String detail
) {
    public DiagnosticObservation {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(villagerId, "villagerId");
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(direction, "direction");
        itemId = Objects.requireNonNull(itemId, "itemId");
        monetaryValue = Objects.requireNonNull(monetaryValue, "monetaryValue");
        villageId = Objects.requireNonNull(villageId, "villageId");
        marketId = Objects.requireNonNull(marketId, "marketId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        detail = Objects.requireNonNullElse(detail, "");
    }
}
