package dev.gumba21.villageeconomy.trade.mapping;

import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.trade.model.ItemIdentityKind;
import dev.gumba21.villageeconomy.trade.model.ItemSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MarketItemMapperTest {
    private static final long NOW = 1_000L;
    private final MarketItemMapper mapper = new MarketItemMapper();

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void ordinaryTrackedItemMapsByRegistryIdentifier() {
        assertMapped(snapshot("minecraft:bread", ItemIdentityKind.ORDINARY));
    }

    @Test
    void damagedToolKeepsCanonicalItemIdentityWhenTracked() {
        ItemSnapshot tool = new ItemSnapshot(
                id("minecraft:iron_pickaxe"),
                1,
                true,
                false,
                ItemIdentityKind.ORDINARY
        );
        assertInstanceOf(
                MarketItemMappingResult.Mapped.class,
                mapper.map(tool, market("minecraft:iron_pickaxe"))
        );
    }

    @Test
    void cosmeticRenameDoesNotChangeEconomicIdentity() {
        ItemStack stack = new ItemStack(Items.BREAD);
        stack.setHoverName(Component.literal("very serious bread"));
        assertInstanceOf(
                MarketItemMappingResult.Mapped.class,
                mapper.map(ItemSnapshot.from(stack), market("minecraft:bread"))
        );
    }

    @Test
    void moddedIdentifierMapsOnlyWhenMarketExplicitlyTracksIt() {
        ItemSnapshot modded = snapshot(
                "example:turnip",
                ItemIdentityKind.ORDINARY
        );
        assertInstanceOf(
                MarketItemMappingResult.Mapped.class,
                mapper.map(modded, market("example:turnip"))
        );
        assertInstanceOf(
                MarketItemMappingResult.Unsupported.class,
                mapper.map(modded, market("minecraft:bread"))
        );
    }

    @Test
    void enchantedBookIsUnsupported() {
        assertUnsupported(ItemIdentityKind.ENCHANTED_BOOK);
    }

    @Test
    void filledMapIsUnsupported() {
        assertUnsupported(ItemIdentityKind.FILLED_MAP);
    }

    @Test
    void potionIsUnsupported() {
        assertUnsupported(ItemIdentityKind.POTION);
    }

    @Test
    void enchantedItemIsUnsupported() {
        assertUnsupported(ItemIdentityKind.ENCHANTED_ITEM);
    }

    @Test
    void identityBearingCustomNbtIsUnsupported() {
        ItemStack stack = new ItemStack(Items.BREAD);
        stack.getOrCreateTag().putString("VillageEconomyTest", "identity");
        assertInstanceOf(
                MarketItemMappingResult.Unsupported.class,
                mapper.map(ItemSnapshot.from(stack), market("minecraft:bread"))
        );
    }

    @Test
    void untrackedOrdinaryItemIsUnsupported() {
        assertInstanceOf(
                MarketItemMappingResult.Unsupported.class,
                mapper.map(
                        snapshot("minecraft:apple", ItemIdentityKind.ORDINARY),
                        market("minecraft:bread")
                )
        );
    }

    private void assertMapped(ItemSnapshot item) {
        assertInstanceOf(
                MarketItemMappingResult.Mapped.class,
                mapper.map(item, market(item.itemId().toString()))
        );
    }

    private void assertUnsupported(ItemIdentityKind kind) {
        assertInstanceOf(
                MarketItemMappingResult.Unsupported.class,
                mapper.map(snapshot("minecraft:bread", kind),
                        market("minecraft:bread"))
        );
    }

    private static ItemSnapshot snapshot(
            String itemId,
            ItemIdentityKind kind
    ) {
        return new ItemSnapshot(id(itemId), 1, false, false, kind);
    }

    private static MarketState market(String itemId) {
        return new MarketState(
                UUID.randomUUID(),
                NOW,
                NOW,
                List.of(new MarketEntry(
                        id(itemId),
                        1.0,
                        1.0,
                        0.5,
                        2.0,
                        10.0,
                        10.0,
                        NOW
                ))
        );
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
