package dev.gumba21.villageeconomy.compat.tradeoverhaul;

import com.unnameduser.tradeoverhaul.common.VillagerTradeData;
import com.unnameduser.tradeoverhaul.client.gui.VillagerTradeScreenHandler;
import com.unnameduser.tradeoverhaul.common.component.VillagerCurrencyComponent;
import com.unnameduser.tradeoverhaul.common.config.ProfessionTradeFile;
import com.unnameduser.tradeoverhaul.common.config.TradeConfigLoader;
import com.unnameduser.tradeoverhaul.common.trade.TradePricing;
import dev.gumba21.villageeconomy.compat.currency.CurrencyBreakdown;
import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.currency.NumismaticCurrencyAdapter;
import dev.gumba21.villageeconomy.compat.dynamictrades.DynamicVillagerTradesAdapter;
import dev.gumba21.villageeconomy.compat.trade.ObservedOffer;
import dev.gumba21.villageeconomy.compat.trade.ObservedTrade;
import dev.gumba21.villageeconomy.compat.trade.TradeClassifier;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import dev.gumba21.villageeconomy.compat.trade.WalletTradeClassification;
import dev.gumba21.villageeconomy.trade.model.ItemSnapshot;
import dev.gumba21.villageeconomy.trade.model.ObservationStatus;
import dev.gumba21.villageeconomy.trade.model.TradeCapture;
import dev.gumba21.villageeconomy.trade.model.TradeExecutionSnapshot;
import dev.gumba21.villageeconomy.trade.model.TransactionSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

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
    private final NumismaticCurrencyAdapter numismaticAdapter;
    private final AtomicLong executionIds = new AtomicLong();

    public TradeOverhaulAdapter(
            DynamicVillagerTradesAdapter dynamicTradesAdapter,
            TradeClassifier tradeClassifier,
            NumismaticCurrencyAdapter numismaticAdapter
    ) {
        this.dynamicTradesAdapter = Objects.requireNonNull(
                dynamicTradesAdapter,
                "dynamicTradesAdapter"
        );
        this.tradeClassifier = Objects.requireNonNull(
                tradeClassifier,
                "tradeClassifier"
        );
        this.numismaticAdapter = Objects.requireNonNull(
                numismaticAdapter,
                "numismaticAdapter"
        );
    }

    public Optional<TradeOverhaulPendingTransaction> beginTransaction(
            VillagerTradeScreenHandler handler,
            ServerPlayer player,
            int clickedSlot,
            TradeDirection direction
    ) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(player, "player");
        Villager villager = handler.getVillager();
        if (villager == null
                || (direction != TradeDirection.PLAYER_BUYS
                && direction != TradeDirection.PLAYER_SELLS)) {
            return Optional.empty();
        }
        Slot slot = handler.getSlot(clickedSlot);
        ItemStack item = slot.getItem();
        if (item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TradeOverhaulPendingTransaction(
                executionIds.incrementAndGet(),
                clickedSlot,
                player.getUUID(),
                villager.getUUID(),
                direction,
                item,
                item.getCount(),
                numismaticAdapter.readPlayerBalance(player)
        ));
    }

    /**
     * Completes a HEAD/RETURN delta capture after Trade Overhaul's
     * handleBuyOnServer or handleSellOnServer method returns. A valid result
     * requires both the source stack and the authoritative Numismatic player
     * balance to have moved in the expected directions.
     */
    public Optional<TradeCapture> completeTransaction(
            VillagerTradeScreenHandler handler,
            ServerPlayer player,
            TradeOverhaulPendingTransaction pending
    ) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(pending, "pending");
        Villager villager = Objects.requireNonNull(
                handler.getVillager(),
                "Trade Overhaul handler lost its villager before completion"
        );
        if (!pending.playerId().equals(player.getUUID())
                || !pending.villagerId().equals(villager.getUUID())) {
            throw new IllegalStateException(
                    "Trade Overhaul transaction identities changed during execution"
            );
        }

        ItemStack before = pending.itemBefore();
        ItemStack after = handler.getSlot(pending.clickedSlot()).getItem();
        int sourceCountAfter = sourceCountAfter(before, after);
        int quantity = pending.sourceCountBefore() - sourceCountAfter;
        MarketValue balanceAfter = numismaticAdapter.readPlayerBalance(player);
        Optional<MarketValue> actualValue = resolveValue(
                pending.direction(),
                pending.playerBalanceBefore(),
                balanceAfter
        );
        if (quantity <= 0
                && pending.playerBalanceBefore().equals(balanceAfter)) {
            return Optional.empty();
        }

        ObservationStatus status;
        Optional<ItemSnapshot> itemSnapshot = Optional.empty();
        WalletTradeClassification classification =
                new WalletTradeClassification(
                        pending.direction(),
                        Optional.empty(),
                        Math.max(0, quantity),
                        Optional.empty()
                );
        if (quantity <= 0) {
            status = ObservationStatus.UNKNOWN_TRADE_SHAPE;
        } else if (actualValue.isEmpty()
                || actualValue.get().baseUnits() <= 0L) {
            status = ObservationStatus.UNKNOWN_MONETARY_VALUE;
        } else {
            ItemStack transferred = before.copy();
            transferred.setCount(quantity);
            itemSnapshot = Optional.of(ItemSnapshot.from(transferred));
            classification = tradeClassifier.classifyWalletTransaction(
                    pending.direction(),
                    transferred,
                    quantity,
                    actualValue.get()
            );
            status = classification.isComplete()
                    ? ObservationStatus.VALID
                    : ObservationStatus.UNKNOWN_TRADE_SHAPE;
        }

        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        int level = data(villager).tradeOverhaul$getProfession().getLevel();
        return Optional.of(new TradeCapture(
                pending.executionId(),
                player.serverLevel().getGameTime(),
                player.getUUID(),
                villager.getUUID(),
                player.serverLevel().dimension().location(),
                villager.blockPosition(),
                profession,
                level,
                classification.direction(),
                itemSnapshot,
                Math.max(0, quantity),
                classification.monetaryValue(),
                TransactionSource.TRADE_OVERHAUL,
                status,
                detailFor(status),
                quantity > 0
                        ? Optional.of(new TradeExecutionSnapshot(
                                ItemSnapshot.from(before),
                                pending.sourceCountBefore(),
                                sourceCountAfter,
                                pending.playerBalanceBefore(),
                                balanceAfter
                        ))
                        : Optional.empty()
        ));
    }

    static int sourceCountAfter(ItemStack before, ItemStack after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (after.isEmpty()) {
            return 0;
        }
        return ItemStack.isSameItemSameTags(before, after)
                ? after.getCount()
                : before.getCount();
    }

    static Optional<MarketValue> resolveValue(
            TradeDirection direction,
            MarketValue before,
            MarketValue after
    ) {
        try {
            if (direction == TradeDirection.PLAYER_BUYS
                    && before.compareTo(after) > 0) {
                return Optional.of(before.subtract(after));
            }
            if (direction == TradeDirection.PLAYER_SELLS
                    && after.compareTo(before) > 0) {
                return Optional.of(after.subtract(before));
            }
        } catch (ArithmeticException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static String detailFor(ObservationStatus status) {
        return switch (status) {
            case VALID -> "committed Trade Overhaul transaction";
            case UNKNOWN_MONETARY_VALUE ->
                    "player currency did not change by a positive exact amount";
            case UNKNOWN_TRADE_SHAPE ->
                    "source item count did not decrease as a committed trade";
            default -> status.name();
        };
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

    public List<TradeOverhaulItemInspection> inspectTradableItems(
            Villager villager
    ) {
        VillagerTradeData data = data(villager);
        List<TradeOverhaulItemInspection> result = new ArrayList<>();
        int size = data.tradeOverhaul$getInventory().getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = data.tradeOverhaul$getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            result.add(new TradeOverhaulItemInspection(
                    ItemSnapshot.from(stack),
                    readConfiguredPrices(villager, stack)
            ));
        }
        return List.copyOf(result);
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
