package dev.gumba21.villageeconomy.compat.trade;

import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeClassifierTest {
    private final TradeClassifier classifier = new TradeClassifier(stack -> {
        if (stack.is(Items.GOLD_INGOT)) {
            return Optional.of(MarketValue.ofBaseUnits(
                    Math.multiplyExact(stack.getCount(), 100L)
            ));
        }
        return Optional.empty();
    });

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void currencyInputAndItemOutputIsPlayerBuy() {
        ObservedTrade trade = classify(
                stack(Items.GOLD_INGOT, 3),
                ItemStack.EMPTY,
                stack(Items.BREAD, 1)
        );

        assertEquals(TradeDirection.PLAYER_BUYS, trade.direction());
        assertEquals(300L, trade.monetaryValue().orElseThrow().baseUnits());
    }

    @Test
    void itemInputAndCurrencyOutputIsPlayerSell() {
        ObservedTrade trade = classify(
                stack(Items.WHEAT, 10),
                ItemStack.EMPTY,
                stack(Items.GOLD_INGOT, 2)
        );

        assertEquals(TradeDirection.PLAYER_SELLS, trade.direction());
        assertEquals(200L, trade.monetaryValue().orElseThrow().baseUnits());
    }

    @Test
    void twoCurrencyInputsAreSummedForPlayerBuy() {
        ObservedTrade trade = classify(
                stack(Items.GOLD_INGOT, 2),
                stack(Items.GOLD_INGOT, 3),
                stack(Items.DIAMOND, 1)
        );

        assertEquals(TradeDirection.PLAYER_BUYS, trade.direction());
        assertEquals(500L, trade.monetaryValue().orElseThrow().baseUnits());
    }

    @Test
    void itemPlusCurrencyInputIsAmbiguous() {
        assertEquals(
                TradeDirection.UNKNOWN,
                classify(
                        stack(Items.WHEAT, 5),
                        stack(Items.GOLD_INGOT, 1),
                        stack(Items.BREAD, 1)
                ).direction()
        );
    }

    @Test
    void itemToItemTradeIsUnknown() {
        assertEquals(
                TradeDirection.UNKNOWN,
                classify(
                        stack(Items.WHEAT, 5),
                        ItemStack.EMPTY,
                        stack(Items.BREAD, 1)
                ).direction()
        );
    }

    @Test
    void currencyToCurrencyTradeIsExchange() {
        ObservedTrade trade = classify(
                stack(Items.GOLD_INGOT, 2),
                ItemStack.EMPTY,
                stack(Items.GOLD_INGOT, 1)
        );

        assertEquals(TradeDirection.EXCHANGE, trade.direction());
        assertEquals(100L, trade.monetaryValue().orElseThrow().baseUnits());
    }

    @Test
    void emptyTradeIsUnknownAndDoesNotCrash() {
        ObservedTrade trade = classify(
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );

        assertEquals(TradeDirection.UNKNOWN, trade.direction());
        assertTrue(trade.primaryItem().isEmpty());
        assertTrue(trade.monetaryValue().isEmpty());
    }

    @Test
    void unknownCurrencyItemIsNotFabricatedAsMoney() {
        assertEquals(
                TradeDirection.UNKNOWN,
                classify(
                        stack(Items.EMERALD, 5),
                        ItemStack.EMPTY,
                        stack(Items.BREAD, 1)
                ).direction()
        );
    }

    @Test
    void multipleNonCurrencyInputsForCurrencyAreAmbiguous() {
        assertEquals(
                TradeDirection.UNKNOWN,
                classify(
                        stack(Items.WHEAT, 5),
                        stack(Items.CARROT, 5),
                        stack(Items.GOLD_INGOT, 1)
                ).direction()
        );
    }

    @Test
    void classificationNeverMutatesObservedStacks() {
        ItemStack first = stack(Items.GOLD_INGOT, 4);
        ItemStack output = stack(Items.BREAD, 2);
        ObservedOffer offer = new ObservedOffer(
                first,
                ItemStack.EMPTY,
                output,
                3,
                12,
                -1,
                true
        );

        ObservedTrade trade = classifier.classify(offer);

        assertEquals(4, first.getCount());
        assertEquals(2, output.getCount());
        assertEquals(4, trade.firstInput().getCount());
        assertEquals(2, trade.output().getCount());
        assertEquals(3, trade.uses());
        assertEquals(12, trade.maximumUses());
        assertEquals(-1, trade.specialPrice());
        assertTrue(trade.dynamicVillagerTradesOffer());
    }

    private ObservedTrade classify(
            ItemStack first,
            ItemStack second,
            ItemStack output
    ) {
        return classifier.classify(new ObservedOffer(
                first,
                second,
                output,
                0,
                12,
                0,
                false
        ));
    }

    private static ItemStack stack(
            net.minecraft.world.item.Item item,
            int count
    ) {
        return new ItemStack(item, count);
    }
}
