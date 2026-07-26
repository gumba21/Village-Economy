package dev.gumba21.villageeconomy.compat.tradeoverhaul;

import com.unnameduser.tradeoverhaul.common.component.VillagerCurrencyComponent;
import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import dev.gumba21.villageeconomy.compat.currency.CurrencyBreakdown;
import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeOverhaulAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void villagerCoinTotalNormalizesExactly() {
        VillagerCurrencyComponent component = componentWith(10_203);

        CurrencyBreakdown breakdown =
                TradeOverhaulAdapter.readCurrencyComponent(component);

        assertEquals(1L, breakdown.gold());
        assertEquals(2L, breakdown.silver());
        assertEquals(3L, breakdown.bronze());
        assertEquals(10_203L, breakdown.total().baseUnits());
    }

    @Test
    void denominationFieldsRemainDistinct() {
        CurrencyBreakdown breakdown =
                TradeOverhaulAdapter.readCurrencyComponent(
                        componentWith(201_405)
                );

        assertEquals(20L, breakdown.gold());
        assertEquals(14L, breakdown.silver());
        assertEquals(5L, breakdown.bronze());
    }

    @Test
    void readOnlySnapshotDoesNotMutateVillagerWallet() {
        VillagerCurrencyComponent component = componentWith(98_765);
        int before = component.getTotalCopper();

        TradeOverhaulAdapter.readCurrencyComponent(component);
        TradeOverhaulAdapter.normalizeVillagerCurrency(component);

        assertEquals(before, component.getTotalCopper());
    }

    @Test
    void playerAndVillagerBalancesRemainSeparateValues() {
        MarketValue playerNumismaticBalance =
                MarketValue.ofBaseUnits(999_999L);
        VillagerCurrencyComponent villagerWallet = componentWith(12_345);

        MarketValue normalizedVillager =
                TradeOverhaulAdapter.normalizeVillagerCurrency(villagerWallet);

        assertEquals(999_999L, playerNumismaticBalance.baseUnits());
        assertEquals(12_345L, normalizedVillager.baseUnits());
    }

    @Test
    void completedPurchaseUsesExactPlayerBalanceDelta() {
        MarketValue value = TradeOverhaulAdapter.resolveValue(
                TradeDirection.PLAYER_BUYS,
                MarketValue.ofBaseUnits(20_000L),
                MarketValue.ofBaseUnits(9_899L)
        ).orElseThrow();

        assertEquals(10_101L, value.baseUnits());
    }

    @Test
    void completedSaleUsesExactPlayerBalanceDelta() {
        MarketValue value = TradeOverhaulAdapter.resolveValue(
                TradeDirection.PLAYER_SELLS,
                MarketValue.ofBaseUnits(99L),
                MarketValue.ofBaseUnits(10_199L)
        ).orElseThrow();

        assertEquals(10_100L, value.baseUnits());
    }

    @Test
    void unchangedOrWrongDirectionBalanceIsNotFabricated() {
        assertTrue(TradeOverhaulAdapter.resolveValue(
                TradeDirection.PLAYER_BUYS,
                MarketValue.ofBaseUnits(100L),
                MarketValue.ofBaseUnits(100L)
        ).isEmpty());
        assertTrue(TradeOverhaulAdapter.resolveValue(
                TradeDirection.PLAYER_SELLS,
                MarketValue.ofBaseUnits(100L),
                MarketValue.ofBaseUnits(99L)
        ).isEmpty());
    }

    @Test
    void actualBulkQuantityComesFromSourceStackDelta() {
        ItemStack before = new ItemStack(Items.BREAD, 32);
        ItemStack after = new ItemStack(Items.BREAD, 22);

        assertEquals(22, TradeOverhaulAdapter.sourceCountAfter(before, after));
        assertEquals(
                10,
                before.getCount()
                        - TradeOverhaulAdapter.sourceCountAfter(before, after)
        );
        assertEquals(
                0,
                TradeOverhaulAdapter.sourceCountAfter(before, ItemStack.EMPTY)
        );
    }

    private static VillagerCurrencyComponent componentWith(int baseUnits) {
        VillagerCurrencyComponent component = new VillagerCurrencyComponent();
        component.setTotalCopper(baseUnits);
        return component;
    }
}
