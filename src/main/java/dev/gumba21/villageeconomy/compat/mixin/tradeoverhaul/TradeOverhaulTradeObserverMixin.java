package dev.gumba21.villageeconomy.compat.mixin.tradeoverhaul;

import com.unnameduser.tradeoverhaul.client.gui.VillagerTradeScreenHandler;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import dev.gumba21.villageeconomy.compat.tradeoverhaul.TradeOverhaulObservationBridge;
import dev.gumba21.villageeconomy.compat.tradeoverhaul.TradeOverhaulPendingTransaction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trade Overhaul 1.0.1 owns completed transactions in
 * handleBuyOnServer/handleSellOnServer. HEAD captures immutable pre-state;
 * RETURN observes only when exact source-item and player-wallet deltas prove
 * the method committed. Early failed/cancelled returns have no deltas and emit
 * nothing.
 */
@Mixin(value = VillagerTradeScreenHandler.class, remap = false)
public abstract class TradeOverhaulTradeObserverMixin {
    @Unique
    private TradeOverhaulPendingTransaction villageeconomy$pendingBuy;
    @Unique
    private TradeOverhaulPendingTransaction villageeconomy$pendingSell;

    @Inject(method = "handleBuyOnServer", at = @At("HEAD"), require = 1)
    private void villageeconomy$beginBuy(
            int clickedSlot,
            Player player,
            boolean fullStack,
            boolean tenItems,
            CallbackInfo callback
    ) {
        villageeconomy$pendingBuy = TradeOverhaulObservationBridge.begin(
                (VillagerTradeScreenHandler) (Object) this,
                player,
                clickedSlot,
                TradeDirection.PLAYER_BUYS
        );
    }

    @Inject(method = "handleBuyOnServer", at = @At("RETURN"), require = 1)
    private void villageeconomy$completeBuy(
            int clickedSlot,
            Player player,
            boolean fullStack,
            boolean tenItems,
            CallbackInfo callback
    ) {
        TradeOverhaulPendingTransaction pending = villageeconomy$pendingBuy;
        villageeconomy$pendingBuy = null;
        TradeOverhaulObservationBridge.complete(
                (VillagerTradeScreenHandler) (Object) this,
                player,
                pending
        );
    }

    @Inject(method = "handleSellOnServer", at = @At("HEAD"), require = 1)
    private void villageeconomy$beginSell(
            int clickedSlot,
            Player player,
            boolean fullStack,
            boolean tenItems,
            CallbackInfo callback
    ) {
        villageeconomy$pendingSell = TradeOverhaulObservationBridge.begin(
                (VillagerTradeScreenHandler) (Object) this,
                player,
                clickedSlot,
                TradeDirection.PLAYER_SELLS
        );
    }

    @Inject(method = "handleSellOnServer", at = @At("RETURN"), require = 1)
    private void villageeconomy$completeSell(
            int clickedSlot,
            Player player,
            boolean fullStack,
            boolean tenItems,
            CallbackInfo callback
    ) {
        TradeOverhaulPendingTransaction pending = villageeconomy$pendingSell;
        villageeconomy$pendingSell = null;
        TradeOverhaulObservationBridge.complete(
                (VillagerTradeScreenHandler) (Object) this,
                player,
                pending
        );
    }
}
