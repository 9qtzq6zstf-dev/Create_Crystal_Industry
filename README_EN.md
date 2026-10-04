# Create: Crystal Industry

**机械动力：晶簇工业** · Minecraft 1.21.1 · NeoForge · Create 6.0.10+

**English | [简体中文](README.md)**

Vanilla's amethyst budding mechanic, generalized to the rest of the ore table. A budding block grows buds and clusters from its six faces, is never consumed, and keeps growing after each harvest. Ore budding blocks also convert the stone around them into the matching ore, so a single budding block amounts to a vein that expands on its own.

---

## 1. Budding Blocks

A budding block rolls for growth independently on each of its six faces. An empty face grows a Small Bud; an existing bud advances through Medium Bud, Large Bud and finally Cluster. Rolls are driven by random ticks, and a successful roll advances one stage. The budding block itself is not consumed, so it keeps growing after each harvest.

### Budding Blocks at a Glance

"Infection" follows a simple rule: each random tick the budding block rolls a 1-in-20 chance, and on success it picks one random position within a radius of 1 (a 3×3×3 volume), converting it to the matching ore if that position holds Stone or Deepslate. Output therefore depends on how much of that volume is Stone or Deepslate, so burying the budding block in stone raises the hit rate. Budding Echo uses a different radius and chance, noted in the table.

| Budding Block | Extra Growth Condition | Cluster Output | Infection |
| --- | --- | --- | --- |
| Rose Quartz | — | Create's Rose Quartz | — |
| Raw Iron | — | Raw Iron | Iron Ore / Deepslate Iron Ore |
| Raw Gold | — | Raw Gold | Gold Ore / Deepslate Gold Ore |
| Raw Copper | — | Raw Copper | Copper Ore / Deepslate Copper Ore |
| Raw Zinc | — | Create's Raw Zinc | Zinc Ore / Deepslate Zinc Ore |
| Diamond | — | Diamond | Diamond Ore / Deepslate Diamond Ore |
| Emerald | — | Emerald | Emerald Ore / Deepslate Emerald Ore |
| Lapis Lazuli | — | Lapis Lazuli ×4–9 | Lapis Lazuli Ore / Deepslate Lapis Lazuli Ore |
| Redstone | — | Redstone | Redstone Ore / Deepslate Redstone Ore |
| Quartz | Full speed only in the Nether; elsewhere a successful roll has a further 1-in-2 chance to fail | Nether Quartz | Netherrack → Nether Quartz Ore |
| Ancient Debris | Full speed only in the Nether, as above, and every growth costs 250 mB of lava (the block is its own 1 B lava tank, see below) | Netherite Scrap ×1–3 | — (reproduction only, see below) |
| Arclight | Every growth costs 10,000 FE (the block holds 1,000,000 FE internally, receive-only, see below) | Arclight | — |
| Glowstone | Full speed only in the Nether, as above | Glowstone Dust | — |
| Echo | Growth space must be at light level 0 | Echo Shard | Any of 12 blocks within a radius of 2 (dirt, sand, stone, tuff, …) → Sculk, 1-in-4 |
| Flammable Ice | Target space must be a water source block | Flammable Ice | — |
| Fluix *(requires AE2)* | Must be on a powered, active ME Grid (uses 1 channel); each growth consumes 200 AE | Fluix Crystal | — |

Drop rules:

| Block | Normal Break | Silk Touch |
| --- | --- | --- |
| Cluster | The item in the "Cluster Output" column above, with Fortune applied (**except the Arclight Cluster**, see below) | The cluster block itself |
| Bud | Nothing | The bud block itself |
| Budding Block | The block one tier below it (Budding Raw Iron drops a Block of Raw Iron, Budding Diamond drops a Block of Diamond; Budding Ancient Debris and Budding Arclight have no tier below them and drop 1 of their own item) | The same; Silk Touch does not change this |

The budding block itself cannot be collected with ordinary tools. The Smart Drill's Silk Touch mode is the only way to obtain it, described in section 3.

Budding Redstone, its buds and its cluster all emit a redstone signal (15 from the budding block and the cluster, 3 / 7 / 11 from the three bud stages), so they work directly as a redstone source. Budding Glowstone and its stages emit light at the same levels. Budding Echo's buds and cluster deliberately emit no light at all: any emission would occupy the very growth space they need.

### The Lava Tank of Budding Ancient Debris

Budding Ancient Debris is the only built-in budding block that burns lava. The block itself is a fluid container holding 1 B, and lava is all it accepts. Each successful growth costs 250 mB, and with less than that in the tank nothing grows, which is why the ones generated in the Nether start empty. 1 B is four growths.

- **Getting lava in and out**: pipes (via the NeoForge fluid capability) or right-clicking with a bucket. One bucket is exactly the tank's capacity, so buckets only work on a full or an empty tank and the leftovers have to be topped up by pipe.
- **Nothing spills when it will not fit**: with the wrong fluid, or with nothing to pour or scoop, the block swallows the click and the bucket's contents are not dumped next to the budding block. To deliberately pour fluid beside it, sneak and right-click.
- **Reading the level**: Engineer's Goggles show the level and the per-growth cost; a comparator emits a 1–15 signal proportional to it.

Scripted budding blocks use the very same tank, see "Fluid Consumption" and "Modifying an Existing Budding Block" in section 6.

The lava travels with the block but not with the item. Breaking the block loses it, and schematics always paste an empty tank.

### Arclight and Current Slurry

Budding Arclight is the only budding block that runs on FE. It holds 1,000,000 FE internally and spends 10,000 FE per successful growth, and with less than that stored nothing grows. It is receive-only, a converter that turns power into matter rather than a battery. Use the Crystal Battery if you want to store FE.

- **Powering it**: any FE source works, on all six faces, up to 10,000 FE per tick.
- **Output**: the cluster drops 1 Arclight and Fortune does not apply. Arclight is an item only, it has no block form.
- **Pressing**: put one Arclight in a Basin under a Mechanical Press, superheated, to get 500 mB of Current Slurry.
- **Current Slurry**: the mod's first fluid, shipped with a Current Slurry Bucket that can fill, pour and scoop. A poured pool has a light level of 15, so it doubles as a light source. The fluid block itself is not meant to be mined, its reason to exist is that a bucket needs something to pour. How to turn the slurry back into FE is not decided yet.
- **Shock**: anything living standing in Current Slurry keeps taking shock damage, 20 per hit, ignoring armour. It works on contact, like lava, and does not require being fully submerged.
- **Sparks**: sparks fly up off the surface of poured slurry, and arclight blocks emit the occasional spark above their top face.
- **Quenching**: with soul soil below the lava and a Flammable Ice Block beside it, that lava cell turns into deepslate. It is vanilla's basalt recipe with a single block swapped:

  | Vanilla basalt | This mod |
  | --- | --- |
  | Soul soil **below** the lava | **Still soul soil** below the lava |
  | Blue ice **beside** the lava | A **Flammable Ice Block** beside it |
  | Lava → basalt | Lava → **deepslate** |

  It sits after vanilla's "lava + water → obsidian / cobblestone" entry, so if water touches the lava at all that earlier rule wins. This is a global rule, it applies to any such combination in the world, not just to anything this mod places.
