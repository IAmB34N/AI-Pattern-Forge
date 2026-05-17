# AI Pattern Forge 1.3.1

## New search syntax

- `@modid` filters by mod (substring match — `@minecr` matches everything from `minecraft`).
- `#tag` filters by item tag, matching either the full id (`#minecraft:logs`) or the path (`#logs`).
- Multiple tokens are AND-ed: `@minecraft #wool red` finds red wool items from Minecraft.
- Plain text still matches name / id / type as before.

## Fixed

- Pressing the inventory keybind (`E` by default) while typing in the search bar no longer closes
  the terminal. Esc still closes the search-box focus / popouts / multi-select before closing the
  screen.

## JEI integration

- Added a JEI plugin (soft dependency — graceful no-op if JEI is missing).
- When **Use JEI search** or **Sync with JEI search** is enabled in Terminal Settings, the terminal
  pushes its search text to JEI's ingredient filter on every search update. JEI's overlay grid will
  filter to match what the terminal is showing.
