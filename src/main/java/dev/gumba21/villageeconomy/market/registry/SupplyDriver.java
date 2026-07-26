package dev.gumba21.villageeconomy.market.registry;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public enum SupplyDriver {
    FARMER("farmer"),
    SMITH("armorer", "toolsmith", "weaponsmith"),
    FLETCHER("fletcher"),
    MASON("mason"),
    BUTCHER("butcher"),
    LEATHERWORKER("leatherworker"),
    LIBRARIAN("librarian");

    private final ResourceLocation[] professionIds;

    SupplyDriver(String... professionPaths) {
        professionIds = new ResourceLocation[professionPaths.length];
        for (int index = 0; index < professionPaths.length; index++) {
            professionIds[index] = new ResourceLocation(
                    "minecraft",
                    professionPaths[index]
            );
        }
    }

    public int count(Map<ResourceLocation, Integer> professionCounts) {
        int count = 0;
        for (ResourceLocation professionId : professionIds) {
            count += professionCounts.getOrDefault(professionId, 0);
        }
        return count;
    }
}
