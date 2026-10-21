package de.knito.billboard.wall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import de.knito.billboard.BillboardConfig;

/**
 * A flat, vertical wall of blocks seen from one side.
 * Column 0 is the viewer's left edge, row 0 is the top row.
 */
public record Wall(BlockPos min, BlockPos max, Direction facing) {
	public static final int MAX_ENGINE_DISTANCE = 16;

	/** Either a wall or the reason why there is none. */
	public record Result(Wall wall, Component error) {
		static Result ok(Wall wall) {
			return new Result(wall, null);
		}

		static Result fail(Component error) {
			return new Result(null, error);
		}

		public boolean isOk() {
			return wall != null;
		}
	}

	/**
	 * Builds the wall spanned by two corners. The engine stands behind the wall, hidden;
	 * the text is read from the other side.
	 */
	public static Result of(BlockPos a, BlockPos b, BlockPos engine) {
		BlockPos min = BlockPos.min(a, b);
		BlockPos max = BlockPos.max(a, b);
		int sizeX = max.getX() - min.getX() + 1;
		int sizeY = max.getY() - min.getY() + 1;
		int sizeZ = max.getZ() - min.getZ() + 1;

		if (sizeX > 1 && sizeZ > 1) {
			return Result.fail(Component.translatable("message.billboard.not_flat", sizeX, sizeY, sizeZ));
		}

		// A 1x1 column could run either way; pick the orientation the engine is looking at most directly.
		boolean alongX;
		if (sizeX > 1) {
			alongX = true;
		} else if (sizeZ > 1) {
			alongX = false;
		} else {
			alongX = Math.abs(engine.getZ() - min.getZ()) >= Math.abs(engine.getX() - min.getX());
		}

		Direction facing;
		if (alongX) {
			int d = engine.getZ() - min.getZ();
			facing = d < 0 ? Direction.SOUTH : d > 0 ? Direction.NORTH : null;
		} else {
			int d = engine.getX() - min.getX();
			facing = d < 0 ? Direction.EAST : d > 0 ? Direction.WEST : null;
		}
		if (facing == null) {
			return Result.fail(Component.translatable("message.billboard.engine_in_plane"));
		}

		Wall wall = new Wall(min, max, facing);
		int maxWidth = BillboardConfig.maxWidth();
		int maxHeight = BillboardConfig.maxHeight();
		if (wall.width() > maxWidth || wall.height() > maxHeight) {
			return Result.fail(Component.translatable("message.billboard.too_big", wall.width(), wall.height(), maxWidth, maxHeight));
		}
		if (wall.distanceSqr(engine) > MAX_ENGINE_DISTANCE * MAX_ENGINE_DISTANCE) {
			return Result.fail(Component.translatable("message.billboard.too_far", MAX_ENGINE_DISTANCE));
		}
		return Result.ok(wall);
	}

	public boolean alongX() {
		return facing.getAxis() == Direction.Axis.Z;
	}

	public int width() {
		return alongX() ? max.getX() - min.getX() + 1 : max.getZ() - min.getZ() + 1;
	}

	public int height() {
		return max.getY() - min.getY() + 1;
	}

	/** World position of a pixel; column 0 is on the viewer's left, row 0 at the top. */
	public BlockPos pixel(int column, int row) {
		// The viewer looks against the facing; their right hand points clockwise from that.
		Direction right = facing.getOpposite().getClockWise();
		int along = right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? column : width() - 1 - column;
		int y = max.getY() - row;
		return alongX() ? new BlockPos(min.getX() + along, y, min.getZ()) : new BlockPos(min.getX(), y, min.getZ() + along);
	}

	public BoundingBox box() {
		return BoundingBox.fromCorners(min, max);
	}

	private double distanceSqr(BlockPos pos) {
		int x = Math.clamp(pos.getX(), min.getX(), max.getX());
		int y = Math.clamp(pos.getY(), min.getY(), max.getY());
		int z = Math.clamp(pos.getZ(), min.getZ(), max.getZ());
		return pos.distSqr(new BlockPos(x, y, z));
	}
}
