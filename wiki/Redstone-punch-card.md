Use **redstone blocks** as pixels and the wall becomes a punch card for redstone behind it.
Use **redstone lamps** as pixels and the text lights up from behind.

## Redstone blocks as pixels

Put a redstone block into the pixel slot. As the text scrolls, every lit pixel is a redstone block, and it powers
what sits next to it, just as a hand-placed redstone block would:

- redstone lamps light up,
- redstone dust carries the signal on,
- repeaters and comparators react,
- pistons, doors and note blocks behind the wall fire.

The engine tells the neighbours about every swap, so all of this happens in the normal redstone way.
It does not send shape updates, so levers, torches and dust attached to the wall stay where they are.

A single moving redstone block is often the most useful: the text `·` (middle dot) is exactly one pixel.
The countdown launch show in [Examples](Examples) uses it to trigger a contraption at the end of the wall.

## Redstone lamps as pixels

Put a redstone lamp into the pixel slot. The text is drawn in lamps, and every lamp pixel that sits next to
a powered block lights up: a lever, a redstone block or powered dust behind the wall. A row of redstone blocks
behind the wall makes a band of light the text runs through. Blocks in the wall may change their own state
like this; they just can't be replaced while the billboard is shown.

## The engine and its own redstone

The engine ignores redstone power coming from the side that touches the wall, so a redstone-block pixel
next to the engine can't start it by accident. All other sides take a pulse: see [Sign and pulse](Sign-and-pulse).
