package dev.gumba21.villageeconomy.village.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    private final Map<ResourceLocation, Integer> professionCounts = new HashMap<>();
    private final Map<ResourceLocation, Integer> professionCountsView =
            Collections.unmodifiableMap(professionCounts);

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
        this(
                id,
                center,
                dimension,
                detectionRadius,
                firstDiscoveredTimestamp,
                lastSeenTimestamp,
                villagerCount,
                workstationCount,
                loaded,
                Map.of()
        );
    }

    public TrackedVillage(
            UUID id,
            BlockPos center,
            ResourceKey<Level> dimension,
            int detectionRadius,
            long firstDiscoveredTimestamp,
            long lastSeenTimestamp,
            int villagerCount,
            int workstationCount,
            boolean loaded,
            Map<ResourceLocation, Integer> professionCounts
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
        replaceProfessionCounts(professionCounts);
    }

    public boolean updateSeen(
            BlockPos newCenter,
            int newDetectionRadius,
            long seenTimestamp,
            int newVillagerCount,
            int newWorkstationCount
    ) {
        return updateSeen(
                newCenter,
                newDetectionRadius,
                seenTimestamp,
                newVillagerCount,
                newWorkstationCount,
                Map.copyOf(professionCounts)
        );
    }

    public boolean updateSeen(
            BlockPos newCenter,
            int newDetectionRadius,
            long seenTimestamp,
            int newVillagerCount,
            int newWorkstationCount,
            Map<ResourceLocation, Integer> newProfessionCounts
    ) {
        BlockPos immutableCenter = newCenter.immutable();
        boolean changed = !center.equals(immutableCenter)
                || detectionRadius != newDetectionRadius
                || lastSeenTimestamp != seenTimestamp
                || villagerCount != newVillagerCount
                || workstationCount != newWorkstationCount
                || !professionCounts.equals(newProfessionCounts);

        center = immutableCenter;
        detectionRadius = newDetectionRadius;
        lastSeenTimestamp = Math.max(firstDiscoveredTimestamp, seenTimestamp);
        villagerCount = Math.max(0, newVillagerCount);
        workstationCount = Math.max(0, newWorkstationCount);
        replaceProfessionCounts(newProfessionCounts);
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

    public Map<ResourceLocation, Integer> getProfessionCounts() {
        return professionCountsView;
    }

    public int getProfessionCount(ResourceLocation professionId) {
        return professionCounts.getOrDefault(professionId, 0);
    }

    private void replaceProfessionCounts(
            Map<ResourceLocation, Integer> newProfessionCounts
    ) {
        Objects.requireNonNull(newProfessionCounts, "professionCounts");
        professionCounts.clear();
        for (Map.Entry<ResourceLocation, Integer> entry
                : newProfessionCounts.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count > 0) {
                professionCounts.put(
                        Objects.requireNonNull(entry.getKey(), "professionId"),
                        count
                );
            }
        }
    }
}
