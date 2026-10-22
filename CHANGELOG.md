## Next version (not released yet)

- Authors are now "Claude, Konitor" (shown in Mod Menu); Konitor is the CurseForge name of the GitHub account Knito58.
- New block: **Script Box**. It runs one Lua program that drives the engine it touches, no other mod needed.
  Program and images live in `<world>/billboard/scripts/<id>/`; the program starts with the world and runs while its chunk is loaded.
  The `engine` table has the same functions as the ComputerCraft peripheral (`drawImage` takes an image name), plus
  `sleep`, `print`, `os.time`/`os.date`/`os.clock`/`os.epoch`/`os.day` and `textutils.formatTime`.
  Recipe: the engine recipe with paper instead of the glass pane. Breaking it drops it blank and deletes its files.
  `/billboard script <pos> restart|stop|status`. Lua VM: Cobalt (MIT), packed into the mod.
- Script Box screen: drag the `.lua` file and images from your file manager onto it. It lists the files (with a button
  to remove each), shows the last output lines and has Restart and Stop. Only the placer and operators may change a box.
  In single player a Folder button opens the box's folder, and a click on a file opens it in your editor.
- Script Box texture: a cardboard box with a paper label of code lines and a small light: dark when stopped,
  flickering green while the program runs, red after an error.
- The engine screen greys out the text, speed, size and Go while a program holds the engine.
- `show(text)` for Script Boxes and ComputerCraft: sets the text and shows it standing still at the left edge
  (setText, go and stop in one). The same text again is not redrawn, so a clock can simply call it every second.
- Engines can be claimed: `claim()`, `release()`, `isClaimed()` for Script Boxes and ComputerCraft. A claimed engine takes
  changes only from its holder and ignores sign and redstone; its screen keeps Stop and Restore as an emergency brake.
- New settings for Script Boxes in `config/billboard.properties` (time per tick, limits, ops only, switch off).

- Letter size 1 to 9: every dot of the 5x7 font becomes n x n blocks, up to 45 x 63 blocks per letter.
  Set it with **Size - / +** in the engine (new row under Speed) or `setSize(n)` / `getSize()` from ComputerCraft.
  A size change redraws a running or stopped text at once.
- The pixel slot is now a sample slot: clicking it with a block copies the block, you keep it.
  Blocks can also be dragged there from JEI or REI. Breaking the engine drops only the wand.
- ComputerCraft: `setPixel("minecraft:gold_block")` and `getPixel()` choose the pixel block from Lua.
- The engine must now touch its wall: place it directly behind one wall block, full face contact.
  It no longer works from up to 16 blocks away, and it refuses a wand marked for a different wall or without both corners.
  An engine that already shows text keeps running; the rule applies at its next start.
- Engine screen messages also go to the chat; a message too long for the status line ends in "...".
- The engine ignores redstone power from the side that touches the wall, so a redstone block pixel can't start it.
- ComputerCraft: `go(column)` starts the text at a wall column, e.g. right-aligned with `go(width - textWidth)`;
  `getWallSize()` returns the width and height of the marked wall.
- New character `·` (middle dot): a single dot in the middle row. From Lua write it as `"\183"`.
- Engines from older versions hand their real pixel blocks back (they pop out on top of the engine).
- Pictures and animations from ComputerCraft: `drawImage(data)` shows a PNG, JPEG, GIF or BMP on the wall,
  every pixel as the block with the nearest color (about 260 vanilla blocks, all free). Animated GIFs play by themselves.
  `drawPixels` draws pixels made by a Lua program; `isPainting`, `isShowingPicture`, `play`. `getWallSize()` also reports a shown wall.
- Bigger walls: up to 256 wide and 384 high (the whole world height) by default, up to 512 wide with `config/billboard.properties`.
- New picture blocks are spread over several ticks (`blocks_per_tick`), so big walls don't freeze the server.

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
