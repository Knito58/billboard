package de.knito.billboard.wall;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Writes text onto a wall that has already been backed up. */
public final class BillboardPainter {
	private BillboardPainter() {
	}

	/**
	 * Draws the text in pixel blocks; unlit pixels keep the wall's own block.
	 * Text that fits is centered; longer text is cut at the right edge.
	 * A wall taller than 7 rows gets the text vertically centered; a shorter one loses the bottom rows.
	 */
	public static void paintStatic(ServerLevel level, Wall wall, String text, BlockState pixel) {
		boolean[][] bitmap = BillboardFont.render(text);
		int width = wall.width();
		int height = wall.height();
		int left = bitmap.length < width ? (width - bitmap.length) / 2 : 0;
		int top = height > BillboardFont.HEIGHT ? (height - BillboardFont.HEIGHT) / 2 : 0;

		for (int column = 0; column < bitmap.length && left + column < width; column++) {
			for (int row = 0; row < BillboardFont.HEIGHT && top + row < height; row++) {
				if (bitmap[column][row]) {
					BlockPos pos = wall.pixel(left + column, top + row);
					level.setBlock(pos, pixel, Block.UPDATE_CLIENTS);
				}
			}
		}
	}
}
