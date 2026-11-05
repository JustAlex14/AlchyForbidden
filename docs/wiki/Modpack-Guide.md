# Modpack Guide

What a modpack maker can change in AlchyForbidden without code: altar rites, what each structure costs to grow, what the Weeping Fountain weeps, spells and carvings, and the structures' shapes. For the general methods (replacing a datapack file, removing a recipe, tags), see the [AlchyX Modpack Guide](https://github.com/JustAlex14/AlchyX/wiki/Modpack-Guide).

## Where each thing lives

| What | Where |
|---|---|
| Altar rites (54) | `data/alchyforbidden/altar_rite/<name>.json` |
| Crafting recipes (altar, guide, rift eye) | `data/alchyforbidden/recipe/<item>.json` |
| What each structure costs to grow | `config/alchyforbidden-common.toml`, `tier2Cost` and `tier3Cost` in each section |
| Weeping Fountain products | the same file, `weeping_fountain.products` |
| Sculk offerings | the same file, `essences.sculkOfferings` |
| Spells | the same file, `spells.<spell id>` |
| Carving upkeep | the same file, `inscription_upkeep.costs` |
| Structure shapes | `data/alchyforbidden/multiblock/<structure>_<tier>.json` |
| Floors the hand-built tier 1 accepts | the `alchyforbidden:altar_base` block tag |

Datapack files go in a datapack or in `kubejs/data/`. Run `/reload` after editing them.

## Altar rites

Most of the mod's items are made at the altar, not at a crafting table. A rite is one JSON file:

```json
{
  "ingredients": [ "minecraft:ender_eye", "minecraft:amethyst_shard", "minecraft:sculk" ],
  "result": { "id": "alchyforbidden:rift_eye" },
  "tier": 1,
  "ticks": 80,
  "sorrow": 20
}
```

| Field | Default | Meaning |
|---|---|---|
| `ingredients` | | 1 to 8 ingredients, one per stand, in any order. Each is an item id, a `#tag`, or any NeoForge ingredient |
| `result` | | the item made, with `count` and components if needed |
| `tier` | 1 | the altar tier the rite needs (1 to 3). A rite above the altar's tier shows as locked |
| `ticks` | 100 | how long it takes (20 = 1 second) |
| `wrath`, `sorrow`, `corruption` | 0 | the essence it costs, taken from the altar's reserve when the rite begins and given back if it breaks |

The altar has 4 stands at tiers 1 and 2, and 8 at tier 3. A rite with more than 4 ingredients needs tier 3 whatever its `tier` says, so give it `"tier": 3` so JEI shows the right tier. The altar holds 400, 1600 and 6400 of each essence at tiers 1, 2 and 3 (`altar.altarReserve`), so a rite can't cost more than that.

### Adding a rite

Any namespace works. `data/mypack/altar_rite/netherite_scrap.json`:

```json
{
  "ingredients": [ "minecraft:ancient_debris", "#c:ingots/gold", "minecraft:blaze_powder", "alchyforbidden:wrath_ember" ],
  "result": { "id": "minecraft:netherite_scrap", "count": 2 },
  "tier": 2,
  "ticks": 200,
  "wrath": 150
}
```

Rites can make items from any mod, and they can ask for rifted items with AlchyX's ingredient: `{ "neoforge:ingredient_type": "alchyx:rifted", "level": 2 }`.

### Changing or removing a rite

A file with the same path replaces the built-in one, for example `data/alchyforbidden/altar_rite/rift_eye.json`. To remove one, replace it with:

```json
{ "neoforge:conditions": [ { "type": "neoforge:false" } ] }
```

After `/reload`, the log line `AlchyForbidden: loaded N altar rite(s)` shows the count went down. Two rites with the same ingredients clash and only one of them is ever used, so give each rite its own set.

## Growing the structures

Each structure (altar, resonator, killing pit, lament well, weeping fountain, binding circle, rift maw, essence infuser, inscribing bier) has its own section in the config, with what its next tier asks for. One entry is `item id*count`:

