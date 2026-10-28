Several Lua Boxes and CC computers may touch the same engine. Without rules they would fight over it.
`claim()` and `release()` decide who may change it.

## The rules

- `engine.claim()` gives the engine to this program. It returns `true`, or `false` plus who holds it. It never waits.
- While an engine is claimed, only its holder may change it. Everyone may still read it (`getText`, `isRunning`, ...).
  A change by anyone else is a Lua error.
- The claim ends with `engine.release()`, or by itself when the program ends, fails, is restarted or stopped,
  its box is broken or unloaded, or the computer shuts down.
- A claimed engine ignores its sign and redstone. In its screen only **Stop** and **Restore** work, as an
  emergency brake; text, speed, size and Go are greyed out.
- An engine nobody claims works as before: screen, sign and every program may change it.

A program that owns its engine alone should simply call `engine.claim()` once at the top.

## Taking turns

Two boxes, each with its own text, share one wall. Each waits for its turn, lets its text run through once,
then gives the engine to the other:

```lua
-- turns.lua: put this into two Lua Boxes, with a different TEXT in each
local TEXT = "BOX ONE"

while true do
  while not engine.claim() do sleep(0.5) end   -- wait for our turn
  engine.setText(TEXT)
  engine.setSpeed(7)
  engine.runOff()
  while engine.isRunningOff() do sleep(0.2) end
  engine.release()                             -- let the other box have it
  sleep(1)                                     -- give it a chance to claim
end
```
