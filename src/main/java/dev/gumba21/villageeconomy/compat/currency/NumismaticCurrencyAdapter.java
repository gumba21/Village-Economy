package dev.gumba21.villageeconomy.compat.currency;

import com.glisco.numismaticoverhaul.ModComponents;
import com.glisco.numismaticoverhaul.item.NumismaticOverhaulItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only server-safe boundary around Numismatic Overhaul 0.2.18.
 */
public final class NumismaticCurrencyAdapter {
    public static final int MAX_MATERIALIZED_STACKS = 4_096;

    private final Item bronzeCoin;
    private final Item silverCoin;
    private final Item goldCoin;

    public NumismaticCurrencyAdapter() {
        this(
                NumismaticOverhaulItems.BRONZE_COIN,
                NumismaticOverhaulItems.SILVER_COIN,
                NumismaticOverhaulItems.GOLD_COIN
        );
    }

    NumismaticCurrencyAdapter(
            Item bronzeCoin,
            Item silverCoin,
            Item goldCoin
    ) {
        this.bronzeCoin = Objects.requireNonNull(bronzeCoin, "bronzeCoin");
        this.silverCoin = Objects.requireNonNull(silverCoin, "silverCoin");
        this.goldCoin = Objects.requireNonNull(goldCoin, "goldCoin");
    }

    public CurrencyBreakdown decompose(MarketValue value) {
        return CurrencyDenominations.decompose(value);
    }

    public MarketValue compose(CurrencyBreakdown breakdown) {
        return CurrencyDenominations.compose(breakdown);
    }

    public boolean isBronzeCoin(ItemStack stack) {
        return isItem(stack, bronzeCoin);
    }

    public boolean isSilverCoin(ItemStack stack) {
        return isItem(stack, silverCoin);
    }

    public boolean isGoldCoin(ItemStack stack) {
        return isItem(stack, goldCoin);
    }

    public boolean isCoin(ItemStack stack) {
        return isBronzeCoin(stack) || isSilverCoin(stack) || isGoldCoin(stack);
    }

    public Optional<MarketValue> valueOfCoinStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        long denomination;
        if (isBronzeCoin(stack)) {
            denomination = CurrencyDenominations.BRONZE_VALUE;
        } else if (isSilverCoin(stack)) {
            denomination = CurrencyDenominations.SILVER_VALUE;
        } else if (isGoldCoin(stack)) {
            denomination = CurrencyDenominations.GOLD_VALUE;
        } else {
            return Optional.empty();
        }
        return Optional.of(MarketValue.ofBaseUnits(
                Math.multiplyExact((long) stack.getCount(), denomination)
        ));
    }

    /**
     * Converts only a pure collection of recognized coin stacks.
     */
    public Optional<MarketValue> valueOfCoinStacks(
            Collection<ItemStack> stacks
    ) {
        Objects.requireNonNull(stacks, "stacks");
        MarketValue total = MarketValue.zero();
        for (ItemStack stack : stacks) {
            Optional<MarketValue> value = valueOfCoinStack(stack);
            if (value.isEmpty()) {
                return Optional.empty();
            }
            total = total.add(value.get());
        }
        return Optional.of(total);
    }

    public CurrencyStackPlan planCoinStacks(MarketValue value) {
        return CurrencyStackPlan.forValue(value);
    }

    /**
     * Produces legal 99-item coin stacks without truncation. Very large
     * requests must use {@link #planCoinStacks(MarketValue)} instead of
     * attempting to allocate an unbounded list.
     */
    public List<ItemStack> createCoinStacks(MarketValue value) {
        CurrencyStackPlan plan = planCoinStacks(value);
        if (plan.requiredStacks() > MAX_MATERIALIZED_STACKS) {
            throw new IllegalArgumentException(
                    "Value requires %d coin stacks; maximum materialized batch is %d"
                            .formatted(
                                    plan.requiredStacks(),
                                    MAX_MATERIALIZED_STACKS
                            )
            );
        }

        List<ItemStack> stacks = new ArrayList<>((int) plan.requiredStacks());
        appendStacks(
                stacks,
                goldCoin,
                plan.breakdown().gold()
        );
        appendStacks(
                stacks,
                silverCoin,
                plan.breakdown().silver()
        );
        appendStacks(
                stacks,
                bronzeCoin,
                plan.breakdown().bronze()
        );
        return List.copyOf(stacks);
    }

    public MarketValue readPlayerBalance(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        long value = ModComponents.CURRENCY.get(player).getValue();
        if (value < 0L) {
            throw new IllegalStateException(
                    "Numismatic player currency component contains a negative balance"
            );
        }
        return MarketValue.ofBaseUnits(value);
    }

    public boolean canAfford(ServerPlayer player, MarketValue price) {
        Objects.requireNonNull(price, "price");
        return readPlayerBalance(player).compareTo(price) >= 0;
    }

    public MarketValue fromMarketPrice(double price) {
        return MarketValue.fromMarketPrice(price);
    }

    private static boolean isItem(ItemStack stack, Item item) {
        return stack != null && !stack.isEmpty() && stack.is(item);
    }

    private static void appendStacks(
            List<ItemStack> output,
            Item item,
            long coinCount
    ) {
        long remaining = coinCount;
        while (remaining > 0L) {
            int count = (int) Math.min(
                    remaining,
                    CurrencyDenominations.COIN_STACK_SIZE
            );
            output.add(new ItemStack(item, count));
            remaining -= count;
        }
    }
}
