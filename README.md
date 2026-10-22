# Billboard Engine

Fabric mod for Minecraft: turns any flat wall into a scrolling text billboard made of blocks.

## Install

Download the jar from [Releases](https://github.com/Knito58/billboard/releases) and put it into the `mods` folder
together with [Fabric API](https://modrinth.com/mod/fabric-api). Minecraft 26.3, Fabric Loader 0.19.5+, Java 25.
On a server, install it on the server and on every client. [CC: Tweaked](https://modrinth.com/mod/cc-tweaked) is optional.

## How to use

1. Craft a **Selector Wand** (redstone torch above a stick) and a **Billboard Engine**
   (iron / redstone / iron, redstone / glass pane / redstone, iron / redstone / iron).
2. Right-click one corner of a flat wall with the wand, then the opposite corner.
   While you hold the wand, the action bar shows the size. Sneak + right-click clears the corners.
3. Place the engine directly behind the wall, touching the back of one wall block with a full face
   (not diagonally). The text is read from the other side, so the engine stays hidden and can sit
   safely inside a building. The engine only accepts a wand with both corners marked on the wall it touches, and it
   checks again at every start.
4. Open the engine and put the wand in the left slot. Then click the right slot with a full block
   (e.g. gold block), or drag one there from [JEI](https://modrinth.com/mod/jei) or
   [REI](https://modrinth.com/mod/rei). The slot only keeps a faded copy as a setting: the block stays
   in your inventory and is never used up. Click with an empty hand to clear it.
5. Type a text and press **Go**. The text scrolls from right to left; **-** / **+** set the speed
   (1 = one column per second, 10 = ten). **Size** - / + sets the letter size from 1 to 9.
   **Stop** freezes it, **Restore** puts the wall back.
   Breaking the engine also restores the wall.

The font is 5x7. Size 1 makes each letter 5 x 7 blocks; size n draws every dot as n x n blocks
(size 2 = 10 x 14, size 9 = 45 x 63, the tallest that fits a 64 high wall). Changing the size redraws a shown text at once.
On a taller wall the text is centered vertically; on a lower one the bottom rows are cut off.
While a billboard is shown, its wall is protected: players, explosions, pistons, mobs and `/setblock`
cannot change it, so pixel blocks can't be mined. Blocks attached to the wall (levers, torches, vines) stay put.
Redstone blocks work as pixels: lamps or dust next to the wall follow the text, like a punch card.
The other way round works too: redstone lamps as pixels light up where levers or redstone blocks sit behind the wall.
Blocks in the wall may still change their own state (a lamp lighting up), they just can't be replaced.
Scrolling pauses while the wall's chunks are not loaded.
Walls containing chests, signs or other block entities are refused. Maximum wall size is 256 x 64.
The original blocks are saved in the world (`data/billboard/wall_backups.dat`), so a crash does not lose them.

## Control by sign and redstone

No other mod needed. Put a sign on the engine (any side, or on top) and give the engine a redstone pulse
(button, lever, observer...) from any side except the one touching the wall, so redstone pixels can't trigger it.
The engine reads the sign, joins its lines with spaces and presses **Go**.
While it runs, it checks the sign every second: when the text changes, the billboard restarts with the new text.
That also works with Create's Display Link, which can write into vanilla signs.
A pulse while the same text is already running changes nothing. Speed, size, Stop and Restore stay in the GUI.
Wax the sign with a honeycomb so nobody can edit it by accident; the engine still reads it.

## Control by ComputerCraft

With [CC: Tweaked](https://modrinth.com/mod/cc-tweaked) installed, the engine is a peripheral of type `billboard_engine`
(computer next to it, or connected by a wired modem). It is optional; Billboard Engine runs fine without CC.

```lua
local b = peripheral.find("billboard_engine")
b.setText("Hello " .. textutils.formatTime(os.time()))  -- up to 64 characters, used by the next go()
b.setSpeed(7)          -- 1 to 10, takes effect at once
b.setSize(3)           -- letter size 1 to 9, takes effect at once (also on a stopped text)
b.setPixel("minecraft:gold_block")  -- any full block, no item needed; getPixel() reads it
print(b.go())          -- true, or false plus a reason such as "need_wand", "need_pixel", "overlap"
b.stop()               -- freeze the text
b.restore()            -- put the wall back
b.runOff()             -- let the text run off the wall once, then put the wall back
b.getText(), b.getSpeed(), b.getSize(), b.isRunning(), b.isShowing(), b.isRunningOff()
```

`runOff()` lets a shown text (running or stopped) scroll on until it has left the wall, then restores the wall.
If nothing is shown, the text first comes in from the right, crosses the wall once and is gone.
Wait for it with `while b.isRunningOff() do sleep(0.5) end`.

## Command

`/billboard runoff <x y z>` does the same as `runOff()` for the engine at that position.
It needs operator rights (level 2), so it also works in a command block.

## How this mod is made

Billboard Engine is written by Claude AI (Anthropic), code, textures and icon included.
Knito58 decides what the mod does and tests every version in game.

## Build and run (Linux)

Requires Java 25.

```
./gradlew runClient    # start Minecraft 26.3 with the mod
./gradlew build        # mod jar ends up in build/libs/
```

To publish a release: set `version` in `gradle.properties`, update `CHANGELOG.md`, merge to main, then
on GitHub open *Actions → Release → Run workflow*. It builds the jar, tags `v<version>` and creates the release page.

In IntelliJ IDEA: *File → Open* the project folder, wait for the Gradle import, then run the
`Minecraft Client` run configuration.