- **Electrified**: a living entity within 3 blocks of an arclight block or of Current Slurry gains the "Electrified" effect, which fades about 3 seconds after leaving. Only slurry actually poured into the world counts, what sits in tanks and pipes does not.
- **Discharge**: when an electrified entity takes damage it discharges, taking another 4 points of lightning damage and arcing to the closest electrified creature within 5 blocks, with a line of sparks drawn between the two. The creature it reaches discharges in turn, so the chain travels down a row, and each one it reaches takes two hits for 8 points. The chain only travels one way, no creature is hit twice, it is capped at 16 creatures and fires at most once per second. Those 4 points ignore armour and bypass invulnerability frames.
- **Death messages**: "%1$s was electrocuted" with no kill credit, "%1$s was struck by lightning caused by %2$s" when the victim still carries kill credit.
- **Shock immunity**: the item tag `create_crystal_industry:shock_immune`. With all four armour slots holding tagged items you are immune to the slurry's shock, to the discharge, and to vanilla's real lightning bolts. The tag lists the four chainmail pieces, the four netherite pieces and Create's three netherite diving items. The check only looks at slot and tag, the four pieces do not have to match.
- **Inactive Budding Arclight**: only a lightning strike turns it into the real thing. Either a bolt lands on it directly, or the block directly above it is a lightning rod and that rod gets struck. The budding block it turns into comes out with a full FE buffer and immediately sprouts a few buds. On its own it is pure decoration: no FE, no growth, no sparks, no block entity.

Budding Arclight currently neither generates naturally nor has a crafting recipe, it is creative-only for now.

### Budding Block Reproduction (Infection)

Beyond converting ore, an ore budding block has a 1-in-25000 chance to infect an adjacent block of the matching ore block, turning it into another budding block. Budding Quartz infects Smooth Quartz Blocks instead, and Budding Fluix infects Fluix Blocks.

Budding Ancient Debris is the one family with no Stone → Ore conversion at all. It only infects an adjacent block of Ancient Debris to make another budding block, and that conversion costs 250 mB of lava too, so getting a second one means hauling Ancient Debris over to sit next to it.

| Budding Block | Block That Can Be Converted Into It |
| --- | --- |
| Raw Iron / Raw Gold / Raw Copper / Raw Zinc | The matching block of raw ore |
| Diamond / Emerald / Lapis Lazuli | The matching block of the ore |
| Redstone | Block of Redstone |
| Quartz | Smooth Quartz Block |
| Ancient Debris | Block of Ancient Debris (itself) |
| Fluix | Fluix Block (requires power) |

This one **can be turned off**: two options in `config/create_crystal_industry-common.toml`.

| Option | Default | Effect |
| --- | --- | --- |
| `infection.buddingInfection` | `true` | Whether budding blocks infect their neighbours. This is the **master switch**: stone growing into ore, a raw metal block turning into a new budding block, the Echo block spreading sculk all count. Turning it off stops budding blocks from converting anything at all |
| `infection.infectingBudding` | empty | Which budding blocks infect their neighbours. Empty = all of them may; a non-empty list means **only** those may, and the rest stop converting anything. Write **family ids** (`raw_iron`, `quartz` — the same id as `worldgen.generate_<id>`) or **block ids** (`kubejs:my_crystal_budding`, which scripted budding blocks need) |

A server owner who wants no conversion at all (**including stone into ore**) sets the first to `false`; one who wants to keep just a couple of budding blocks working sets the second. Both apply to scripted budding blocks too — the test is the block's family id or block id (see `.transform` in section 6).

#### Infection Recipes (datapack-writable)

Stone → ore, a raw metal block → a new budding block, Echo → sculk... **there is no difference between them any more: they are all the same recipe**, `create_crystal_industry:budding_conversion`. The 21 built-in ones are generated from the family table (see "Data Generation" in section 7); a pack overrides one by shipping a JSON with the same id, and KubeJS `.transform(...)` writes the very same thing.

**One recipe = one rule**, not one A → B pair:

```json
{
  "type": "create_crystal_industry:budding_conversion",
  "budding": "create_crystal_industry:raw_iron_budding",
  "chance": 20,
  "radius": 1,
  "replacements": [
    { "input": "minecraft:stone", "output": "minecraft:iron_ore" },
    { "input": "minecraft:deepslate", "output": "minecraft:deepslate_iron_ore" }
  ]
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `budding` | yes | Which budding block does it. It **must be a budding block this mod drives** (a built-in family, or one registered by a script through `CustomBudding`) — name anything else (Obsidian, say) and the recipe still loads but never fires, and JEI will not show it |
| `chance` | yes | Chance base n: a 1-in-n roll each random tick (≥ 1) |
| `radius` | yes | Pick radius r: on a hit, one block is taken at random from the (2r+1)³ cube around the budding block, minus its own cell (0–8) |
| `gated` | no | The hit must also pass an energy check (defaults to `false`) — Ancient Debris' lava and Fluix's AE |
| `replacements` | yes | The swap table, **first match wins**; each entry has `input` (what to replace) and `output` (what to put there) |

Semantics worth knowing:

- **One roll covers one recipe**: in the JSON above, Stone and Deepslate share the same 1-in-20 roll and the same cell pick — splitting them into two recipes would double the ore output. JEI splits the two replacements into two pages for display, which is only presentation.
- `input` accepts a single block id, a list of block ids, or a `"#block_tag"` (e.g. `"input": ["minecraft:stone", "minecraft:deepslate"]`, `"input": "#c:stones"`).
- An **omitted** `output` means "turn it into this budding block itself" (that is how regeneration is written), so the recipe never has to reference its own budding block.
- The radius is not a throughput knob: only one cell is picked per random tick, so a larger radius reaches further but each cell is proportionally less likely. To make something convert faster, change `chance`.

Editing and removing:

- **Edit**: ship a JSON with the same id, e.g. `data/create_crystal_industry/recipe/budding_conversion/raw_iron_budding/stone_to_iron_ore.json`.
- **Add**: just drop in a new JSON; the engine claims it by its `budding` field.
- **Remove**: a datapack cannot remove a recipe that ships inside the jar — use KubeJS (`ServerEvents.recipes(e => e.remove(...))`), or stop that budding block with the config options above.

### Growth Speed

The chance lives in the family table (each family's own `Growth.speed` in `BuddingFamilies`), there is no config key for it:

| Tier | Chance to Advance per Random Tick | Which budding blocks |
| --- | --- | --- |
| Fast | 1/1, a successful roll always advances | Redstone, Lapis Lazuli |
| Normal | 1/5, the same as vanilla Budding Amethyst | everything else |
| Slow | 1/20 | Ancient Debris (its cluster drops Netherite Scrap, so it is treated as a scarce resource) |
| Very Slow | 1/50 | no member by default (it is there for scripts and addons) |

To retune one specific budding block, use KubeJS `CustomBudding.modify(...)` (section 6) instead of editing and recompiling the source. To retune a whole family, edit the family table.

---

## 2. Accelerators

A vanilla budding block sees a random tick about once every 68 seconds on average, which makes natural growth effectively unobservable. An accelerator applies one random tick to each of the six adjacent blocks that can receive one, compressing that process into seconds.

| | Accelerator | Mechanical Accelerator |
| --- | --- | --- |
| Power source | FE | Rotational force on the back face |
| Cost per pass | 100 FE | No energy |
| Trigger rate | Once every `acceleratorIntervalTicks` ticks (default 1, i.e. every tick) | Same |
| Strength | Fixed; every pass applies | Scales linearly with RPM, capped at 256 RPM |
| Effect at full speed | 6 random ticks per tick | Identical to the Accelerator |
| Internal buffer | 10,000 FE, max input 100 FE/t | — |

- Adjacent Accelerators balance energy among themselves.
- A Mechanical Accelerator below 256 RPM scales its effect with RPM; above 256 RPM it gains nothing further.
- The `acceleratorIntervalTicks` config key applies to both types, and a larger value means slower growth. The Accelerator settles its FE cost per pass, so slowing it down also reduces its power draw.
- Only neighbours that can receive a random tick are ticked, using exactly vanilla's check. Air, stone and the like are skipped on the spot, so the Goggles multiplier is a ceiling. A modded block that forgot `.randomTicks()` is not accelerated.

Random ticks drive far more than clusters. Crops, saplings, copper oxidation and every other random-tick-driven mechanic are accelerated along with them, which makes an accelerator a general-purpose tick accelerator as well.

Wearing Engineer's Goggles and looking at a vanilla Budding Amethyst shows the same growth speed and multiplier line. The vanilla block itself is untouched, nothing is added to your save data.

---

## 3. Machines and Equipment

| Item | Description |
| --- | --- |
| **Smart Drill** | A mechanical drill with two modes, switchable from the value slot on its side. Normal mode breaks blocks at twice the speed of an ordinary Mechanical Drill. Silk Touch mode has the same speed but drops blocks intact, and it is the only mode that collects a budding block itself. A filter item can restrict which blocks it breaks. |
| **Mechanical Cleaner** | Has every airflow function of the Encased Fan, and additionally pulls items caught in the airflow into its own 27-slot inventory. Without rotational power it still passively collects items directly in front of it. If a container sits directly in front, it exchanges items with it directly, never dropping anything into the world. Waterlogged while blowing, it washes items. |
| **Resonance Table** | Put an item on top and any machine holding a Resonance Filter filters by that item. The sliders on its four sides set how many items the top may hold. While powered by redstone it switches to a powered appearance and freezes the current filter. |
| **Resonance Filter** | Goes into the filter slot of a Funnel, Chute or Basin. It stores no rules of its own, it reads them live from the Resonance Tables in its network and takes the union. A List Filter or Attribute Filter on a table has its contents mirrored along with it. |
| **Night Vision Goggles** | Engineer's Goggles modified with an Echo Shard. While worn, a keybind toggles night vision (default `N`), and the wearer is also immune to the Darkness effect. Wears in the vanilla helmet slot or a Curios head slot. |
| **Echo Spyglass** | While in use, your line of sight passes through blocks and renders them as outlines, for up to 60 seconds per use. Sneak-right-click opens a filter screen that accepts an item or a Create List Filter. With no filter it matches `#c:ores`. |
| **Flammable Ice** | Flammable Ice (400 ticks) and Flammable Ice Blocks (4000 ticks) are both fuels, and both are listed among the superheated fuels for Create's Blaze Burner. Nine Flammable Ice craft into one Flammable Ice Block. |

