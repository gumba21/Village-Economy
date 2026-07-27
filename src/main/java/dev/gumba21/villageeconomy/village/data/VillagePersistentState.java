package dev.gumba21.villageeconomy.village.data;

import dev.gumba21.villageeconomy.VillageEconomy;
import dev.gumba21.villageeconomy.config.VillageEconomyConfig;
import dev.gumba21.villageeconomy.debug.VillageEconomyDebugLogger;
import dev.gumba21.villageeconomy.market.data.MarketEntry;
import dev.gumba21.villageeconomy.market.data.MarketState;
import dev.gumba21.villageeconomy.market.registry.DefaultTradeGoods;
import dev.gumba21.villageeconomy.market.registry.TradeGoodDefinition;
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

    private static final int CURRENT_DATA_VERSION = 4;
    private static final String VILLAGES_KEY = "Villages";
    private static final String MARKETS_KEY = "Markets";

    private final Map<UUID, TrackedVillage> villages = new LinkedHashMap<>();
    private final Map<UUID, MarketState> markets = new LinkedHashMap<>();

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

        if (root.contains(VILLAGES_KEY, Tag.TAG_LIST)) {
            ListTag savedVillages = root.getList(VILLAGES_KEY, Tag.TAG_COMPOUND);
            for (int index = 0; index < savedVillages.size(); index++) {
                try {
                    VillageReadResult result =
                            readVillage(savedVillages.getCompound(index), now);
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
        }

        if (root.contains(MARKETS_KEY, Tag.TAG_LIST)) {
            ListTag savedMarkets = root.getList(MARKETS_KEY, Tag.TAG_COMPOUND);
            for (int index = 0; index < savedMarkets.size(); index++) {
                try {
                    MarketReadResult result =
                            readMarket(savedMarkets.getCompound(index), now);
                    UUID villageId = result.market().getVillageId();
                    if (!state.villages.containsKey(villageId)) {
                        repaired = true;
                        VillageEconomyDebugLogger.info(
                                "Market repair: removed orphan market for village={}",
                                villageId
                        );
                        continue;
                    }
                    if (state.markets.putIfAbsent(villageId, result.market()) != null) {
                        repaired = true;
                        VillageEconomyDebugLogger.info(
                                "Market repair: removed duplicate market for village={}",
                                villageId
                        );
                        continue;
                    }
                    if (result.repaired()) {
                        repaired = true;
                        VillageEconomyDebugLogger.info(
                                "Market repair: village={}, trackedItems={}",
                                villageId,
                                result.market().size()
                        );
                    }
                } catch (RuntimeException invalidEntry) {
                    repaired = true;
                    VillageEconomyDebugLogger.info(
                            "Market repair: skipped invalid market record index={}",
                            index
                    );
                }
            }
        } else if (!state.villages.isEmpty()) {
            repaired = true;
        }

        if (repaired || root.getInt("DataVersion") < CURRENT_DATA_VERSION) {
            state.setDirty();
        }
        VillageEconomyDebugLogger.info(
                "Loaded persistent state: villages={}, markets={}, trackedItems={}, repaired={}",
                state.size(),
                state.marketSize(),
                state.marketEntryCount(),
                repaired
        );
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

        ListTag savedMarkets = new ListTag();
        for (MarketState market : markets.values()) {
            savedMarkets.add(writeMarket(market));
        }
        root.put(MARKETS_KEY, savedMarkets);

        VillageEconomyDebugLogger.info(
                "Saving persistent state: villages={}, markets={}, trackedItems={}",
                villages.size(),
                markets.size(),
                marketEntryCount()
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
        markets.remove(id);
        setDirty();
        return true;
    }

    public Collection<MarketState> getMarkets() {
        return Collections.unmodifiableCollection(markets.values());
    }

    public Optional<MarketState> getMarket(UUID villageId) {
        return Optional.ofNullable(markets.get(villageId));
    }

    public boolean hasMarket(UUID villageId) {
        return markets.containsKey(villageId);
    }

    public boolean addMarket(MarketState market) {
        UUID villageId = market.getVillageId();
        if (!villages.containsKey(villageId) || markets.containsKey(villageId)) {
            return false;
        }
        markets.put(villageId, market);
        setDirty();
        return true;
    }

    public void putMarket(MarketState market) {
        if (!villages.containsKey(market.getVillageId())) {
            throw new IllegalArgumentException(
                    "Cannot store a market for an untracked village"
            );
        }
        markets.put(market.getVillageId(), market);
        setDirty();
    }

    public boolean removeMarket(UUID villageId) {
        if (markets.remove(villageId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public int marketSize() {
        return markets.size();
    }

    public int marketEntryCount() {
        int count = 0;
        for (MarketState market : markets.values()) {
            count += market.size();
        }
        return count;
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

        ListTag professions = new ListTag();
        for (Map.Entry<ResourceLocation, Integer> profession
                : village.getProfessionCounts().entrySet()) {
            CompoundTag professionTag = new CompoundTag();
            professionTag.putString("Id", profession.getKey().toString());
            professionTag.putInt("Count", profession.getValue());
            professions.add(professionTag);
        }
        tag.put("Professions", professions);
        return tag;
    }

    private static VillageReadResult readVillage(CompoundTag tag, long now) {
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

        Map<ResourceLocation, Integer> professionCounts = new LinkedHashMap<>();
        if (tag.contains("Professions", Tag.TAG_LIST)) {
            ListTag savedProfessions =
                    tag.getList("Professions", Tag.TAG_COMPOUND);
            for (int index = 0; index < savedProfessions.size(); index++) {
                CompoundTag professionTag = savedProfessions.getCompound(index);
                ResourceLocation professionId =
                        ResourceLocation.tryParse(professionTag.getString("Id"));
                int count = professionTag.getInt("Count");
                if (professionId == null || count <= 0
                        || professionCounts.putIfAbsent(professionId, count) != null) {
                    repaired = true;
                }
            }
        } else {
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
                false,
                professionCounts
        );
        return new VillageReadResult(village, repaired);
    }

    private static CompoundTag writeMarket(MarketState market) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("VillageId", market.getVillageId());
        tag.putLong("CreatedAt", market.getCreationTimestamp());
        tag.putLong("LastUpdated", market.getLastUpdateTimestamp());

        ListTag savedEntries = new ListTag();
        for (MarketEntry entry : market.getEntries()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Item", entry.getItemId().toString());
            entryTag.putDouble("BasePrice", entry.getBasePrice());
            entryTag.putDouble("CurrentPrice", entry.getCurrentPrice());
            entryTag.putDouble("MinimumMultiplier", entry.getMinimumMultiplier());
            entryTag.putDouble("MaximumMultiplier", entry.getMaximumMultiplier());
            entryTag.putDouble("Supply", entry.getSupply());
            entryTag.putDouble("Demand", entry.getDemand());
            entryTag.putLong("LastModified", entry.getLastModifiedTimestamp());
            entryTag.putLong(
                    "PendingDemandAccumulator",
                    entry.getPendingDemandAccumulator()
            );
            entryTag.putLong(
                    "PendingSupplyAccumulator",
                    entry.getPendingSupplyAccumulator()
            );
            entryTag.putLong(
                    "PendingObservationCount",
                    entry.getPendingObservationCount()
            );
            entryTag.putLong(
                    "LastDemandAccumulator",
                    entry.getLastDemandAccumulator()
            );
            entryTag.putLong(
                    "LastSupplyAccumulator",
                    entry.getLastSupplyAccumulator()
            );
            entryTag.putDouble(
                    "LastNetPressure",
                    entry.getLastNetPressure()
            );
            entryTag.putDouble(
                    "LastRecoveryContribution",
                    entry.getLastRecoveryContribution()
            );
            entryTag.putLong(
                    "LastSimulationTick",
                    entry.getLastSimulationTick()
            );
            savedEntries.add(entryTag);
        }
        tag.put("Entries", savedEntries);
        return tag;
    }

    private static MarketReadResult readMarket(CompoundTag tag, long now) {
        if (!tag.hasUUID("VillageId")) {
            throw new IllegalArgumentException("Market village UUID is missing");
        }
        UUID villageId = tag.getUUID("VillageId");
        boolean repaired = false;

        long createdAt = tag.getLong("CreatedAt");
        if (createdAt <= 0L) {
            createdAt = now;
            repaired = true;
        }
        long lastUpdated = tag.getLong("LastUpdated");
        if (lastUpdated < createdAt) {
            lastUpdated = createdAt;
            repaired = true;
        }

        Map<ResourceLocation, MarketEntry> entries = new LinkedHashMap<>();
        if (tag.contains("Entries", Tag.TAG_LIST)) {
            ListTag savedEntries = tag.getList("Entries", Tag.TAG_COMPOUND);
            for (int index = 0; index < savedEntries.size(); index++) {
                try {
                    MarketEntryReadResult result =
                            readMarketEntry(savedEntries.getCompound(index), lastUpdated);
                    if (entries.putIfAbsent(
                            result.entry().getItemId(),
                            result.entry()
                    ) != null) {
                        repaired = true;
                        continue;
                    }
                    repaired |= result.repaired();
                } catch (RuntimeException invalidEntry) {
                    repaired = true;
                }
            }
        } else {
            repaired = true;
        }

        for (TradeGoodDefinition definition : DefaultTradeGoods.all()) {
            if (entries.containsKey(definition.itemId())) {
                continue;
            }
            entries.put(
                    definition.itemId(),
                    defaultEntry(definition, lastUpdated)
            );
            repaired = true;
        }

        MarketState market = new MarketState(
                villageId,
                createdAt,
                lastUpdated,
                entries.values()
        );
        return new MarketReadResult(market, repaired);
    }

    private static MarketEntryReadResult readMarketEntry(
            CompoundTag tag,
            long fallbackTimestamp
    ) {
        ResourceLocation itemId = ResourceLocation.tryParse(tag.getString("Item"));
        if (itemId == null) {
            throw new IllegalArgumentException("Market item identifier is invalid");
        }
        TradeGoodDefinition definition = DefaultTradeGoods.get(itemId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported market item: " + itemId
                ));

        boolean repaired = false;
        double basePrice = tag.getDouble("BasePrice");
        if (!isFinitePositive(basePrice)) {
            basePrice = definition.basePrice();
            repaired = true;
        }

        double minimumMultiplier = tag.getDouble("MinimumMultiplier");
        if (!Double.isFinite(minimumMultiplier)
                || minimumMultiplier
                < VillageEconomyConfig.MIN_MINIMUM_PRICE_MULTIPLIER
                || minimumMultiplier
                > VillageEconomyConfig.MAX_MINIMUM_PRICE_MULTIPLIER) {
            minimumMultiplier =
                    VillageEconomyConfig.DEFAULT_MINIMUM_PRICE_MULTIPLIER;
            repaired = true;
        }

        double maximumMultiplier = tag.getDouble("MaximumMultiplier");
        if (!Double.isFinite(maximumMultiplier)
                || maximumMultiplier
                < VillageEconomyConfig.MIN_MAXIMUM_PRICE_MULTIPLIER
                || maximumMultiplier
                > VillageEconomyConfig.MAX_MAXIMUM_PRICE_MULTIPLIER
                || maximumMultiplier < minimumMultiplier) {
            maximumMultiplier =
                    VillageEconomyConfig.DEFAULT_MAXIMUM_PRICE_MULTIPLIER;
            repaired = true;
        }

        double currentPrice = tag.getDouble("CurrentPrice");
        if (!isFinitePositive(currentPrice)
                || currentPrice < basePrice * minimumMultiplier
                || currentPrice > basePrice * maximumMultiplier) {
            currentPrice = basePrice;
            repaired = true;
        }

        double supply = tag.getDouble("Supply");
        if (!isFiniteNonNegative(supply)) {
            supply = definition.initialSupply();
            repaired = true;
        }
        double demand = tag.getDouble("Demand");
        if (!isFiniteNonNegative(demand)) {
            demand = definition.initialDemand();
            repaired = true;
        }

        long lastModified = tag.getLong("LastModified");
        if (lastModified <= 0L) {
            lastModified = fallbackTimestamp;
            repaired = true;
        }

        long pendingDemand = tag.getLong("PendingDemandAccumulator");
        long pendingSupply = tag.getLong("PendingSupplyAccumulator");
        long pendingObservations = tag.getLong("PendingObservationCount");
        long lastDemand = tag.getLong("LastDemandAccumulator");
        long lastSupply = tag.getLong("LastSupplyAccumulator");
        long lastSimulationTick = tag.getLong("LastSimulationTick");
        if (pendingDemand < 0L
                || pendingSupply < 0L
                || pendingObservations < 0L
                || lastDemand < 0L
                || lastSupply < 0L
                || lastSimulationTick < 0L) {
            pendingDemand = Math.max(0L, pendingDemand);
            pendingSupply = Math.max(0L, pendingSupply);
            pendingObservations = Math.max(0L, pendingObservations);
            lastDemand = Math.max(0L, lastDemand);
            lastSupply = Math.max(0L, lastSupply);
            lastSimulationTick = Math.max(0L, lastSimulationTick);
            repaired = true;
        }
        double lastNetPressure = tag.getDouble("LastNetPressure");
        if (!Double.isFinite(lastNetPressure)
                || lastNetPressure < -1.0
                || lastNetPressure > 1.0) {
            lastNetPressure = 0.0;
            repaired = true;
        }
        double lastRecoveryContribution =
                tag.getDouble("LastRecoveryContribution");
        if (!Double.isFinite(lastRecoveryContribution)) {
            lastRecoveryContribution = 0.0;
            repaired = true;
        }

        MarketEntry entry = new MarketEntry(
                itemId,
                basePrice,
                currentPrice,
                minimumMultiplier,
                maximumMultiplier,
                supply,
                demand,
                lastModified,
                pendingDemand,
                pendingSupply,
                pendingObservations,
                lastDemand,
                lastSupply,
                lastNetPressure,
                lastRecoveryContribution,
                lastSimulationTick
        );
        return new MarketEntryReadResult(entry, repaired);
    }

    private static MarketEntry defaultEntry(
            TradeGoodDefinition definition,
            long timestamp
    ) {
        return new MarketEntry(
                definition.itemId(),
                definition.basePrice(),
                definition.basePrice(),
                VillageEconomyConfig.DEFAULT_MINIMUM_PRICE_MULTIPLIER,
                VillageEconomyConfig.DEFAULT_MAXIMUM_PRICE_MULTIPLIER,
                definition.initialSupply(),
                definition.initialDemand(),
                timestamp
        );
    }

    private static boolean isFinitePositive(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static boolean isFiniteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private record VillageReadResult(TrackedVillage village, boolean repaired) {
    }

    private record MarketReadResult(MarketState market, boolean repaired) {
    }

    private record MarketEntryReadResult(MarketEntry entry, boolean repaired) {
    }
}
