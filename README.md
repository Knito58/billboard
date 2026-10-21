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
3. Place the engine behind the wall, at most 16 blocks away. The text is read from the other side,
   so the engine stays hidden and can sit safely inside a building.
4. Open the engine and put the wand in the left slot. Then click the right slot with a full block
   (e.g. gold block), or drag one there from [JEI](https://modrinth.com/mod/jei) or
   [REI](https://modrinth.com/mod/rei). The slot only keeps a faded copy as a setting: the block stays
   in your inventory and is never used up. Click with an empty hand to clear it.
5. Type a text and press **Go**. The text scrolls from right to left; **-** / **+** set the speed
   (1 = one column per second, 10 = ten). **Stop** freezes it, **Restore** puts the wall back.
   Breaking the engine also restores the wall.

The font is 5x7. On a taller wall the text is centered vertically; on a lower one the bottom rows are cut off.
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
(button, lever, observer...). The engine reads the sign, joins its lines with spaces and presses **Go**.
While it runs, it checks the sign every second: when the text changes, the billboard restarts with the new text.
That also works with Create's Display Link, which can write into vanilla signs.
A pulse while the same text is already running changes nothing. Speed, Stop and Restore stay in the GUI.
Wax the sign with a honeycomb so nobody can edit it by accident; the engine still reads it.

## Control by ComputerCraft

With [CC: Tweaked](https://modrinth.com/mod/cc-tweaked) installed, the engine is a peripheral of type `billboard_engine`
(computer next to it, or connected by a wired modem). It is optional; Billboard Engine runs fine without CC.

```lua
local b = peripheral.find("billboard_engine")
b.setText("Hello " .. textutils.formatTime(os.time()))  -- up to 64 characters, used by the next go()
b.setSpeed(7)          -- 1 to 10, takes effect at once
b.setPixel("minecraft:gold_block")  -- any full block, no item needed; getPixel() reads it
print(b.go())          -- true, or false plus a reason such as "need_wand", "need_pixel", "overlap"
b.stop()               -- freeze the text
b.restore()            -- put the wall back
b.getText(), b.getSpeed(), b.isRunning(), b.isShowing()
```

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