Resonance Tables and Filters form a network. Sneak-right-click an existing table while holding a table to make the held one remember that network, and place it elsewhere to add the new table to the same network. Sneak-right-click a table while holding a Resonance Filter to join that network, and sneak-right-click in the air to leave. What a network filters can be read out with a Display Link.

---

## 4. World Generation

Each budding block generates at the depth of its corresponding ore, usually embedded in a vein.

| Budding Block | Dimension | Y Range | Placement |
| --- | --- | --- | --- |
| Diamond | Overworld | −64 – 16 | Vein, rarity 1/16 |
| Emerald | Overworld | −16 – 320 | Vein, rarity 1/16 |
| Raw Iron | Overworld | −24 – 56 | Vein, rarity 1/16 |
| Raw Gold | Overworld | −64 – 32 | Vein, rarity 1/16 |
| Raw Copper | Overworld | −16 – 112 | Vein, rarity 1/16 |
| Raw Zinc | Overworld | −63 – 70 | Vein, rarity 1/16 |
| Lapis Lazuli | Overworld | −64 – 64 | Vein, rarity 1/16 |
| Redstone | Overworld | −63 – 15 | Vein, rarity 1/16 |
| Quartz | Nether | 10 above bedrock to 10 below the top | Vein, rarity 1/8 |
| Ancient Debris | Nether | 8 – 22 | Vein, rarity 1/16, with 2 Ancient Debris next to the budding block; the tank generates empty |
| Glowstone | Nether | At natural glowstone blobs | Replaces the lowest block of a glowstone blob, at a chance set by `worldgen.glowstoneBuddingChance` (default 0.1); `worldgen.glowstoneGenerateBuds` and related keys control the buds that come with it |
| Echo | Overworld | −64 – 0 | Deep Dark, generated inside Sculk |
| Flammable Ice | Overworld | Below the deep-ocean seafloor | Structure, 1-in-256 per chunk (`worldgen.flammableIceChance`), with soul sand scattered around it (`worldgen.soulSandGenerate`) |
| Rose Quartz | — | — | Does not generate naturally |
| Arclight | — | — | Does not generate naturally (how to obtain it is undecided; creative-only for now) |
| Fluix | — | — | Does not generate naturally |

Each one can be toggled individually in the config file (`worldgen.generate_<budding id>`). The Flammable Ice structure and Budding Glowstone have their own additional chance settings.

The config file groups its options into tables: `[worldgen]` world generation, `[infection]` budding infection, `[accelerator]` accelerators, `[spyglass]` Echo Spyglass, `[crystalBattery]` Crystal Battery and `[cleaner]` Mechanical Cleaner (the client config has `[goggles]` for the Night Vision Goggles). The table name is the prefix of its options.

Breaking a naturally generated Budding Echo summons a Warden. The check reads the block's `can_summon` state, so a Budding Echo you placed yourself never triggers it.

---

## 5. Compatibility

| Mod | Relation | Notes |
| --- | --- | --- |
| Create | Required | 6.0.10+ |
| AE2 | Optional | Enables Budding Fluix; AE2's own Growth Accelerator accelerates this mod's budding blocks too, and the Engineer's Goggles multiplier counts it |
| Curios | Optional | Night Vision Goggles fit a Curios head slot (without Curios they use the vanilla helmet slot) |
| JEI | Optional | Adds a "Budding Block Info" page: one page per budding block covering growth conditions, growth speed and generation conditions, plus an "Infection" page listing what the block turns into what and how likely; reachable from the budding block, its buds, its cluster and the cluster's output |
| KubeJS | Optional | Register your own budding blocks from a script, see the next section |

Without AE2 the mod starts normally and simply does not register any Fluix content. The budding blocks (including vanilla Budding Amethyst), both Accelerators, the Smart Drill, the Mechanical Cleaner, and the Resonance Table together with its Resonance Filter all ship with Ponder scenes, one scene per feature, and Engineer's Goggles read out how they are running.

The mod provides 31 advancements across five branches: budding blocks, accelerators, machines, equipment, and the deep ocean and Deep Dark.

---

## 6. Adding and Modifying Budding Blocks with KubeJS

The growth engine is public, so you can register custom budding blocks with KubeJS — as many as you like — and you can retune budding blocks that **already exist** (built-in families, ones another script made, ones from an addon). Both uses share the same option object, and neither needs Java or a data pack.

Scripts live in the game directory under `kubejs/startup_scripts/`; the file name is free as long as it ends in `.js`. `CustomBudding` and `CustomBuddingOptions` are plain globals in scripts — no `Java.loadClass` needed.

### Tutorial 1: Building a Budding Block From Scratch

**Step 1 — make it exist.** Create a script file whose whole content is one line:

```js
// kubejs/startup_scripts/my_crystal.js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'my_crystal', 20)   // 1-in-20 chance
})
```

Check it in game with `/give @s kubejs:my_crystal_budding` (without a namespace the id lands under `kubejs`). **If the block does not show up, read the log first**: `logs/kubejs/startup.log` reports `Loaded N/N KubeJS startup scripts ... with 0 errors`, and it is also where script errors appear (a broken script is skipped as a whole, not line by line).

