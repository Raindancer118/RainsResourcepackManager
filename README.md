# Rain's Resourcepack Manager (`rrp`)

Install, remove, require and combine resource packs on a PaperMC server — from a command or from
a full inventory GUI — install the datapack half that belongs to them, and hand out the items
they add.

```
/rrp                     the GUI
/rrp catalog             what the pack host offers
/rrp install yeukpack    resource pack + datapack, applied to everyone online
/rrp required yeukpack true
/rrp mode merged         one combined pack instead of several prompts
/rrp give wisdom_book Raindancer118 3
```

## What it does

| | |
|---|---|
| **Catalogue** | Reads `index.json` from the pack host (default `https://mc-packs.raindancer118.de/`). Nothing about any pack is hardcoded — new packs and new items appear after a refresh. Cached locally, so a server without internet keeps working. |
| **Install / uninstall** | Downloads the pack, verifies the published sha1, keeps a local copy, and installs the datapack half into the world. Uninstalling removes exactly what RRP created and nothing else. |
| **Required** | Per pack. Required packs go out in one request that the client must accept; optional ones follow in a second one. |
| **Order** | Packs are applied in order; later packs win when files overlap. `/rrp move <pack> up\|down`. |
| **Combining** | Several packs are merged into one zip and served over RRP's own HTTP server, so players see one prompt instead of one per pack. Falls back to stacked whenever combining is impossible — and says why. |
| **Items** | `/rrp give <item> [player\|all] [amount]` with tab completion, built from the catalogue's item definitions through the server's own item parser. |
| **GUI** | Everything above, as menus: `/rrp gui`. |

## Every command has a GUI equivalent

Both front ends are thin layers over one class, `RrpService` — that property holds by
construction, not by discipline.

| Command | GUI |
|---|---|
| `/rrp list`, `enable`, `disable`, `required`, `move`, `uninstall`, `datapack …` | *Installed packs* → a pack |
| `/rrp catalog`, `install`, `install <url> <id>` | *Catalogue* |
| `/rrp give` | *Items* (real item previews, amount selector, player picker) |
| `/rrp mode`, `set …`, `reload` | *Settings* |
| `/rrp merge`, `apply` | buttons on the main screen |

## Stack

| Thing | Choice | Why |
|---|---|---|
| Platform | `paper-api` 1.21.11 | Compiled for Java 21 so the plugin loads on 1.21.11 **and** 26.x servers — the same range the packs support |
| Descriptor | `paper-plugin.yml` | Modern loader; `plugin.yml` is legacy |
| Commands | Brigadier via `LifecycleEvents.COMMANDS` | `onCommand` + `commands:` is the old way |
| Text | Adventure + MiniMessage | No `§` codes anywhere |
| Packs to clients | `ResourcePackRequest` (Adventure) | Multi-pack support, per-request `required`, stable pack UUIDs |
| HTTP | `com.sun.net.httpserver` | Serves the combined pack without a shaded web server |
| Dependencies | **none shaded** | Paper already ships gson, snakeyaml, adventure, brigadier |
| Folia | not supported | RRP touches world folders and a shared HTTP server; claiming support without an audit would be a guess |

## Two things worth knowing

**Datapack registries need a restart.** `/minecraft:reload` rebuilds functions, tags, loot and
advancements — but *not* datapack registries. A pack that adds an enchantment or a jukebox song
is only half live until the server restarts, and RRP says so instead of pretending otherwise.

**The datapacks folder is `<world>/datapacks`, not `World#getWorldFolder()`.** On modern Paper
that accessor points at `world/dimensions/minecraft/overworld`, where a datapack is silently
ignored. RRP builds the path from the world container plus the level name.

## Configuration

`plugins/RRP/config.yml`, and every key that matters is also reachable through
`/rrp set <key> <value>` and the settings screen. Combining needs `http.enabled: true` plus an
`http.public-url` clients can actually reach (or an external web server pointed at
`plugins/RRP/merged/`).

## Building

```
JAVA_HOME=/usr/lib/jvm/java-25-temurin ./gradlew build   # → build/libs/rrp-1.0.0.jar
```

## Verified

Against a real Paper 26.2-87 server, not just compiled: install of `yeukpack` from the live
catalogue (2.5 MB, sha1 matched), the datapack landing in `world/datapacks` and being listed as
enabled, all three catalogue items building through the server's item parser, a second pack
installed from a URL, combining both into one zip whose served sha1 matches the announced one,
the HTTP server refusing traversal and unknown paths, `required` / `move` / `disable` /
`datapack install|remove` / `uninstall` round trips, state surviving a restart, and all seven GUI
screens rendering without an exception (checked at every startup).

Not verified: how the menus *look* to a player and whether a client accepts the packs — that
needs a real client.
