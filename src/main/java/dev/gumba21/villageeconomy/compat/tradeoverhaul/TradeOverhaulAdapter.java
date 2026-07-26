package dev.gumba21.villageeconomy.compat.tradeoverhaul;

import com.unnameduser.tradeoverhaul.common.VillagerTradeData;
import com.unnameduser.tradeoverhaul.common.component.VillagerCurrencyComponent;
import com.unnameduser.tradeoverhaul.common.config.ProfessionTradeFile;
import com.unnameduser.tradeoverhaul.common.config.TradeConfigLoader;
import com.unnameduser.tradeoverhaul.common.trade.TradePricing;
import dev.gumba21.villageeconomy.compat.currency.CurrencyBreakdown;
import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.dynamictrades.DynamicVillagerTradesAdapter;
import dev.gumba21.villageeconomy.compat.trade.ObservedOffer;
import dev.gumba21.villageeconomy.compat.trade.ObservedTrade;
import dev.gumba21.villageeconomy.compat.trade.TradeClassifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only server-side boundary around Trade Overhaul 1.0.1.
 *
 * <p>Trade Overhaul's villager wallet remains separate from Numismatic
 * Overhaul's player component even though both use the same denomination
 * ratios.</p>
 */
public final class TradeOverhaulAdapter {
    private final DynamicVillagerTradesAdapter dynamicTradesAdapter;
    private final TradeClassifier tradeClassifier;

    public TradeOverhaulAdapter(
            DynamicVillagerTradesAdapter dynamicTradesAdapter,
            TradeClassifier tradeClassifier
    ) {
        this.dynamicTradesAdapter = Objects.requireNonNull(
                dynamicTradesAdapter,
                "dynamicTradesAdapter"
        );
        this.tradeClassifier = Objects.requireNonNull(
                tradeClassifier,
                "tradeClassifier"
        );
    }

    public boolean isTradeOverhaulEntity(Entity entity) {
        return entity instanceof VillagerTradeData;
    }

    public CurrencyBreakdown readCurrency(Villager villager) {
        return readCurrencyComponent(data(villager).tradeOverhaul$getCurrency());
    }

    public TradeOverhaulVillagerSnapshot inspectVillager(Villager villager) {
        VillagerTradeData data = data(villager);
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());

        List<ObservedOffer> offers = dynamicTradesAdapter.inspectAll(
                villager.getOffers()
        );
        List<ObservedTrade> classified = new ArrayList<>(offers.size());
        for (ObservedOffer offer : offers) {
            classified.add(tradeClassifier.classify(offer));
        }

        return new TradeOverhaulVillagerSnapshot(
                readCurrencyComponent(data.tradeOverhaul$getCurrency()),
                profession,
                data.tradeOverhaul$getProfession().getLevel(),
                classified
        );
    }

    /**
     * Reads Trade Overhaul's configured buy/sell prices without applying,
     * replacing, or mutating them.
     */
    public Optional<TradePriceSnapshot> readConfiguredPrices(
            Villager villager,
            ItemStack stack
    ) {
        Objects.requireNonNull(stack, "stack");
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        ProfessionTradeFile professionFile =
                TradeConfigLoader.getProfession(profession);
        if (professionFile == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TradePriceSnapshot(
                BuiltInRegistries.ITEM.getKey(stack.getItem()),
                TradePricing.getBuyPrice(stack.copy(), professionFile),
                TradePricing.getSellPrice(stack.copy(), professionFile)
        ));
    }

    /**
     * Public for isolated read-only compatibility tests.
     */
    public static CurrencyBreakdown readCurrencyComponent(
            VillagerCurrencyComponent component
    ) {
        Objects.requireNonNull(component, "component");
        CurrencyBreakdown breakdown = CurrencyBreakdown.of(
                component.getGold(),
                component.getSilver(),
                component.getCopper()
        );
        int reportedTotal = component.getTotalCopper();
        if (reportedTotal < 0
                || breakdown.total().baseUnits() != (long) reportedTotal) {
            throw new IllegalStateException(
                    "Trade Overhaul villager currency fields are inconsistent"
            );
        }
        return breakdown;
    }

    public static MarketValue normalizeVillagerCurrency(
            VillagerCurrencyComponent component
    ) {
        return readCurrencyComponent(component).total();
    }

    private static VillagerTradeData data(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        if (!(villager instanceof VillagerTradeData tradeData)) {
            throw new IllegalArgumentException(
                    "Villager is not initialized by required Trade Overhaul"
            );
        }
        return tradeData;
    }
}