**Step 2 — place it and watch it grow.** The random tick is already wired up, so it grows buds and then clusters on its own. Vanilla random ticks are slow (tens of seconds on average), so either put an accelerator next to it or raise the chance: `.chance(1)` advances on every successful roll.

**Step 3 — give it conditions.** Every option is one line, and they mix freely (see "Option Reference" below):

```js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'my_crystal', new CustomBuddingOptions()
    .chance(10)                                // 1-in-10 per random tick
    .minLight(4).maxLight(12)                  // only grows at light 4–12
    .growthDimensions('minecraft:overworld')    // full speed in the Overworld only
    .growthBiomes('warm')
    .outsideGrowthChance(0.1))                  // a tenth of the chance outside your turf
})
```

Verify with **Engineer's Goggles**: looking at your block lists the options in force (phrased qualitatively, "slow", "must be dark enough"). JEI has a page per budding block too.

**Step 4 — make it burn fluid.** `needfluid` also gives the block a tank:

```js
    .needfluid('minecraft:lava', 250, 1000)    // lava only, 250 mB per growth, 1 B tank
```

Fill it with a pipe or by right-clicking with a bucket (a bucket fills it exactly when empty): **when the tank cannot pay the cost, nothing grows** (no slowdown). The Goggles show the level and the cost per growth, a comparator reads the level, and a bucket that cannot be used is swallowed instead of spilling next to the block (sneak-right-click to pour it out as usual).

**Step 5 — make it drop something.** `dropItem` controls what the cluster drops on a normal break (omit it and it drops nothing; Silk Touch always drops the cluster itself):

```js
    .dropItem('mypack:my_shard', 2)             // 2 items, Fortune applies
    .buddingLevel('stone').stageLevel('iron')   // stone pickaxe for the block, iron for buds/cluster
```

**Step 6 — give it a look and a name.** Up to here it uses the default textures (vanilla amethyst), which is why it works with no assets at all. For your own look, prepare five 16×16 textures and drop them into

```
kubejs/assets/<namespace>/textures/block/<file>.png
```

(the namespace is the part before the `:` in the block id; without one it is `kubejs`), then point `buddingTexture('mypack:block/my_budding')` / `stageTextures(...)` at them. The simplest way to name things is `.displayName('My Budding Block').stageDisplayNames('Small Bud', 'Medium Bud', 'Large Bud', 'Cluster')` — one call covers every language; omit it and KubeJS derives English names from the ids, or write per-language keys (`block.<namespace>.<block id>`) in `kubejs/assets/<namespace>/lang/zh_cn.json`. Sounds need no assets — just name a vanilla sound (`'stone'` / `'amethyst'` / `'crop'` …).

**Step 7 — let it convert its neighbours.** `transform(input, output, n[, r])` makes the budding block swap a nearby block: a 1-in-n chance per random tick, then one block is picked within radius r (default 1, i.e. the immediate neighbours). You can call it repeatedly (each call is its own rule with its own n); the input may also be a block tag:

```js
    .transform('minecraft:iron_block', 'mypack:my_crystal_budding')   // turn adjacent iron blocks into itself
    .transform('#c:storage_blocks/iron', 'mypack:my_crystal_budding') // tag input: every block in the tag counts
    .transform('minecraft:stone', 'minecraft:iron_ore', 20)           // pick the output and the chance: 1-in-20
    .transform('minecraft:stone', 'minecraft:iron_ore', 20, 3)        // 4th argument: pick radius 3 (7x7x7)
```

Naming your own budding block as the output (`<namespace>:<id>_budding` for scripted blocks) is exactly how the built-in families multiply themselves. Those rules are infection recipes just like stone → ore, and the infection config governs all of them (see "Budding Block Reproduction" in section 1); stone → iron ore do not. The third argument is the chance base: a **1-in-n chance per random tick** (radius 1). Omitting it means 25000, the same tier as the built-in regeneration, so put accelerators next to it if you want to see it soon; pick a small n (say 20) for a conversion you want to watch happen — a stone-to-iron-ore rule fires every few seconds next to an accelerator. Each transform has its own n and they do not interfere.

### Tutorial 2: Retuning a Built-in Family (Budding Flammable Ice)

Goal: **Budding Flammable Ice must be fed water before it grows, its chance becomes 1, and its growth space needs light 12.** Add it one line at a time with `modify`:

```js
// kubejs/startup_scripts/modify_flammable_ice.js
StartupEvents.registry('block', event => {
  CustomBudding.modify('create_crystal_industry:flammable_ice_budding', new CustomBuddingOptions()
    .needfluid('minecraft:water', 250, 1000)  // add a water tank: 250 mB per growth, 1 B capacity
    .chance(1)                                // every successful roll advances (was 1/5)
    .minLight(12))                            // growth space needs light 12 (was unrestricted)
})
```

What matters:

- **This merges, it does not replace.** Only the tank, the chance and the light floor changed — everything else about Flammable Ice (including its "the growth space must be a water source" rule) is untouched. For clearing a single option there are explicit spellings: `maxLight(-1)` / `minLight(-1)` = that end unbounded, `requiresWater(false)` = no longer needs water, `growthDimensions()` with no arguments = drop the dimension restriction, `needfluid('none')` = drop the fluid requirement, `buddingLevel('none')` = drop the mining tier.
- **The id is the budding block's block id** (`..._budding`); the trailing `_budding` may be omitted, and a bare id lands in the `kubejs` namespace. A target that does not exist is not silent: the startup log warns that the `modify` target is not a budding block driven by this mod.
- **The water tank and the water-source rule are separate**: the latter is the family's own rule, the former is this new gate; both must be satisfied.
- **Reload the chunk** (or place a fresh block) to pick up the new parameters: the tank is built from the definition when the block entity is created. If a tank switched fluids still holds the old one (say lava on a block a script turned into water), the Goggles say "wrong fluid in the tank" — pull it out with a pipe or a bucket.
- **Verify**: with an empty tank it does not grow even while sitting in water → fill one bucket of water and it starts growing; below light 12 it does not grow; JEI and the Goggles show the retuned chance, light floor and level.
- Budding blocks that run on AE (Fluix) or FE (Arclight) **cannot take a fluid tank** — their block entity has to hold a grid node / an FE buffer, and a script that tries throws on the spot (adding one would silently skip their power cost).

### Tutorial 3 · Deployment and Troubleshooting Cheatsheet

| Symptom | Look here first |
| --- | --- |
| The whole script did nothing | `logs/kubejs/startup.log`, the `with N errors` line — a script error skips the entire script |
| Block not found | Check with `/give` rather than the creative tabs (scripted blocks go to the `kubejs` page by default; `group` changes that) |
| Budding block will not grow | Goggles show its current conditions; accelerators only tick blocks that **take random ticks** (every budding block this mod registers does) |
| Cluster drops nothing | Check `dropItem` — omitting it means nothing drops (Silk Touch is separate) |
| A `modify` had no effect | The target warning in the startup log, and the "reload the chunk" rule above |
| Server behaves differently from single-player | Scripts run on both sides; keep the server's and the client's scripts identical (a mismatch is display-only) |

Two hard requirements: `create` and `modify` **must live in startup scripts** (`kubejs/startup_scripts/`), and startup scripts **run once when the game starts** (`/reload` does not re-run them) — JEI's budding pages are built at that same moment, so editing a script (or deleting a line) takes a game restart to take effect.

### One Line Registers a Whole Family

The budding block plus its small bud, medium bud, large bud and cluster, five blocks in one call.

