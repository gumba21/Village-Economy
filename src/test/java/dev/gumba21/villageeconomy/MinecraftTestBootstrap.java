package dev.gumba21.villageeconomy;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * Initializes the vanilla registries required by tests that use Minecraft
 * resource keys and NBT types.
 */
public final class MinecraftTestBootstrap {
    private static boolean initialized;

    private MinecraftTestBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        initialized = true;
    }
}
