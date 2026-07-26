package dev.gumba21.villageeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.gumba21.villageeconomy.compat.CompatibilityManager;
import dev.gumba21.villageeconomy.compat.CompatibilityVersions;
import dev.gumba21.villageeconomy.compat.currency.CurrencyBreakdown;
import dev.gumba21.villageeconomy.compat.currency.CurrencyDenominations;
import dev.gumba21.villageeconomy.compat.currency.MarketValue;
import dev.gumba21.villageeconomy.compat.trade.ObservedTrade;
import dev.gumba21.villageeconomy.compat.tradeoverhaul.TradeOverhaulItemInspection;
import dev.gumba21.villageeconomy.compat.tradeoverhaul.TradeOverhaulVillagerSnapshot;
import dev.gumba21.villageeconomy.market.MarketManager;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.market.simulation.MarketSimulationResult;
import dev.gumba21.villageeconomy.trade.mapping.MarketItemMapper;
import dev.gumba21.villageeconomy.trade.mapping.MarketItemMappingResult;
import dev.gumba21.villageeconomy.trade.observation.DiagnosticObservation;
import dev.gumba21.villageeconomy.trade.observation.TradeObservationService;
import dev.gumba21.villageeconomy.trade.observation.TradeObservationSummary;
import dev.gumba21.villageeconomy.village.VillageManager;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class VillageEconomyCommands {
    private static final int SAMPLE_PRICE_COUNT = 5;
    private static final int DEFAULT_RECENT_TRADES = 10;
    private static final int MAX_RECENT_TRADES = 50;
    private static final int MAX_INSPECTED_OFFERS = 25;

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
                                .then(Commands.literal("compatibility")
                                        .executes(
                                                VillageEconomyCommands
                                                        ::showCompatibility
                                        )
                                        .then(Commands.literal("currency")
                                                .then(Commands.argument(
                                                        "baseUnits",
                                                        LongArgumentType.longArg(0L)
                                                ).executes(
                                                        VillageEconomyCommands
                                                                ::showCurrency
                                                ))))
                                .then(Commands.literal("trades")
                                        .executes(
                                                VillageEconomyCommands
                                                        ::showTradeSummary
                                        )
                                        .then(Commands.literal("recent")
                                                .executes(context ->
                                                        showRecentTrades(
                                                                context,
                                                                DEFAULT_RECENT_TRADES
                                                        ))
                                                .then(Commands.argument(
                                                        "count",
                                                        IntegerArgumentType.integer(
                                                                1,
                                                                MAX_RECENT_TRADES
                                                        )
                                                ).executes(context ->
                                                        showRecentTrades(
                                                                context,
                                                                IntegerArgumentType
                                                                        .getInteger(
                                                                                context,
                                                                                "count"
                                                                        )
                                                        ))))
                                        .then(Commands.literal("inspect")
                                                .executes(
                                                        VillageEconomyCommands
                                                                ::inspectNearestVillager
                                                ))
                                        .then(Commands.literal("clear")
                                                .executes(
                                                        VillageEconomyCommands
                                                                ::clearTradeDiagnostics
                                                )))
                )
        );
    }

    private static int showTradeSummary(
            CommandContext<CommandSourceStack> context
    ) {
        TradeObservationService service = tradeService(context.getSource());
        TradeObservationSummary summary = service.diagnostics().summary();
        context.getSource().sendSuccess(
                () -> Component.literal(
                        (
                                "Trade observation: hook=%s, total=%d, valid=%d, "
                                + "unknown/unsupported=%d, duplicates=%d, "
                                + "noVillage=%d, noMarket=%d"
                        ).formatted(
                                        service.isHookInitialized()
                                                ? "initialized"
                                                : "inactive",
                                        summary.total(),
                                        summary.valid(),
                                        summary.unknownOrUnsupported(),
                                        summary.duplicatesSuppressed(),
                                        summary.noVillage(),
                                        summary.noMarket()
                                )
                ),
                false
        );
        summary.mostRecent().ifPresent(observation ->
                context.getSource().sendSuccess(
                        () -> Component.literal(
                                "Most recent: " + formatObservation(observation)
                        ),
                        false
                )
        );
        if (summary.mostRecent().isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("Most recent: none"),
                    false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showRecentTrades(
            CommandContext<CommandSourceStack> context,
            int count
    ) {
        List<DiagnosticObservation> recent = tradeService(context.getSource())
                .diagnostics()
                .recent(count);
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Recent trade observations (newest first): " + recent.size()
                ),
                false
        );
        for (DiagnosticObservation observation : recent) {
            context.getSource().sendSuccess(
                    () -> Component.literal(formatObservation(observation)),
                    false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int clearTradeDiagnostics(
            CommandContext<CommandSourceStack> context
    ) {
        tradeService(context.getSource()).clearDiagnostics();
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Cleared in-memory trade diagnostics; persisted villages "
                                + "and markets were not changed."
                ),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int inspectNearestVillager(
            CommandContext<CommandSourceStack> context
    ) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(
                    "This command requires an in-world server player."
            ));
            return 0;
        }

        AABB search = player.getBoundingBox().inflate(16.0);
        Optional<Villager> nearest = player.serverLevel()
                .getEntitiesOfClass(
                        Villager.class,
                        search,
                        villager -> villager.isAlive() && !villager.isRemoved()
                )
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr));
        if (nearest.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No villager found within 16 blocks."
            ));
            return 0;
        }

        Villager villager = nearest.get();
        VillageManager villageManager = VillageManager.get(source.getServer());
        TradeOverhaulVillagerSnapshot snapshot =
                CompatibilityManager.getInstance()
                        .tradeOverhaul()
                        .inspectVillager(villager);
        Optional<TrackedVillage> village = villageManager.findVillage(
                player.serverLevel().dimension(),
                villager.blockPosition()
        ).filter(TrackedVillage::isLoaded);
        Optional<MarketState> market = village.flatMap(tracked ->
                villageManager.getMarketManager().getMarket(tracked.getId())
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Villager %s | profession=%s | level=%d | village=%s | market=%s"
                                .formatted(
                                        villager.getUUID(),
                                        snapshot.profession(),
                                        snapshot.professionLevel(),
                                        village.map(value -> value.getId().toString())
                                                .orElse("unresolved"),
                                        market.map(value ->
                                                        value.getVillageId().toString())
                                                .orElse("unresolved")
                                )
                ),
                false
        );

        MarketItemMapper mapper = new MarketItemMapper();
        List<TradeOverhaulItemInspection> tradableItems =
                CompatibilityManager.getInstance()
                        .tradeOverhaul()
                        .inspectTradableItems(villager);
        int displayed = 0;
        for (TradeOverhaulItemInspection inspection : tradableItems) {
            if (displayed++ >= MAX_INSPECTED_OFFERS) {
                break;
            }
            String mapping = market.map(value ->
                            describeMapping(mapper.map(inspection.item(), value)))
                    .orElse("no market");
            String prices = inspection.configuredPrices()
                    .map(value -> "buy=%d, sell=%d base units".formatted(
                            value.playerBuyPrice(),
                            value.playerSellPrice()
                    ))
                    .orElse("prices unavailable");
            source.sendSuccess(
                    () -> Component.literal(
                            "  Trade Overhaul item %s x%d | %s | mapping=%s"
                                    .formatted(
                                            inspection.item().itemId(),
                                            inspection.item().count(),
                                            prices,
                                            mapping
                                    )
                    ),
                    false
            );
        }

        for (ObservedTrade offer : snapshot.offers()) {
            if (displayed++ >= MAX_INSPECTED_OFFERS) {
                break;
            }
            source.sendSuccess(
                    () -> Component.literal(
                            "  Merchant offer %s | item=%s | value=%s | dynamic=%s"
                                    .formatted(
                                            offer.direction(),
                                            offer.primaryItem()
                                                    .map(Object::toString)
                                                    .orElse("unknown"),
                                            offer.monetaryValue()
                                                    .map(VillageEconomyCommands
                                                            ::formatValue)
                                                    .orElse("unknown"),
                                            offer.dynamicVillagerTradesOffer()
                                    )
                    ),
                    false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static TradeObservationService tradeService(
            CommandSourceStack source
    ) {
        return VillageManager.get(source.getServer())
                .getTradeObservationService();
    }

    private static String formatObservation(
            DiagnosticObservation observation
    ) {
        return "[%s] %s %dx %s for %s | villager=%s | village=%s | market=%s"
                .formatted(
                        observation.status(),
                        observation.direction(),
                        observation.itemQuantity(),
                        observation.itemId().map(Object::toString)
                                .orElse("unknown"),
                        observation.monetaryValue()
                                .map(VillageEconomyCommands::formatValue)
                                .orElse("unknown value"),
                        observation.villagerId(),
                        observation.villageId().map(Object::toString)
                                .orElse("none"),
                        observation.marketId().map(Object::toString)
                                .orElse("none")
                );
    }

    private static String formatValue(MarketValue value) {
        CurrencyBreakdown breakdown =
                CurrencyDenominations.decompose(value);
        return "%dg %ds %db (%d base units)".formatted(
                breakdown.gold(),
                breakdown.silver(),
                breakdown.bronze(),
                value.baseUnits()
        );
    }

    private static String describeMapping(MarketItemMappingResult result) {
        if (result instanceof MarketItemMappingResult.Mapped mapped) {
            return "mapped:" + mapped.itemId();
        }
        if (result instanceof MarketItemMappingResult.Unsupported unsupported) {
            return "unsupported:" + unsupported.reason();
        }
        return "ambiguous:"
                + ((MarketItemMappingResult.Ambiguous) result).reason();
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

    private static int showCompatibility(
            CommandContext<CommandSourceStack> context
    ) {
        CommandSourceStack source = context.getSource();
        CompatibilityManager manager = CompatibilityManager.getInstance();
        CompatibilityVersions versions = manager.versions();
        source.sendSuccess(
                () -> Component.literal(
                        "Village Economy compatibility: "
                                + (manager.isHealthy() ? "healthy" : "failed")
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "Village Economy " + versions.villageEconomy()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "Numismatic Overhaul "
                                + versions.numismaticOverhaul()
                                + " detected"
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "Trade Overhaul " + versions.tradeOverhaul()
                                + " detected"
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "Dynamic Villager Trades "
                                + versions.dynamicVillagerTrades()
                                + " detected"
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "Ratios: gold=%d, silver=%d, bronze=%d base units"
                                .formatted(
                                        CurrencyDenominations.GOLD_VALUE,
                                        CurrencyDenominations.SILVER_VALUE,
                                        CurrencyDenominations.BRONZE_VALUE
                                )
                ),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int showCurrency(
            CommandContext<CommandSourceStack> context
    ) {
        long baseUnits = LongArgumentType.getLong(context, "baseUnits");
        CompatibilityManager manager = CompatibilityManager.getInstance();
        MarketValue value = MarketValue.ofBaseUnits(baseUnits);
        CurrencyBreakdown breakdown = manager.numismatic().decompose(value);
        MarketValue roundTrip = manager.numismatic().compose(breakdown);
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "%d base units = %d gold, %d silver, %d bronze; roundTrip=%d"
                                .formatted(
                                        baseUnits,
                                        breakdown.gold(),
                                        breakdown.silver(),
                                        breakdown.bronze(),
                                        roundTrip.baseUnits()
                                )
                ),
                false
        );
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
