A program can do everything the engine screen can, and more: set the text, speed, size and pixel block, start,
stop, let a text run off once, show pictures and animations.

**Scripting needs no other mod.** The [Script Box](Script-Box) runs Lua right in the game.
If you already use [CC: Tweaked](https://modrinth.com/mod/cc-tweaked), the engine is also a ComputerCraft peripheral
with the same functions. Pick whichever you like; both talk to the same engine.

| | Script Box (built in) | CC: Tweaked computer (optional) |
| --- | --- | --- |
| Extra mod | None | CC: Tweaked. For Minecraft 26.3 it is only on Modrinth, not on CurseForge. |
| Lua | [Cobalt](https://github.com/cc-tweaked/Cobalt), the same Lua engine CC uses, packed into this mod | CC's own |
| Placement | Touches the engine | Next to the engine, or connected with a wired modem |
| Getting the engine | `engine` is ready to use | `local engine = peripheral.find("billboard_engine")` |
| Writing programs | In any text editor on your computer, then drag the file onto the box | CC's editor in game, or files on the computer's disk |
| Images | Up to 8, dropped onto the box: `drawImage("name.png")` | File bytes from the disk or from `http`: `drawImage(data)` |
| Besides the engine | `sleep`, `print`, `os.time`, `os.date`, `os.clock`, `os.day`, `os.epoch`, `textutils.formatTime`, standard Lua | Everything CC has: files, network, monitors, other peripherals |
| Who may change the program | The player who placed the box, and operators | Whoever can open the computer |
| Starts | With the world or chunk, by itself | With `startup.lua`, as usual in CC |

A program written for one runs on the other after changing the line that finds the engine.
The examples in this wiki use `engine`; for CC put this line on top:

```lua
local engine = peripheral.find("billboard_engine")
```

The only real difference is `drawImage`: the Script Box takes the **name** of an image in its folder,
CC takes the image **data** (the bytes of the file).

Next: [Script Box](Script-Box), [Lua reference](Lua-reference), [Examples](Examples).
