## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for Minecraft 26.3. Minecraft 26.3 needs Java 25.
2. Put the Billboard Engine jar and [Fabric API](https://modrinth.com/mod/fabric-api) into the `mods` folder.
3. On a server: install both on the server **and** on every client.

[CC: Tweaked](https://modrinth.com/mod/cc-tweaked) is optional. Everything except the ComputerCraft peripheral works without it.

## Recipes

**Selector Wand**: a redstone torch above a stick.

| | | |
|:-:|:-:|:-:|
| | Redstone torch | |
| | Stick | |

**Billboard Engine**

| | | |
|:-:|:-:|:-:|
| Iron ingot | Redstone | Iron ingot |
| Redstone | Glass pane | Redstone |
| Iron ingot | Redstone | Iron ingot |

**Script Box**: the engine recipe with paper in the middle instead of the glass pane.

| | | |
|:-:|:-:|:-:|
| Iron ingot | Redstone | Iron ingot |
| Redstone | Paper | Redstone |
| Iron ingot | Redstone | Iron ingot |

With JEI or REI installed you can look them up in game, as usual.

## Your first billboard

1. Build a flat wall, one block thick, for example 40 wide and 9 high out of stone.
2. Right-click one corner of the wall with the **Selector Wand**, then the opposite corner.
   The action bar shows the size while you hold the wand.
3. Go behind the wall and place the **Billboard Engine** so that it touches the back of one wall block with a full face.
   The text is read from the front, so the engine stays hidden.
4. Open the engine. Put the wand into the left slot. Click the right slot with a block, for example a gold block.
   The slot only copies it; you keep your block.
5. Type `HELLO WORLD` and press **Go**. The text scrolls from right to left.

**Stop** freezes the text, **Restore** puts the wall back.

Next: [Walls and the Selector Wand](Walls-and-the-Selector-Wand) has all the rules for walls,
[The engine screen](The-engine-screen) every button.
