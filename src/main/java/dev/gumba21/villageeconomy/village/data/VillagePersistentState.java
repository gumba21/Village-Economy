package dev.gumba21.villageeconomy.village.data;

import dev.gumba21.villageeconomy.VillageEconomy;
import dev.gumba21.villageeconomy.config.VillageEconomyConfig;
import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class VillagePersistentState extends SavedData {
    public static final String DATA_NAME = "villageeconomy_villages";

    private static final int CURRENT_DATA_VERSION = 1;
    private static final String VILLAGES_KEY = "Villages";

    private final Map<UUID, TrackedVillage> villages = new LinkedHashMap<>();

    public static VillagePersistentState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                VillagePersistentState::load,
                VillagePersistentState::new,
                DATA_NAME
        );
    }

    public static VillagePersistentState load(CompoundTag root) {
        VillagePersistentState state = new VillagePersistentState();
        boolean repaired = false;
        long now = System.currentTimeMillis();

        if (!root.contains(VILLAGES_KEY, Tag.TAG_LIST)) {
            return state;
        }

        ListTag savedVillages = root.getList(VILLAGES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < savedVillages.size(); index++) {
            try {
                ReadResult result = readVillage(savedVillages.getCompound(index), now);
                if (state.isDuplicate(result.village())) {
                    VillageEconomy.LOGGER.warn(
                            "Skipped duplicate village record {} while loading persistent data",
                            result.village().getId()
                    );
                    repaired = true;
                    continue;
                }
                state.villages.put(result.village().getId(), result.village());
                repaired |= result.repaired();
            } catch (RuntimeException invalidEntry) {
                VillageEconomy.LOGGER.warn(
                        "Skipped invalid village record {} while loading persistent data",
                        index,
                        invalidEntry
                );
                repaired = true;
            }
        }

        if (repaired || root.getInt("DataVersion") < CURRENT_DATA_VERSION) {
            state.setDirty();
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putInt("DataVersion", CURRENT_DATA_VERSION);
        ListTag savedVillages = new ListTag();
        for (TrackedVillage village : villages.values()) {
            savedVillages.add(writeVillage(village));
        }
        root.put(VILLAGES_KEY, savedVillages);
        VillageEconomyDebugLogger.info(
                "Saving {} tracked villages to persistent state",
                villages.size()
        );
        return root;
    }

    public Collection<TrackedVillage> getVillages() {
        return Collections.unmodifiableCollection(villages.values());
    }

    public int size() {
        return villages.size();
    }

    public boolean add(TrackedVillage village) {
        if (isDuplicate(village)) {
            return false;
        }
        villages.put(village.getId(), village);
        setDirty();
        return true;
    }

    public boolean remove(UUID id) {
        if (villages.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public Optional<TrackedVillage> findContaining(
            ResourceKey<Level> dimension,
            BlockPos position
    ) {
        return villages.values().stream()
                .filter(village -> village.contains(dimension, position))
                .min((first, second) -> Long.compare(
                        first.horizontalDistanceSquared(position),
                        second.horizontalDistanceSquared(position)
                ));
    }

    public Optional<TrackedVillage> findNearest(
            ResourceKey<Level> dimension,
            BlockPos position
    ) {
        return villages.values().stream()
                .filter(village -> village.getDimension().equals(dimension))
                .min((first, second) -> Long.compare(
                        first.horizontalDistanceSquared(position),
                        second.horizontalDistanceSquared(position)
                ));
    }

    private boolean isDuplicate(TrackedVillage candidate) {
        if (villages.containsKey(candidate.getId())) {
            return true;
        }
        return villages.values().stream().anyMatch(existing -> {
            int radius = Math.max(
                    existing.getDetectionRadius(),
                    candidate.getDetectionRadius()
            );
            return existing.getDimension().equals(candidate.getDimension())
                    && existing.horizontalDistanceSquared(candidate.getCenter())
                    <= (long) radius * radius;
        });
    }

    private static CompoundTag writeVillage(TrackedVillage village) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", village.getId());
        tag.putInt("CenterX", village.getCenter().getX());
        tag.putInt("CenterY", village.getCenter().getY());
        tag.putInt("CenterZ", village.getCenter().getZ());
        tag.putString("Dimension", village.getDimension().location().toString());
        tag.putInt("DetectionRadius", village.getDetectionRadius());
        tag.putLong("FirstDiscovered", village.getFirstDiscoveredTimestamp());
        tag.putLong("LastSeen", village.getLastSeenTimestamp());
        tag.putInt("VillagerCount", village.getVillagerCount());
        tag.putInt("WorkstationCount", village.getWorkstationCount());
        tag.putBoolean("Loaded", village.isLoaded());
        return tag;
    }

    private static ReadResult readVillage(CompoundTag tag, long now) {
        boolean repaired = false;
        UUID id;
        if (tag.hasUUID("Id")) {
            id = tag.getUUID("Id");
        } else {
            id = UUID.randomUUID();
            repaired = true;
        }

        if (!tag.contains("CenterX", Tag.TAG_INT)
                || !tag.contains("CenterY", Tag.TAG_INT)
                || !tag.contains("CenterZ", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Village center is missing");
        }
        BlockPos center = new BlockPos(
                tag.getInt("CenterX"),
                tag.getInt("CenterY"),
                tag.getInt("CenterZ")
        );

        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimensionId == null) {
            dimensionId = Level.OVERWORLD.location();
            repaired = true;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

        int radius = tag.getInt("DetectionRadius");
        if (radius < VillageEconomyConfig.MIN_VILLAGE_DETECTION_RADIUS
                || radius > VillageEconomyConfig.MAX_VILLAGE_DETECTION_RADIUS) {
            radius = VillageEconomyConfig.DEFAULT_VILLAGE_DETECTION_RADIUS;
            repaired = true;
        }

        long firstDiscovered = tag.getLong("FirstDiscovered");
        if (firstDiscovered <= 0L) {
            firstDiscovered = now;
            repaired = true;
        }
        long lastSeen = tag.getLong("LastSeen");
        if (lastSeen < firstDiscovered) {
            lastSeen = firstDiscovered;
            repaired = true;
        }

        int villagerCount = Math.max(0, tag.getInt("VillagerCount"));
        int workstationCount = Math.max(0, tag.getInt("WorkstationCount"));
        if (villagerCount != tag.getInt("VillagerCount")
                || workstationCount != tag.getInt("WorkstationCount")) {
            repaired = true;
        }

        TrackedVillage village = new TrackedVillage(
                id,
                center,
                dimension,
                radius,
                firstDiscovered,
                lastSeen,
                villagerCount,
                workstationCount,
                false
        );
        return new ReadResult(village, repaired);
    }

    private record ReadResult(TrackedVillage village, boolean repaired) {
    }
}
