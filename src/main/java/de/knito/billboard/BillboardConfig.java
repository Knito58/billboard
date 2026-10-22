package de.knito.billboard;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

import de.knito.billboard.wall.Picture;

/**
 * Server settings in {@code config/billboard.properties}. The file is written with the defaults
 * on first start; values outside the allowed range are clamped.
 */
public final class BillboardConfig {
	/** Hard ceiling for the wall width; a config cannot go above it. */
	public static final int LIMIT_WIDTH = 512;
	/** Hard ceiling for the wall height: the full build height of the overworld. */
	public static final int LIMIT_HEIGHT = 384;

	private static int maxWidth = 256;
	private static int maxHeight = LIMIT_HEIGHT;
	private static int blocksPerTick = 8192;
	private static boolean scriptsEnabled = true;
	private static boolean scriptsOpsOnly = false;
	private static int scriptMillisPerTick = 2;
	private static int scriptMaxSecondsWithoutSleep = 5;
	private static int scriptTooBusySeconds = 30;
	private static int scriptMaxLuaKb = 64;
	private static int scriptMaxImages = 8;
	private static int scriptMaxImageKb = 2048;

	private BillboardConfig() {
	}

	public static int maxWidth() {
		return maxWidth;
	}

	public static int maxHeight() {
		return maxHeight;
	}

	/** How many picture blocks an engine may change per game tick; a bigger change is spread over several ticks. */
	public static int blocksPerTick() {
		return blocksPerTick;
	}

	public static boolean scriptsEnabled() {
		return scriptsEnabled;
	}

	/** Only operators may put files into Script Boxes, restart or stop them. */
	public static boolean scriptsOpsOnly() {
		return scriptsOpsOnly;
	}

	/** Lua time each Script Box gets per game tick before it pauses until the next tick. */
	public static int scriptMillisPerTick() {
		return scriptMillisPerTick;
	}

	public static int scriptMaxSecondsWithoutSleep() {
		return scriptMaxSecondsWithoutSleep;
	}

	public static int scriptTooBusySeconds() {
		return scriptTooBusySeconds;
	}

	public static int scriptMaxLuaKb() {
		return scriptMaxLuaKb;
	}

	public static int scriptMaxImages() {
		return scriptMaxImages;
	}

	public static int scriptMaxImageKb() {
		return scriptMaxImageKb;
	}

	static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("billboard.properties");
		Properties properties = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				properties.load(reader);
			} catch (IOException e) {
				Billboard.LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		maxWidth = read(properties, "max_width", maxWidth, 1, LIMIT_WIDTH);
		maxHeight = read(properties, "max_height", maxHeight, 1, LIMIT_HEIGHT);
		blocksPerTick = read(properties, "blocks_per_tick", blocksPerTick, 256, 262144);
		scriptsEnabled = readBoolean(properties, "scripts_enabled", scriptsEnabled);
		scriptsOpsOnly = readBoolean(properties, "scripts_ops_only", scriptsOpsOnly);
		scriptMillisPerTick = read(properties, "script_ms_per_tick", scriptMillisPerTick, 1, 20);
		scriptMaxSecondsWithoutSleep = read(properties, "script_max_busy_seconds", scriptMaxSecondsWithoutSleep, 1, 60);
		scriptTooBusySeconds = read(properties, "script_too_busy_seconds", scriptTooBusySeconds, 5, 3600);
		scriptMaxLuaKb = read(properties, "script_max_lua_kb", scriptMaxLuaKb, 1, 1024);
		scriptMaxImages = read(properties, "script_max_images", scriptMaxImages, 0, 64);
		scriptMaxImageKb = read(properties, "script_max_image_kb", scriptMaxImageKb, 16, Picture.MAX_BYTES / 1024);
		// A file from an older version lacks the newer keys: write it again with the values just read.
		if (!Files.exists(file) || !properties.containsKey("script_max_image_kb")) {
			write(file);
		}
	}

	private static int read(Properties properties, String key, int fallback, int min, int max) {
		String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			return Math.clamp(Integer.parseInt(value.strip()), min, max);
		} catch (NumberFormatException e) {
			Billboard.LOGGER.warn("billboard.properties: {}={} is not a number, using {}", key, value, fallback);
			return fallback;
		}
	}

	private static boolean readBoolean(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.strip());
	}

	private static void write(Path file) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				writer.write("""
						# Billboard Engine settings. Read at game or server start.

						# Largest wall the Selector Wand may mark, in blocks. Up to %d wide and %d high.
						max_width=%d
						max_height=%d

						# Picture blocks an engine may change per tick (20 ticks per second). A bigger change,
						# such as a new picture on a large wall, is spread over several ticks. Lower it if
						# pictures make the server lag; raise it for faster animations on a strong machine.
						blocks_per_tick=%d

						# Script Boxes run Lua programs on the server. Switch them off, or allow only operators
						# to give them files and to restart or stop them (good for public servers).
						scripts_enabled=%b
						scripts_ops_only=%b

						# Lua time per Script Box and tick in milliseconds; then the program pauses until the next tick.
						script_ms_per_tick=%d
						# A program that runs this many seconds without sleep() is stopped.
						script_max_busy_seconds=%d
						# A program that uses its whole time for this many seconds, with no sleep() longer than one tick, is stopped as too busy.
						script_too_busy_seconds=%d

						# Largest program file, number of images per box, largest image file.
						script_max_lua_kb=%d
						script_max_images=%d
						script_max_image_kb=%d
						""".formatted(LIMIT_WIDTH, LIMIT_HEIGHT, maxWidth, maxHeight, blocksPerTick,
						scriptsEnabled, scriptsOpsOnly, scriptMillisPerTick, scriptMaxSecondsWithoutSleep, scriptTooBusySeconds,
						scriptMaxLuaKb, scriptMaxImages, scriptMaxImageKb));
			}
		} catch (IOException e) {
			Billboard.LOGGER.warn("Could not write {}", file, e);
		}
	}
}
