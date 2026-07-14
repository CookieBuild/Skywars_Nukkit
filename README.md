# Cookie Build SkyWars

Paper 26 reimplementation of the historical Nukkit SkyWars game. The plugin uses
CookieDough for queues, parties, NPC admission, player state, match statistics,
progression, rewards and retention telemetry.

## Map archives

Place clean world-template archives in `skywars_maps/` next to the Paper server
jar. The archive must contain the world files at its root (for example
`level.dat`, `region/`), not inside an additional directory.

The historical source repository only contains coordinates; it does not contain
the four map worlds. The expected archive names are:

- `legacy-0.zip`
- `legacy-1.zip`
- `legacy-2.zip`
- `legacy-4.zip`

Coordinates from the original configuration have been migrated to `config.yml`.
The waiting positions and kill heights are conservative estimates and should be
checked against the recovered worlds before production deployment.

## Local validation

From the `Cookies` directory:

```sh
./gradlew :SkyWars:test :SkyWars:build
```
