# Duo-modded: Tethered

A duo mod for me and my gf. Minecraft **1.21.1**, **NeoForge**.

You both wake up on a tiny island in the sky, **tied together by a rope**, next to a glowing **Heart Tree** seed.
Feed the tree together and it grows, raising new rings of islands around it. At full bloom, the Weaver asks you one question.

## How to play
1. Install NeoForge 1.21.1 and put `tethered-0.1.0.jar` in `mods/` (you **both** need it).
2. Create a world, click **World Type** until it says **Tethered Sky**.
3. Open to LAN (or use a server). When the second player joins, you're tied together automatically.
4. Read *The Weaver's Note* in the starter chest.

## Controls (rebindable under Options → Controls → Tethered)
| Key | Action |
|---|---|
| Crouch | **Brace**: you hold firm, your partner swings/dangles |
| `R` (hold) | **Haul**: reel the rope in to pull your partner up |
| Crouch + `G` | **Launch**: throw your partner across a gap |
| `H` | **Ender Call**: teleport to your partner (needs Ender Thread) |
| `U` | **Unhook**: loosen the rope (Golden Thread only) |
| `J` | **Weaver's Journal**: map, quests, tree |

## The rope
| Tier | Length | How to get it |
|---|---|---|
| Red Twine | 8 | Start (or a **Red Thread** on another player) |
| Leather | 12 | Leather Tether (leather + string) |
| Chain | 16 | Chain Tether (8 chains + Leather Tether) |
| Ender Thread | 24 | Ender pearls + gold + Chain Tether. Unlocks Ender Call |
| Golden Thread | 32 | Choose to **keep** the thread at the end. Unlocks Unhook |

- If one of you falls, the rope catches you (no fall damage while it's holding you).
- If one of you would die, you go **down** instead. Your partner crouches next to you for 3 s to help you up.
- The void still kills. If you both fall, you both fall.
- Go through a portal and your partner gets pulled through with you.

## The world map
You start on **Heartrest**, a small island with the Heart Tree. Seven more islands float far away:

| # | Island | Distance | Theme | Quests use |
|---|---|---|---|---|
| 1 | Hearthmeadow | ~200 | meadows, animals | Farmer's Delight, Vinery |
| 2 | Ironreach | ~360 | ore-rich rock | Create, Ex Deorum |
| 3 | Windward Docks | ~540 | high & windy | Paragliders, Immersive Aircraft, the Aether |
| 4 | Arcanum | ~720 | birch & old stone | Ars Nouveau, Occultism, enchanting |
| 5 | Frostfall | ~920 | snow & ice | Ice and Fire (dragons) |
| 6 | Gloomhollow | ~1160 | dark forest | the Nether, Wither, Mowzie's Mobs, Cataclysm |
| 7 | Bloomreach | ~1500 | cherry blossoms | the Weaver's question (ending) |

Islands are built when you get close, so the far ones really are undiscovered.

**The loop:** grow the Heart Tree → its roots grow a **Rootway** bridge to the next island → do that island's
quest chapter together → get a **Heart Shard** → bring it home → the tree grows → next island.

**Anchor Stones:** each island has one in the middle. Right-click it *together* to attune it.

## The Heart Tree
Right-click it to see what it wants. It only grows when you're **both** next to it.
Every stage unlocks a **blessing** for the whole world:

| Stage | Name | Blessing |
|---|---|---|
| 1 | Sprout | **Green Thumb**: crops near the tree grow faster |
| 2 | Sapling | **Together**: regeneration while you're within 12 blocks of each other |
| 3 | Young Tree | **Heart Paths**: fast travel between the tree and attuned anchors (journal map) |
| 4 | Grown Tree | **Deep Roots**: rope +4 blocks |
| 5 | Elder Tree | **Shared Table**: when one eats, the other is fed a little |
| 6 | Ancient Tree | **Heartward**: no monsters spawn near the tree |
| 7 | Bloom | the last root reaches Bloomreach |

Rope upgrades need the tree too: Leather at stage 2, Chain at 3, Ender at 4.

## The Weaver's Journal (press J)
- **Map**: every island, the Rootways, you (white) and your partner (pink). Click an attuned island to travel.
- **Quests**: a chapter per island, tasks, rewards and a *Hand in items* button.
- **Heart Tree**: stage and blessings.
The HUD also shows an arrow to the next island.

Quests for mods you don't have are hidden automatically, so the story always works.

## Adding to it
Everything is datapack JSON, so it's easy to extend without code:
- `data/tethered/tethered_islands/*.json`: islands (position, size, blocks, ores, biome, trees, mobs, loot)
- `data/tethered/tethered_quests/*.json`: quest chapters (task types: item, kill, advancement, visit, attune,
  tree_stage, together; rewards: item, shard, xp, unlock, ending)
- `data/tethered/heart_tree_stages/*.json`: what each stage needs and which island it unlocks

## Commands
- `/tether status`, `/tether travel <island|tree>`, `/hearttree info`
- Operators: `/tether pair <a> <b>`, `/tether cut <player>`, `/tether tier <player> <1-5>`,
  `/hearttree grow` (skip a stage), `/hearttree unlockall`, `/hearttree startisland` (build a starter island where you stand)

## Building
`./gradlew build` → `build/libs/tethered-0.1.0.jar`.
Every push is also built by GitHub Actions; download the jar from the run's **Artifacts**.

See [MODS.md](MODS.md) for the modpack list.
