package de.knito.billboard.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import de.knito.billboard.Billboard;

/**
 * The files of the Script Boxes: {@code <world>/billboard/scripts/<id>/} holds one .lua program and its images.
 * The block itself keeps only the id.
 */
public final class ScriptStorage {
	private static final Pattern ID = Pattern.compile("[0-9a-f]{12}");
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}");
	private static final List<String> IMAGE_TYPES = List.of(".png", ".jpg", ".jpeg", ".gif", ".bmp");

	private ScriptStorage() {
	}

	public static Path root(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("billboard").resolve("scripts").toAbsolutePath().normalize();
	}

	/** The folder of a box id, or null for an id that is not ours (so a crafted id can never point elsewhere). */
	public static Path folder(MinecraftServer server, String id) {
		return id != null && ID.matcher(id).matches() ? root(server).resolve(id) : null;
	}

	public static String newId(MinecraftServer server) {
		while (true) {
			String id = String.format(Locale.ROOT, "%012x", java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFFFL);
			if (!Files.exists(root(server).resolve(id))) {
				return id;
			}
		}
	}

	/** A file name a player may use: letters, digits, _ - . and no folders. */
	public static boolean isValidName(String name) {
		return NAME.matcher(name).matches() && !name.contains("..");
	}

	public static boolean isProgram(String name) {
		return name.toLowerCase(Locale.ROOT).endsWith(".lua");
	}

	public static boolean isImage(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return IMAGE_TYPES.stream().anyMatch(lower::endsWith);
	}

	/** All files in the folder, sorted by name; empty if there is no folder yet. */
	public static List<Path> files(Path folder) {
		if (folder == null || !Files.isDirectory(folder)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(folder)) {
			return stream.filter(Files::isRegularFile).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
		} catch (IOException e) {
			Billboard.LOGGER.warn("Could not list {}", folder, e);
			return List.of();
		}
	}

	/** The .lua files in the folder; a box with several of them refuses to start. */
	public static List<Path> programs(Path folder) {
		return files(folder).stream().filter(p -> isProgram(p.getFileName().toString())).toList();
	}

	/** An image file of the box by its name, or null if there is no such image. */
	public static Path image(Path folder, String name) {
		if (folder == null || !isValidName(name) || !isImage(name)) {
			return null;
		}
		Path file = folder.resolve(name);
		return Files.isRegularFile(file) ? file : null;
	}

	public static void delete(Path folder) {
		if (folder == null || !Files.isDirectory(folder)) {
			return;
		}
		try {
			for (Path file : files(folder)) {
				Files.deleteIfExists(file);
			}
			Files.deleteIfExists(folder);
		} catch (IOException e) {
			Billboard.LOGGER.warn("Could not delete {}", folder, e);
		}
	}

	/** Copies the files of one box to a new one, for a box copied with creative pick-block. */
	public static void copy(Path from, Path to) {
		if (from == null || to == null || !Files.isDirectory(from)) {
			return;
		}
		try {
			Files.createDirectories(to);
			for (Path file : files(from)) {
				Files.copy(file, to.resolve(file.getFileName()));
			}
		} catch (IOException e) {
			Billboard.LOGGER.warn("Could not copy {} to {}", from, to, e);
		}
	}
}
