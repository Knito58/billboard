package de.knito.billboard.wall;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws text frames onto a wall that has already been backed up.
 * Lit pixels get the pixel block, unlit pixels keep (or get back) the wall's own block.
 */
public final class BillboardPainter {
	/**
	 * Neighbours hear about the change, so redstone blocks as pixels power lamps and dust next to
	 * the wall like a punch card. Shape updates are skipped: levers, torches or vines attached to
	 * the wall stay where they are while the blocks behind them are swapped.
	 */
	static final int WALL_UPDATE = Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private BillboardPainter() {
	}

	/** Number of scroll steps until the text shows the same again: text, then a wall-wide gap. */
	public static int period(Wall wall, boolean[][] bitmap) {
		return bitmap.length + wall.width();
	}

	/** Offset at which the text starts at the wall's left edge. */
	public static int startOffset(Wall wall) {
		return wall.width();
	}

	/**
	 * Whether a wall pixel is lit at the given scroll offset. The text moves right to left as the offset grows.
	 * A wall taller than 7 rows gets the text vertically centered; a lower one loses the bottom rows.
	 */
	static boolean lit(Wall wall, boolean[][] bitmap, int offset, int column, int row) {
		int height = wall.height();
		int top = height > BillboardFont.HEIGHT ? (height - BillboardFont.HEIGHT) / 2 : 0;
		int textRow = row - top;
		if (textRow < 0 || textRow >= BillboardFont.HEIGHT) {
			return false;
		}
		int textColumn = column + offset - wall.width();
		return textColumn >= 0 && textColumn < bitmap.length && bitmap[textColumn][textRow];
	}

	/** Paints a whole frame onto a wall that still shows its original blocks. */
	public static void paint(ServerLevel level, Wall wall, boolean[][] bitmap, int offset, BlockState pixel) {
		ProtectedAreas.bypass(() -> {
			for (int column = 0; column < wall.width(); column++) {
				for (int row = 0; row < wall.height(); row++) {
					if (lit(wall, bitmap, offset, column, row)) {
						level.setBlock(wall.pixel(column, row), pixel, WALL_UPDATE);
					}
				}
			}
		});
	}

	/** Changes only the pixels that differ between two frames. */
	public static void step(ServerLevel level, BlockPos engine, BackupData backups, Wall wall, boolean[][] bitmap,
			int oldOffset, int newOffset, BlockState pixel) {
		ProtectedAreas.bypass(() -> {
			for (int column = 0; column < wall.width(); column++) {
				for (int row = 0; row < wall.height(); row++) {
					boolean now = lit(wall, bitmap, newOffset, column, row);
					if (now == lit(wall, bitmap, oldOffset, column, row)) {
						continue;
					}
					BlockPos pos = wall.pixel(column, row);
					BlockState target = now ? pixel : backups.original(engine, pos);
					if (target != null) {
						level.setBlock(pos, target, WALL_UPDATE);
					}
				}
			}
		});
	}
}
