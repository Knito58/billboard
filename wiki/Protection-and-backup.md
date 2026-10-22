## The backup

When a billboard starts, the engine saves the original blocks of the whole wall in the world save
(`data/billboard/wall_backups.dat`). A crash or a server restart does not lose them.

The wall comes back:

- when you press **Restore**,
- when you break the engine,
- after `runOff()` or `/billboard runoff`, once the text has left the wall.

**Stop** does not restore: the text stays where it is.

## The frozen wall

While a billboard is shown (running or stopped), no block of its wall can be replaced:

- players can't mine it, also not in creative mode (*This block belongs to an active billboard. Press Restore on its engine first.*),
- explosions (TNT, creepers) leave it standing,
- pistons can't push or pull it,
- mobs can't change it (endermen, withers),
- `/setblock` and `/fill` can't change it.

That is why the pixel block is free: nobody can mine a gold-block text and keep the gold.

Blocks in the wall may still change their own state, for example a redstone lamp lighting up.
Blocks attached to the wall from outside (levers, torches, vines, buttons) stay put.

## Unloaded chunks

Scrolling pauses while the wall's chunks are not loaded and goes on when you come back.
A billboard far away from all players therefore does not use any server time.
