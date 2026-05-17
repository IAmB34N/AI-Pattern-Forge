# AI Pattern Forge 1.2.1

AI Pattern Forge adds an AE2-style terminal block for creating encoded patterns faster. The v1.2.3 UI is built to look closer to AE2's Pattern Encoding Terminal, with a large searchable block grid, side controls, craftability filtering, and a pattern encoding area.

## Features

- Searches every block item in the loaded pack.
- Filters by Craftable, Uncraftable, or All.
- Sorts by item name, result count, or mod.
- Supports ascending and descending sort order.
- Includes visible type toggles for Items, Fluids, Energy, and Chemicals.
- Includes terminal style options: small, medium, tall, and full-height.
- Encodes AE2 crafting, stonecutting, and processing-style patterns when a matching recipe exists.
- Requires an AE2 blank pattern and one available AE2 channel.
- No AE power cost.
- Block textures no longer include the AI text on the side.

## Recipe

```
Fluix Block       Calculation Processor      Fluix Block
Logic Processor   Pattern Encoding Terminal  Logic Processor
Sky Stone Block   Crafting Unit              Sky Stone Block
```


## v1.2.3 real-inventory patch

- Replaced fake drawn inventory slots with real player inventory/hotbar slots.
- Clicking an item in your inventory now selects an available encodable autocraft pattern for that item when one exists.
- The forge still consumes an AE2 blank pattern only when you press encode.


## v1.2.3 changes

- Adds Option 2 multi-select behavior: left click selects one pattern, Shift + left click adds/removes patterns from the bulk selection, and Ctrl + A selects all visible craftable recipes.
- The encode button now encodes the whole selected set and consumes one blank pattern per encoded pattern.
- Clicking a real player inventory item selects the matching autocraft pattern; Shift-clicking an inventory item toggles it in the multi-select queue.
- Adds a session cache for the terminal recipe/block list so reopening the forge is much faster after the first load.
- Keeps the AE2-inspired layout as an original implementation rather than copying AE2 source directly.


## v1.2.3 notes
- Moved the recipe from `data/aipatternforge/recipes` to the Minecraft 1.21+ `data/aipatternforge/recipe` folder so it loads correctly.
- Reworked GUI indexing so the terminal opens immediately and builds the recipe/block catalog over client ticks instead of freezing while scanning the whole pack.
- Added server-side recipe caching so bulk encoding does not rescan every recipe once per selected pattern.
- Continued the AE2-lookalike terminal layout direction while keeping real player inventory slots.


## Credits / License

AI Pattern Forge depends on Applied Energistics 2. The terminal-style GUI is adapted from AE2 19.2.17 terminal GUI layout/style concepts, which are licensed under LGPL-3.0-or-later. Credit for the original AE2 GUI work goes to the Applied Energistics 2 team. This mod is unofficial and is not endorsed by AE2. Source code should be provided with releases for LGPL-3.0 compliance.
## License and Credits

### Clarification

AI Pattern Forge’s original code is licensed under the MIT License.

Portions of the GUI layout, terminal styling, and bundled GUI assets are adapted from Applied Energistics 2, which is licensed under LGPL-3.0. Those AE2-derived portions remain subject to the LGPL-3.0 license.

Credit for the original AE2 GUI design, terminal layout, and related assets goes to the Applied Energistics 2 team.

AI Pattern Forge is an unofficial add-on and is not affiliated with, endorsed by, or maintained by the Applied Energistics 2 team.
