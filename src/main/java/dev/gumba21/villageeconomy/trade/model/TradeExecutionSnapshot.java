package dev.gumba21.villageeconomy.trade.model;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;

import java.util.Objects;

public record TradeExecutionSnapshot(
        ItemSnapshot itemBefore,
        int sourceCountBefore,
        int sourceCountAfter,
        MarketValue playerBalanceBefore,
        MarketValue playerBalanceAfter
) {
    public TradeExecutionSnapshot {
        Objects.requireNonNull(itemBefore, "itemBefore");
        Objects.requireNonNull(playerBalanceBefore, "playerBalanceBefore");
        Objects.requireNonNull(playerBalanceAfter, "playerBalanceAfter");
        if (sourceCountBefore <= 0 || sourceCountAfter < 0) {
            throw new IllegalArgumentException("Invalid source item counts");
        }
    }
}
