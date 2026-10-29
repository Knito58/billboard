## On a server

- Install Billboard Engine and Fabric API on the server **and** on every client.
- Billboard API (Lua Box, Data Pipes) is optional. If you use it, it must be on the server and the clients too.
- CC: Tweaked is optional. If you use it, it must be on the server and the clients too, as usual.
- Lua Boxes run on the server. Their files and settings belong to [Billboard API](https://github.com/Knito58/Billboard-API); an admin restarts a box
  with `/luabox <x y z> restart`. Each box gets only a small slice of Lua time per tick, so a bad program
  can't freeze the server.
- Big pictures are spread over ticks by `blocks_per_tick`. Lower it if a new picture makes the server stutter.
- Walls in unloaded chunks pause and cost nothing.

All settings: [Commands and settings](Commands-and-settings).

## Questions

**The engine says "Engine must touch the wall".**
It has to stand directly behind the wall and touch one wall block with a full face. Diagonal or a block away is not enough.

**My text is cut off at the bottom.**
The wall is lower than the text. At size 1 a text needs 7 rows, at size n 7 x n. Mark a taller wall or pick a smaller size.

**Can I mine the gold-block text?**
No. The wall is frozen while a billboard is shown, and the pixel block was never taken from you.

**What happens if the game crashes while a text is shown?**
The original wall is saved in the world. After the restart the billboard is still there and Restore brings the wall back.

**Why is my red picture orange?**
Blocks can't be as bright as a screen; pure red is closest to orange concrete. See
[Pictures and animations](Pictures-and-animations#colors).

**Do I need CC: Tweaked?**
No. The [Lua Box](Lua-Box) runs Lua without any other mod. CC: Tweaked is supported if you already use it.

**Can the engine show text on a floor or ceiling?**
No, only on walls standing upright.

**Does it work with Create?**
A Display Link can write into a sign on the engine; see [Sign and pulse](Sign-and-pulse).

**My script stops with "Too long without sleep()" or "Too busy".**
Every loop needs a `sleep()`. See [When does the program run?](Lua-Box#when-does-the-program-run)

**Something else is wrong.**
Please open an issue: [github.com/Knito58/billboard/issues](https://github.com/Knito58/billboard/issues).
