# Billboard Engine

Fabric mod for Minecraft: turns any flat wall into a scrolling text billboard made of blocks,
or, with ComputerCraft, into a picture or animation made of blocks.

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
(size 2 = 10 x 14, size 9 = 45 x 63). Changing the size redraws a shown text at once.
On a taller wall the text is centered vertically; on a lower one the bottom rows are cut off.
While a billboard is shown, its wall is protected: players, explosions, pistons, mobs and `/setblock`
cannot change it, so pixel blocks can't be mined. Blocks attached to the wall (levers, torches, vines) stay put.
Redstone blocks work as pixels: lamps or dust next to the wall follow the text, like a punch card.
The other way round works too: redstone lamps as pixels light up where levers or redstone blocks sit behind the wall.
Blocks in the wall may still change their own state (a lamp lighting up), they just can't be replaced.
Scrolling pauses while the wall's chunks are not loaded.
Walls containing chests, signs or other block entities are refused. Maximum wall size is 256 wide and 384 high, the full height of the world
(a server can allow up to 512 wide, see [Settings](#settings)).
The original blocks are saved in the world (`data/billboard/wall_backups.dat`), so a crash does not lose them.

## Control by sign and redstone

No other mod needed. Put a sign on the engine (any side, or on top) and give the engine a redstone pulse
(button, lever, observer...) from any side except the one touching the wall, so redstone pixels can't trigger it.
The engine reads the sign, joins its lines with spaces and presses **Go**.
While it runs, it checks the sign every second: when the text changes, the billboard restarts with the new text.
That also works with Create's Display Link, which can write into vanilla signs.
A pulse while the same text is already running changes nothing. Speed, size, Stop and Restore stay in the GUI.
Wax the sign with a honeycomb so nobody can edit it by accident; the engine still reads it.

## Script Box (Lua, no other mod needed)

The Script Box runs one Lua program that drives the engine it touches. Craft it like the engine, with paper
in the middle instead of the glass pane (4 iron, 4 redstone, 1 paper), and place it against the engine.

- One box = one program. It starts when the world (or its chunk) loads and runs while the chunk is loaded.
  It does not remember anything between runs; it starts again from the top.
- Right-click the box to open its screen: its files, the last output lines (errors in red), **Restart** and **Stop**.
  Stop is the hand brake: the program stays off, also after a restart of the world, until Restart.
- Drag files from your file manager onto the screen: one `.lua` program plus images (PNG, JPEG, GIF, BMP).
  A new `.lua` file replaces the old program, whatever its name, and starts it. An image replaces the image of
  the same name. The **x** behind a file removes it. There is no editor in the game: write the program in any text editor
  and drop it again after a change.
- In single player the **Folder** button opens the box's folder in your file manager, and a click on a file opens it
  with your system's program for it (your text editor for `.lua`). After saving a change there, press **Restart**.
- Only the player who placed the box and operators may change it (with `scripts_ops_only=true` only operators).
  Others can look at the screen.
- The files live in the world folder: `saves/<world>/billboard/scripts/<id>/`
  (on a server `world/billboard/scripts/<id>/`). A server admin can also edit them there directly;
  `/billboard script <x y z> status` shows the folder, `restart` starts the program again, `stop` stops it.
- Breaking the box drops it blank and deletes its folder. Keep your originals on your own disk.

The `engine` table has the same functions as the ComputerCraft peripheral below, so a CC script runs after
replacing `peripheral.find("billboard_engine")` with `engine`. Only `drawImage` takes the name of an image
in the box's folder instead of the file's bytes:

A clock in four lines:

```lua
engine.claim()
while true do
  engine.show(os.date("%H:%M"))      -- or textutils.formatTime(os.time(), true) for Minecraft time
  sleep(1)
end
```

More:

```lua
engine.claim()                       -- this box alone controls the engine (see below)
engine.setText("Hello")
print(engine.go())                   -- print goes to the box's output lines
sleep(1)                             -- seconds; every program must sleep now and then
engine.drawImage("mona.png", { fit = "fill" })
print(os.date("%H:%M"), textutils.formatTime(os.time(), true))
```

Besides `engine` a program has standard Lua (`string`, `table`, `math`, `utf8`, `coroutine`), `sleep`, `print`,
`os.time`, `os.date`, `os.clock`, `os.day`, `os.epoch` and `textutils.formatTime` as in CC: Tweaked.
No files, no internet, no peripherals.

Limits: each box gets 2 ms of Lua time per tick and then pauses until the next tick. A program that runs
5 seconds without `sleep()`, or uses its whole time for 30 seconds without ever sleeping longer than one tick,
is stopped with a message. Program files up to 64 KB, up to 8 images of up to 2 MB each. All of this can be
changed in [Settings](#settings).

### Sharing an engine: claim and release

Several Script Boxes and CC computers may touch the same engine. `engine.claim()` gives it to one of them:
it returns `true`, or `false` plus who holds it (it never waits). While an engine is claimed, only its holder
may change it; everyone may still read it (`getText`, `isRunning`, ...). The claim ends with `engine.release()`,
or by itself when the program ends, fails, is restarted or stopped, its box is broken or unloaded, or the
computer shuts down. A claimed engine ignores its sign and redstone; in its screen only Stop and Restore work,
as an emergency brake. An engine nobody claims works as before.

```lua
while not engine.claim() do sleep(1) end   -- wait for our turn
-- ... show something ...
engine.release()                           -- let the next box have it
```

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
b.show("12:34")        -- setText, go and stop in one: the text stands still at the left edge;
                       -- the same text again is not redrawn, so a clock can call it every second
b.go(10)               -- same, but the text starts at wall column 10 (0 = left edge)
local w, h = b.getWallSize()  -- size of the shown wall, else the one the wand marks; or nil plus a reason
b.stop()               -- freeze the text
b.restore()            -- put the wall back
b.runOff()             -- let the text run off the wall once, then put the wall back
b.getText(), b.getSpeed(), b.getSize(), b.isRunning(), b.isShowing(), b.isRunningOff()
b.claim()  b.release()  b.isClaimed()   -- see "Sharing an engine" above
```

`runOff()` lets a shown text (running or stopped) scroll on until it has left the wall, then restores the wall.
If nothing is shown, the text first comes in from the right, crosses the wall once and is gone.
Wait for it with `while b.isRunningOff() do sleep(0.5) end`.

### Pictures and animations

The engine can also show an image file on the wall. Every wall pixel becomes the block whose color comes
closest; about 260 plain full blocks are used (wool, concrete, terracotta, planks, stones, ores, metal blocks...),
all for free, like the pixel block. A portrait is recognizable at 64 blocks wide and looks good from 128 up.
PNG, JPEG, GIF and BMP are read by the mod; an animated GIF plays by itself on the engine.
Needs the wand in the engine (the marked wall), but no pixel block and no text.

```lua
local b = peripheral.find("billboard_engine")
local w, h = b.getWallSize()                    -- size of the marked (or shown) wall
local file = fs.open("mona.png", "rb")          -- from the computer's disk ...
local data = file.readAll() file.close()
-- local data = http.get("https://example.com/cat.gif", nil, true).readAll()   -- ... or from the web
print(b.drawImage(data))                        -- true, or false plus a reason such as "need_wand"
b.drawImage(data, { fit = "fill", dither = true, loop = false })
while b.isPainting() do sleep(0.1) end          -- wait until the picture (an animation: its first frame) is on the wall
b.stop()  b.play()                              -- freeze an animation, let it run again
b.drawPixels(1, 1, 2, 1, "\255\0\0\0\0\255")    -- x, y (from 1, top left), width, height, RGB bytes
b.restore()                                     -- put the wall back
b.isShowingPicture()
```

- `fit = "fit"` (default) shows the whole image centered; the wall keeps its own blocks around it.
  `"fill"` covers the whole wall and cuts off what sticks out, `"stretch"` pulls the image to the wall's shape.
- Transparent parts of an image keep the wall's own blocks. So does alpha below 128 in `drawPixels`
  (4 bytes per pixel: red, green, blue, alpha).
- `dither = true` mixes neighbouring blocks for in-between colors. Good for big still pictures,
  not for animations (it makes them flicker).
- Only changed blocks are set, at most `blocks_per_tick` per tick, so a new picture on a big wall
  builds up over a few ticks (about a second for 512 x 384). A new picture on the same wall replaces the old one
  without flashing the original wall in between.
- Big animations are limited by your computer redrawing the changed chunks, not by the server. Small or mostly
  still animations run at their full speed; one that changes the whole of a 256 x 256 wall manages a few frames per second.
- Limits: file up to 16 MB, image up to 4096 x 4096, all frames together up to 16 million wall pixels
  (a 256 x 256 wall takes 256 frames). A computer's disk holds 1 MB unless raised in the CC: Tweaked config,
  so bigger files come over `http`.
- Colors: blocks can't be as bright and saturated as a screen. Pure red (255, 0, 0) ends up as orange concrete;
  red concrete is about (142, 32, 32).
- The picture is not saved with the engine: after a restart the wall keeps the last picture (still protected,
  Restore works), but an animation stands still and the next `drawPixels` starts on a fresh picture.
  A program in `startup.lua` can simply send it again.

Blocks used for pictures are full, opaque, have no block entity, do not fall, tick, glow or give redstone power
(no grass, no copper that oxidizes, no sand, no glowstone). A few more are left out by the block tag
`billboard:not_for_pictures` (TNT, pumpkin heads, sponge, bedrock, redstone lamps, ...); a data pack can add to it.
The colors were measured from the vanilla textures with `tools/block_colors.py`; resource packs don't change them,
and blocks from other mods are not used.

## Settings

`config/billboard.properties` is written on first start (on a server: on the server).

```
max_width=256         # largest wall the wand may mark, up to 512
max_height=384        # 384 is the full height of the world
blocks_per_tick=8192  # picture blocks an engine may change per tick

scripts_enabled=true          # Script Boxes allowed at all
scripts_ops_only=false        # only operators may give boxes files, restart or stop them
script_ms_per_tick=2          # Lua time per box and tick
script_max_busy_seconds=5     # stopped after this long without sleep()
script_too_busy_seconds=30    # stopped after using its whole time this long
script_max_lua_kb=64
script_max_images=8
script_max_image_kb=2048
```

An older `billboard.properties` gets the new lines added at the next start; your values stay.

To see a 512 wide wall as a whole you stand about 350 blocks away, which needs a render distance of about 22 chunks.
Lower `blocks_per_tick` if pictures make the server lag.

## Command

`/billboard runoff <x y z>` does the same as `runOff()` for the engine at that position.
`/billboard script <x y z> restart|stop|status` controls a Script Box; `status` also lists its folder and output lines.
They need operator rights (level 2), so they also work in a command block.

## How this mod is made

Billboard Engine is written by Claude AI (Anthropic), code, textures and icon included.
The Script Box runs Lua with [Cobalt](https://github.com/cc-tweaked/Cobalt), CC: Tweaked's Lua VM (MIT license),
packed into the mod; see `THIRD_PARTY_NOTICES.md`.
Konitor decides what the mod does and tests every version in game.

## Build and run (Linux)

Requires Java 25.

```
./gradlew runClient    # start Minecraft 26.3 with the mod
./gradlew build        # mod jar ends up in build/libs/
```

To publish a release: set `version` in `gradle.properties`, update `CHANGELOG.md`, merge to main, then
on GitHub open *Actions → Release → Run workflow*. It builds the jar, tags `v<version>` and creates the release page.
Then it uploads the jar to CurseForge, with the newest `CHANGELOG.md` section as changelog
(needs the repository secret `CURSEFORGE_TOKEN`; without it this step is skipped).
*Actions → CurseForge upload → Run workflow* with a tag such as `v1.1.0` uploads an existing release again.

In IntelliJ IDEA: *File → Open* the project folder, wait for the Gradle import, then run the
`Minecraft Client` run configuration.
Only Fabric API is loaded with the mod in these runs. To test with CC: Tweaked, JEI or other mods,
put their jar files into `run/mods`.
