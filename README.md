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

The repository contains the buildable project foundation and its configuration system.
Village detection, trade hooks, economy calculations, compatibility layers, mixins, and other
gameplay systems have not been implemented yet.

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
| `enabled` | `true` | `true` / `false` | Master switch for future Village Economy systems. |
| `debugLogging` | `false` | `true` / `false` | Enables additional diagnostic logging. |
| `villageDetectionRadius` | `64` | `1`–`512` | Radius in blocks for future village detection. |
| `marketUpdateIntervalTicks` | `1200` | `20`–`1728000` | Ticks between future market updates. |
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
