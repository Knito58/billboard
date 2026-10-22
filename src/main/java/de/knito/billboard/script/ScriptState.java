package de.knito.billboard.script;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** What the Script Box's light shows. A sleeping program counts as running, so the block state rarely changes. */
public enum ScriptState implements StringRepresentable {
	STOPPED, RUNNING, ERROR;

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
