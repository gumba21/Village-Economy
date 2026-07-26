package dev.gumba21.villageeconomy.compat.dynamictrades;

import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import dev.gumba21.villageeconomy.compat.trade.ObservedOffer;
import io.github.orlouge.dynamicvillagertrades.trade_offers.ExtendedTradeOffer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicVillagerTradesAdapterTest {
    private final DynamicVillagerTradesAdapter adapter =
            new DynamicVillagerTradesAdapter();

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void inspectsNormalMerchantOffer() {
        MerchantOffer offer = offer();
        offer.increaseUses();
        offer.addToSpecialPriceDiff(2);

        ObservedOffer observed = adapter.inspect(offer);

        assertFalse(observed.dynamicVillagerTradesOffer());
        assertEquals(3, observed.firstInput().getCount());
        assertEquals(1, observed.output().getCount());
        assertEquals(1, observed.uses());
        assertEquals(12, observed.maximumUses());
        assertEquals(2, observed.specialPrice());
    }

    @Test
    void recognizesAndUnwrapsExtendedOffer() {
        MerchantOffer base = offer();
        ExtendedTradeOffer extended = new ExtendedTradeOffer(
                base,
                Map.of("quality", 1.25),
                false
        );

        ObservedOffer observed = adapter.inspect(extended);

        assertTrue(adapter.isDynamicOffer(extended));
        assertTrue(observed.dynamicVillagerTradesOffer());
        assertEquals(Items.WHEAT, observed.firstInput().getItem());
        assertEquals(Items.BREAD, observed.output().getItem());
    }

    @Test
    void inspectAllSkipsNullAndReturnsCopies() {
        MerchantOffer offer = offer();
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        offers.add(null);

        var observed = adapter.inspectAll(offers);
        offer.getBaseCostA().setCount(1);

        assertEquals(1, observed.size());
        assertEquals(3, observed.get(0).firstInput().getCount());
    }

    @Test
    void unusualEmptyOfferDoesNotCrash() {
        MerchantOffer unusual = new MerchantOffer(
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                1,
                0,
                0.0F
        );

        ObservedOffer observed = adapter.inspect(unusual);

        assertTrue(observed.firstInput().isEmpty());
        assertTrue(observed.output().isEmpty());
    }

    private static MerchantOffer offer() {
        return new MerchantOffer(
                new ItemStack(Items.WHEAT, 3),
                new ItemStack(Items.BREAD, 1),
                12,
                2,
                0.05F
        );
    }
}
