## Commands

All commands need operator rights (level 2), so they also work in a command block.

| Command | Does |
| --- | --- |
| `/billboard runoff <x y z>` | Lets the text of the engine at that position run off the wall once, then restores the wall. Same as `runOff()`. |

## Settings

`config/billboard.properties` is written at the first start. On a server it is the server's file that counts.
An older file gets new lines added at the next start; your values stay.

The Lua Box has its own command (`/luabox`) and settings (`config/billboard_api.properties`), see [Billboard API](https://github.com/Knito58/Billboard-API).

| Setting | Default | Meaning |
| --- | ---: | --- |
| `max_width` | 256 | Widest wall the wand may mark, up to 512. |
| `max_height` | 384 | Highest wall; 384 is the full height of the world. |
| `blocks_per_tick` | 8192 | Picture blocks an engine may change per tick. Lower it if pictures make the server lag. |

## Block tag

`billboard:not_for_pictures` lists blocks that pictures never use. A data pack can add to it,
see [Pictures and animations](Pictures-and-animations#the-blocks).

## Files in the world

| Path | Holds |
| --- | --- |
| `data/billboard/wall_backups.dat` | The original blocks of every active wall. |
