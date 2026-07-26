package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.trade.model.TradeCapture;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;

import java.util.Optional;

@FunctionalInterface
public interface VillageOwnershipResolver {
    Optional<TrackedVillage> resolve(TradeCapture capture);
}
