package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.compat.trade.TradeDirection;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionDeduplicatorTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID VILLAGER = UUID.randomUUID();

    @Test
    void duplicateInternalCallbackIsSuppressed() {
        TransactionDeduplicator deduplicator =
                new TransactionDeduplicator(8, 2L);
        TransactionKey key = key(1L, PLAYER, VILLAGER);

        assertFalse(deduplicator.isDuplicate(key, 10L));
        assertTrue(deduplicator.isDuplicate(key, 10L));
    }

    @Test
    void legitimateIdenticalSameTickTradesRemainDistinct() {
        TransactionDeduplicator deduplicator =
                new TransactionDeduplicator(8, 2L);

        assertFalse(deduplicator.isDuplicate(key(1L, PLAYER, VILLAGER), 10L));
        assertFalse(deduplicator.isDuplicate(key(2L, PLAYER, VILLAGER), 10L));
    }

    @Test
    void differentPlayersAndVillagersRemainDistinct() {
        TransactionDeduplicator deduplicator =
                new TransactionDeduplicator(8, 2L);
        assertFalse(deduplicator.isDuplicate(key(1L, PLAYER, VILLAGER), 10L));
        assertFalse(deduplicator.isDuplicate(
                key(1L, UUID.randomUUID(), VILLAGER),
                10L
        ));
        assertFalse(deduplicator.isDuplicate(
                key(1L, PLAYER, UUID.randomUUID()),
                10L
        ));
    }

    @Test
    void entriesExpireQuickly() {
        TransactionDeduplicator deduplicator =
                new TransactionDeduplicator(8, 2L);
        TransactionKey key = key(1L, PLAYER, VILLAGER);
        deduplicator.isDuplicate(key, 10L);
        deduplicator.expire(13L);

        assertFalse(deduplicator.isDuplicate(key, 13L));
    }

    @Test
    void cacheStaysBounded() {
        TransactionDeduplicator deduplicator =
                new TransactionDeduplicator(4, 100L);
        for (long execution = 1L; execution <= 100L; execution++) {
            deduplicator.isDuplicate(
                    key(execution, PLAYER, VILLAGER),
                    10L
            );
        }
        assertTrue(deduplicator.size() <= 4);
    }

    private static TransactionKey key(
            long executionId,
            UUID player,
            UUID villager
    ) {
        return new TransactionKey(
                executionId,
                player,
                villager,
                TradeDirection.PLAYER_BUYS,
                new ResourceLocation("minecraft:bread"),
                4,
                12L
        );
    }
}
