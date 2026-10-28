package de.knito.billboard;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

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
		// Written only when missing. An older file keeps its script_* lines: Billboard API takes them over for the
		// Lua Box on its first start, and the engine itself no longer reads them.
		if (!Files.exists(file)) {
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

						# The Lua Box and its settings moved to Billboard API: config/billboard_api.properties.
						""".formatted(LIMIT_WIDTH, LIMIT_HEIGHT, maxWidth, maxHeight, blocksPerTick));
			}
		} catch (IOException e) {
			Billboard.LOGGER.warn("Could not write {}", file, e);
		}
	}
}
