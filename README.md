# Village Economy

Village Economy is a Fabric 1.20.1 addon designed to extend:

- Trade Overhaul
- Dynamic Villager Trades
- Numismatic Overhaul

Rather than replacing those mods, Village Economy gives every village its own local economy.

## Vision

Each village should feel alive.

Selling lots of bread in one village should lower its value there.
Mining towns should value ores differently from farming villages.
Villagers should slowly recover from market changes over time.

## Target Versions

- Minecraft 1.20.1
- Fabric Loader 0.19.3
- Java 17

## Current Status

The repository contains the buildable project foundation, configuration system, and server-side
village tracking. Trade hooks, economy calculations, compatibility layers, gameplay mixins, and
other market systems have not been implemented yet.

Fabric API and Cloth Config are required runtime dependencies. Mod Menu is optional and provides
access to the graphical configuration screen when installed.

## Configuration

Village Economy loads its server-safe configuration during mod initialization. The file is stored
at `config/villageeconomy.json` and is created automatically when it does not exist:

```json
{
  "enabled": true,
  "debugLogging": false,
  "villageDetectionRadius": 64,
  "marketUpdateIntervalTicks": 1200,
  "priceChangeStrength": 0.15,
  "minimumPriceMultiplier": 0.5,
  "maximumPriceMultiplier": 2.0,
  "recoveryRate": 0.02
}
```

| Setting | Default | Valid range | Description |
| --- | ---: | ---: | --- |
| `enabled` | `true` | `true` / `false` | Master switch for village tracking and future systems. |
| `debugLogging` | `false` | `true` / `false` | Enables additional diagnostic logging. |
| `villageDetectionRadius` | `64` | `1`–`512` | Radius used to group and match loaded village records. |
| `marketUpdateIntervalTicks` | `1200` | `20`–`1728000` | Ticks between loaded-village scans. |
| `priceChangeStrength` | `0.15` | `0.0`–`1.0` | Strength of future price adjustments. |
| `minimumPriceMultiplier` | `0.5` | `0.01`–`1.0` | Lowest multiplier future calculations may apply. |
| `maximumPriceMultiplier` | `2.0` | `1.0`–`10.0` | Highest multiplier future calculations may apply. |
| `recoveryRate` | `0.02` | `0.0`–`1.0` | Fraction of future market imbalance recovered per update. |

Every setting is validated when loaded or saved. Missing, incorrectly typed, non-finite, fractional
integer, and out-of-range values are replaced with their defaults and the repaired file is written
back to disk. If the JSON is malformed, the original file is moved to a timestamped
`villageeconomy.json.malformed-*.bak` file and a valid default configuration is generated. If a
filesystem error prevents recovery, the mod continues with safe in-memory defaults instead of
crashing Minecraft. Each load outcome is logged as loaded, created, repaired, reset, or an
in-memory fallback.

With Mod Menu installed, every setting is available through a Cloth Config screen with its valid
range and description. Saving the screen writes the file and applies the values immediately; no
Minecraft restart or config reload is required. Dedicated server administrators can edit the JSON
file directly and restart the server to load those external edits.

## Village Tracking

Village tracking runs only on the logical server, including the integrated server used by
singleplayer. It never registers a client tick or runs village logic in a client-only context.

At startup, Village Economy loads its tracked-village state. While `enabled` is `true`, the central
`VillageManager` scans once immediately and then every `marketUpdateIntervalTicks`. Each scan:

1. Visits each loaded dimension without loading or generating chunks.
2. Reads loaded villager entities once and asks vanilla whether their positions are part of a
   village.
3. Groups nearby villagers using `villageDetectionRadius` and counts their distinct assigned job
   sites as workstations.
4. Matches groups to existing records by dimension and distance, preserving stable UUIDs.
5. Marks records outside loaded chunks as unloaded instead of deleting them.
6. Removes a record only when its center chunk is loaded and vanilla no longer considers its
   center a village.

Each record stores its stable UUID, center, dimension, detection radius, discovery and last-seen
timestamps, villager and assigned-workstation counts, and current loaded state. The manager also
supports containing-village and nearest-village queries for later features. This PR does not use
those records to alter trades or prices.

### Persistent Storage

Village data uses Minecraft's per-world persistent-state system under the unique key
`villageeconomy_villages`. Minecraft writes it to the world's `data` directory, normally as:

```text
<world>/data/villageeconomy_villages.dat
```

Changes mark only this state as dirty so Minecraft saves it through its normal world-save cycle;
unrelated world data is never read or overwritten. Missing state creates an empty collection.
Older records receive safe defaults for newly introduced fields, invalid records are skipped
without stopping the server, and loaded-state flags are recalculated after a restart.

### Debug Logging

Set `debugLogging` to `true` to log loaded config values, persistent load/save counts, scan
start/end, discoveries, updates, removals, and scan duration. These diagnostic messages are
suppressed when `debugLogging` is `false`. Configuration creation/repair warnings and invalid
persistent-record warnings remain visible because they describe recovery actions rather than
routine debug output.

### Debug Command

Operators with permission level 2 can run:

```text
/villageeconomy villages
```

The command reports the number of tracked villages followed by each village's UUID, dimension,
center, villager count, loaded state, and age since discovery. It only inspects state and does not
change gameplay.

## Building

Install Java 17. The included Gradle wrapper downloads Gradle and all declared dependencies, so no
separate Gradle installation is required. Run the full build and test suite with:

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The remapped release jar is written to:

```text
build/libs/village-economy-0.1.0.jar
```

GitHub Actions runs the same build for pull requests, pushes to `main`, and manual dispatches, then uploads the release jar as a workflow artifact.

## Repository Layout

```text
src/main/java/       Java sources
src/main/resources/  Fabric metadata and resources
gradle/wrapper/      Gradle wrapper
.github/workflows/   Continuous integration
```

## License

Village Economy is available under the [MIT License](LICENSE).
