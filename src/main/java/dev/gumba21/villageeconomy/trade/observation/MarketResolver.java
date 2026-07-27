package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.market.data.MarketState;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface MarketResolver {
    Optional<MarketState> resolve(UUID villageId);
}
