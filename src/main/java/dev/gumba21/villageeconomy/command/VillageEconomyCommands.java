package dev.gumba21.villageeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.gumba21.villageeconomy.village.VillageManager;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.Collection;

public final class VillageEconomyCommands {
    private VillageEconomyCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> dispatcher.register(
                        Commands.literal("villageeconomy")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("villages")
                                        .executes(VillageEconomyCommands::listVillages))
                )
        );
    }

    private static int listVillages(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Collection<TrackedVillage> villages =
                VillageManager.get(source.getServer()).getVillages();
        source.sendSuccess(
                () -> Component.literal("Tracked villages: " + villages.size()),
                false
        );

        long now = System.currentTimeMillis();
        for (TrackedVillage village : villages) {
            String line = "%s | %s | %s | villagers=%d | loaded=%s | age=%s".formatted(
                    village.getId(),
                    village.getDimension().location(),
                    village.getCenter().toShortString(),
                    village.getVillagerCount(),
                    village.isLoaded(),
                    formatAge(now - village.getFirstDiscoveredTimestamp())
            );
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
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
