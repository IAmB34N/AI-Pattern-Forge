# AI Pattern Forge 1.3.3

## New block face textures

- Replaced the front and side textures with a new design: a dark inset frame around a glowing
  cyan core when the forge is online, dimmed to a flat dark face when it's offline.
- All four cardinal faces (front + 3 sides) now share the same look so the block reads as
  uniform from any horizontal angle.
- Light level still tracks the `online` blockstate: 0 when offline, 8 when online.

## Fixed

- `ai_pattern_forge_online` model was using the OFF side texture even in the online state. The
  block now correctly switches the side texture to `ai_pattern_forge_side_online` when online,
  so all four faces glow together.
