package dev.gumba21.villageeconomy.compat.trade;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.currency.NumismaticCurrencyAdapter;
import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Pure read-only classification logic. Unknown or mixed shapes are never
 * guessed.
 */
public final class TradeClassifier {
    private final Function<ItemStack, Optional<MarketValue>> currencyReader;
    private final Set<String> warnedUnknownShapes = ConcurrentHashMap.newKeySet();

    public TradeClassifier(NumismaticCurrencyAdapter numismaticAdapter) {
        this(numismaticAdapter::valueOfCoinStack);
    }

    TradeClassifier(
            Function<ItemStack, Optional<MarketValue>> currencyReader
    ) {
        this.currencyReader = currencyReader;
    }

    public ObservedTrade classify(ObservedOffer offer) {
        ItemStack first = offer.firstInput();
        ItemStack second = offer.secondInput();
        ItemStack output = offer.output();

        Optional<MarketValue> firstMoney = readCurrency(first);
        Optional<MarketValue> secondMoney = readCurrency(second);
        Optional<MarketValue> outputMoney = readCurrency(output);
        int inputCurrencies = present(firstMoney) + present(secondMoney);
        int inputItems = nonCurrencyItem(first, firstMoney)
                + nonCurrencyItem(second, secondMoney);

        TradeDirection direction = TradeDirection.UNKNOWN;
        Optional<MarketValue> money = Optional.empty();
        Optional<ResourceLocation> primary = Optional.empty();

        if (!output.isEmpty() && outputMoney.isEmpty()
                && inputCurrencies > 0 && inputItems == 0) {
            direction = TradeDirection.PLAYER_BUYS;
            money = Optional.of(sum(firstMoney, secondMoney));
            primary = itemId(output);
        } else if (outputMoney.isPresent()
                && inputCurrencies == 0 && inputItems == 1) {
            direction = TradeDirection.PLAYER_SELLS;
            money = outputMoney;
            primary = !first.isEmpty() ? itemId(first) : itemId(second);
        } else if (outputMoney.isPresent()
                && inputCurrencies > 0 && inputItems == 0) {
            direction = TradeDirection.EXCHANGE;
            money = outputMoney;
            primary = itemId(output);
        }

        if (direction == TradeDirection.UNKNOWN) {
            logUnknownOnce(first, second, output);
        }

        return new ObservedTrade(
                primary,
                direction,
                first,
                second,
                output,
                money,
                offer.uses(),
                offer.maximumUses(),
                offer.specialPrice(),
                offer.dynamicVillagerTradesOffer()
        );
    }

    private Optional<MarketValue> readCurrency(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return currencyReader.apply(stack);
    }

    private static int present(Optional<MarketValue> value) {
        return value.isPresent() ? 1 : 0;
    }

    private static int nonCurrencyItem(
            ItemStack stack,
            Optional<MarketValue> currency
    ) {
        return stack != null && !stack.isEmpty() && currency.isEmpty() ? 1 : 0;
    }

    private static MarketValue sum(
            Optional<MarketValue> first,
            Optional<MarketValue> second
    ) {
        MarketValue total = MarketValue.zero();
        if (first.isPresent()) {
            total = total.add(first.get());
        }
        if (second.isPresent()) {
            total = total.add(second.get());
        }
        return total;
    }

    private static Optional<ResourceLocation> itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private void logUnknownOnce(
            ItemStack first,
            ItemStack second,
            ItemStack output
    ) {
        String key = shapePart(first) + '|' + shapePart(second)
                + "->" + shapePart(output);
        if (warnedUnknownShapes.add(key)) {
            VillageEconomyDebugLogger.info(
                    "Unsupported trade shape observed during compatibility inspection: {}",
                    key
            );
        }
    }

    private static String shapePart(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