```js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'example_crystal', 20)   // 1-in-20 chance
})
```

The resulting block ids are `kubejs:example_crystal_budding` and `_small_bud` / `_medium_bud` / `_large_bud` / `_cluster`. The budding block's random tick is already wired into the growth engine, and the buds and cluster carry a `FACING` property (pointing back at the budding block). Textures and sounds default to vanilla amethyst, so the script runs with no assets at all.

The `id` may include a namespace (`'mypack:example_crystal'`); without one it lands in the `kubejs` namespace.

### Full Example

Copy the following into `kubejs/startup_scripts/` and it runs as-is. The comments explain each option.

```js
// kubejs/startup_scripts/my_crystal.js
StartupEvents.registry('block', event => {
  // The options object is the third argument to create. The chain must be written here: options
  // are read once, at the moment create is called, so CustomBudding.create(event, id).chance(20)
  // does not work — by then the blocks have already been built.
  // Assigning fields and chaining are equivalent and can be mixed:
  //   const opts = new CustomBuddingOptions()
  //   opts.chance = 20 …
  //   CustomBudding.create(event, id, opts)
  const family = CustomBudding.create(event, 'mypack:example_crystal', new CustomBuddingOptions()
    .chance(20)                                  // 1-in-20 chance to advance a stage per random tick (default 5)
    .maxLight(7)                                 // max light at the growth space, 0–15; negative = unlimited (default -1)
    .minLight(1)                                 // min light at the growth space, 0–15; negative = unlimited (default -1)
                                                 // both lines = only light 1–7 advances; one line = only that end is bound
                                                 // (the Echo "must be pitch dark" case is just .maxLight(0))
                                                 // a min above the max throws immediately
    .requiresWater(false)                        // default false = no water needed
                                                 // the Flammable Ice form is .requiresWater() (target must be a water source)
    .needfluid('minecraft:lava', 250, 1000)      // growth burns fluid (like Budding Ancient Debris burns lava):
                                                 // 250 mB per successful growth, tank holds up to 1 B; omit = no fluid
                                                 // see "Fluid Consumption" below
    .growthDimensions('minecraft:overworld')     // growth dimension ids, multiple allowed; omit = any dimension
    .growthBiomes('warm')                        // growth biomes, see "Growth Environment" below; omit = any biome
                                                 // writing this and the line above intersects the two
    .outsideGrowthChance(0.1)                    // chance to keep growing outside your turf (0–1, default 0.5)
                                                 // 0 = never grows outside it; meaningless without the two lines above
    .displayName('Example Budding Block')        // omit and KubeJS names it from the id
    .stageDisplayNames('Small Bud', 'Medium Bud', 'Large Bud', 'Cluster')   // order: small → medium → large → cluster
                                                 // pass null for one entry to keep its automatic name
    .dropItem('mypack:my_shard', 2)              // what the cluster drops on a normal break (default: nothing)
                                                 // Fortune adds 0–level per level; Silk Touch always drops the cluster itself
    .buddingTexture('mypack:block/my_crystal')   // budding block texture
    .stageTextures('mypack:block/my_small_bud',  // the four stage textures, same order as above
                   'mypack:block/my_medium_bud',
                   'mypack:block/my_large_bud',
                   'mypack:block/my_cluster')
    .buddingSound('stone')                       // break sound of the budding block, a vanilla sound name (default 'amethyst')
    .stageSound('crop')                          // break sound of the buds and cluster, shared by all four stages
    .buddingTool('axe')                          // mining tool of the budding block: pickaxe / axe / shovel / hoe
                                                 // (or a full tag id); default 'pickaxe'
    .stageTool('pickaxe')                        // mining tool of the buds and cluster, shared by all four stages
    .buddingLevel('stone')                       // mining tier of the budding block: stone / iron / diamond
                                                 // 'none', null or omitted = no tier
    .stageLevel('stone')                         // mining tier of the buds and cluster, shared by all four stages
    .group('building_blocks'))                   // creative tab: default 'kubejs', null = no tab at all

  // family holds the five block ids; they are record accessors, so the parentheses are required:
  const budding = family.budding()               // 'mypack:example_crystal_budding'
  const cluster = family.cluster()               // 'mypack:example_crystal_cluster'
  console.info(`registered: ${budding} / ${cluster}`)
})
```

### Option Reference

Chained methods share their names with the fields; the two forms are equivalent and can be mixed. This table lists every option, with the default in parentheses.

| Option | Value | Effect |
| --- | --- | --- |
| `chance(n)` | integer ≥ 1 (5) | A 1-in-n chance to advance a stage per random tick |
| `maxLight(n)` | 0–15, negative = unlimited (−1) | Upper light bound at the growth space |
| `minLight(n)` | 0–15, negative = unlimited (−1) | Lower light bound at the growth space |
| `requiresWater()` | — (false) | Target space must be a water source block |
| `needfluid(id, cost, capacity)` / `needfluid(id)` | fluid id or `'#tag'` + two amounts in mB (250 / 1000) | Growth burns fluid: each successful growth drains `cost` mB, and it stops growing when the tank cannot pay |
| `growthDimensions(...)` | dimension ids, multiple allowed | Grows normally only in these dimensions |
| `growthBiomes(...)` | biome ids / tags / climate keywords, multiple allowed | Grows normally only in these biomes |
| `outsideGrowthChance(x)` | 0–1 (0.5) | Chance to keep growing outside your turf |
| `displayName(s)` | string | Budding block display name; automatic if omitted |
| `stageDisplayNames(...)` | four strings, `null` allowed | Display names of the four stages, order: small → medium → large → cluster |
| `dropItem(id)` / `dropItem(id, n)` | item id, optional count (1) | What the cluster drops on a normal break |
| `dropCount(n)` | integer (1) | Drop count; anything below 1 is treated as 1 |
| `buddingTexture(s)` | texture path | Budding block texture (defaults to vanilla Budding Amethyst) |
| `stageTextures(...)` | four texture paths | The four stage textures, same order |
| `buddingSound(s)` / `stageSound(s)` | vanilla sound name (`amethyst`) | Break sound; an unknown name throws immediately and lists every valid name |
| `buddingTool(s)` / `stageTool(s)` / `tool(s)` | tool name or full tag id (`pickaxe`) | Mining tool; `tool` sets both at once |
| `buddingLevel(s)` / `stageLevel(s)` | tier name or full tag id (unset) | Mining tier; `'none'` or `null` = no tier |
| `transform(input, output, n, r)` / `transform(input, output, n)` / `transform(input, output)` | block id, input may be `'#tag'`, may be called repeatedly; n = the chance base (25000), r = the pick radius (default 1, max 8) | Converts neighbouring blocks: a 1-in-n chance per random tick, then one block is picked within radius r; rules are governed by the config like the built-in ones |
| `group(s)` | tab id (`kubejs`) | Creative tab; `null` = no tab |

### Growth Environment

Writing both `growthDimensions` and `growthBiomes` intersects them: a position counts as your turf only if its dimension *and* its biome both match. Outside your turf, every successful roll makes a second roll and only grows with probability `outsideGrowthChance`. Writing neither treats everywhere alike.

Each `growthBiomes` entry may be:

| Form | Example | Meaning |
| --- | --- | --- |
| Biome id | `'minecraft:lush_caves'` | That biome |
| Biome tag | `'#minecraft:is_nether'` | Every biome in the tag |
| Climate keyword | `'cold'` / `'warm'` / `'hot'` | Bucketed by base temperature; the Nether counts as `hot` everywhere and the End as `cold` |
| Negation | `'!cold'`, `'!#minecraft:is_taiga'` | A leading `!` excludes |

