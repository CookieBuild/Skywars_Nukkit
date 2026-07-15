# SkyWars map conversion record

The four archives in `maps/` were recovered from the deployed Nukkit worlds and
converted on 2026-07-15. `game-3` is historical map id 4 and is therefore
packaged as `legacy-4.zip`. Source files were checksummed before copying and
were never used as in-place conversion targets.

## Semantic conversion

Every observed block state is allowlisted in `correspondence-table.json`.
Important Nukkit/Java differences are explicit: invisible bedrock becomes a
barrier; Nukkit wood slabs, concrete, concrete powder, and stained glass map to
their Java 1.12 numeric IDs before DataFixer; trapdoor open/top bits are swapped;
and legacy block-entity IDs are namespaced. Eight malformed floating-button
metadata values in map 1 are reoriented toward their single adjacent log
support. Five skull block entities missing from that source are synthesized as
skeleton skulls so the blocks survive Java validation.

The converter stops Mojang 1.21.4 and 26.1.2 immediately after optimizer
completion. This prevents legacy vines from receiving destructive neighbor
ticks and prevents current terrain/aquifer generation below the Nukkit Y=0
floor. It then requires exact pre/post chunk and non-air block counts, zero
embedded entities, valid Anvil sectors/palettes, and no blocks below Y=0.

## Results

| Archive | Source | Chunks | Blocks before / after | Preserved block entities | Removed entities | Chunk DataVersion |
| --- | --- | ---: | ---: | --- | ---: | --- |
| `legacy-0.zip` | `game-0` | 53 | 36,304 / 36,304 | 70 chests | 0 | 4790 |
| `legacy-1.zip` | `game-1` | 58 | 37,507 / 37,507 | 44 chests, 5 repaired skulls | 0 | 4790 |
| `legacy-2.zip` | `game-2` | 74 | 61,817 / 61,817 | 56 chests, 12 flower-pot states | 3 animals | 4790 |
| `legacy-4.zip` | `game-3` | 48 | 10,357 / 10,357 | 58 chests, 4 banners, 3 skulls | 0 | 4189 / 4790 |

The map 4 chunks left at DataVersion 4189 are valid 1.21.4 chunks inspected by
the 26.1.2 optimizer that required no rewrite; its level data is 4790 and the
archive is current-server compatible.

Archive SHA-256 values:

- `legacy-0.zip`: `0c92e1df661abdf1e8a582945862cd4de57bed7d27e5c6ad9a751a014385de40`
- `legacy-1.zip`: `cf229330eba2ddf2379ed6f38fdcede1e2bf4c47a2687ad9d028d13c3839c89c`
- `legacy-2.zip`: `e39a212dbf4138a0bc8a0842aabdd877ba5230849419f89605d31b40d3c7749a`
- `legacy-4.zip`: `9a017628a69d5c88fedc3a8934f876e6eb83edba77c80f152f311b791589fe0a`

Each archive has matching `*.remap.json` and `*.audit.json` evidence in this
directory.
