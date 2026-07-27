package dev.gumba21.villageeconomy.village;

import dev.gumba21.villageeconomy.VillageEconomy;
import dev.gumba21.villageeconomy.command.VillageEconomyCommands;
import dev.gumba21.villageeconomy.config.VillageEconomyConfigManager;
import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import dev.gumba21.villageeconomy.market.MarketManager;
import dev.gumba21.villageeconomy.market.simulation.MarketSimulationResult;
import dev.gumba21.villageeconomy.market.simulation.MarketSimulator;
import dev.gumba21.villageeconomy.market.simulation.MarketObservationAccumulator;
import dev.gumba21.villageeconomy.market.simulation.SimulationParameters;
import dev.gumba21.villageeconomy.trade.mapping.VillageOwnershipIndex;
import dev.gumba21.villageeconomy.trade.model.TradeCapture;
import dev.gumba21.villageeconomy.trade.observation.TradeObservationService;
import dev.gumba21.villageeconomy.village.data.TrackedVillage;
import dev.gumba21.villageeconomy.village.data.VillagePersistentState;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadEvidence;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadReconciler;
import dev.gumba21.villageeconomy.village.lifecycle.VillageLoadReconciliation;
import dev.gumba21.villageeconomy.village.lifecycle.VillageReactivationMatch;
import dev.gumba21.villageeconomy.village.lifecycle.VillageReactivationMatcher;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

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
    private static final int MISSING_SCANS_BEFORE_REMOVAL = 2;
    private static final Predicate<Villager> ACTIVE_VILLAGER =
            villager -> villager.isAlive() && !villager.isRemoved();

    private static volatile VillageManager instance;
    private static boolean eventsRegistered;

    private final MinecraftServer server;
    private final VillagePersistentState state;
    private final MarketManager marketManager;
    private final MarketSimulator marketSimulator = new MarketSimulator();
    private final MarketObservationAccumulator marketObservationAccumulator;
    private final TradeObservationService tradeObservationService;
    private final VillageOwnershipIndex villageOwnershipIndex =
            new VillageOwnershipIndex();

    private final List<DetectionCluster> clusters = new ArrayList<>();
    private final List<TrackedVillage> villageSnapshot = new ArrayList<>();
    private final Set<UUID> matchedVillageIds = new HashSet<>();
    private final Map<ResourceKey<Level>, ServerLevel> levelsByDimension = new HashMap<>();
    private final Map<ResourceKey<Level>, List<Villager>>
            loadedVillagersByDimension = new HashMap<>();
    private final Map<UUID, Integer> consecutiveMissingVillageScans =
            new HashMap<>();
    private final VillageLoadReconciler loadReconciler =
            new VillageLoadReconciler();
    private final VillageReactivationMatcher reactivationMatcher =
            new VillageReactivationMatcher();

    private int ticksUntilScan;
    private boolean enabledLastTick;
    private boolean reactivationProbeRequested = true;

    private VillageManager(MinecraftServer server, VillagePersistentState state) {
        this.server = server;
        this.state = state;
        this.marketManager = new MarketManager(state);
        this.marketManager.ensureMarkets(state.getVillages());
        this.marketObservationAccumulator =
                new MarketObservationAccumulator(marketManager);
        this.tradeObservationService = new TradeObservationService(
                this::resolveVillageOwnership,
                marketManager::getMarket
        );
        this.tradeObservationService.registerListener(
                marketObservationAccumulator
        );
        this.tradeObservationService.markHookInitialized();
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
        ServerEntityEvents.ENTITY_LOAD.register(VillageManager::onEntityLoaded);
        ServerChunkEvents.CHUNK_LOAD.register(VillageManager::onChunkLoaded);
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> requestReactivationProbe(server)
        );
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
        VillageEconomy.LOGGER.info(
                "Trade observation hook initialized: source=Trade Overhaul"
        );
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

    private static void onEntityLoaded(Entity entity, ServerLevel level) {
        if (!(entity instanceof Villager villager)) {
            return;
        }
        VillageManager manager = instance;
        if (manager != null && manager.server == level.getServer()) {
            manager.reactivateFromVillager(level, villager);
        }
    }

    private static void onChunkLoaded(ServerLevel level, LevelChunk chunk) {
        VillageManager manager = instance;
        if (manager != null
                && manager.server == level.getServer()
                && manager.isRelevantUnloadedVillageChunk(
                        level,
                        chunk.getPos().x,
                        chunk.getPos().z
                )) {
            manager.reactivationProbeRequested = true;
        }
    }

    private static void requestReactivationProbe(MinecraftServer server) {
        VillageManager manager = instance;
        if (manager != null && manager.server == server) {
            manager.reactivationProbeRequested = true;
        }
    }

    private static synchronized void onServerStopping(MinecraftServer server) {
        VillageManager manager = instance;
        if (manager == null || manager.server != server) {
            return;
        }
        manager.markAllUnloaded("server_stopping");
        VillageEconomyDebugLogger.info(
                "Prepared {} tracked villages for persistent save",
                manager.state.size()
        );
    }

    private static synchronized void onServerStopped(MinecraftServer server) {
        VillageManager manager = instance;
        if (manager != null && manager.server == server) {
            manager.tradeObservationService.shutdown();
            manager.villageOwnershipIndex.clear();
            manager.loadReconciler.clear();
            manager.consecutiveMissingVillageScans.clear();
            instance = null;
        }
    }

    private void tick() {
        VillageEconomyConfigManager configManager =
                VillageEconomyConfigManager.getInstance();
        if (!configManager.isVillageTrackingEnabled()) {
            if (enabledLastTick) {
                markAllUnloaded("tracking_disabled");
            }
            enabledLastTick = false;
            ticksUntilScan = 0;
            reactivationProbeRequested = false;
            return;
        }

        int interval = configManager.getMarketUpdateIntervalTicks();
        int radius = configManager.getVillageDetectionRadius();
        if (!enabledLastTick) {
            ticksUntilScan = 0;
            enabledLastTick = true;
        } else if (ticksUntilScan > interval) {
            ticksUntilScan = interval;
        }

        if (reactivationProbeRequested && ticksUntilScan > 0) {
            reactivationProbeRequested = false;
            reactivatePersistedVillages(radius);
        }

        if (ticksUntilScan > 0) {
            ticksUntilScan--;
            return;
        }

        reactivationProbeRequested = false;
        scan(radius, interval);
        ticksUntilScan = Math.max(1, interval) - 1;
    }

    private void scan(int radius, int interval) {
        long startedAt = System.nanoTime();
        long now = System.currentTimeMillis();

        clusters.clear();
        matchedVillageIds.clear();
        refreshRuntimeSnapshots();

        VillageEconomyDebugLogger.info(
                "Village scan started: radius={}, interval={} ticks",
                radius,
                interval
        );

        for (Map.Entry<ResourceKey<Level>, List<Villager>> entry
                : loadedVillagersByDimension.entrySet()) {
            ServerLevel level = levelsByDimension.get(entry.getKey());
            List<Villager> loadedVillagers = entry.getValue();
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
                        true,
                        cluster.professionCounts
                );
                if (state.add(village)) {
                    marketManager.createMarket(village.getId());
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
            consecutiveMissingVillageScans.remove(existing.getId());
            reconcileLoadedState(
                    existing,
                    true,
                    collectLoadEvidence(existing)
            );
            if (existing.updateSeen(
                    center,
                    radius,
                    now,
                    cluster.villagerCount,
                    cluster.workstations.size(),
                    cluster.professionCounts
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

            VillageLoadEvidence evidence = collectLoadEvidence(village);
            reconcileLoadedState(
                    village,
                    false,
                    evidence
            );
            if (removeIfConfirmedMissing(village, evidence)) {
                removed++;
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
        simulateMarkets(now);
    }

    /**
     * Lightweight lifecycle-only pass. It is triggered by runtime entity,
     * chunk, and player arrival and deliberately does not run market
     * simulation or village discovery.
     */
    private void reactivatePersistedVillages(int configuredRadius) {
        refreshRuntimeSnapshots();

        for (Map.Entry<ResourceKey<Level>, List<Villager>> entry
                : loadedVillagersByDimension.entrySet()) {
            ServerLevel level = levelsByDimension.get(entry.getKey());
            for (Villager villager : entry.getValue()) {
                reactivateFromVillager(level, villager, configuredRadius);
            }
        }

        // If entity callbacks are unavailable for an already-loaded area, a
        // relevant loaded chunk plus a nearby player is still strong evidence.
        for (TrackedVillage village : state.getVillages()) {
            if (village.isLoaded()) {
                continue;
            }
            VillageLoadEvidence evidence = collectLoadEvidence(village);
            if (evidence.loadedTrackedVillagerCount() > 0
                    || evidence.relevantLoadedChunkCount() == 0
                    || evidence.nearbyPlayerCount() == 0) {
                continue;
            }
            reactivate(
                    village,
                    null,
                    -1L,
                    "relevant_chunk_loaded",
                    evidence,
                    1,
                    false
            );
        }
    }

    private void reactivateFromVillager(
            ServerLevel level,
            Villager villager
    ) {
        VillageEconomyConfigManager configManager =
                VillageEconomyConfigManager.getInstance();
        if (!configManager.isVillageTrackingEnabled()) {
            return;
        }
        int radius = configManager.getVillageDetectionRadius();
        reactivateFromVillager(level, villager, radius);
    }

    private void reactivateFromVillager(
            ServerLevel level,
            Villager villager,
            int configuredRadius
    ) {
        Optional<VillageReactivationMatch> match =
                reactivationMatcher.matchLoadedVillager(
                        villager.getUUID(),
                        level.dimension(),
                        villager.blockPosition(),
                        state.getVillages(),
                        configuredRadius
                );
        if (match.isEmpty()) {
            return;
        }
        VillageReactivationMatch reactivation = match.get();
        VillageLoadEvidence evidence = collectLoadEvidenceForLevel(
                level,
                reactivation.village()
        );
        if (evidence.loadedTrackedVillagerCount() == 0) {
            evidence = new VillageLoadEvidence(
                    evidence.trackedVillagerCount(),
                    1,
                    evidence.relevantLoadedChunkCount(),
                    evidence.nearbyPlayerCount()
            );
        }
        reactivate(
                reactivation.village(),
                reactivation.villagerId(),
                reactivation.distanceSquared(),
                "persisted_villager_loaded",
                evidence,
                reactivation.candidateCount(),
                reactivation.equalDistanceTie()
        );
    }

    private void reactivate(
            TrackedVillage village,
            UUID matchedVillagerId,
            long distanceSquared,
            String reason,
            VillageLoadEvidence evidence,
            int candidateCount,
            boolean equalDistanceTie
    ) {
        boolean previousLoaded = village.isLoaded();
        VillageLoadReconciliation reconciliation = loadReconciler.reconcile(
                village,
                false,
                evidence
        );
        if (!reconciliation.changed() || !village.isLoaded()) {
            return;
        }

        state.setDirty();
        consecutiveMissingVillageScans.remove(village.getId());
        if (matchedVillagerId != null) {
            villageOwnershipIndex.associateLoadedVillager(
                    matchedVillagerId,
                    village
            );
        }
        VillageEconomyDebugLogger.info(
                "Village persisted reactivation: uuid={}, previous={}, new={}, reason={}, "
                        + "matchedVillager={}, distanceBlocks={}, trackedVillagers={}, "
                        + "loadedTrackedVillagers={}, relevantLoadedChunks={}, nearbyPlayers={}, "
                        + "candidates={}, equalDistanceTie={}",
                village.getId(),
                previousLoaded,
                village.isLoaded(),
                reason,
                matchedVillagerId == null ? "none" : matchedVillagerId,
                distanceSquared < 0L ? -1.0D : Math.sqrt(distanceSquared),
                evidence.trackedVillagerCount(),
                evidence.loadedTrackedVillagerCount(),
                evidence.relevantLoadedChunkCount(),
                evidence.nearbyPlayerCount(),
                candidateCount,
                equalDistanceTie
        );
    }

    private void refreshRuntimeSnapshots() {
        levelsByDimension.clear();
        loadedVillagersByDimension.values().forEach(List::clear);
        for (ServerLevel level : server.getAllLevels()) {
            levelsByDimension.put(level.dimension(), level);
            List<Villager> loadedVillagers =
                    loadedVillagersByDimension.computeIfAbsent(
                            level.dimension(),
                            ignored -> new ArrayList<>()
                    );
            level.getEntities(EntityType.VILLAGER, ACTIVE_VILLAGER, loadedVillagers);
        }
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

    private VillageLoadEvidence collectLoadEvidence(TrackedVillage village) {
        ServerLevel level = levelsByDimension.get(village.getDimension());
        if (level == null) {
            return VillageLoadEvidence.absent(village.getVillagerCount());
        }

        int loadedTrackedVillagers = 0;
        List<Villager> villagers =
                loadedVillagersByDimension.get(village.getDimension());
        if (villagers != null) {
            for (Villager villager : villagers) {
                if (village.contains(
                        village.getDimension(),
                        villager.blockPosition()
                )) {
                    loadedTrackedVillagers++;
                }
            }
        }

        int nearbyPlayers = 0;
        for (var player : level.players()) {
            if (village.contains(village.getDimension(), player.blockPosition())) {
                nearbyPlayers++;
            }
        }

        return new VillageLoadEvidence(
                village.getVillagerCount(),
                loadedTrackedVillagers,
                countRelevantLoadedChunks(level, village),
                nearbyPlayers
        );
    }

    private static VillageLoadEvidence collectLoadEvidenceForLevel(
            ServerLevel level,
            TrackedVillage village
    ) {
        List<Villager> villagers = new ArrayList<>();
        level.getEntities(EntityType.VILLAGER, ACTIVE_VILLAGER, villagers);
        int loadedTrackedVillagers = 0;
        for (Villager villager : villagers) {
            if (village.contains(
                    village.getDimension(),
                    villager.blockPosition()
            )) {
                loadedTrackedVillagers++;
            }
        }

        int nearbyPlayers = 0;
        for (var player : level.players()) {
            if (village.contains(village.getDimension(), player.blockPosition())) {
                nearbyPlayers++;
            }
        }
        return new VillageLoadEvidence(
                village.getVillagerCount(),
                loadedTrackedVillagers,
                countRelevantLoadedChunks(level, village),
                nearbyPlayers
        );
    }

    private boolean isRelevantUnloadedVillageChunk(
            ServerLevel level,
            int chunkX,
            int chunkZ
    ) {
        for (TrackedVillage village : state.getVillages()) {
            if (village.isLoaded()
                    || !village.getDimension().equals(level.dimension())) {
                continue;
            }
            BlockPos center = village.getCenter();
            int radius = village.getDetectionRadius();
            int minimumChunkX =
                    SectionPos.blockToSectionCoord(center.getX() - radius);
            int maximumChunkX =
                    SectionPos.blockToSectionCoord(center.getX() + radius);
            int minimumChunkZ =
                    SectionPos.blockToSectionCoord(center.getZ() - radius);
            int maximumChunkZ =
                    SectionPos.blockToSectionCoord(center.getZ() + radius);
            if (chunkX >= minimumChunkX
                    && chunkX <= maximumChunkX
                    && chunkZ >= minimumChunkZ
                    && chunkZ <= maximumChunkZ) {
                return true;
            }
        }
        return false;
    }

    private static int countRelevantLoadedChunks(
            ServerLevel level,
            TrackedVillage village
    ) {
        BlockPos center = village.getCenter();
        int radius = village.getDetectionRadius();
        int minimumChunkX =
                SectionPos.blockToSectionCoord(center.getX() - radius);
        int maximumChunkX =
                SectionPos.blockToSectionCoord(center.getX() + radius);
        int minimumChunkZ =
                SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maximumChunkZ =
                SectionPos.blockToSectionCoord(center.getZ() + radius);
        int loadedChunks = 0;

        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (level.hasChunk(chunkX, chunkZ)) {
                    loadedChunks++;
                }
            }
        }
        return loadedChunks;
    }

    private boolean removeIfConfirmedMissing(
            TrackedVillage village,
            VillageLoadEvidence evidence
    ) {
        ServerLevel level = levelsByDimension.get(village.getDimension());
        if (level == null
                || evidence.loadedTrackedVillagerCount() > 0
                || evidence.relevantLoadedChunkCount()
                < relevantChunkCount(village)
                || level.isVillage(village.getCenter())) {
            consecutiveMissingVillageScans.remove(village.getId());
            return false;
        }

        int missingScans = consecutiveMissingVillageScans.merge(
                village.getId(),
                1,
                (previous, ignored) -> Math.min(
                        MISSING_SCANS_BEFORE_REMOVAL,
                        previous + 1
                )
        );
        if (missingScans < MISSING_SCANS_BEFORE_REMOVAL) {
            return false;
        }

        consecutiveMissingVillageScans.remove(village.getId());
        loadReconciler.forget(village.getId());
        if (!state.remove(village.getId())) {
            return false;
        }
        VillageEconomyDebugLogger.info(
                "Village removed after confirmed absence: uuid={}, dimension={}, center={}",
                village.getId(),
                village.getDimension().location(),
                village.getCenter().toShortString()
        );
        return true;
    }

    private static int relevantChunkCount(TrackedVillage village) {
        BlockPos center = village.getCenter();
        int radius = village.getDetectionRadius();
        int minimumChunkX =
                SectionPos.blockToSectionCoord(center.getX() - radius);
        int maximumChunkX =
                SectionPos.blockToSectionCoord(center.getX() + radius);
        int minimumChunkZ =
                SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maximumChunkZ =
                SectionPos.blockToSectionCoord(center.getZ() + radius);
        return (maximumChunkX - minimumChunkX + 1)
                * (maximumChunkZ - minimumChunkZ + 1);
    }

    private void reconcileLoadedState(
            TrackedVillage village,
            boolean detectedThisScan,
            VillageLoadEvidence evidence
    ) {
        VillageLoadReconciliation reconciliation = loadReconciler.reconcile(
                village,
                detectedThisScan,
                evidence
        );
        if (reconciliation.changed()) {
            state.setDirty();
        }
        logLoadedStateReconciliation(reconciliation);
    }

    private static void logLoadedStateReconciliation(
            VillageLoadReconciliation reconciliation
    ) {
        if (!reconciliation.changed() && !reconciliation.rejectedUnload()) {
            return;
        }
        VillageLoadEvidence evidence = reconciliation.evidence();
        String event = reconciliation.rejectedUnload()
                ? "Village unload transition rejected"
                : "Village loaded-state transition";
        VillageEconomyDebugLogger.info(
                "{}: uuid={}, previous={}, new={}, reason={}, trackedVillagers={}, "
                        + "loadedTrackedVillagers={}, relevantLoadedChunks={}, nearbyPlayers={}",
                event,
                reconciliation.villageId(),
                reconciliation.previousLoaded(),
                reconciliation.newLoaded(),
                reconciliation.reason(),
                evidence.trackedVillagerCount(),
                evidence.loadedTrackedVillagerCount(),
                evidence.relevantLoadedChunkCount(),
                evidence.nearbyPlayerCount()
        );
    }

    private void markAllUnloaded(String reason) {
        for (TrackedVillage village : state.getVillages()) {
            VillageLoadReconciliation reconciliation =
                    loadReconciler.forceUnloaded(village, reason);
            if (reconciliation.changed()) {
                state.setDirty();
                logLoadedStateReconciliation(reconciliation);
            }
        }
    }

    public Collection<TrackedVillage> getVillages() {
        return state.getVillages();
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }

    public TradeObservationService getTradeObservationService() {
        return tradeObservationService;
    }

    public MarketSimulationResult simulateMarketsNow() {
        return simulateMarkets(System.currentTimeMillis());
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

    private Optional<TrackedVillage> resolveVillageOwnership(
            TradeCapture capture
    ) {
        return villageOwnershipIndex.resolve(
                capture.villagerId(),
                capture.dimensionId(),
                capture.villagerPosition(),
                state.getVillages()
        );
    }

    private MarketSimulationResult simulateMarkets(long timestamp) {
        VillageEconomyConfigManager configManager =
                VillageEconomyConfigManager.getInstance();
        SimulationParameters parameters = new SimulationParameters(
                configManager.getMinimumPriceMultiplier(),
                configManager.getMaximumPriceMultiplier(),
                configManager.getPriceChangeStrength(),
                configManager.getRecoveryRate()
        );
        return marketSimulator.simulateAll(
                state.getVillages(),
                marketManager,
                parameters,
                timestamp,
                server.overworld().getGameTime()
        );
    }

    private static final class DetectionCluster {
        private final ResourceKey<Level> dimension;
        private final Set<BlockPos> workstations = new HashSet<>();
        private final Map<ResourceLocation, Integer> professionCounts =
                new HashMap<>();

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

            ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(villager.getVillagerData().getProfession());
            if (professionId != null) {
                professionCounts.merge(professionId, 1, Integer::sum);
            }

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