Hitting any negation drops the position immediately; otherwise at least one positive entry must match. When only negations are written, the positive side is treated as "every biome", so `growthBiomes('!cold')` reads as "anywhere but cold biomes".

### Fluid Consumption

`needfluid(fluid, cost, capacity)` makes a budding block "burn fluid to grow" like Budding Ancient Debris does: the block carries a small tank, every **successful** growth drains one cost, and when the tank cannot pay that cost it simply does not grow (no slowdown, no discount).

| Argument | Form | Meaning |
| --- | --- | --- |
| Fluid | `'minecraft:lava'`, `'#minecraft:lava'` or `'create:potion[minecraft:potion_contents={potion:"minecraft:swiftness"}]'` | A fluid id matches by **fluid type**, so both the still and the flowing variant of the same fluid count (bucketed or pumped in, either works); a `#` prefix writes a fluid tag and accepts every fluid in it; a bracketed **data component** list (same shape as vanilla item syntax, SNBT values) narrows it to one concrete variant — that is how Create's single potion fluid is split into individual potions |
| Cost | integer mB, default 250 | Drained per successful growth; must be ≥ 1 |
| Capacity | integer mB, default 1000 | Tank size; must be ≥ the cost, otherwise it could never grow a single stage (that combination throws immediately) |

Passing only the fluid (`needfluid('minecraft:lava')`) uses the default 250 / 1000, the same price as Budding Ancient Debris (one bucket). A misspelled fluid throws right away instead of quietly creating a block that never grows.

A **data component** list matches as a **subset**: the fluid in the tank only has to *contain* those components, extra ones do not matter (identical to NeoForge's `DataComponentFluidIngredient.of(false, stack)`, which is what Create uses internally). A **tag cannot carry components** — a tag may hold several fluids and one set of components cannot speak for all of them, so that combination throws. The typical use is Create's potions: every potion shares the single `create:potion` fluid, so `needfluid('create:potion')` can only mean "any potion", while the bracketed form above rejects the other potions and makes the Goggles and JEI say "Potion of Swiftness" instead of a flat "Potion". The potion **level** is not part of vanilla's name (Swiftness I and II are both `item.minecraft.potion.effect.swiftness`; the level only shows on the effect lines), and this line exists to pin down exactly which one, so the level is appended following vanilla's own `potion.potency` convention — it reads "Potion of Swiftness II".

With a **fluid id**, the Goggles and JEI show the fluid's translated name (`minecraft:lava` -> "Lava", `create:honey` -> "Honey" — the fluid carries its own translated name, this mod does not have to do anything). A **tag** has no name of its own, so there is a separate key with a fallback, `create_crystal_industry.fluid_tag.<namespace>.<path>`, and this mod ships the common ones already: `#minecraft:lava` / `#minecraft:water`, plus the NeoForge common tags `#c:*` (water, lava, milk, potion, the various stews, experience, ... — `#c:chocolate` and `#c:tea` among them, contributed by Create; only `#c:gaseous` and `#c:hidden_from_recipe_viewers` are deliberately left out, as they describe a fluid's nature rather than naming one). Other tags can be translated by a modpack or addon, and an untranslated one is shown verbatim as `#mymod:syrup`.

How fluid gets in:

- **Pipes and pumps**: the block exposes the NeoForge fluid capability, so Create's fluid pipes, pumps or any machine that understands it can fill and drain it;
- **Held containers, right-click**: a bucket fills it or scoops it out. One bucket is 1000 mB, so a bucket only helps at "empty" or "full"; odd amounts like half a bucket need pipes (same as Budding Ancient Debris). When the tank will not take the fluid (wrong fluid) or has nothing to scoop, the block swallows the click — nothing is dumped next to the budding block; sneak and right-click if you want to pour it there on purpose. **Potions have to go through pipes**: a potion bottle exposes no item fluid capability (Create only registers one for buckets), so right-clicking with one does not fill the tank;
- **Comparators** read the level: 0 when empty, otherwise 1–15 proportionally;
- **Engineer's Goggles** gain two lines: the current amount and the cost per growth, plus a red "it will not grow" line while the tank cannot pay.

The tank does not travel with the block: breaking it (Silk Touch included) discards the fluid, so drain it with a pipe before moving it.

> A fluid requirement needs a block entity that can store fluid. Built-in families and blocks created by `CustomBudding` carry one automatically (the same generic tank, see "The Lava Tank of Budding Ancient Debris" in section 1). Writing `fluidRequirement(...)` on a hand-rolled low-level block only adds a parameter to the definition — the tank and the payment hook are yours to implement, see "Low-Level Interface" below.

### Drops, Tools and Mining Tiers

- **Drops**: buds drop themselves only under Silk Touch, whatever breaks them. Clusters drop what `dropItem(item, count)` names on a normal break and the cluster itself under Silk Touch; with no `dropItem` a normal break drops nothing. The count benefits from Fortune, adding 0–level per level, matching this mod's own cluster loot tables.
- **Mining tool** decides *what mines fastest*. The bare names `pickaxe` / `axe` / `shovel` / `hoe` are the complete set of vanilla mining tags (`#minecraft:mineable/*`); any other bare name throws immediately, which prevents registering a tag nobody reads. You may also write a full block tag id (containing `:`, such as `'mymod:mineable/wrench'`). Writing `null` attaches no tag at all, making bare hands the fastest. Use `tool(...)` to give all five blocks the same tool.
- **Mining tier** decides *what may collect the drop*. A bare name resolves to a vanilla tier tag, `#minecraft:needs_<name>_tool`: `'stone'` / `'iron'` / `'diamond'`. A full tag id also works (e.g. `'neoforge:needs_netherite_tool'`). Omitting it, or writing `'none'`, means no tier.

  Setting a tier also gives the block `requiresCorrectToolForDrops`, because a bare tag is read by nothing on its own. That has two consequences: **a tool below the tier drops nothing at all**, Silk Touch included, and **the tool type must match as well** — with the budding block set to axe plus `'stone'`, a stone axe collects it and a stone pickaxe does not. To allow bare-hand collection, leave the tier unset, which is what this mod's own buds and clusters do.

### Modifying an Existing Budding Block

The same options can retune a budding block that is **already registered** — a built-in family, one another script made, or one from an addon:

```js
// kubejs/startup_scripts/my_tweaks.js
StartupEvents.registry('block', event => {
  CustomBudding.modify('create_crystal_industry:ancient_debris_budding', new CustomBuddingOptions()
    .chance(10)                                 // the default is the Slow tier (1/20)
    .growthDimensions('minecraft:overworld')    // also drop the "Nether only" restriction
    .needfluid('none'))                         // and stop burning lava
})
```

**Options you leave out keep the block's current value** (this merges, it does not replace): the snippet above only touches the chance, the dimension and the fluid — nothing else about Ancient Debris changes. There are explicit spellings for clearing a single option too: `maxLight(-1)` / `minLight(-1)` = that end unbounded, `requiresWater(false)` = no longer needs water, `growthDimensions()` with no arguments = drop the dimension restriction (`growthBiomes()` likewise), `needfluid('none')` = drop the fluid requirement, `buddingLevel('none')` = drop the mining tier.

The id is the **block id** (`..._budding`); the trailing `_budding` may be omitted, and a bare id lands in the `kubejs` namespace (same as `create`). A target that does not exist, or is not a budding block driven by this mod's engine, gets a warning in the startup log — the script runs during the block registration event, when other mods' blocks are not in the registry yet, so it cannot be checked on the spot.

