package dev.gumba21.villageeconomy.trade.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record VillageReference(
        UUID villageId,
        ResourceLocation dimensionId,
        BlockPos center
) {
    public VillageReference {
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        center = Objects.requireNonNull(center, "center").immutable();
    }

    @Override
    public BlockPos center() {
        return center.immutable();
    }
}
