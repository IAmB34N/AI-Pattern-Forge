# AI Pattern Forge 1.3.2

## Ctrl+A now selects items, not search text

Pressing **Ctrl+A** in the terminal now always selects every visible craftable recipe, even when
the search box is focused. Previously the search field caught the shortcut and just highlighted
its own text. Triple-click in the search box still works for selecting text.

## Tab-hold recipe overlay

Hover an item in the grid and **hold Tab** to see its recipe overlaid near the cursor:

- Crafting recipes show the 3x3 input grid (shaped or shapeless) + arrow + result.
- Smelting / Blasting / Smoking / Campfire / Stonecutting show input -> arrow -> result.
- Releasing Tab dismisses the overlay and returns to the normal item tooltip.

Recipes are resolved client-side via `RecipeManager.byKey(...)`, so no extra packets are sent
to the server.

## Performance

- Catalog build budget bumped from 256 to 1024 entries per client tick, so the first-open lag on
  large modpacks drops to roughly a quarter of what it was. The catalog still builds in the
  background after world join, so by the time you open the terminal it's usually already ready.
- Grid items now render via `GuiGraphics.renderFakeItem`, which skips the durability-bar and
  cooldown-overlay setup. Modest gain per cell, but with up to 126 cells rendered at 60 fps in
  Full mode it adds up.
