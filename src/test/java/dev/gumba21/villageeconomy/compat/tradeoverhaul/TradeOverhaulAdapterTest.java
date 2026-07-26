package dev.gumba21.villageeconomy.compat.tradeoverhaul;

import com.unnameduser.tradeoverhaul.common.component.VillagerCurrencyComponent;
import dev.gumba21.villageeconomy.compat.currency.CurrencyBreakdown;
import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeOverhaulAdapterTest {
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

    private static VillagerCurrencyComponent componentWith(int baseUnits) {
        VillagerCurrencyComponent component = new VillagerCurrencyComponent();
        component.setTotalCopper(baseUnits);
        return component;
    }
}
