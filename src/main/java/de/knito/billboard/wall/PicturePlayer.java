package de.knito.billboard.wall;

import java.util.Arrays;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import de.knito.billboard.BillboardConfig;

/**
 * Puts a {@link Picture} onto a wall that has been backed up, and plays it if it is an animation.
 * It remembers what each wall pixel shows, so only changed blocks are set, at most
 * {@link BillboardConfig#blocksPerTick()} per tick. A big change takes a few ticks instead of one long one.
 * Not saved with the world: after a restart the wall keeps the last picture until it is restored or replaced.
 */
public final class PicturePlayer {
	/** What the wall shows is not known, e.g. text was there before: the pixel is written in any case. */
	private static final short UNKNOWN = -2;

	private final Picture picture;
	private final short[] shown;
	private short[] target;
	private int frame;
	private int ticksInFrame;
	private int remaining;
	private int cursor;
	private boolean playing;
	private final boolean loop;

	/** Starts a picture; the previous player of the same wall tells which blocks are already right. */
	public PicturePlayer(Picture picture, PicturePlayer previous, boolean loop) {
		this.picture = picture;
		this.loop = loop;
		int size = picture.width() * picture.height();
		if (previous != null && previous.shown.length == size) {
			shown = previous.shown;
		} else {
			shown = new short[size];
			Arrays.fill(shown, UNKNOWN);
		}
		playing = picture.frameCount() > 1;
		showFrame(0);
	}

	/** A player for drawing on: the frame on the wall now, as a still picture. */
	public PicturePlayer still() {
		if (picture.frameCount() == 1) {
			playing = false;
			return this;
		}
		Picture copy = new Picture(picture.width(), picture.height(), picture.palette(), List.of(target.clone()), new int[] {1});
		return new PicturePlayer(copy, this, false);
	}

	public Picture picture() {
		return picture;
	}

	/** True while the animation runs. */
	public boolean isPlaying() {
		return playing;
	}

	/** True while blocks of the current frame still have to be set. */
	public boolean isPainting() {
		return remaining > 0;
	}

	/** Freezes the animation on the current frame. */
	public void stop() {
		playing = false;
	}

	/** Lets an animation run again from the frame it shows. */
	public boolean play() {
		playing = picture.frameCount() > 1;
		return playing;
	}

	/** After drawing into the frame: count again which pixels need a new block. */
	public void changed() {
		showFrame(frame);
	}

	private void showFrame(int index) {
		frame = index;
		target = picture.frame(index);
		ticksInFrame = 0;
		remaining = 0;
		for (int i = 0; i < target.length; i++) {
			if (shown[i] != target[i]) {
				remaining++;
			}
		}
	}

	/** One game tick: set the next blocks, and go to the next frame once this one is complete and its time is up. */
	public void tick(ServerLevel level, BlockPos engine, BackupData backups, Wall wall) {
		ticksInFrame++;
		if (remaining > 0) {
			paint(level, engine, backups, wall);
		}
		if (playing && remaining == 0 && ticksInFrame >= picture.delay(frame)) {
			int next = frame + 1;
			if (next >= picture.frameCount()) {
				if (!loop) {
					playing = false;
					return;
				}
				next = 0;
			}
			showFrame(next);
		}
	}

	private void paint(ServerLevel level, BlockPos engine, BackupData backups, Wall wall) {
		int width = picture.width();
		int budget = BillboardConfig.blocksPerTick();
		ProtectedAreas.bypass(() -> {
			int left = budget;
			while (left > 0 && remaining > 0) {
				int i = cursor;
				cursor = (cursor + 1) % target.length;
				short want = target[i];
				if (shown[i] == want) {
					continue;
				}
				BlockPos pos = wall.pixel(i % width, i / width);
				BlockState state = want == Picture.KEEP ? backups.original(engine, pos) : picture.palette().state(want);
				if (state != null) {
					BillboardPainter.setQuietly(level, pos, state);
				}
				shown[i] = want;
				remaining--;
				left--;
			}
		});
	}
}
