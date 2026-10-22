Right-click the Billboard Engine to open its screen.

![The engine screen with wand, pixel block, text, speed, size and the Go, Stop and Restore buttons](images/02-engine-screen.jpg)

| Part | What it does |
| --- | --- |
| Left slot | The **Selector Wand** with both corners marked on the wall this engine touches. Other wands are refused. Breaking the engine drops it. |
| Right slot | The **pixel block**. Click it with any full block, or drag one there from [JEI](https://modrinth.com/mod/jei) or [REI](https://modrinth.com/mod/rei). The slot only keeps a faded copy as a setting: your block stays in your inventory and is never used up. Click with an empty hand to clear it. |
| Text | Up to 64 characters. See [Letters and sizes](Letters-and-sizes) for the characters. |
| Speed - / + | 1 to 10 block columns per second. Default 5. Takes effect at once. |
| Size - / + | Letter size 1 to 9. A size change redraws a shown text at once. |
| **Go** | Starts the text from the right edge of the wall. |
| **Stop** | Freezes the text where it is. Go starts it again from the right. |
| **Restore** | Puts the original wall back. |
| Status line | What the engine is doing, or why it can't start. The same message also goes to your chat, so long messages can be read in full. |

## Messages

| Message | Meaning |
| --- | --- |
| Put a selector wand with two marked corners in the left slot. | No wand, or one without both corners. |
| Pick a pixel block | The right slot is empty. |
| Engine must touch the wall. | The engine does not touch the marked wall with a full face. |
| The engine must stand behind the wall, not beside it | It stands in the same plane as the wall. |
| The wall is ... It must be flat | The corners are not in one plane. |
| The wall is ... Maximum is ... | Too big; see [settings](Commands-and-settings). |
| Can't use this wall: ... | A block entity (chest, sign, ...) is in the wall. |
| That wall overlaps another billboard. | Another engine is using part of it. |
| Part of the wall is not loaded. Come closer. | Some of its chunks are not loaded. |
| The engine is controlled by ... Only Stop and Restore work. | A program has [claimed](Sharing-an-engine) the engine. Text, speed, size and Go are greyed out. |

## Breaking the engine

Breaking the engine restores the wall and drops the engine and the wand. The pixel block is only a copy, so nothing else drops.
