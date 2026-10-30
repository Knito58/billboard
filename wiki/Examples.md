All examples are written for the [Lua Box](Lua-Box). For CC: Tweaked add
`local engine = peripheral.find("billboard_engine")` at the top
(and see the note at [Picture slide show](#picture-slide-show)).

## Clock

![A station clock at dusk showing 23:24 in white blocks](images/07-station-clock.jpg)

### A real time clock

The real time of the computer the world runs on (the server), standing still at the left edge.
`show()` does not redraw the same text, so calling it every second costs nothing.

```lua
engine.claim()
while true do
  engine.show(os.date("%H:%M"))
  sleep(1)
end
```

### A game time clock

`os.time()` gives the Minecraft time of day as hours with a fraction, for example `14.5` for 14:30,
so it needs formatting. `textutils.formatTime(time, true)` gives `9:05` before ten o'clock, one digit less, so the
clock would change its width. `string.format("%02d:%02d", hour, minute)` always writes two digits: `09:05`.
A Minecraft minute lasts less than a real second (a day is 20 minutes), so this clock looks twice a second.

```lua
engine.claim()
while true do
  local t = os.time()                             -- 14.5 means 14:30
  local h = math.floor(t)
  local m = math.floor((t - h) * 60)
  engine.show(string.format("%02d:%02d", h, m))   -- "09:05", not "9:05"
  sleep(0.5)
end
```

## Changing messages

```lua
local MESSAGES = { "WELCOME", "SHOP OPEN 9-17", "SALE: DIAMONDS" }

engine.claim()
engine.setPixel("minecraft:gold_block")
engine.setSpeed(6)
while true do
  for _, text in ipairs(MESSAGES) do
    engine.setText(text)
    engine.runOff()                                  -- comes in from the right, crosses once
    while engine.isRunningOff() do sleep(0.5) end
  end
end
```

## Right-aligned number

`go(column)` starts the text at a wall column. A text is `(6 * #text - 1) * size` columns wide.

```lua
engine.claim()
engine.setSpeed(1)          -- slow, so the text does not move a column between go() and stop()
local width = engine.getWallSize()
local function right(text)
  engine.setText(text)
  engine.go(math.max(0, width - (6 * #text - 1) * engine.getSize()))
  engine.stop()
end
for n = 10, 0, -1 do
  right(tostring(n))
  sleep(1)
end
```

## Picture slide show

Drop the program and the images onto the box.

```lua
local SLIDES = { "mona.png", "blocks.png" }
local SECONDS = 10

engine.claim()
while true do
  for _, name in ipairs(SLIDES) do
    print(name, engine.drawImage(name, { fit = "fit" }))
    sleep(SECONDS)
  end
end
```

On CC, `drawImage` takes the bytes of the file instead of its name:
`local f = fs.open(name, "rb") local data = f.readAll() f.close() engine.drawImage(data)`.

## A moving pixel

The middle dot `·` is exactly one pixel. With a redstone block as pixel it walks along the wall and powers what
sits behind each column on its way; see [Redstone punch card](Redstone-punch-card).

```lua
engine.claim()
engine.setPixel("minecraft:redstone_block")
engine.setSize(1)
engine.setSpeed(4)
engine.setText("\183")       -- the middle dot
engine.runOff()
while engine.isRunningOff() do sleep(0.2) end
```

## Countdown launch show

![The launch site with the countdown on the wall](images/10-countdown-show.jpg)

A countdown from 10 at the right end of the wall in gold. The 0 runs off, then a single redstone block walks to the
left end of the wall and sets off a contraption there: a lamp, a repeater line, a door, a minecart, fireworks.
One engine does it all.

```lua
local START, SIZE = 10, 1
local width = engine.getWallSize()
if not width then error("The engine has no wall marked (wand missing?)", 0) end
local function rightColumn(text) return math.max(0, width - (6 * #text - 1) * SIZE) end

engine.claim()
engine.setPixel("minecraft:gold_block")
engine.setSize(SIZE)
engine.setSpeed(1)
for n = START, 0, -1 do
  engine.setText(tostring(n))
  engine.go(rightColumn(tostring(n)))
  engine.stop()
  sleep(1)
end

engine.setSpeed(8)                               -- the 0 walks off to the left
engine.runOff()
while engine.isRunningOff() do sleep(0.2) end

engine.setPixel("minecraft:redstone_block")      -- one dot carries the signal to the left end
engine.setText("\183")
engine.setSpeed(4)
engine.runOff()
while engine.isRunningOff() do sleep(0.2) end
print("Launched.")
```

Behind the left end of the wall, at the height of the text's middle row, a redstone lamp or comparator picks up the passing dot.
Build your contraption from there.
