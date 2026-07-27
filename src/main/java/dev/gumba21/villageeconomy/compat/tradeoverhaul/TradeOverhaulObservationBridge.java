package dev.gumba21.villageeconomy.compat.tradeoverhaul;

import com.unnameduser.tradeoverhaul.client.gui.VillagerTradeScreenHandler;
import dev.gumba21.villageeconomy.VillageEconomy;
import dev.gumba21.villageeconomy.compat.CompatibilityManager;
import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import dev.gumba21.villageeconomy.village.VillageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Failure-isolating edge between the Trade Overhaul mixin and the
 * third-party-free observation service.
 */
public final class TradeOverhaulObservationBridge {
    private static final Set<String> WARNED_FAILURES =
            ConcurrentHashMap.newKeySet();

    private TradeOverhaulObservationBridge() {
    }

    public static TradeOverhaulPendingTransaction begin(
            VillagerTradeScreenHandler handler,
            Player player,
            int clickedSlot,
            TradeDirection direction
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || serverPlayer.level().isClientSide()) {
            return null;
        }
        try {
            VillageManager.get(serverPlayer.getServer())
                    .getTradeObservationService()
                    .markHookInitialized();
            return CompatibilityManager.getInstance()
                    .tradeOverhaul()
                    .beginTransaction(
                            handler,
                            serverPlayer,
                            clickedSlot,
                            direction
                    )
                    .orElse(null);
        } catch (RuntimeException failure) {
            warnOnce("begin:" + direction, failure);
            return null;
        }
    }

    public static void complete(
            VillagerTradeScreenHandler handler,
            Player player,
            TradeOverhaulPendingTransaction pending
    ) {
        if (pending == null || !(player instanceof ServerPlayer serverPlayer)
                || serverPlayer.level().isClientSide()) {
            return;
        }
        try {
            CompatibilityManager.getInstance()
                    .tradeOverhaul()
                    .completeTransaction(handler, serverPlayer, pending)
                    .ifPresent(capture ->
                    VillageManager.get(serverPlayer.getServer())
                            .getTradeObservationService()
                            .observe(capture)
            );
        } catch (RuntimeException failure) {
            warnOnce("complete:" + pending.direction(), failure);
        }
    }

    private static void warnOnce(String key, RuntimeException failure) {
        VillageEconomyDebugLogger.info(
                "Trade Overhaul observation adapter failure: {}",
                failure.toString()
        );
        if (WARNED_FAILURES.add(key)) {
            VillageEconomy.LOGGER.warn(
                    "Village Economy could not observe a completed Trade Overhaul "
                            + "transaction; gameplay remains unchanged",
                    failure
            );
        }
    }
}
