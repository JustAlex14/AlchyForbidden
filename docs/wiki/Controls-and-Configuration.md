# Controls and Configuration

## Keys

Set in the game's Controls menu, under AlchyForbidden.

| Key | Does |
|---|---|
| Cast spell | casts the selected ring's spell |
| Next spell / Previous spell | selects another ring |
| Spell slots (K) | opens the screen where you order your rings |
| Call / send back the familiar | calls the selected familiar, or sends it back |
| Next familiar / Previous familiar | selects another familiar ring |

## Common config

`config/alchyforbidden-common.toml` sets every cost, rate and limit. On a server it is sent to the players, so screens and tooltips show the server's numbers.

| Section | What it sets |
|---|---|
| `altar` | what each tier of each structure asks for, growth speed |
| `noise` | how loud growth is, and what muffles it |
| `eye` | how far the Rift Eye feels an altar and looks for an ancient city |
| `essences` | how much each source of Wrath, Sorrow and Corruption gives, vessel sizes |
| `resonator`, `killing_pit`, `lament_well` | each source's rates and reach |
| `obelisk` | link reach and branches |
| `weeping_fountain` | what it weeps at each tier, and how fast |
| `inscribing_bier`, `inscription_upkeep` | carving costs and what carvings use while they work |
| `binding_circle`, `spells` | every spell's tier, costs, cooldown and power |
| `rift_maw` | summon costs, odds of altered creatures, arena sizes |
| `essence_infuser` | its rates |
| `mob_stacks` | the Rift Knot's stacks |

Each setting has a comment in the file that explains it.

## Datapacks

Every structure tier is an AlchyX pattern in `data/alchyforbidden/multiblock/` (`altar_1` to `altar_3`, `rift_maw_1` to `rift_maw_4`, and so on), so a datapack can change their shapes. See [Pattern Format](https://github.com/JustAlex14/AlchyX/wiki/Pattern-Format).
