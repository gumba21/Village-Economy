package dev.gumba21.villageeconomy.trade.model;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;

import java.util.Objects;
import java.util.Set;

/**
 * Small immutable item identity snapshot. Arbitrary NBT is deliberately not
 * retained.
 */
public record ItemSnapshot(
        ResourceLocation itemId,
        int count,
        boolean damaged,
        boolean customName,
        ItemIdentityKind identityKind
) {
    private static final Set<String> COSMETIC_OR_DAMAGE_TAGS = Set.of(
            "display",
            "Damage",
            "RepairCost"
    );

    public ItemSnapshot {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(identityKind, "identityKind");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    public static ItemSnapshot from(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || stack.getCount() <= 0) {
            throw new IllegalArgumentException("Cannot snapshot an empty item stack");
        }

        Item item = stack.getItem();
        ItemIdentityKind kind;
        if (item == Items.ENCHANTED_BOOK) {
            kind = ItemIdentityKind.ENCHANTED_BOOK;
        } else if (item == Items.FILLED_MAP) {
            kind = ItemIdentityKind.FILLED_MAP;
        } else if (item instanceof PotionItem) {
            kind = ItemIdentityKind.POTION;
        } else if (stack.isEnchanted()) {
            kind = ItemIdentityKind.ENCHANTED_ITEM;
        } else if (hasIdentityBearingCustomData(stack.getTag())) {
            kind = ItemIdentityKind.CUSTOM_DATA;
        } else {
            kind = ItemIdentityKind.ORDINARY;
        }

        return new ItemSnapshot(
                BuiltInRegistries.ITEM.getKey(item),
                stack.getCount(),
                stack.isDamaged(),
                stack.hasCustomHoverName(),
                kind
        );
    }

    private static boolean hasIdentityBearingCustomData(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        for (String key : tag.getAllKeys()) {
            if (!COSMETIC_OR_DAMAGE_TAGS.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
