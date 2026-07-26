package dev.gumba21.villageeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.gumba21.villageeconomy.market.MarketManager;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.market.simulation.MarketSimulationResult;
import dev.gumba21.villageeconomy.village.VillageManager;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Locale;

public final class VillageEconomyCommands {
    private static final int SAMPLE_PRICE_COUNT = 5;

    private VillageEconomyCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> dispatcher.register(
                        Commands.literal("villageeconomy")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("villages")
                                        .executes(VillageEconomyCommands::listVillages))
                                .then(Commands.literal("market")
                                        .executes(VillageEconomyCommands::listMarkets)
                                        .then(Commands.literal("simulate")
                                                .executes(
                                                        VillageEconomyCommands
                                                                ::simulateMarkets
                                                )))
                )
        );
    }

    private static int listVillages(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        VillageManager manager = VillageManager.get(source.getServer());
        Collection<TrackedVillage> villages = manager.getVillages();
        MarketManager marketManager = manager.getMarketManager();
        source.sendSuccess(
                () -> Component.literal("Tracked villages: " + villages.size()),
                false
        );

        long now = System.currentTimeMillis();
        for (TrackedVillage village : villages) {
            String line = "%s | %s | %s | villagers=%d | loaded=%s | market=%s | age=%s"
                    .formatted(
                    village.getId(),
                    village.getDimension().location(),
                    village.getCenter().toShortString(),
                    village.getVillagerCount(),
                    village.isLoaded(),
                    marketManager.hasMarket(village.getId()),
                    formatAge(now - village.getFirstDiscoveredTimestamp())
            );
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int listMarkets(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Collection<MarketState> markets = VillageManager.get(source.getServer())
                .getMarketManager()
                .getMarkets();
        source.sendSuccess(
                () -> Component.literal("Tracked markets: " + markets.size()),
                false
        );

        long now = System.currentTimeMillis();
        for (MarketState market : markets) {
            String header = "%s | items=%d | lastUpdate=%s ago".formatted(
                    market.getVillageId(),
                    market.size(),
                    formatAge(now - market.getLastUpdateTimestamp())
            );
            source.sendSuccess(() -> Component.literal(header), false);

            int displayed = 0;
            for (MarketEntry entry : market.getEntries()) {
                if (displayed >= SAMPLE_PRICE_COUNT) {
                    break;
                }
                String sample = (
                        "  %s | price=%s | base=%s | supply=%s | "
                                + "demand=%s | multiplier=%s"
                ).formatted(
                        entry.getItemId(),
                        formatDecimal(entry.getCurrentPrice()),
                        formatDecimal(entry.getBasePrice()),
                        formatDecimal(entry.getSupply()),
                        formatDecimal(entry.getDemand()),
                        formatDecimal(
                                entry.getCurrentPrice() / entry.getBasePrice()
                        )
                );
                source.sendSuccess(() -> Component.literal(sample), false);
                displayed++;
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int simulateMarkets(
            CommandContext<CommandSourceStack> context
    ) {
        CommandSourceStack source = context.getSource();
        MarketSimulationResult result = VillageManager.get(source.getServer())
                .simulateMarketsNow();
        source.sendSuccess(
                () -> Component.literal(
                        "Simulated one market tick: villages=%d, pricesChanged=%d"
                                .formatted(
                                        result.villagesUpdated(),
                                        result.pricesChanged()
                                )
                ),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static String formatAge(long ageMillis) {
        Duration age = Duration.ofMillis(Math.max(0L, ageMillis));
        long days = age.toDays();
        if (days > 0L) {
            return days + "d " + age.minusDays(days).toHours() + "h";
        }
        long hours = age.toHours();
        if (hours > 0L) {
            return hours + "h " + age.minusHours(hours).toMinutes() + "m";
        }
        long minutes = age.toMinutes();
        if (minutes > 0L) {
            return minutes + "m " + age.minusMinutes(minutes).toSeconds() + "s";
        }
        return age.toSeconds() + "s";
    }
}
