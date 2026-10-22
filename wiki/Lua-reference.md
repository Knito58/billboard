These functions are the same in the [Script Box](Script-Box) (`engine.xxx`) and in
[CC: Tweaked](Two-ways-to-script) (`local engine = peripheral.find("billboard_engine")`).
The only difference is the first argument of `drawImage`.

## How results work

- Functions that can fail return `true`, or `false` plus a short **reason** such as `"need_wand"`
  (list below). Check them: `local ok, reason = engine.go()`.
- A wrong argument (speed 12, an unknown block) is a Lua **error** with a message.
- While another program has [claimed](Sharing-an-engine) the engine, every function that changes something
  is an error (`engine is claimed by ...`). Reading functions always work.

## Text

| Function | Does | Returns |
| --- | --- | --- |
| `setText(text)` | Sets the text for the next `go()`. Up to 64 characters. The wall keeps the old text until then. | |
| `getText()` | | the text |
| `setSpeed(n)` | 1 to 10 block columns per second. Takes effect at once. | |
| `getSpeed()` | | 1 to 10 |
| `setSize(n)` | Letter size 1 to 9. Takes effect at once, also on a stopped text. | |
| `getSize()` | | 1 to 9 |
| `setPixel(id)` | Pixel block for the next `go()`, for example `"minecraft:gold_block"`. Any full block; no item needed. | |
| `getPixel()` | | the block id, or `nil` |
| `go()` | Starts the text from the right edge. | `true`, or `false` + reason |
| `go(column)` | The text starts at that wall column (0 = left edge). | `true`, or `false` + reason |
| `show(text)` | `setText`, `go` and `stop` in one: the text stands still at the left edge. The same text again is not redrawn, so a clock can call it every second. | `true`, or `false` + reason |
| `stop()` | Freezes the text (or an animation) where it is. | `true`, or `false, "not_running"` |
| `restore()` | Puts the wall back. | `true`, or `false, "nothing_to_restore"` |
| `runOff()` | A shown text scrolls on until it has left the wall, then the wall is restored. On an empty wall the text comes in from the right, crosses once and is gone. | `true`, or `false` + reason |
| `isRunning()` | | `true` while the text (or an animation) moves |
| `isShowing()` | | `true` while the wall shows text, running or stopped |
| `isRunningOff()` | | `true` until a `runOff()` is done and the wall is back |
| `getWallSize()` | | width, height of the shown wall, else of the wall the wand marks; or `nil` + reason |

To place a text at the right end: it is `(6 * #text - 1) * size` columns wide, so
`engine.go(width - (6 * #text - 1) * size)`.

## Pictures

| Function | Does | Returns |
| --- | --- | --- |
| `drawImage(name, options)` | **Script Box**: shows the image file of that name from the box's folder. | `true`, or `false` + reason |
| `drawImage(data, options)` | **CC: Tweaked**: shows an image from its bytes (read from a file in binary mode, or from `http.get(url, nil, true)`). | `true`, or `false` + reason |
| `drawPixels(x, y, w, h, data)` | Draws a rectangle of pixels made by your program. `x`, `y` from 1 at the top left. `data` = 3 bytes per pixel (red, green, blue) or 4 (plus alpha; below 128 keeps the wall's own block), row by row. | `true`, or `false` + reason |
| `isPainting()` | | `true` until the picture (or the first frame of an animation) is completely on the wall |
| `isShowingPicture()` | | `true` while the wall shows a picture, not text |
| `play()` | Lets an animation that was stopped with `stop()` run again. | `true` or `false` |

`options` is a table, all fields optional:

| Option | Values | Default |
| --- | --- | --- |
| `fit` | `"fit"` whole image, centered, wall's own blocks around it; `"fill"` covers the wall, cuts off the rest; `"stretch"` pulls the image to the wall's shape | `"fit"` |
| `dither` | `true` mixes neighbouring blocks for in-between colors. Good for still pictures, makes animations flicker. | `false` |
| `loop` | `false` plays an animated GIF once and stops on the last frame | `true` |

Everything about colors, blocks and limits: [Pictures and animations](Pictures-and-animations).

## Sharing

| Function | Does | Returns |
| --- | --- | --- |
| `claim()` | Takes the engine for this program alone. Never waits. | `true`, or `false` + who holds it |
| `release()` | Gives the engine free again. | `true`, or `false` if this program did not hold it |
| `isClaimed()` | | `false`, or `true` + who holds it |

Details: [Sharing an engine](Sharing-an-engine).

## Reasons

| Reason | Meaning |
| --- | --- |
| `need_wand` | No wand in the engine, or one without both corners. |
| `need_pixel` | No pixel block set (`setPixel`). |
| `need_text` | The text is empty. |
| `not_attached` | The engine does not touch the marked wall with a full face. |
| `engine_in_plane` | The engine stands beside the wall in the same plane instead of behind it. |
| `not_flat` | The wand's corners are not in one plane. |
| `too_big` | The wall is larger than the server allows. |
| `other_dimension` | The corners are in another dimension. |
| `overlap` | The wall overlaps another billboard. |
| `not_loaded` | Part of the wall is not loaded. |
| `block_entity` | A chest, sign or other block entity is in the wall. |
| `not_running` | `stop()` with nothing moving. |
| `nothing_to_restore` | `restore()` with nothing shown. |

## Only in the Script Box

`sleep(seconds)`, `print(...)` (to the box's output lines), `os.time()`, `os.date(format)`, `os.clock()`,
`os.day()`, `os.epoch()`, `textutils.formatTime(time, twentyFour)`, as in CC: Tweaked.
Plus standard Lua: `string`, `table`, `math`, `utf8`, `coroutine`.

Characters above 127, like `·` or `ä`, are written as byte escapes in Lua: `"\183"` is `·`, `"\228"` is `ä`.
Typing `·` directly into a file saved as UTF-8 gives two wrong characters.
