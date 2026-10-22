A billboard is a flat rectangle of blocks, one block thick, marked with the **Selector Wand**.
Any blocks can be in it: stone, glass, wood, wool, a mix. They are put back exactly as they were when the billboard ends.

## Marking a wall

- Right-click one corner block with the wand: *Corner 1 set*. Then right-click the opposite corner: *Corner 2 set*.
- While you hold the wand, the action bar shows the size, for example `Wall: 40 wide x 9 high`.
  If the two corners are not in one plane, it says `not flat (must be 1 block thick)`.
- Sneak + right-click clears both corners.
- The wand remembers the corners itself, so one wand belongs to one wall. Make another wand for another wall.

The wall may face north, south, east or west. Walls lying flat on the ground (floors, ceilings) are not supported.

## Size

- Up to **256 wide** and **384 high** (the full height of the world).
- A server can allow up to **512 wide** with `max_width` in [the settings](Commands-and-settings).
- There is no minimum, but a 5x7 letter needs 5 x 7 blocks to be readable.

To see a 512-wide wall as a whole you stand about 350 blocks away, which needs a render distance of about 22 chunks.

## Where the engine goes

The engine stands **behind** the wall and must touch the back of one wall block with a **full face** (not diagonally,
not beside the wall in the same plane). The text reads on the far side, so the engine can be hidden inside a building.

The engine only takes a wand with both corners marked on the wall it touches. It checks this again every time
it starts, so moving the engine away or re-marking the wand stops it from starting.

## Walls that are refused

- Walls with **block entities** in them: chests, signs, furnaces, banners, heads and the like.
  The message names the block and its position.
- Walls that **overlap** another active billboard.
- Walls in another dimension than the engine.
- Walls that are partly in unloaded chunks: come closer.

What happens to the wall while it is used: [Protection and backup](Protection-and-backup).
