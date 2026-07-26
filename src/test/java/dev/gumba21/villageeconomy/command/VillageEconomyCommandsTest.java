package dev.gumba21.villageeconomy.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillageEconomyCommandsTest {
    @Test
    void formatsVillageAgeAtUsefulScales() {
        assertEquals("8s", VillageEconomyCommands.formatAge(8_000L));
        assertEquals("3m 5s", VillageEconomyCommands.formatAge(185_000L));
        assertEquals("2h 15m", VillageEconomyCommands.formatAge(8_100_000L));
        assertEquals("3d 4h", VillageEconomyCommands.formatAge(273_600_000L));
        assertEquals("0s", VillageEconomyCommands.formatAge(-1L));
    }
}
