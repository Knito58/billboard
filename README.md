# Billboard

Fabric mod for Minecraft: turns any flat wall into a scrolling text billboard made of blocks.

## Status

Step 1 of 4: project skeleton. The mod adds one placeholder block, the **Billboard Engine**
(creative tab "Functional Blocks", or `/give @s billboard:billboard_engine`).

## Build and run (Linux)

Requires Java 25.

```
./gradlew runClient    # start Minecraft 26.3 with the mod
./gradlew build        # mod jar ends up in build/libs/
```

In IntelliJ IDEA: *File → Open* the project folder, wait for the Gradle import, then run the
`Minecraft Client` run configuration.
