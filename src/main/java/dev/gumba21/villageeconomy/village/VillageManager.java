package dev.gumba21.villageeconomy.village;

import dev.gumba21.villageeconomy.command.VillageEconomyCommands;
import dev.gumba21.villageeconomy.config.VillageEconomyConfigManager;
import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import dev.gumba21.villageeconomy.village.data.VillagePersistentState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class VillageManager {
    private static final Predicate<Villager> ACTIVE_VILLAGER =
            villager -> villager.isAlive() && !villager.isRemoved();

    private static volatile VillageManager instance;
    private static boolean eventsRegistered;

    private final MinecraftServer server;
    private final VillagePersistentState state;

    private final List<Villager> loadedVillagers = new ArrayList<>();
    private final List<DetectionCluster> clusters = new ArrayList<>();
    private final List<TrackedVillage> villageSnapshot = new ArrayList<>();
    private final Set<UUID> matchedVillageIds = new HashSet<>();
    private final Map<ResourceKey<Level>, ServerLevel> levelsByDimension = new HashMap<>();

    private int ticksUntilScan;
    private boolean enabledLastTick;

    private VillageManager(MinecraftServer server, VillagePersistentState state) {
        this.server = server;
        this.state = state;
    }

    public static synchronized void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        eventsRegistered = true;
        VillageEconomyCommands.register();
        ServerLifecycleEvents.SERVER_STARTED.register(VillageManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(VillageManager::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(VillageManager::onServerStopped);
        ServerTickEvents.END_SERVER_TICK.register(VillageManager::onServerTick);
    }

    public static VillageManager get(MinecraftServer server) {
        VillageManager manager = instance;
        if (manager == null || manager.server != server) {
            throw new IllegalStateException("Village manager is not active for this server");
        }
        return manager;
    }

    private static synchronized void onServerStarted(MinecraftServer server) {
        VillagePersistentState state = VillagePersistentState.get(server);
        instance = new VillageManager(server, state);
        VillageEconomyDebugLogger.info(
                "Loaded {} tracked villages from persistent state",
                state.size()
        );
    }

    private static void onServerTick(MinecraftServer server) {
        VillageManager manager = instance;
        if (manager != null && manager.server == server) {
            manager.tick();
        }
    }

    private static synchronized void onServerStopping(MinecraftServer server) {
        VillageManager manager = instance;
        if (manager == null || manager.server != server) {
            return;
        }
        manager.markAllUnloaded();
        VillageEconomyDebugLogger.info(
                "Prepared {} tracked villages for persistent save",
                manager.state.size()
        );
    }

    private static synchronized void onServerStopped(MinecraftServer server) {
        VillageManager manager = instance;
        if (manager != null && manager.server == server) {
            instance = null;
        }
    }

    private void tick() {
        VillageEconomyConfigManager configManager =
                VillageEconomyConfigManager.getInstance();
        if (!configManager.isVillageTrackingEnabled()) {
            if (enabledLastTick) {
                markAllUnloaded();
            }
            enabledLastTick = false;
            ticksUntilScan = 0;
            return;
        }

        int interval = configManager.getMarketUpdateIntervalTicks();
        if (!enabledLastTick) {
            ticksUntilScan = 0;
            enabledLastTick = true;
        } else if (ticksUntilScan > interval) {
            ticksUntilScan = interval;
        }

        if (ticksUntilScan > 0) {
            ticksUntilScan--;
            return;
        }

        scan(configManager.getVillageDetectionRadius(), interval);
        ticksUntilScan = Math.max(1, interval) - 1;
    }

    private void scan(int radius, int interval) {
        long startedAt = System.nanoTime();
        long now = System.currentTimeMillis();

        clusters.clear();
        matchedVillageIds.clear();
        levelsByDimension.clear();

        VillageEconomyDebugLogger.info(
                "Village scan started: radius={}, interval={} ticks",
                radius,
                interval
        );

        for (ServerLevel level : server.getAllLevels()) {
            levelsByDimension.put(level.dimension(), level);
            loadedVillagers.clear();
            level.getEntities(EntityType.VILLAGER, ACTIVE_VILLAGER, loadedVillagers);

            for (Villager villager : loadedVillagers) {
                BlockPos position = villager.blockPosition();
                if (!level.isVillage(position)) {
                    continue;
                }
                addToCluster(level.dimension(), villager, radius);
            }
        }

        int discovered = 0;
        int updated = 0;
        int removed = 0;

        for (DetectionCluster cluster : clusters) {
            BlockPos center = cluster.center();
            TrackedVillage existing = findClosestUnmatched(
                    cluster.dimension,
                    center,
                    radius
            );
            if (existing == null) {
                TrackedVillage village = new TrackedVillage(
                        UUID.randomUUID(),
                        center,
                        cluster.dimension,
                        radius,
                        now,
                        now,
                        cluster.villagerCount,
                        cluster.workstations.size(),
                        true
                );
                if (state.add(village)) {
                    matchedVillageIds.add(village.getId());
                    discovered++;
                    VillageEconomyDebugLogger.info(
                            "Village discovered: uuid={}, dimension={}, center={}, villagers={}, "
                                    + "workstations={}",
                            village.getId(),
                            village.getDimension().location(),
                            village.getCenter().toShortString(),
                            village.getVillagerCount(),
                            village.getWorkstationCount()
                    );
                }
                continue;
            }

            matchedVillageIds.add(existing.getId());
            if (existing.updateSeen(
                    center,
                    radius,
                    now,
                    cluster.villagerCount,
                    cluster.workstations.size()
            )) {
                state.setDirty();
                updated++;
                VillageEconomyDebugLogger.info(
                        "Village updated: uuid={}, center={}, villagers={}, workstations={}",
                        existing.getId(),
                        existing.getCenter().toShortString(),
                        existing.getVillagerCount(),
                        existing.getWorkstationCount()
                );
            }
        }

        villageSnapshot.clear();
        villageSnapshot.addAll(state.getVillages());
        for (TrackedVillage village : villageSnapshot) {
            if (matchedVillageIds.contains(village.getId())) {
                continue;
            }

            ServerLevel level = levelsByDimension.get(village.getDimension());
            if (level == null || !level.hasChunkAt(village.getCenter())) {
                if (village.updateLoadedState(false)) {
                    state.setDirty();
                }
                continue;
            }

            if (!level.isVillage(village.getCenter())) {
                if (state.remove(village.getId())) {
                    removed++;
                    VillageEconomyDebugLogger.info(
                            "Village removed: uuid={}, dimension={}, center={}",
                            village.getId(),
                            village.getDimension().location(),
                            village.getCenter().toShortString()
                    );
                }
                continue;
            }

            if (village.updateSeen(
                    village.getCenter(),
                    radius,
                    now,
                    0,
                    0
            )) {
                state.setDirty();
                updated++;
            }
        }

        long durationMicros = (System.nanoTime() - startedAt) / 1_000L;
        VillageEconomyDebugLogger.info(
                "Village scan finished: tracked={}, discovered={}, updated={}, removed={}, "
                        + "duration={} µs",
                state.size(),
                discovered,
                updated,
                removed,
                durationMicros
        );
    }

    private void addToCluster(
            ResourceKey<Level> dimension,
            Villager villager,
            int radius
    ) {
        BlockPos position = villager.blockPosition();
        long radiusSquared = (long) radius * radius;
        DetectionCluster closest = null;
        long closestDistance = Long.MAX_VALUE;

        for (DetectionCluster cluster : clusters) {
            if (!cluster.dimension.equals(dimension)) {
                continue;
            }
            long distance = cluster.horizontalDistanceSquared(position);
            if (distance <= radiusSquared && distance < closestDistance) {
                closest = cluster;
                closestDistance = distance;
            }
        }

        if (closest == null) {
            closest = new DetectionCluster(dimension);
            clusters.add(closest);
        }
        closest.add(villager);
    }

    private TrackedVillage findClosestUnmatched(
            ResourceKey<Level> dimension,
            BlockPos center,
            int radius
    ) {
        long radiusSquared = (long) radius * radius;
        long closestDistance = Long.MAX_VALUE;
        TrackedVillage closest = null;

        for (TrackedVillage village : state.getVillages()) {
            if (matchedVillageIds.contains(village.getId())
                    || !village.getDimension().equals(dimension)) {
                continue;
            }
            long distance = village.horizontalDistanceSquared(center);
            if (distance <= radiusSquared && distance < closestDistance) {
                closest = village;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private void markAllUnloaded() {
        boolean changed = false;
        for (TrackedVillage village : state.getVillages()) {
            changed |= village.updateLoadedState(false);
        }
        if (changed) {
            state.setDirty();
        }
    }

    public Collection<TrackedVillage> getVillages() {
        return state.getVillages();
    }

    public Optional<TrackedVillage> findVillage(
            ResourceKey<Level> dimension,
            BlockPos position
    ) {
        return state.findContaining(dimension, position);
    }

    public Optional<TrackedVillage> findNearestVillage(
            ResourceKey<Level> dimension,
            BlockPos position
    ) {
        return state.findNearest(dimension, position);
    }

    private static final class DetectionCluster {
        private final ResourceKey<Level> dimension;
        private final Set<BlockPos> workstations = new HashSet<>();

        private long totalX;
        private long totalY;
        private long totalZ;
        private int villagerCount;

        private DetectionCluster(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        private void add(Villager villager) {
            BlockPos position = villager.blockPosition();
            totalX += position.getX();
            totalY += position.getY();
            totalZ += position.getZ();
            villagerCount++;

            Optional<GlobalPos> jobSite =
                    villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (jobSite.isPresent()
                    && jobSite.get().dimension().equals(dimension)) {
                workstations.add(jobSite.get().pos().immutable());
            }
        }

        private BlockPos center() {
            return new BlockPos(
                    (int) Math.round((double) totalX / villagerCount),
                    (int) Math.round((double) totalY / villagerCount),
                    (int) Math.round((double) totalZ / villagerCount)
            );
        }

        private long horizontalDistanceSquared(BlockPos position) {
            BlockPos center = center();
            long deltaX = (long) center.getX() - position.getX();
            long deltaZ = (long) center.getZ() - position.getZ();
            return deltaX * deltaX + deltaZ * deltaZ;
        }
    }
}
