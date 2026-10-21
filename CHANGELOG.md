## Next version (not released yet)

- Letter size 1 to 9: every dot of the 5x7 font becomes n x n blocks, up to 45 x 63 blocks per letter.
  Set it with **Size - / +** in the engine (new row under Speed) or `setSize(n)` / `getSize()` from ComputerCraft.
  A size change redraws a running or stopped text at once.

## Billboard Engine 1.0.1 for Minecraft 26.3 (Fabric)

**Install:** put `billboard-1.0.1.jar` into the `mods` folder, together with
[Fabric API](https://modrinth.com/mod/fabric-api). Needs Fabric Loader 0.19.5 or newer and Java 25.

- The mod is now called Billboard Engine (formerly Billboard). Mod id, items, recipes and saved walls are unchanged; worlds from 1.0.0 load as before.
- New mod icon, shown in mod lists.

## Billboard 1.0.0 for Minecraft 26.3 (Fabric)

First release. Turns any flat wall into a scrolling text billboard made of blocks.

**Install:** put `billboard-1.0.0.jar` into the `mods` folder, together with
[Fabric API](https://modrinth.com/mod/fabric-api). Needs Fabric Loader 0.19.5 or newer and Java 25.
Works in single player and on servers (install it on both server and clients).

- Selector Wand marks a flat wall up to 256 x 64; the Billboard Engine behind it scrolls a 5x7 text across it.
- Any full block can be the pixel; it is not used up. Speed 1 to 10, Go / Stop / Restore.
- The original wall is backed up in the world save and comes back on Restore or when the engine is broken.
- While shown, the wall is protected against players, explosions, pistons and `/setblock`.
- Redstone friendly: redstone blocks as pixels drive lamps, dust, repeaters and comparators behind the wall like a punch card; levers and torches on the wall stay attached.
- Control without the GUI: a sign on the engine plus a redstone pulse shows the sign's text, and a running engine follows edits to the sign (also from Create's Display Link).
- Optional [CC: Tweaked](https://modrinth.com/mod/cc-tweaked) support: the engine is a `billboard_engine` peripheral with `setText`, `setSpeed`, `go`, `stop`, `restore` and more.
