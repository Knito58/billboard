## Commands

All commands need operator rights (level 2), so they also work in a command block.

| Command | Does |
| --- | --- |
| `/billboard runoff <x y z>` | Lets the text of the engine at that position run off the wall once, then restores the wall. Same as `runOff()`. |
| `/billboard script <x y z> status` | Shows the Script Box's state, its folder and its last output lines. |
| `/billboard script <x y z> restart` | Starts the box's program again from the top. |
| `/billboard script <x y z> stop` | Stops the program (the hand brake, as the Stop button). |

## Settings

`config/billboard.properties` is written at the first start. On a server it is the server's file that counts.
An older file gets new lines added at the next start; your values stay.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `max_width` | 256 | Widest wall the wand may mark, up to 512. |
| `max_height` | 384 | Highest wall; 384 is the full height of the world. |
| `blocks_per_tick` | 8192 | Picture blocks an engine may change per tick. Lower it if pictures make the server lag. |
| `scripts_enabled` | true | Script Boxes allowed at all. |
| `scripts_ops_only` | false | Only operators may give boxes files, restart or stop them. |
| `script_ms_per_tick` | 2 | Lua time per box and tick, in milliseconds. |
| `script_max_busy_seconds` | 5 | A program is stopped after this long without `sleep()`. |
| `script_too_busy_seconds` | 30 | A program is stopped after using its whole time this long. |
| `script_max_lua_kb` | 64 | Largest program file. |
| `script_max_images` | 8 | Images per box. |
| `script_max_image_kb` | 2048 | Largest image file per box. |

## Block tag

`billboard:not_for_pictures` lists blocks that pictures never use. A data pack can add to it,
see [Pictures and animations](Pictures-and-animations#the-blocks).

## Files in the world

| Path | Holds |
| --- | --- |
| `data/billboard/wall_backups.dat` | The original blocks of every active wall. |
| `billboard/scripts/<id>/` | One folder per Script Box: its program and images. |
