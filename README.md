# Cookie Build SkyWars

Paper 26 reimplementation of the historical Nukkit SkyWars game. The plugin uses
CookieDough for queues, parties, NPC admission, player state, match statistics,
progression, rewards and retention telemetry.

## Recovered map archives

The four recovered Nukkit worlds are converted to current Paper world templates
and versioned in `maps/`:

- original `game-0` -> `maps/legacy-0.zip`
- original `game-1` -> `maps/legacy-1.zip`
- original `game-2` -> `maps/legacy-2.zip`
- original `game-3` (historical map id 4) -> `maps/legacy-4.zip`

Each archive must contain `level.dat` and `region/` at its root, not inside an
additional directory. Missing, nested, empty, corrupt, or coordinate-mismatched
templates are rejected, so the lobby cannot expose an empty arena.

The legacy island spawns are preserved in `config.yml` and validated against
solid terrain when a map loads. The first `legacy-1` spawn is centered at
`-1589.5`, and the `legacy-2` spectator spawn is raised to y=60 so it does not
intersect the recovered center stair.

Install all versioned archives into the sibling Paper 26 test server with:

```sh
./gradlew :SkyWars:installMaps
```

The default destination is `../Paper26_Test_Server/skywars_maps` relative to
the `Cookies` checkout. Override it for another server with an absolute or
`Cookies`-root-relative path:

```sh
./gradlew :SkyWars:installMaps \
  -PcookiebuildSkyWarsMapInstallDir=/path/to/server/skywars_maps
```

## Local validation

From the `Cookies` directory:

```sh
./gradlew :SkyWars:test :SkyWars:build
```
