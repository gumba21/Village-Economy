package dev.gumba21.villageeconomy.compat.dynamictrades;

import dev.gumba21.villageeconomy.compat.trade.ObservedOffer;
import io.github.orlouge.dynamicvillagertrades.trade_offers.ExtendedTradeOffer;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only adapter for vanilla and Dynamic Villager Trades merchant offers.
 */
public final class DynamicVillagerTradesAdapter {
    public boolean isDynamicOffer(MerchantOffer offer) {
        return offer instanceof ExtendedTradeOffer;
    }

    public ObservedOffer inspect(MerchantOffer offer) {
        Objects.requireNonNull(offer, "offer");
        return new ObservedOffer(
                offer.getBaseCostA(),
                offer.getCostB(),
                offer.getResult(),
                offer.getUses(),
                offer.getMaxUses(),
                offer.getSpecialPriceDiff(),
                isDynamicOffer(offer)
        );
    }

    public List<ObservedOffer> inspectAll(MerchantOffers offers) {
        Objects.requireNonNull(offers, "offers");
        List<ObservedOffer> inspected = new ArrayList<>(offers.size());
        for (MerchantOffer offer : offers) {
            if (offer != null) {
                inspected.add(inspect(offer));
            }
        }
        return List.copyOf(inspected);
    }
}