```toml
[altar]
tier2Cost = ["minecraft:sculk*16", "minecraft:amethyst_shard*8", "minecraft:echo_shard*2", "alchyforbidden:corruption_spore*8"]
tier3Cost = [...]
```

These are the main gates of the mod's progression. Swap in items from other mods to tie it to the rest of the pack, or raise the counts to slow it down.

## Essences from items

`essences.sculkOfferings` is what the altar's pour slot turns into Sculk Corruption, as `item id=corruption per item`:

```toml
sculkOfferings = ["minecraft:sculk=2", "minecraft:sculk_vein=1", "minecraft:sculk_sensor=12", "minecraft:sculk_shrieker=20", "minecraft:sculk_catalyst=40", "somemod:void_dust=5"]
```

## The Weeping Fountain

`weeping_fountain.products` lists what it can weep, as `item id=essence*cost/seconds@tier`:

- `essence`: `wrath`, `sorrow` or `corruption`, paid from the nearest awake altar for each item
- `cost`: how much of it
- `seconds`: time per item at the fountain's first tier (`speed` makes higher tiers faster)
- `tier`: the fountain tier that unlocks it

```toml
products = ["minecraft:amethyst_shard=sorrow*8/8@1", "minecraft:ghast_tear=sorrow*60/30@2", "minecraft:wither_skeleton_skull=wrath*500/180@3", "minecraft:shulker_shell=corruption*300/120@3"]
```

The fountain is a good place to give a pack a slow, renewable source of something rare.

## Spells

Each of the 31 spells has a section `spells.<id>` (`rift_bolt`, `mist_step`, `bone_spear`, `black_hole`...):

| Setting | Meaning |
|---|---|
| `tier` | the Binding Circle tier that opens it, and the ring tier it needs (1 to 3) |
| `castCost` | `[wrath, sorrow, corruption]` per cast |
| `cooldownSeconds` | time before it can be cast again |
| `powerPercent` | its damage |
| `imbueEssence` | `[wrath, sorrow, corruption]` to imbue a ring with it |
| `imbueItems` | items to imbue a ring with it, as `item id*count` |

The `spells` section above them has pack-wide multipliers (`damagePercent`, `costPercent`, `cooldownPercent`, `imbueEssencePercent`) and what spells may hit (`targets`, `hitPlayers`). Change those first if the whole set feels too strong or too cheap.

## Carvings

The carvings and what they cost to carve are fixed in the mod. What they use while they work is in `inscription_upkeep.costs`, as `carving id=essence*amount` (a second's worth, or one use for `second_leap` and `second_breath`). A carving that is not listed costs nothing. The `inscribing_bier` section sets how many carvings a body can hold and how much stronger each makes the others.

## Structure shapes and tags

Every tier of every structure is an AlchyX pattern in `data/alchyforbidden/multiblock/` (`altar_1` to `altar_3`, `rift_maw_1` to `rift_maw_4`, and so on). A file with the same path replaces it; keep its `core` and `anchors`. The format is on [Pattern Format](https://github.com/JustAlex14/AlchyX/wiki/Pattern-Format).

The first tier of each structure is built by hand on a floor of `#alchyforbidden:altar_base` (polished deepslate, deepslate bricks and tiles by default). To accept other floors, add to the tag:

`data/alchyforbidden/tags/block/altar_base.json`

```json
{
  "replace": false,
  "values": [ "minecraft:polished_blackstone_bricks" ]
}
```

`#alchyforbidden:rift_eats` is what a growing structure may swallow around it (natural ground, sculk). Add a mod's natural stone or soil if a structure built on it does not grow.

## Shipping it

- Rites, recipes, shapes and tags are server data: ship them in KubeJS `data` or a global datapack. JEI shows the server's rites.
- Put the edited `config/alchyforbidden-common.toml` in the pack's `config` folder. It is sent to players when they join, so screens show the server's costs.
