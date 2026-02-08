# UrMathsTeacher AntiCheat

A lightweight Spigot anti-cheat plugin with configurable checks for speed, fly, reach, and autoclicking.

## Features
- **Speed check** (blocks per second)
- **Fly check** (air-ticks threshold)
- **Reach check** (attack distance)
- **Autoclicker check** (clicks per second)
- Configurable alerts and kick thresholds

## Commands
- `/anticheat reload` — reloads configuration
- `/anticheat status` — shows enabled checks

## Permissions
- `anticheat.admin` — manage the plugin and receive alerts
- `anticheat.bypass` — bypass all checks

## Build
```bash
mvn -q package
```

The plugin jar is in `target/` after the build.
