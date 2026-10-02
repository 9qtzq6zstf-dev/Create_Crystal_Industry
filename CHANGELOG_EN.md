# Changelog

This file records what each push brings in. Version numbers follow `mod_version` in
`gradle.properties`, and an entry is filed under the version it shipped as. The date is that of the
last commit in the batch.

Coverage starts at the 1.0.0 release; the earlier development cycle (1.0.0-beta.*) is not covered.
Once a batch is done, add a new section at the top.

---

## 1.0.4 · 2026-10-02

### Changed

- Budding Glowstone's chance to replace Glowstone: default 0.5 → 0.1 (config key
  `glowstoneBuddingChance`). The outdated default written into both READMEs is updated to match.
- Both READMEs now cover the Resonance Table and the Resonance Filter, with a section on networking
  and linking gestures (sneak-right-click a table to remember its network, sneak-right-click a
  filter to join, sneak-right-click in the air to leave, read a network back with a Display Link).
  The prose throughout was rewritten in Ponder's voice with implementation details moved out of the
  body: Chinese 63,047 → 54,301 characters, English 69,630 → 60,015. Also corrects the claim that
  every machine has an Engineer's Goggles panel, naming the machines that do instead.
- Three new Ponder scenes for the Resonance family: the table, the filter, and a Display Link
  reading a table.
- The text of the five existing Ponder scenes was rewritten in Create's register: the subject is
  the machine or the mechanic itself, no second person, no em dashes, no semicolons in Chinese.

### Fixed

- The gearing in the Ponder scene for the display source: the large cogwheel was placed
  orthogonally to the small one, which looks adjacent but does not connect. Moved to the diagonal.
  All three scenes that have gearing (including the Smart Drill and the Mechanical Cleaner) now
  resolve to a complete rotation chain.
- `budding_growth.text_1` / `text_5`: the Java default strings and the language files had drifted
  apart for a while, now unified on the Java copy.

## 1.0.3 · 2026-10-02

1.0.3-beta.1.4 promoted to release. No further changes, see the next section.

## 1.0.3-beta.1.4 · 2026-09-28 ~ 2026-10-02

### Added

