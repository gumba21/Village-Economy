package dev.gumba21.villageeconomy.compat.trade;

import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Neutral classified trade shape owned by Village Economy.
 */
public record ObservedTrade(
        Optional<ResourceLocation> primaryItem,
        TradeDirection direction,
        ItemStack firstInput,
        ItemStack secondInput,
        ItemStack output,
        Optional<MarketValue> monetaryValue,
        int uses,
        int maximumUses,
        int specialPrice,
        boolean dynamicVillagerTradesOffer
) {
    public ObservedTrade {
        primaryItem = Objects.requireNonNull(primaryItem, "primaryItem");
        direction = Objects.requireNonNull(direction, "direction");
        firstInput = Objects.requireNonNull(firstInput, "firstInput").copy();
        secondInput = Objects.requireNonNull(secondInput, "secondInput").copy();
        output = Objects.requireNonNull(output, "output").copy();
        monetaryValue = Objects.requireNonNull(monetaryValue, "monetaryValue");
    }

    @Override
    public ItemStack firstInput() {
        return firstInput.copy();
    }

    @Override
    public ItemStack secondInput() {
        return secondInput.copy();
    }

    @Override
    public ItemStack output() {
        return output.copy();
    }
}
