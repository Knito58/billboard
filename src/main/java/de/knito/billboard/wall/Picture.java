package de.knito.billboard.wall;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.Node;

/**
 * A picture or animation prepared for one wall: every frame holds, per wall pixel (row by row,
 * column 0 on the viewer's left), the index of a block in the {@link BlockColors.Palette}, or
 * {@link #KEEP} where the wall keeps its own block (transparent parts and borders).
 */
public final class Picture {
	public static final short KEEP = -1;
	/** Largest image file accepted. */
	public static final int MAX_BYTES = 16 * 1024 * 1024;
	/** Largest side of a decoded image. */
	public static final int MAX_SIDE = 4096;
	/** Decoded pixels of all frames together, so a long animated GIF cannot fill the server's memory. */
	public static final long MAX_SOURCE_PIXELS = 64L * 1024 * 1024;
	/** Wall pixels of all frames together (a 256 x 256 wall can hold 256 frames). */
	public static final long MAX_WALL_PIXELS = 16L * 1024 * 1024;

	/** How an image that does not have the wall's shape is placed. */
	public enum Fit {
		/** Whole image visible, centered; the wall shows its own blocks around it. */
		FIT,
		/** Wall completely covered; what sticks out is cut off. */
		FILL,
		/** Pulled to the wall's shape. */
		STRETCH
	}

	/** Thrown with a message for the player or the Lua program. */
	public static final class PictureException extends Exception {
		public PictureException(String message) {
			super(message);
		}
	}

	/** A decoded image: ARGB frames in its own size, and how long each frame shows, in ticks. */
	public record Source(int width, int height, List<int[]> frames, int[] delays) {
	}

	private final int width;
	private final int height;
	private final BlockColors.Palette palette;
	private final List<short[]> frames;
	private final int[] delays;

	public Picture(int width, int height, BlockColors.Palette palette, List<short[]> frames, int[] delays) {
		this.width = width;
		this.height = height;
		this.palette = palette;
		this.frames = frames;
		this.delays = delays;
	}

