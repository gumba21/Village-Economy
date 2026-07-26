package dev.gumba21.villageeconomy.village.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

public final class TrackedVillage {
    private final UUID id;
    private final long firstDiscoveredTimestamp;

    private BlockPos center;
    private ResourceKey<Level> dimension;
    private int detectionRadius;
    private long lastSeenTimestamp;
    private int villagerCount;
    private int workstationCount;
    private boolean loaded;

    public TrackedVillage(
            UUID id,
            BlockPos center,
            ResourceKey<Level> dimension,
            int detectionRadius,
            long firstDiscoveredTimestamp,
            long lastSeenTimestamp,
            int villagerCount,
            int workstationCount,
            boolean loaded
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.center = Objects.requireNonNull(center, "center").immutable();
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.detectionRadius = detectionRadius;
        this.firstDiscoveredTimestamp = firstDiscoveredTimestamp;
        this.lastSeenTimestamp = lastSeenTimestamp;
        this.villagerCount = villagerCount;
        this.workstationCount = workstationCount;
        this.loaded = loaded;
    }

    public boolean updateSeen(
            BlockPos newCenter,
            int newDetectionRadius,
            long seenTimestamp,
            int newVillagerCount,
            int newWorkstationCount
    ) {
        BlockPos immutableCenter = newCenter.immutable();
        boolean changed = !center.equals(immutableCenter)
                || detectionRadius != newDetectionRadius
                || lastSeenTimestamp != seenTimestamp
                || villagerCount != newVillagerCount
                || workstationCount != newWorkstationCount
                || !loaded;

        center = immutableCenter;
        detectionRadius = newDetectionRadius;
        lastSeenTimestamp = Math.max(firstDiscoveredTimestamp, seenTimestamp);
        villagerCount = Math.max(0, newVillagerCount);
        workstationCount = Math.max(0, newWorkstationCount);
        loaded = true;
        return changed;
    }

    public boolean updateLoadedState(boolean newLoaded) {
        if (loaded == newLoaded) {
            return false;
        }
        loaded = newLoaded;
        return true;
    }

    public boolean contains(ResourceKey<Level> targetDimension, BlockPos position) {
        return dimension.equals(targetDimension)
                && horizontalDistanceSquared(center, position)
                <= (long) detectionRadius * detectionRadius;
    }

    public long horizontalDistanceSquared(BlockPos position) {
        return horizontalDistanceSquared(center, position);
    }

    private static long horizontalDistanceSquared(BlockPos first, BlockPos second) {
        long deltaX = (long) first.getX() - second.getX();
        long deltaZ = (long) first.getZ() - second.getZ();
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    public UUID getId() {
        return id;
    }

    public BlockPos getCenter() {
        return center;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public int getDetectionRadius() {
        return detectionRadius;
    }

    public long getFirstDiscoveredTimestamp() {
        return firstDiscoveredTimestamp;
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public int getVillagerCount() {
        return villagerCount;
    }

    public int getWorkstationCount() {
        return workstationCount;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
