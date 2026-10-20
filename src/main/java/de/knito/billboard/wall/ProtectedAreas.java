package de.knito.billboard.wall;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Fast lookup of every wall that currently shows a billboard. While a wall is listed here,
 * nothing but the mod itself may change its blocks: no players, explosions, pistons or mobs.
 * Otherwise pixel blocks could be mined and duplicated, or the backup would no longer match the wall.
 */
public final class ProtectedAreas {
	private static final Map<ResourceKey<Level>, List<BoundingBox>> AREAS = new HashMap<>();
	private static int bypassDepth;

	private ProtectedAreas() {
	}

	static void update(ResourceKey<Level> dimension, List<BoundingBox> boxes) {
		if (boxes.isEmpty()) {
			AREAS.remove(dimension);
		} else {
			AREAS.put(dimension, List.copyOf(boxes));
		}
	}

	public static void clear() {
		AREAS.clear();
		bypassDepth = 0;
	}

	public static boolean isProtected(Level level, BlockPos pos) {
		if (AREAS.isEmpty() || bypassDepth > 0 || !(level instanceof ServerLevel)) {
			return false;
		}
		List<BoundingBox> boxes = AREAS.get(level.dimension());
		if (boxes == null) {
			return false;
		}
		for (BoundingBox box : boxes) {
			if (box.isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Runs the mod's own wall changes past the protection. */
	public static void bypass(Runnable action) {
		bypassDepth++;
		try {
			action.run();
		} finally {
			bypassDepth--;
		}
	}
}
