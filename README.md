# Duo-modded: Tethered

A two-player story modpack for Minecraft **1.21.1** (**NeoForge**). Think StoneBlock + One Block + SkyFactory, with a fantasy story, boss Trials, night events and RPG Boons.

## The story
The Weaver wove the world on her Loom. The Unraveler pulled it apart, thread by thread, until only the Void was left.
One knot held: the **Heartstone**, deep inside the **Last Stone**, a colossal boulder floating in the void.
With her last strength the Weaver tied two souls together with a Red Thread and wove them in beside it:
*"Remember the world for me."*

## How it plays
1. **You wake inside the stone** (the Hollow Heart chamber) next to the Heartstone. There's no way out but to dig.
2. **Break the block on top of the Heartstone.** It immediately weaves a new one: logs, dirt, ore and, later, modded blocks, mobs and cache chests.
   Every break is a *memory*.
3. **Mine the Last Stone** (StoneBlock-style). Richer ore lies deeper: coal and copper in the crust; iron, gold, redstone and lapis in the mantle; diamonds in the core.
   **Thread Ore** gives Lost Threads, and hidden **Remnants** spill loot. The caves are dark and monsters live there.
4. **Wake the next Age.** When the Heartstone has enough memories it asks for an **offering**. Make it, then both of you face its **Trial**, a boss.
   Win, and a whole Age of the world comes back: new blocks, mobs, loot, a blessing, a Weaver's Favor each and a new story chapter.
5. **Climb to the summit.** The Weaver's World Tree grows there with every Age. Its roots reach out to the **Lost Shards**, faraway islands with their own quests.
   Each one gives the **Heart Shard** that the next offering needs.
6. **Survive the nights.** The Unraveler sends events your way (see below).
7. **Forge the Loom Heart** (it takes a Wither's star) and face **the Unraveler** himself.

## The ten Ages
| # | Age | Brings back | Trial |
|---|---|---|---|
| 0 | Age of Silence | stone, wood, coal, copper | – |
| 1 | Age of Roots | soil, every tree, farm animals, crops (Farmer's Delight, Botany Pots, BoP, BWG) | Stoneborn |
| 2 | Age of the Hunt | beasts and monsters (Naturalist, Born in Chaos, Mowzie's), weapons | Thornmother |
| 3 | Age of Iron | ores, Create, AE2, Mystical Agriculture, storage, Waystones | The Wroughtnaut |
| 4 | Age of Arcana | Ars Nouveau, Iron's Spells, Occultism, enchanting, brewing | The Hollow Magus |
| 5 | Age of Frost and Flame | the Nether, ice, Ice and Fire | Cinderwing |
| 6 | Age of the Deep | sculk, the Undergarden, Deeper and Darker, diamonds | Gravemaw |
| 7 | Age of the Heavens | the Aether, Eternal Starlight, gliders and aircraft | Skyrender |
| 8 | Age of the Weaver | endgame loot, the Loom | Herald of Unmaking |
| 9 | The Rewoven World | everything (post-game) | **The Unraveler** |

## World events
At nightfall (sometimes by day) one of these can happen. A boss bar shows the progress, and winning gives Lost Threads, loot and sometimes a Weaver's Favor.
- **Hollow Tide**: waves of glowing Hollow monsters climb toward you. They get tougher with every Age.
- **Threadfall**: threads and loot fall around you for a minute.
- **The Wandering Weaver**: a trader who takes Lost Threads (saplings, lava, ender pearls, Favors and more).
- **The Crimson Weave**: monsters get stronger, and they drop threads.
- **Heart Tremor**: a champion claws its way out of the Heartstone.
- **Unraveling Storm**: lightning hunts anyone outside and leaves threads behind.

## Boons (RPG)
Every **Weaver's Favor** (from each new Age, quests and events, or crafted from 8 Lost Threads + a diamond) lets you pick **one of three Boons**.
Each Boon has a few ranks:
- **Stat Boons**: Stoneheart, Thread-Sharp, Quarrier, Swiftstep, Long Reach, Ironhide, Fortune's Thread, Featherfall, Deep Lungs, Steadfast, Long Stride.
- **Special Boons**: Twin Strike (stronger near your partner), Lifeline, Heart's Echo, Bloodthread, Nightsight, Hearty, Scholar, Hollowbane, Threadfinder.

## The two of you
- If one of you would die, you go **down** instead. Your partner crouches next to you for 3 s to revive you.
- **Thread Call** (`H`, from the Age of Iron): teleport to your partner.
- **Blessings** from the Ages: Green Thumb, Together (regeneration near each other), Heart Paths (fast travel), Deep Roots, Shared Table, Heartward and Bloom.
- The old physical rope still exists: set `ropePhysics = true` in `config/tethered-common.toml`.

## Controls
| Key | Action |
|---|---|
| `J` | Weaver's Journal: Heartstone, quests, map and story |
| `H` | Thread Call |
| Crouch near a downed partner | Revive |
| Right-click the Heartstone | Offering / status / start the Trial (empty hand) |
| Rope mode only | `R` haul, crouch + `G` launch, `U` unhook |

## Start a world
Create a world and click **World Type** until it says **Tethered: The Last Stone**. Open it to LAN (or use a server).
The second player is bonded automatically.

## Adding to it
Everything is datapack JSON:
- `data/tethered/tethered_ages/*.json`: each Age (block/mob/loot pools, offering, Trial boss, story)
- `data/tethered/tethered_events/*.json`: world events (mobs per Age, rewards, trades)
- `data/tethered/tethered_quests/*.json`: quest chapters (task types: item, kill, advancement, visit, attune, age, heart, event, dimension, boons, together)
- `data/tethered/tethered_islands/*.json`: the Lost Shards

Anything from a mod that isn't installed is skipped automatically.

## Commands
- `/heart info`, `/tether status`, `/tether travel <heart|tree|island>`
- Operators: `/heart advance`, `/heart breaks <n>`, `/heart tp`, `/tether event start <id>`, `/tether event stop`, `/tether favor <player> [n]`

## Building
`./gradlew build` → `build/libs/tethered-0.1.0.jar`. GitHub Actions builds every push, makes the `.mrpack`, and plays a full-pack server through every Age and event.

See [MODS.md](MODS.md) for the modpack list.