- Budding Arclight and Budding Ancient Debris, and the Current Slurry chain behind them.
  - Budding Ancient Debris: veins in the Nether at Y 8–22, same mining tier as Ancient Debris. The
    only budding block that runs on lava, carrying a 1 B lava tank and spending 250 mB per
    successful growth; with less than that stored nothing grows. The cluster drops 1–3 Netherite
    Scraps (Fortune applies), and the block drops 1 Ancient Debris on a normal break. Naturally
    generated ones come with an empty tank.
  - Budding Arclight: the only budding block that runs on FE. It holds 1,000,000 FE internally and
    spends 10,000 FE per successful growth, receive-only. The cluster drops 1 Arclight (a new item,
    item form only). It does not generate naturally and has no recipe yet.
  - Current Slurry: the mod's first fluid, light level 15. Anything living in it keeps taking 20
    armour-piercing shock damage. Made by pressing one Arclight in a Basin, superheated, for
    500 mB.
  - The "Electrified" effect: gained within 3 blocks of an arclight block or of Current Slurry,
    fading about 3 seconds after leaving. An electrified entity that takes damage takes 4 more
    shock damage itself and discharges to the nearest electrified entity within 5 blocks, drawing a
    spark between the two; the discharge counts as damage taken, so the chain keeps running. Shock
    was split into two damage types of its own, both armour-piercing true damage, sharing one set
    of death messages.
  - `#create_crystal_industry:shock_immune` armour (the four chainmail pieces, the four netherite
    pieces and Create's three netherite diving items, 11 in all) is immune to the slurry's shock and
    to real lightning, and no longer gains Electrified.
  - Sparks now ride on vanilla `animateTick` instead of a client-side scan.
- Inactive Budding Arclight: a decorative block that turns into Budding Arclight when struck by
  lightning (or when a lightning rod directly above it is struck), arriving fully charged and
  sprouting buds on the spot. Acquisition is undecided, creative only for now.
- The Resonance Table and the Resonance Filter. The filter stores no rules of its own: every test
  reads the items sitting on each table in its network and takes the union. A List Filter on a table
  has its allow/deny list and its 18 slots mirrored whole, an Attribute Filter mirrors its attribute
  set, and a plain item degrades to a type match. Change what sits on a table and every machine
  holding that filter changes its mind on the next test. When the network cannot be read, nothing
  passes.
  - Networking: sneak-right-click an existing table while holding a table to make the held one
    remember that network and join it when placed; sneak-right-click a table while holding a filter
    to link it.
  - A table accepts redstone power, freezing its filter at the moment it is powered.
  - A new Display Link source lists everything the network is filtering onto a Flap Display.
- Recipes for both (the table is Polished Rose Quartz / Electron Tube / Brass Casing in a vertical
  column, the filter is Polished Rose Quartz / Wool / Polished Rose Quartz in a row, matching the
  shape of Create's own List Filter recipe).
- KubeJS: `.needfluid(fluid id or #tag, mB per growth, tank capacity in mB)` lets a scripted budding
  block run on a fluid; `CustomBudding.modify(block id, options)` retunes the chance, light range,
  water requirement, growth environment, fluid tank, drops and mining tier of any existing budding
  block.
- Lava turns into deepslate with soul soil below it and a Flammable Ice Block beside it, with
  vanilla's fizzle sound and its interceptable event hook.

### Changed

- Budding Ancient Debris switches to the unpowered texture when the tank cannot pay for a growth;
  growth chance 1/50 → 1/20.
- `needfluid` accepts fluids with data components, down to one specific potion. Fluid names,
  dimension names, biome names and biome tag names now all follow the language.
- The lava tank moved out of a dedicated block entity into the family table, shared with scripted
  budding blocks. The block entity id changes from `ancient_debris_budding` to
  `fluid_tank_budding`, so a Budding Ancient Debris already placed in an old save loses its tank
  contents once (the block itself keeps working).
- The growth speed config keys are gone; chances moved into the family table (Redstone and Lapis are
  fast, Ancient Debris very slow, everything else normal, matching the old defaults one for one).
- The Accelerators only tick neighbours that can receive a random tick.
- Arclight Clusters no longer take Fortune.
- In the Engineer's Goggles, only the inserted dimension and biome names in the three
  growth-condition lines are tinted; the sentences themselves stay grey.
- All 32 advancement descriptions rewritten, checking each trigger against the code rather than
  guessing, and mentioning budding block / cluster where it fits.
- The Arclight family (Arclight and the inactive block) and the Current Slurry Bucket are held back
  from the creative tab. They are still registered and obtainable with `/give`.
- The network outline switches from two alternating colours to a single colour sliding back and
  forth along a rose quartz ramp (0.01 ≈ a 5-second cycle).
- Textures and models for the Ancient Debris family and the Resonance family.

### Fixed

- Right-clicking a budding block with a full lava bucket no longer pours lava onto the floor (a
  bucket that cannot be used is swallowed; sneak-right-click still pours it deliberately).
- `ConcurrentModificationException` crash caused by the network outline on an integrated server.

## 1.0.3-beta.1.3 · 2026-09-25 ~ 2026-09-28

- Buds and Clusters gained the common tags `c:buds` / `c:clusters`, so other mods filtering blocks
  by category can see them.
- The Night Vision Goggles and the Echo Spyglass overlays moved onto the vanilla layer that draws
  the spyglass scope (`camera_overlays`), restoring the same order against the chat and the hotbar
  that the vanilla spyglass has, instead of interleaving with them.
- Fixed the Smart Drill dropping an extra item on a contraption: in Silk Touch mode a budding block
  also dropped its normal-break product.
- Fixed buds and clusters registered by a KubeJS script being unable to reach a contraption's
  storage.
- Along the way: the Fast growth tier now defaults to Redstone and Lapis, and the Normal tier no
  longer contains them (otherwise the same id appeared under two config keys); the Echo Spyglass
  scan radius defaults to 64 instead of 16.

## 1.0.3-beta.1.2 · 2026-09-25

- Fixed a dedicated server crash: a common class referenced a client-only renderer unconditionally,
  the dist check moved to the call site.

## 1.0.3-beta.1.1 · 2026-09-25

- Fixed the Echo Spyglass icon vanishing from the hotbar while in use: the `using` predicate hitting
  `overrides` replaces the whole model, and the hidden model it swapped in carried a `gui` scale of
  zero.

## 1.0.3-beta.1.0 · 2026-09-25

- Fixed the Echo Spyglass outline flickering under Iris: `mc.renderBuffers().bufferSource()` is
  replaced by Iris with a deferred-batching implementation, so the outline now builds its own
  buffer source.

## 1.0.2 · 2026-09-21 ~ 2026-09-25

### Added

- The Crystal Battery: placing another layer on an existing 2x2 / 3x3 structure completes the whole
  layer; right-clicking crystals fills every empty slot in the whole block, sneak-right-clicking
  fills only the clicked slot, and creative mode consumes nothing; its own textures and models;
  contraption compatibility; it lights up with charge.
- Growth environment: a dimension list plus biome conditions (a concrete biome id, a `#biome` tag,
  or a climate keyword cold·warm·hot), `!` negates, and both written together intersect. Outside
  the allowed area each successful roll makes a second roll against `outsideGrowthChance`. KubeJS
  mirrors it with `.growthDimensions(...)` / `.growthBiomes(...)` / `.outsideGrowthChance(0.1)`.
- The Echo Spyglass scan now walks chunk → section → position, using a section's palette to skip a
  whole section at once (solid stone and pure air read no positions at all); results keep the
  nearest ones to the player, so a full view truncates the far side rather than whichever side was
  scanned first; consecutive scans that return the same result skip the packet entirely.

### Changed

- The Engineer's Goggles and JEI no longer print concrete numbers for growth conditions (chances,
  seconds and Y ranges all became qualitative, "slow", "deep underground · very rare"), and a
  budding block with an environment requirement says "only grows fastest in X".
- The half-finished Crystal Battery was pulled from the creative tab (the block and item are still
  registered and obtainable with `/give`).
- Both READMEs restructured: Budding Blocks at a Glance, Budding Block Reproduction, Growth Speed,
  Option Reference, Growth Environment, Drops and Mining Tiers, Climate Keywords and others each
  became their own section.
- The wording of the Engineer's Goggles "light requirement" line for scripted budding blocks:
  `min_light` used to read "the growth space cannot be pitch dark", which is not the same thing as
  the light floor it actually expresses, now "the growth space needs some light".

### Fixed

- Fixed the bubble column direction beside the Flammable Ice structure: `BUBBLE_COLUMN` defaults to
  the downward column, it is now explicitly set to the upward one.
- Fixed the Crystal Battery client read missing the block update the tanks send, which baked the
  connected texture with stale data and made the whole top face disappear.

## 1.0.2-beta.1.1 · 2026-09-20

- "It Really Does Grow" now checks the block: breaking a complete cluster completes it. The old
  trigger required Silk Touch on the player's very first break.

## 1.0.2-beta.1.0 · 2026-09-20

- 32 advancements in 5 chapters, on a tab of their own. Picking items up and placing blocks use
  vanilla triggers; the signature moments (a successful acceleration, watching it grow all the way,
  a Silk Touch harvest, waking the Warden) use `minecraft:impossible` granted from code, and budding
  blocks registered with KubeJS go through the same listener.
- Fixed the Echo Spyglass GUI model: now uses `neoforge:separate_transforms`, with a flat texture in
  the GUI and the 3D model in hand; added `gui_light: front`; fixed the item frame orientation;
  removed the decorator that drew the icon twice.

## 1.0.1 · 2026-09-19 ~ 2026-09-20

### Added

- KubeJS budding blocks can customise their break sound and their mining tool.
- The mining tool split into `buddingTool` / `stageTool`, both defaulting to a pickaxe; the old
  `tool('hoe')` spelling is kept as shorthand for setting both at once.
- Mining tiers `buddingLevel` / `stageLevel` (`stone` / `iron` / `diamond`, and tag-style spellings
  like `needs_stone_tool` are accepted too). Setting a tier also sets
  `requiresCorrectToolForDrops`, which makes the tool type a hard requirement as well: the right
  tier with the wrong tool drops nothing.

## 1.0.1-beta.1.0 · 2026-09-20

- Pre-release version number, plus a mod description (it goes into the jar's `mods.toml` and shows
  in the mod list and on the project page). This number was dropped in the next commit, which
  restarts at 1.0.2; 1.0.1 had already been released.

## 1.0.0 · 2026-09-18 ~ 2026-09-19

### Added

- KubeJS growth definitions support a minimum light `minLight`, which together with `maxLight` forms
  a closed range; writing only one end is fine, and a floor above the ceiling fails on the spot. The
  Engineer's Goggles scripted-block readout and the JEI Budding Block Info page each gained a
  "light ≥ n" line.

### Changed

- Loot from scripted clusters now benefits from Fortune, adding 0 to level items per level, matching
  the formula in this mod's own cluster loot tables; Silk Touch still drops the cluster itself.
- The README's light option example spells out all three spellings: both lines together form a
  range, one line alone limits just that end, and the Echo family's "must be pitch dark" is
  `.maxLight(0)` alone.
