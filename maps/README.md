# SkyWars map assets

Versioned, converted Paper world archives belong in this directory:

- `legacy-0.zip` from the recovered `game-0` world
- `legacy-1.zip` from the recovered `game-1` world
- `legacy-2.zip` from the recovered `game-2` world
- `legacy-4.zip` from the recovered `game-3` world (historical map id 4)

Every zip must contain `level.dat` and `region/` at its root. Run
`./gradlew :SkyWars:installMaps` from the `Cookies` repository to copy all four
archives into the configured Paper server.