| Can be changed | Notes |
| --- | --- |
| Chance, light bounds, water requirement, growth dimension / biomes, outside-turf chance | The regular growth parameters |
| `needfluid(...)` | Give **any** budding block a fluid cost, change it, or drop it — the tank is generic (`FluidTankBuddingBlockEntity`), and Ancient Debris' lava runs on it too |
| `dropItem(...)` / `dropCount(...)` | Changes the cluster's drops; it takes over for built-in families too (their loot table no longer applies). `.dropItem('none')` = drop nothing. Writing `.dropCount(...)` alone throws — overriding drops replaces the loot table wholesale, so name the item too. JEI's budding page (the slot in the corner) and pressing R on the product follow the override |
| `buddingLevel(...)` / `stageLevel(...)` | Mining tier, enforced at runtime: vanilla's three tiers only (`stone` / `iron` / `diamond`), and only the "do you get drops" step — the block tags themselves are unchanged |
| `transform(input, output, n[, r])` | Appends infection rules (call it repeatedly, each with its own n and r); the block's own rules are never displaced. To stop one budding block, list the others in the config; to stop everything, use the master switch |

**Cannot be changed** (fixed at registration; a script that sets them throws on the spot): textures `buddingTexture` / `stageTextures`, break sounds `buddingSound` / `stageSound`, break tools `buddingTool` / `stageTool`, translated names `displayName` / `stageDisplayNames`, and the **creative tab `group`**. The first four are baked into block properties and resources; `group` is which page the block belongs to. `modify` exists to **add traits to a budding block**, not to change its identity — use `create` for that.

Two things to keep in mind:

- It **must live in a startup script** (the same `StartupEvents.registry` as `create`): it changes things fixed at registration time, and JEI's budding info page is built once at startup.
- Scripts run **on both sides** (one pass on the client, one on a dedicated server), so both agree on the parameters; if a server's scripts differ from the client's, the Goggles and JEI will show values that do not match the server's actual behaviour (display only).

> For a budding block whose fluid parameters were changed (or whose tank was added / removed), the tank is built with the new parameters when the **block entity is created**: blocks already placed in the world pick them up after a chunk reload (or when you place a fresh one).

### What Comes Next

Block ids follow a fixed pattern: `<namespace>:<id>_budding` and `_small_bud` / `_medium_bud` / `_large_bud` / `_cluster`. **Write recipes and tags in `server_scripts/`** (startup scripts have no such events) and use those id strings directly:

```js
// kubejs/server_scripts/my_crystal.js
ServerEvents.recipes(event => {
  event.smelting('minecraft:amethyst_shard', 'mypack:example_crystal_cluster')   // smelt the cluster into amethyst shards
})

ServerEvents.tags('block', event => {
  event.add('minecraft:mineable/axe', 'mypack:example_crystal_budding')          // attach one more mining tag
})
```

The value `create` returns, `family`, exposes five accessors for the block ids: `budding()` / `smallBud()` / `mediumBud()` / `largeBud()` / `cluster()`. They are **method calls, so the parentheses are required**; writing `family.budding` yields the method object rather than an id. They are useful for registering related items inside the same startup script, whereas `server_scripts/` should just use the id strings above.

### Behavior Notes

- **Tags are attached automatically.** The budding block joins `#c:budding_blocks`, the three buds join `#c:buds` and the cluster joins `#c:clusters` (block and item tags for all three — NeoForge keeps those categories separate). The five blocks join the matching mining tags via `buddingTool` / `stageTool` and the matching tier tags via `buddingLevel` / `stageLevel` (by default only `#minecraft:mineable/pickaxe`, with no tier). As a result the Smart Drill's Silk Touch mode collects your budding block directly and AE2's Growth Accelerator accelerates it, with no tags to write by hand.
- **Goggles**: wearing Engineer's Goggles and looking at a custom budding block shows the current growth multiplier along with its growth speed, light requirement, water requirement and growth environment (dimension / biome, phrased qualitatively as "Slow" or "must be dark enough" without exact numbers); blocks with `needfluid` also report the tank level and the cost per growth (a level is state, not a parameter, so those get exact numbers). Built-in families normally show only the multiplier line; those with `growthDimensions` / `growthBiomes` gain an extra "fastest only in X" line, and **family blocks that a script has `modify`ed** show the lines above with their new values — a retuned block says so in game.
- **Creative tab**: KubeJS-registered blocks go into no tab by default, which makes people think registration failed, so these land in the KubeJS tab unless told otherwise. `group(...)` switches to a vanilla tab, and `group(null)` omits the tab entirely (reachable only via `/give`).
- **Names and appearance** come from resource packs and language files. Without a display name, KubeJS derives an English title from the id (`example_crystal_small_bud` → "Example Crystal Small Bud"). The block item and the block share one translation key, so the inventory, dropped items and creative tab all change together.

### Known Limitations

- A scripted budding block's chance comes entirely from `chance` in the script and takes part in no global tier; to retune an **existing** budding block (built-in families included) use `CustomBudding.modify(...)`.
- Features **baked into block properties**, such as light emission and redstone output, cannot be configured from a script. Those require the addon Java route described in section 7.

### Low-Level Interface

For full control, load the growth engine and the definition class yourself and assemble them by hand. `CustomBudding` and `CustomBuddingOptions` are bound as script globals by this mod's KubeJS plugin and need no `Java.loadClass`; the engine and definition do.

```js
const BuddingGrowthEngine = Java.loadClass('com.minecart.yunxian.budding.BuddingGrowthEngine')
const GrowthDefinition  = Java.loadClass('com.minecart.yunxian.budding.GrowthDefinition')

// of(small, medium, large, cluster, n): the four stage blocks plus the chance base n (1-in-n per random tick).
// Stage blocks are written as ids; vanilla amethyst buds, this mod's buds and clusters, and blocks you
// registered yourself all work. The call returns the definition, so the chain continues from it:
const definition = GrowthDefinition.of('minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud',
                                       'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster', 20)
    .growthDimensions('minecraft:overworld')   // growth dimensions, multiple allowed
    .growthBiomes('warm', '!hot')              // growth biomes: ids / '#tags' / keywords cold·warm·hot; ! negates
    .outsideGrowthChance(0.1)                  // chance to keep growing outside your turf (0 = never)

// Light and water live on the same chain (light 0–15, negative = that end unbounded; a min above the max throws):
//   .maxLight(7).minLight(1)   // only light 1–7 advances
//   .requiresWater()           // target must be a water source
// Fluid consumption is on the chain too, but on its own it drains nothing:
//   .fluidRequirement('minecraft:lava', 250, 1000)   // lava only, 250 mB per growth, 1 B tank
// The engine pays through the gate the caller passes in (the fourth argument of tryGrow), and the tank has to
// come from the block: blocks made by CustomBudding bring one along (see "Fluid Consumption" above), while a plain
// event.create block has no block entity at all — there the call is just a parameter nothing ever reads.
// The full overload of of() also works: of(small, medium, large, cluster, n, maxLight, minLight, requiresWater);
// when only the max is wanted, omit the min: of(small, medium, large, cluster, n, maxLight, requiresWater).

event.create('my_budding').randomTick(ctx => {
  BuddingGrowthEngine.tryGrow(ctx.block.getLevel(), ctx.block.getPos(), ctx.random, definition)
})
```

> Building the definition at the top level of the script is safe, because the stage blocks are either vanilla or ones you registered yourself. **If the definition references another mod's blocks**, it must move into the `randomTick` callback and be cached there: those blocks are not registered yet when the script runs, and building it at the top level throws immediately.

A runnable example also ships in the local development directory: `run/kubejs/startup_scripts/custom_budding_example.js`. `run/` is listed in `.gitignore` and is not distributed with the repository.

