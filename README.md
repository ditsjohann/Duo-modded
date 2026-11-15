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

## The Heart Tree
Right-click the heart with an empty hand to see what it wants, or with items to offer them.
It only grows when **both of you** are near it.

| Stage | Needs | New islands |
|---|---|---|
| Sprout | 64 cobblestone, 32 logs, 16 wheat | Meadows: trees, animals, coal/iron/copper |
| Young Tree | 16 iron, 16 bread, 8 leather, 16 wool | Stone islands with ores, a desert island |
| Grown Tree | 48 copper, 16 gold, 32 redstone, 4 diamonds | Jungle, swamp and snowy islands |
| Old Tree | 32 obsidian, 8 ender pearls, 8 blaze rods | Dark shadow islands (ancient city loot) |
| Bloom | 4 diamond blocks, 8 eyes of ender, 4 golden apples | Cherry blossom islands + the ending |

### Adding mods to the Heart Tree
Stages are plain JSON in `data/<namespace>/heart_tree_stages/`. A datapack (or KubeJS) can override them
or add stage 6, 7, ... Requirements take item ids or tags (`"tag": "c:ingots/tin"`). Island rings can use
any mod's blocks, biomes and tree features. Missing mods are skipped, not crashed.

Advancements `tethered:heart_tree/stage_N`, `tethered:paired`, `tethered:bound_forever` etc. can be used
as FTB Quests tasks.

## Commands
- `/tether status`, `/hearttree info`
- Operators: `/tether pair <a> <b>`, `/tether cut <player>`, `/tether tier <player> <1-5>`,
  `/hearttree grow` (skip a stage), `/hearttree startisland` (build a starter island where you stand)

## Building
`./gradlew build` → `build/libs/tethered-0.1.0.jar`.
Every push is also built by GitHub Actions; download the jar from the run's **Artifacts**.

See [MODS.md](MODS.md) for the modpack list.
