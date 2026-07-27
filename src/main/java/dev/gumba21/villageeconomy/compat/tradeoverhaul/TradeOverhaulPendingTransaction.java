package dev.gumba21.villageeconomy.compat.tradeoverhaul;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Short-lived immutable pre-execution snapshot. It stores no entity,
 * component, handler, or inventory references.
 */
public record TradeOverhaulPendingTransaction(
        long executionId,
        int clickedSlot,
        UUID playerId,
        UUID villagerId,
        TradeDirection direction,
        ItemStack itemBefore,
        int sourceCountBefore,
        MarketValue playerBalanceBefore
) {
    public TradeOverhaulPendingTransaction {
        if (executionId <= 0L) {
            throw new IllegalArgumentException("executionId must be positive");
        }
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(villagerId, "villagerId");
        Objects.requireNonNull(direction, "direction");
        itemBefore = Objects.requireNonNull(itemBefore, "itemBefore").copy();
        Objects.requireNonNull(playerBalanceBefore, "playerBalanceBefore");
        if (itemBefore.isEmpty() || sourceCountBefore <= 0) {
            throw new IllegalArgumentException("source item must be present");
        }
    }

    @Override
    public ItemStack itemBefore() {
        return itemBefore.copy();
    }
}
