package de.knito.billboard.block;

/**
 * Something that can claim an engine: a Lua Box or a CC: Tweaked computer. While one holds the claim,
 * only it may change the engine; everyone may still read it. Claims are not saved.
 */
public interface EngineController {
	/** Shown to players and scripts, such as "Lua Box at 12 64 -3". */
	String describe();

	/** False once the program behind it has ended; the engine then forgets the claim by itself. */
	boolean isActive();
}
