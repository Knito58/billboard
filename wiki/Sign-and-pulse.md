The engine can show the text of a sign without anyone opening its screen.

1. Put a sign on the engine: on any side or on top.
2. Give the engine a redstone pulse (button, lever, observer, daylight sensor ...) from any side except the one
   touching the wall.
3. The engine joins the sign's lines with spaces and presses **Go**.

While it runs, the engine reads the sign every second. When the text changes, the billboard starts again with
the new text. A pulse while the same text is already running changes nothing.

- Wax the sign with a honeycomb so nobody can edit it by accident. The engine still reads it.
- Create's **Display Link** writes into vanilla signs, so it can feed a billboard this way, without any Create
  support in this mod.
- Speed, size, Stop and Restore stay in the engine's screen.
- An engine that a program has [claimed](Sharing-an-engine) ignores its sign and redstone.

For anything more (clocks, changing texts, pictures, timing) use [scripting](Two-ways-to-script).
