package dev.gumba21.villageeconomy.compat.currency;

import com.glisco.numismaticoverhaul.item.NumismaticOverhaulItems;
import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumismaticCurrencyAdapterTest {
    private final NumismaticCurrencyAdapter adapter =
            new NumismaticCurrencyAdapter();

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void identifiesAllThreeCoinItems() {
        assertTrue(adapter.isBronzeCoin(
                new ItemStack(NumismaticOverhaulItems.BRONZE_COIN)
        ));
        assertTrue(adapter.isSilverCoin(
                new ItemStack(NumismaticOverhaulItems.SILVER_COIN)
        ));
        assertTrue(adapter.isGoldCoin(
                new ItemStack(NumismaticOverhaulItems.GOLD_COIN)
        ));
        assertFalse(adapter.isCoin(new ItemStack(Items.EMERALD)));
    }

    @Test
    void readsCoinStackValuesExactly() {
        assertEquals(
                30_000L,
                adapter.valueOfCoinStack(new ItemStack(
                        NumismaticOverhaulItems.GOLD_COIN,
                        3
                )).orElseThrow().baseUnits()
        );
    }

    @Test
    void combinesMultiplePureCoinStacks() {
        assertEquals(
                10_203L,
                adapter.valueOfCoinStacks(List.of(
                        new ItemStack(NumismaticOverhaulItems.GOLD_COIN, 1),
                        new ItemStack(NumismaticOverhaulItems.SILVER_COIN, 2),
                        new ItemStack(NumismaticOverhaulItems.BRONZE_COIN, 3)
                )).orElseThrow().baseUnits()
        );
    }

    @Test
    void unknownCurrencyItemDoesNotBecomeMoney() {
        assertTrue(adapter.valueOfCoinStack(
                new ItemStack(Items.EMERALD)
        ).isEmpty());
        assertTrue(adapter.valueOfCoinStacks(List.of(
                new ItemStack(NumismaticOverhaulItems.BRONZE_COIN),
                new ItemStack(Items.EMERALD)
        )).isEmpty());
    }

    @Test
    void createsMultipleLegalStacksInsteadOfTruncating() {
        List<ItemStack> stacks = adapter.createCoinStacks(
                MarketValue.ofBaseUnits(
                        100L * CurrencyDenominations.GOLD_VALUE
                )
        );

        assertEquals(2, stacks.size());
        assertEquals(99, stacks.get(0).getCount());
        assertEquals(1, stacks.get(1).getCount());
        assertEquals(
                100L * CurrencyDenominations.GOLD_VALUE,
                adapter.valueOfCoinStacks(stacks).orElseThrow().baseUnits()
        );
    }

    @Test
    void maximumMaterializedBatchIsExact() {
        long goldCoins = (long) NumismaticCurrencyAdapter.MAX_MATERIALIZED_STACKS
                * CurrencyDenominations.COIN_STACK_SIZE;
        MarketValue value = MarketValue.ofBaseUnits(Math.multiplyExact(
                goldCoins,
                CurrencyDenominations.GOLD_VALUE
        ));

        List<ItemStack> stacks = adapter.createCoinStacks(value);

        assertEquals(
                NumismaticCurrencyAdapter.MAX_MATERIALIZED_STACKS,
                stacks.size()
        );
        assertEquals(value, adapter.valueOfCoinStacks(stacks).orElseThrow());
    }

    @Test
    void oversizedMaterializationFailsClearlyButKeepsExactPlan() {
        long goldCoins = ((long) NumismaticCurrencyAdapter.MAX_MATERIALIZED_STACKS
                + 1L) * CurrencyDenominations.COIN_STACK_SIZE;
        MarketValue value = MarketValue.ofBaseUnits(Math.multiplyExact(
                goldCoins,
                CurrencyDenominations.GOLD_VALUE
        ));

        CurrencyStackPlan plan = adapter.planCoinStacks(value);
        assertTrue(
                plan.requiredStacks()
                        > NumismaticCurrencyAdapter.MAX_MATERIALIZED_STACKS
        );
        assertEquals(value, plan.breakdown().total());
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.createCoinStacks(value)
        );
    }
}
