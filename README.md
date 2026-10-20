# Billboard

Fabric mod for Minecraft: turns any flat wall into a scrolling text billboard made of blocks.

## Status

Step 2 of 4: engine, selector wand and static text. Scrolling and wall protection come in step 3.

## How to use

1. Craft a **Selector Wand** (redstone torch above a stick) and a **Billboard Engine**
   (iron / redstone / iron, redstone / glass pane / redstone, iron / redstone / iron).
2. Right-click one corner of a flat wall with the wand, then the opposite corner.
   While you hold the wand, the action bar shows the size. Sneak + right-click clears the corners.
3. Place the engine in front of the wall (on the side the text should be read from), at most 16 blocks away.
4. Open the engine, put the wand in the left slot and a block (e.g. gold block) in the right slot.
   The block is not used up; it only sets what the letters are made of.
5. Type a text and press **Go**. **Restore** puts the wall back. Breaking the engine also restores the wall.

The font is 5x7. On a taller wall the text is centered vertically; on a lower one the bottom rows are cut off.
Text longer than the wall is cut at the right edge (until scrolling arrives).
Walls containing chests, signs or other block entities are refused. Maximum wall size is 256 x 64.
The original blocks are saved in the world (`data/billboard/wall_backups.dat`), so a crash does not lose them.

## Build and run (Linux)

Requires Java 25.

```
./gradlew runClient    # start Minecraft 26.3 with the mod
./gradlew build        # mod jar ends up in build/libs/
```

In IntelliJ IDEA: *File → Open* the project folder, wait for the Gradle import, then run the
`Minecraft Client` run configuration.
