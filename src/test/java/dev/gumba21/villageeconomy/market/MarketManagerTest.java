package dev.gumba21.villageeconomy.market;

import dev.gumba21.villageeconomy.MinecraftTestBootstrap;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.market.registry.DefaultTradeGoods;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import dev.gumba21.villageeconomy.village.data.VillagePersistentState;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadEvidence;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadReconciler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketManagerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void createsDefaultsAndPreventsDuplicateMarketInstances() {
        VillagePersistentState state = new VillagePersistentState();
        UUID villageId = addVillage(state, 0);
        MarketManager manager = new MarketManager(state);

        MarketState first = manager.createMarket(villageId);
        MarketState second = manager.createMarket(villageId);

        assertSame(first, second);
        assertEquals(1, state.marketSize());
        assertEquals(DefaultTradeGoods.size(), first.size());
    }

    @Test
    void retrievesCurrentPriceByMinecraftItem() {
        VillagePersistentState state = new VillagePersistentState();
        UUID villageId = addVillage(state, 0);
        MarketManager manager = new MarketManager(state);
        manager.createMarket(villageId);

        assertEquals(
                1.0,
                manager.getPrice(villageId, Items.WHEAT).orElseThrow()
        );
        assertTrue(manager.getPrice(villageId, Items.NETHER_STAR).isEmpty());
    }

    @Test
    void generatesOnlyMarketsMissingFromTrackedVillages() {
        VillagePersistentState state = new VillagePersistentState();
        UUID firstVillage = addVillage(state, 0);
        UUID secondVillage = addVillage(state, 500);
        MarketManager manager = new MarketManager(state);
        manager.createMarket(firstVillage);

        assertEquals(1, manager.ensureMarkets(state.getVillages()));
        assertTrue(manager.hasMarket(firstVillage));
        assertTrue(manager.hasMarket(secondVillage));
        assertEquals(0, manager.ensureMarkets(state.getVillages()));
    }

    @Test
    void removesAndResetsMarketsWithoutCreatingDuplicates() {
        VillagePersistentState state = new VillagePersistentState();
        UUID villageId = addVillage(state, 0);
        MarketManager manager = new MarketManager(state);
        MarketState original = manager.createMarket(villageId);

        MarketState reset = manager.resetMarket(villageId);

        assertFalse(original == reset);
        assertEquals(1, state.marketSize());
        assertTrue(manager.removeMarket(villageId));
        assertFalse(manager.removeMarket(villageId));
        assertFalse(manager.hasMarket(villageId));
    }

    @Test
    void loadedStateLifecycleDoesNotChangeMarketOwnership() {
        VillagePersistentState state = new VillagePersistentState();
        UUID villageId = addVillage(state, 0);
        TrackedVillage village = state.getVillages().iterator().next();
        MarketManager manager = new MarketManager(state);
        MarketState market = manager.createMarket(villageId);
        VillageLoadReconciler reconciler = new VillageLoadReconciler();
        VillageLoadEvidence absent =
                VillageLoadEvidence.absent(village.getVillagerCount());

        reconciler.reconcile(village, false, absent);
        reconciler.reconcile(village, false, absent);
        reconciler.reconcile(
                village,
                false,
                new VillageLoadEvidence(village.getVillagerCount(), 1, 1, 0)
        );

        assertTrue(village.isLoaded());
        assertSame(market, manager.getMarket(villageId).orElseThrow());
        assertEquals(1, state.marketSize());
    }

    private UUID addVillage(VillagePersistentState state, int x) {
        UUID id = UUID.randomUUID();
        state.add(new TrackedVillage(
                id,
                new BlockPos(x, 64, 0),
                Level.OVERWORLD,
                64,
                1_000L,
                1_000L,
                2,
                1,
                true
        ));
        return id;
    }
}
