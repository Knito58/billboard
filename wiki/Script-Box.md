The **Script Box** runs one Lua program that drives the engine it touches. No other mod is needed.

## Setting it up

1. Craft it: the engine recipe with **paper** in the middle instead of the glass pane (4 iron, 4 redstone, 1 paper).
2. Place it so it touches the engine. The engine needs its wand and wall as usual
   ([Getting started](Getting-started)).
3. Write the program in any text editor on your computer and save it as a `.lua` file.
4. Right-click the box and drag the file from your file manager onto its screen. The program starts at once.

The light on the box shows its state: dark when stopped, flickering green while the program runs, red after an error.

## The screen

- **Files**: the program and the images. A click on a file opens it with your system's program for it
  (in single player; your text editor for `.lua`). The **x** behind a file removes it.
- **Output**: the last lines the program printed. Errors are red.
- **Restart** starts the program again from the top. Press it after changing the file.
- **Stop** is the hand brake: the program stays off, also after a restart of the world, until you press Restart.
- **Folder** (single player only) opens the box's folder in your file manager.

## Files

- One `.lua` program. A new `.lua` file replaces the old program, whatever its name, and starts it.
- Up to 8 images: PNG, JPEG, GIF or BMP, each up to 2 MB. An image replaces the image of the same name.
  Show one with `engine.drawImage("name.png")`, see [Pictures and animations](Pictures-and-animations).
- The program file may be up to 64 KB.
- They live in the world folder: `saves/<world>/billboard/scripts/<id>/`, on a server `world/billboard/scripts/<id>/`.
  `/billboard script <x y z> status` shows which folder belongs to a box.
  You can also edit them right there and press Restart.
- **Breaking the box drops it blank and deletes its folder.** Keep your originals on your own disk.

## When does the program run?

- It starts when the world (or the box's chunk) loads, and runs while the chunk is loaded.
- It does not remember anything between runs: after a restart it begins at the top again.
- A program must `sleep()` now and then. Each box gets 2 ms of Lua time per tick and then pauses until the next tick.
  A program that runs 5 seconds without `sleep()`, or uses its whole time for 30 seconds without ever sleeping
  longer than one tick, is stopped with a message.

All limits can be changed in [the settings](Commands-and-settings).

## Who may change a box

Only the player who placed it and operators may drop files, remove files, Restart or Stop it.
Everyone else can look at the screen. With `scripts_ops_only=true` only operators may change boxes.
`scripts_enabled=false` switches Script Boxes off on a server.

## What a program has

- The `engine` table: every function in the [Lua reference](Lua-reference).
- Standard Lua: `string`, `table`, `math`, `utf8`, `coroutine`.
- `sleep(seconds)`, `print(...)`.
- As in CC: Tweaked: `os.time()` (Minecraft time), `os.date()` (real time of the server), `os.clock()`,
  `os.day()`, `os.epoch()`, `textutils.formatTime(time, twentyFour)`.
- No files, no internet, no other peripherals. For those, use [CC: Tweaked](Two-ways-to-script).

## A first program

```lua
-- clock.lua: the server's time, standing still on the wall
engine.claim()
while true do
  engine.show(os.date("%H:%M"))
  sleep(1)
end
```

`engine.claim()` makes this box the only one allowed to change the engine: see [Sharing an engine](Sharing-an-engine).
More programs: [Examples](Examples).
