package de.knito.billboard.script;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Helper threads for the Script Boxes. The Lua programs themselves always run in the server tick. */
final class ScriptThreads {
	/** Wakes a Lua program when its time slice for this tick is used up, so it pauses until the next tick. */
	static final ScheduledExecutorService ALARM = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().daemon().name("Billboard Script Alarm").factory());
	/** Reads and decodes image files for drawImage, so a large GIF does not hold up the tick. */
	static final ExecutorService IMAGES = Executors.newFixedThreadPool(2,
			Thread.ofPlatform().daemon().name("Billboard Script Images ", 1).factory());

	private ScriptThreads() {
	}
}
