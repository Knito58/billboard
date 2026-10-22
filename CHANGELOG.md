## Billboard Engine 1.1.0 for Minecraft 26.3 (Fabric)

**Install:** put `billboard-1.1.0.jar` into the `mods` folder, together with
[Fabric API](https://modrinth.com/mod/fabric-api). Needs Fabric Loader 0.19.5 or newer and Java 25.
[CC: Tweaked](https://modrinth.com/mod/cc-tweaked) is optional. Worlds from 1.0.x load as before.

### Script Box (new block)

- Runs one Lua program that drives the engine it touches, no other mod needed.
  Recipe: the engine recipe with paper instead of the glass pane.
- Drag the `.lua` file and up to 8 images from your file manager onto its screen. The screen lists the files
  (with a button to remove each), shows the last output lines and has Restart and Stop.
  Only the placer and operators may change a box. In single player a Folder button opens the box's folder,
  and a click on a file opens it in your editor.
- Files live in `<world>/billboard/scripts/<id>/`; the program starts with the world and runs while its chunk is loaded.
  Breaking the box drops it blank and deletes its files.
- The `engine` table has the same functions as the ComputerCraft peripheral (`drawImage` takes an image name), plus
  `sleep`, `print`, `os.time` (Minecraft time), `os.date` (real time), `os.clock`, `os.epoch`, `os.day` and `textutils.formatTime`.
- Texture: a cardboard box with a paper label of code lines and a small light: dark when stopped,
  flickering green while the program runs, red after an error.
- `/billboard script <pos> restart|stop|status`. New settings in `config/billboard.properties`
  (time per tick, limits, operators only, switch off). Lua VM: Cobalt (MIT), packed into the mod.

### Engine

- Letter size 1 to 9: every dot of the 5x7 font becomes n x n blocks, up to 45 x 63 blocks per letter.
  Set it with **Size - / +** in the engine screen. A size change redraws a running or stopped text at once.
- The pixel slot is now a sample slot: clicking it with a block copies the block, you keep it.
  Blocks can also be dragged there from JEI or REI. Breaking the engine drops only the wand.
  Engines from older versions hand their real pixel blocks back (they pop out on top of the engine).
- The engine must now touch its wall: place it directly behind one wall block, full face contact.
  It no longer works from up to 16 blocks away, and it refuses a wand marked for a different wall or without both corners.
  An engine that already shows text keeps running; the rule applies at its next start.
- The engine ignores redstone power from the side that touches the wall, so a redstone block pixel can't start it.
- Engine screen messages also go to the chat; a message too long for the status line ends in "...".
- New character `·` (middle dot): a single dot in the middle row. From Lua write it as `"\183"`.
- Bigger walls: up to 256 wide and 384 high (the whole world height) by default, up to 512 wide with `config/billboard.properties`.
- The engine screen greys out text, speed, size and Go while a program holds the engine.

### Lua control (Script Box and ComputerCraft)

- Pictures and animations: `drawImage(data)` shows a PNG, JPEG, GIF or BMP on the wall,
  every pixel as the block with the nearest color (about 260 vanilla blocks, all free). Animated GIFs play by themselves.
  `drawPixels` draws pixels made by a Lua program; `isPainting`, `isShowingPicture`, `play`.
  Big pictures are placed over several ticks (`blocks_per_tick`), so the server doesn't freeze.
- `show(text)`: sets the text and shows it standing still at the left edge (setText, go and stop in one).
  The same text again is not redrawn, so a clock can simply call it every second.
- `runOff()`: the text scrolls through once and the wall is restored afterwards (also `/billboard runoff`).
- `setSize(n)` / `getSize()`, `setPixel("minecraft:gold_block")` / `getPixel()`.
- `go(column)` starts the text at a wall column, e.g. right-aligned with `go(width - textWidth)`;
  `getWallSize()` returns the width and height of the marked or shown wall.
- `claim()`, `release()`, `isClaimed()`: a claimed engine takes changes only from its holder and ignores sign
  and redstone; its screen keeps Stop and Restore as an emergency brake.

### Other

- Authors are shown as Claude and Konitor.

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
