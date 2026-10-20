package de.knito.billboard.wall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import de.knito.billboard.Billboard;

/**
 * Original blocks of every wall that currently shows a billboard, per dimension.
 * Kept in the world save so a crash or restart never loses a wall.
 */
public class BackupData extends SavedData {
	/** One saved wall: its box, which side it faces, and the original states (palette + index per block, x/y/z order). */
	public record Entry(Wall wall, List<BlockState> palette, int[] indices) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("min").forGetter(e -> e.wall.min()),
				BlockPos.CODEC.fieldOf("max").forGetter(e -> e.wall.max()),
				Direction.CODEC.fieldOf("facing").forGetter(e -> e.wall.facing()),
				BlockState.CODEC.listOf().fieldOf("palette").forGetter(Entry::palette),
				Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("blocks").forGetter(Entry::indices)
		).apply(i, (min, max, facing, palette, indices) -> new Entry(new Wall(min, max, facing), palette, indices)));

		static Entry capture(ServerLevel level, Wall wall) {
			List<BlockState> palette = new ArrayList<>();
			Map<BlockState, Integer> lookup = new HashMap<>();
			int[] indices = new int[volume(wall)];
			int n = 0;
			for (BlockPos pos : positions(wall)) {
				BlockState state = level.getBlockState(pos);
				indices[n++] = lookup.computeIfAbsent(state, s -> {
					palette.add(s);
					return palette.size() - 1;
				});
			}
			return new Entry(wall, palette, indices);
		}

		void restore(ServerLevel level) {
			ProtectedAreas.bypass(() -> restoreNow(level));
		}

		private void restoreNow(ServerLevel level) {
			int n = 0;
			for (BlockPos pos : positions(wall)) {
				int index = n < indices.length ? indices[n] : -1;
				n++;
				if (index < 0 || index >= palette.size()) {
					continue;
				}
				BlockState original = palette.get(index);
				if (level.getBlockState(pos) != original) {
					level.setBlock(pos, original, BillboardPainter.WALL_UPDATE);
				}
			}
		}

		/** The block that was at this wall position before the billboard started. */
		BlockState original(BlockPos pos) {
			BlockPos min = wall.min();
			BlockPos size = wall.max().subtract(min).offset(1, 1, 1);
			// Same order as BlockPos.betweenClosed: x fastest, then y, then z.
			int index = (pos.getX() - min.getX()) + size.getX() * ((pos.getY() - min.getY()) + size.getY() * (pos.getZ() - min.getZ()));
			if (index < 0 || index >= indices.length || indices[index] < 0 || indices[index] >= palette.size()) {
				return null;
			}
			return palette.get(indices[index]);
		}

		private static int volume(Wall wall) {
			BlockPos size = wall.max().subtract(wall.min()).offset(1, 1, 1);
			return size.getX() * size.getY() * size.getZ();
		}

		private static Iterable<BlockPos> positions(Wall wall) {
			// betweenClosed walks x fastest, then y, then z; the order only has to stay the same.
			return BlockPos.betweenClosed(wall.min(), wall.max());
		}
	}

	private record Stored(BlockPos engine, Entry entry) {
		static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("engine").forGetter(Stored::engine),
				Entry.CODEC.fieldOf("backup").forGetter(Stored::entry)
		).apply(i, Stored::new));
	}

	private static final Codec<Map<BlockPos, Entry>> MAP_CODEC = Stored.CODEC.listOf().xmap(
			list -> {
				Map<BlockPos, Entry> result = new HashMap<>();
				list.forEach(stored -> result.put(stored.engine(), stored.entry()));
				return result;
			},
			map -> map.entrySet().stream().map(e -> new Stored(e.getKey(), e.getValue())).toList()
	);

	public static final Codec<BackupData> CODEC = RecordCodecBuilder.create(i -> i.group(
			MAP_CODEC.optionalFieldOf("walls", Map.of()).forGetter(d -> d.walls)
	).apply(i, BackupData::new));

	// Our own data has no vanilla data fixer; command storage's type is a pass-through, so nothing gets rewritten.
	public static final SavedDataType<BackupData> TYPE = new SavedDataType<>(
			Billboard.id("wall_backups"), BackupData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	/** Keyed by engine position. */
	private final Map<BlockPos, Entry> walls;
	private ResourceKey<Level> dimension;

	public BackupData() {
		this(Map.of());
	}

	private BackupData(Map<BlockPos, Entry> walls) {
		this.walls = new HashMap<>(walls);
	}

	public static BackupData get(ServerLevel level) {
		BackupData data = level.getDataStorage().computeIfAbsent(TYPE);
		if (data.dimension == null) {
			data.dimension = level.dimension();
			data.syncProtection();
		}
		return data;
	}

	private void syncProtection() {
		if (dimension != null) {
			ProtectedAreas.update(dimension, walls.values().stream().map(e -> e.wall().box()).toList());
		}
	}

	/** Original block at a wall position, or null if the engine has no backup or the position is outside it. */
	public BlockState original(BlockPos engine, BlockPos pos) {
		Entry entry = walls.get(engine);
		return entry == null ? null : entry.original(pos);
	}

	public boolean has(BlockPos engine) {
		return walls.containsKey(engine);
	}

	public Wall wallOf(BlockPos engine) {
		Entry entry = walls.get(engine);
		return entry == null ? null : entry.wall();
	}

	/** True if the wall overlaps a billboard owned by a different engine. */
	public boolean overlapsOther(BlockPos engine, Wall wall) {
		for (Map.Entry<BlockPos, Entry> e : walls.entrySet()) {
			if (!e.getKey().equals(engine) && e.getValue().wall().box().intersects(wall.box())) {
				return true;
			}
		}
		return false;
	}

	/** Saves the current blocks of the wall. Must happen before anything on the wall changes. */
	public void backup(ServerLevel level, BlockPos engine, Wall wall) {
		walls.put(engine.immutable(), Entry.capture(level, wall));
		setDirty();
		syncProtection();
	}

	/** Puts the original blocks back and forgets the backup. Returns false if there was none. */
	public boolean restore(ServerLevel level, BlockPos engine) {
		Entry entry = walls.get(engine);
		if (entry == null) {
			return false;
		}
		entry.restore(level);
		walls.remove(engine);
		setDirty();
		syncProtection();
		return true;
	}
}