---

## 7. Development

### Adding a Budding Block

Every trait of a budding block — light, water, power and fluid requirements, growth rules, block conversion, light and sound, drops — lives in a single table in
`src/main/java/com/minecart/yunxian/budding/BuddingFamilies.java`. **Adding a budding family amounts to adding one entry to that table**; block registration, growth logic, Goggles readouts, creative tabs, world generation switches and Ponder entries are all derived from it.

Growth speed is in that table too: pass a `GrowthSpeed` tier to that entry's `Growth(...)` (`NORMAL` / `FAST` / `SLOW` / `VERY_SLOW`, see `BuddingFamily.GrowthSpeed`) — omitting it means Normal. Redstone and Lapis Lazuli are Fast, Ancient Debris is Slow, everything else is Normal (the Very Slow tier has no built-in member). To retune an **existing** budding block (built-in families included) you do not have to touch the table: a script can call `CustomBudding.modify(...)`.

### Data Generation

Generate the JSON assets with `./gradlew runData`. **AE2 must be present in `run/mods` before running it**, otherwise the task aborts outright — this is what prevents the already-generated Fluix assets from being judged stale and deleted. The output directory `src/generated/resources` is under version control.

Data generation owns the following files; do not write them by hand: `blockstates/`, `models/block/`, `models/item/`, the loot tables for budding blocks and buds, the `c:budding_blocks` / `c:buds` / `c:clusters` tags, the `mineable/pickaxe` and `needs_*_tool` tags, and the **infection recipes** (`data/create_crystal_industry/recipe/budding_conversion/`, produced from each family's declarations in the family table — see "Infection Recipes" in section 1).

Still hand-written: textures (including `.mcmeta`), the loot tables for clusters and Fluix (their structure and mod conditions cannot be reproduced by the generator), world generation JSON, language files, plain-item models (`models/item/`), and the crafting/processing recipes (everything under `data/create_crystal_industry/recipe/` except `budding_conversion/`).

> The generator **validates that textures exist** (model generation reads `ExistingFileHelper`), so while a new family's textures are still being drawn you have to drop placeholder images into the target paths, run `runData`, then delete the placeholders and keep only the folders. Fluid textures and tint are not the generator's business: their paths are hard-coded in `client/ModFluidExtensions`.

### Wiring a Block Into the Growth Engine

Growth checks and placement are public (`budding/BuddingGrowthEngine`), and three kinds of caller share one code path: this mod's own budding blocks, an addon's blocks, and blocks registered from a KubeJS script. The engine deliberately **excludes** random-tick side effects (conversion and spreading) and growth energy; the block owns those, and energy is requested through a `GrowthGate` callback before placement.

**Addon mods (Java)**

```java
// 1) Your budding block: either use the public GenericBuddingBlock (passing your own buds/cluster),
//    or implement randomTick yourself and call the engine
DeferredBlock<Block> myBudding = MY_BLOCKS.register("my_budding",
        () -> new GenericBuddingBlock(myFamilySpec, properties, small, medium, large, cluster));

// 2) Declare it in your own @Mod constructor (must happen before the block registration event):
BuddingRegistration.declareBuddingBlock(myBudding.get());   // use the shared Goggles block entity
// If the family declares a fluid requirement (the last argument of Growth takes a FluidRequirement), add:
// BuddingRegistration.declareFluidBuddingBlock(myBudding.getId());
```

`declareBuddingBlock` is not a courtesy call you can skip: when a chunk restores block entities from NBT it validates `BlockEntityType#isValid` (`LevelChunk:392`), and a block absent from the shared block entity's valid-block list has its block entity **discarded after a chunk reload**, at which point the Goggles readout stops working. The same holds for the fluid tank: this mod derives its tank list from its own family table, and **your family is not in that table**, so a family with a fluid requirement must declare itself.

A block built on `GenericBuddingBlock` is complete at this point: it carries its own family definition, which the JEI Budding Block Info page reads directly. Blocks that **assemble the low-level interface themselves** (implementing `randomTick` and calling the engine) have no definition to read, so they need one more declaration — and don't forget `.randomTicks()` in the block properties: without it neither vanilla nor an accelerator will ever touch the block (`GenericBuddingBlock` copies its properties wholesale from vanilla Budding Amethyst, so it carries the flag already):

```java
// 3) Let the JEI "Budding Block Info" page list its growth speed / light / water requirements
BuddingRegistration.declareGrowthDefinition(myBudding.get(),
        () -> GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));
```

Definitions are taken lazily through a `Supplier`, so calling this before the block and its stage blocks exist is safe — which is exactly the situation during script registration.

**The two call forms of the engine**

```java
// Free growth (the common case for scripts and addons)
BuddingGrowthEngine.tryGrow(level, pos, random, GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));

// With the paid hook (this mod's own families use it: the AE2 budding block pays energy before placement)
BuddingGrowthEngine.tryGrow(serverLevel, pos, random, definition, gate);
```

A definition can take further gates: `growthDimensions(Level.NETHER)` restricts normal growth to the listed dimensions, and `growthBiomes("minecraft:lush_caves")` adds a biome filter. Writing both intersects them; outside your turf each successful check makes a second roll and only grows with probability `outsideGrowthChance(0.5)` — 0.5 is exactly what Budding Quartz and Budding Glowstone use, and 0 stops growth there entirely.

Biome entries resolve in this order: hitting any **negation** drops the position immediately; otherwise at least one **positive** entry must match; when only negations are written, the positive side counts as "every biome" (`growthBiomes("!cold")` means anywhere but cold biomes).

A block wired up by hand through the low-level interface has one optional extra: **honour script `modify` overrides** so a pack author can retune it the same way they retune this mod's budding blocks — wrap your own definition in `BuddingOverrides.apply(this, definition)` (both of this mod's budding block classes do exactly that). Skipping it is fine too; a script's `CustomBudding.modify` can then still change its drops, creative tab and mining tier, but not its growth parameters.

Everything else is yours to supply: block registration and textures, items, loot tables, and adding the block to the `#c:budding_blocks` tag (the Smart Drill's Silk Touch mode and AE2's Growth Accelerator read it).
The same goes for your buds and clusters — add them to `#c:buds` and `#c:clusters` so other mods filtering by category recognise them.

### Climate Keywords

How the `cold` / `warm` / `hot` keywords are bucketed:

| Keyword | Coverage |
| --- | --- |
| `cold` | The whole End, or a biome base temperature below 0.3 (snowy plains, ice spikes, snowy taiga, frozen ocean, snowy slopes, frozen peaks, plus cool biomes such as taiga and windswept hills) |
| `warm` | Everything else (plains, forest, jungle, swamp, ocean, mushroom fields, stony peaks, …) |
| `hot` | The whole Nether, or a biome base temperature of at least 1.2 (desert, badlands, savanna) |

The "whole End / whole Nether" cases go through the biome tags `#minecraft:is_end` / `#minecraft:is_nether`, so modded dimensions using those biome sets count as well. The thresholds are adjustable: the two constants in `GrowthEnvironment.Climate`.

---

## License

**This mod may be included in modpacks published on CurseForge without asking for permission** — public or private, free or monetized.

The modpack must use **the CurseForge packaging method**: reference this mod in `manifest.json` by CurseForge project ID and file ID so the launcher downloads it from CurseForge itself. **Do not bundle the mod jar inside the archive**, and do not publish it on any platform other than CurseForge. The condition is that you credit the author (YunXian_LI) and link to the official download page.

See [LICENSE.txt](LICENSE.txt) for the full terms.