	/** A single frame where the wall keeps all its own blocks, to draw on with {@link #blit}. */
	public static Picture blank(int width, int height, BlockColors.Palette palette) {
		short[] frame = new short[width * height];
		Arrays.fill(frame, KEEP);
		return new Picture(width, height, palette, List.of(frame), new int[] {1});
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public BlockColors.Palette palette() {
		return palette;
	}

	public int frameCount() {
		return frames.size();
	}

	public short[] frame(int index) {
		return frames.get(index);
	}

	public int delay(int index) {
		return delays[index];
	}

	/** Reads a PNG, JPEG, GIF or BMP file. An animated GIF gives all its frames, put together the way a browser shows them. */
	public static Source decode(byte[] data) throws PictureException {
		if (data.length > MAX_BYTES) {
			throw new PictureException("The image file is larger than " + MAX_BYTES / (1024 * 1024) + " MB");
		}
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw new PictureException("Not an image file (PNG, JPEG, GIF or BMP expected)");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, false);
				return reader.getFormatName().equalsIgnoreCase("gif") ? decodeGif(reader) : decodeStill(reader);
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			throw new PictureException("The image could not be read: " + e.getMessage());
		}
	}

	private static Source decodeStill(ImageReader reader) throws IOException, PictureException {
		checkSize(reader.getWidth(0), reader.getHeight(0));
		BufferedImage image = reader.read(0);
		int w = image.getWidth();
		int h = image.getHeight();
		return new Source(w, h, List.of(image.getRGB(0, 0, w, h, null, 0, w)), new int[] {1});
	}

	private static Source decodeGif(ImageReader reader) throws IOException, PictureException {
		int count = reader.getNumImages(true);
		int screenWidth = reader.getWidth(0);
		int screenHeight = reader.getHeight(0);
		IIOMetadata streamData = reader.getStreamMetadata();
		if (streamData != null) {
			Node screen = child((IIOMetadataNode) streamData.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
			if (screen != null) {
				screenWidth = Math.max(screenWidth, number(screen, "logicalScreenWidth", screenWidth));
				screenHeight = Math.max(screenHeight, number(screen, "logicalScreenHeight", screenHeight));
			}
		}
		checkSize(screenWidth, screenHeight);
		if ((long) screenWidth * screenHeight * count > MAX_SOURCE_PIXELS) {
			throw new PictureException("The animation has too many frames for its size (" + count + " frames of " + screenWidth + " x " + screenHeight + ")");
		}

		List<int[]> frames = new ArrayList<>();
		int[] delays = new int[count];
		int[] canvas = new int[screenWidth * screenHeight];
		for (int i = 0; i < count; i++) {
			IIOMetadataNode tree = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
			Node descriptor = child(tree, "ImageDescriptor");
			Node control = child(tree, "GraphicControlExtension");
			int left = descriptor == null ? 0 : number(descriptor, "imageLeftPosition", 0);
			int top = descriptor == null ? 0 : number(descriptor, "imageTopPosition", 0);
			String disposal = control == null ? "none" : attribute(control, "disposalMethod", "none");
			int hundredths = control == null ? 10 : number(control, "delayTime", 10);
			// Browsers show frames without (or with a tiny) delay for a tenth of a second; so does the billboard.
			delays[i] = Math.max(1, Math.round((hundredths <= 1 ? 10 : hundredths) / 5f));

			BufferedImage image = reader.read(i);
			int w = image.getWidth();
			int h = image.getHeight();
			int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
			int[] before = disposal.equals("restoreToPrevious") ? canvas.clone() : null;
			for (int y = 0; y < h; y++) {
				int cy = top + y;
				if (cy < 0 || cy >= screenHeight) {
					continue;
				}
				for (int x = 0; x < w; x++) {
					int cx = left + x;
					int argb = pixels[y * w + x];
					if (cx >= 0 && cx < screenWidth && argb >>> 24 != 0) {
						canvas[cy * screenWidth + cx] = argb;
					}
				}
			}
			frames.add(canvas.clone());
			if (before != null) {
				canvas = before;
			} else if (disposal.equals("restoreToBackgroundColor")) {
				for (int y = Math.max(0, top); y < Math.min(screenHeight, top + h); y++) {
					Arrays.fill(canvas, y * screenWidth + Math.max(0, left), y * screenWidth + Math.min(screenWidth, left + w), 0);
				}
			}
		}
		return new Source(screenWidth, screenHeight, frames, delays);
	}

	private static void checkSize(int width, int height) throws PictureException {
		if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE) {
			throw new PictureException("The image is " + width + " x " + height + "; at most " + MAX_SIDE + " x " + MAX_SIDE + " is accepted");
		}
	}

	private static Node child(Node node, String name) {
		for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
			if (c.getNodeName().equals(name)) {
				return c;
			}
		}
		return null;
	}

	private static String attribute(Node node, String name, String fallback) {
		Node attribute = node.getAttributes().getNamedItem(name);
		return attribute == null ? fallback : attribute.getNodeValue();
	}

	private static int number(Node node, String name, int fallback) {
		try {
			return Integer.parseInt(attribute(node, name, String.valueOf(fallback)));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Scales every frame to the wall and picks the nearest block color for each pixel. */
	public static Picture render(Source source, int width, int height, Fit fit, boolean dither, BlockColors.Palette palette)
			throws PictureException {
		if ((long) width * height * source.frames().size() > MAX_WALL_PIXELS) {
			throw new PictureException("Too many frames for this wall: " + source.frames().size() + " frames of "
					+ width + " x " + height + " is more than " + MAX_WALL_PIXELS + " pixels");
		}
		// Where the image lands on the wall, in wall pixels; it may stick out (FILL) or leave borders (FIT).
		double scaleX = (double) width / source.width();
		double scaleY = (double) height / source.height();
		if (fit == Fit.FIT) {
			scaleX = scaleY = Math.min(scaleX, scaleY);
		} else if (fit == Fit.FILL) {
			scaleX = scaleY = Math.max(scaleX, scaleY);
		}
		double left = (width - source.width() * scaleX) / 2;
		double top = (height - source.height() * scaleY) / 2;

		List<short[]> frames = new ArrayList<>();
		for (int[] argb : source.frames()) {
			int[] scaled = scale(argb, source.width(), source.height(), width, height, left, top, scaleX, scaleY);
			frames.add(match(scaled, width, height, dither, palette));
		}
		return new Picture(width, height, palette, frames, source.delays().clone());
	}

	/** Box filter: every wall pixel gets the average of the image pixels it covers (or the nearest one when enlarging). */
	private static int[] scale(int[] src, int sw, int sh, int width, int height, double left, double top, double scaleX, double scaleY) {
		int[] out = new int[width * height];
		for (int y = 0; y < height; y++) {
			double v0 = (y - top) / scaleY;
			double v1 = (y + 1 - top) / scaleY;
			if (v1 <= 0 || v0 >= sh) {
				continue;
			}
			int y0 = Math.clamp((long) Math.floor(v0), 0, sh - 1);
			int y1 = Math.clamp((long) Math.ceil(v1), y0 + 1, sh);
			for (int x = 0; x < width; x++) {
				double u0 = (x - left) / scaleX;
				double u1 = (x + 1 - left) / scaleX;
				if (u1 <= 0 || u0 >= sw) {
					continue;
				}
				int x0 = Math.clamp((long) Math.floor(u0), 0, sw - 1);
				int x1 = Math.clamp((long) Math.ceil(u1), x0 + 1, sw);
				long a = 0;
				long r = 0;
				long g = 0;
				long b = 0;
				for (int sy = y0; sy < y1; sy++) {
					for (int sx = x0; sx < x1; sx++) {
						int p = src[sy * sw + sx];
						int alpha = p >>> 24;
						a += alpha;
						r += (p >> 16 & 0xFF) * alpha;
						g += (p >> 8 & 0xFF) * alpha;
						b += (p & 0xFF) * alpha;
					}
				}
				int n = (y1 - y0) * (x1 - x0);
				out[y * width + x] = a == 0 ? 0 : (int) (a / n) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
			}
		}
		return out;
	}

	/** Mostly transparent pixels keep the wall's block; the rest get the nearest block, with Floyd-Steinberg dithering if asked. */
	private static short[] match(int[] argb, int width, int height, boolean dither, BlockColors.Palette palette) {
		short[] out = new short[width * height];
		float[] error = dither ? new float[(width + 2) * 2 * 3] : null;
		for (int y = 0; y < height; y++) {
			if (dither) {
				// Two error rows: this one and the next; swap them each row.
				System.arraycopy(error, (width + 2) * 3, error, 0, (width + 2) * 3);
				Arrays.fill(error, (width + 2) * 3, error.length, 0);
			}
			for (int x = 0; x < width; x++) {
				int p = argb[y * width + x];
				if (p >>> 24 < 128) {
					out[y * width + x] = KEEP;
					continue;
				}
				int r = p >> 16 & 0xFF;
				int g = p >> 8 & 0xFF;
				int b = p & 0xFF;
				if (dither) {
					int e = (x + 1) * 3;
					r = Math.clamp(Math.round(r + error[e]), 0, 255);
					g = Math.clamp(Math.round(g + error[e + 1]), 0, 255);
					b = Math.clamp(Math.round(b + error[e + 2]), 0, 255);
				}
				int index = palette.nearest(r, g, b);
				out[y * width + x] = (short) index;
				if (dither && index >= 0) {
					int c = palette.rgb(index);
					spread(error, width, x, r - (c >> 16 & 0xFF), g - (c >> 8 & 0xFF), b - (c & 0xFF));
				}
			}
		}
		return out;
	}

	private static void spread(float[] error, int width, int x, int dr, int dg, int db) {
		int row = (width + 2) * 3;
		add(error, (x + 2) * 3, dr, dg, db, 7 / 16f);
		add(error, row + x * 3, dr, dg, db, 3 / 16f);
		add(error, row + (x + 1) * 3, dr, dg, db, 5 / 16f);
		add(error, row + (x + 2) * 3, dr, dg, db, 1 / 16f);
	}

	private static void add(float[] error, int at, int dr, int dg, int db, float share) {
		error[at] += dr * share;
		error[at + 1] += dg * share;
		error[at + 2] += db * share;
	}

	/**
	 * Draws raw pixels from a program into the first frame: 3 bytes (red, green, blue) or 4 bytes (plus alpha)
	 * per pixel, row by row. Pixels outside the wall are skipped.
	 */
	public void blit(int x, int y, int w, int h, byte[] data, int bytesPerPixel) {
		short[] frame = frames.getFirst();
		for (int row = 0; row < h; row++) {
			int wy = y + row;
			if (wy < 0 || wy >= height) {
				continue;
			}
			for (int column = 0; column < w; column++) {
				int wx = x + column;
				if (wx < 0 || wx >= width) {
					continue;
				}
				int at = (row * w + column) * bytesPerPixel;
				boolean opaque = bytesPerPixel < 4 || (data[at + 3] & 0xFF) >= 128;
				frame[wy * width + wx] = opaque
						? (short) palette.nearest(data[at] & 0xFF, data[at + 1] & 0xFF, data[at + 2] & 0xFF)
						: KEEP;
			}
		}
	}
}
