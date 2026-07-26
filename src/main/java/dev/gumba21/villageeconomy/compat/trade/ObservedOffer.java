package dev.gumba21.villageeconomy.compat.trade;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Immutable read-only copy of the common fields exposed by a merchant offer.
 */
public record ObservedOffer(
        ItemStack firstInput,
        ItemStack secondInput,
        ItemStack output,
        int uses,
        int maximumUses,
        int specialPrice,
        boolean dynamicVillagerTradesOffer
) {
    public ObservedOffer {
        firstInput = Objects.requireNonNull(firstInput, "firstInput").copy();
        secondInput = Objects.requireNonNull(secondInput, "secondInput").copy();
        output = Objects.requireNonNull(output, "output").copy();
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
