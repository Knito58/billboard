package de.knito.billboard.wall;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;

import de.knito.billboard.Billboard;

/**
 * The blocks a picture is made of, each with the average color of its side texture.
 * The colors come from {@code billboard/block_colors.json}, measured from the vanilla textures
 * by {@code tools/block_colors.py}, because a server has no textures of its own.
 * Only blocks that stay as they are when placed in a wall are kept: see {@link #usable(BlockState)}.
 */
public final class BlockColors {
	/** Blocks that are never used for pictures, on top of the rules in {@link #usable(BlockState)}. Data packs can add to it. */
	public static final TagKey<Block> EXCLUDED = TagKey.create(Registries.BLOCK, Billboard.id("not_for_pictures"));

	private static Palette palette;

	private BlockColors() {
	}

	/** The current palette, built on first use after a start or a data pack reload. */
	public static synchronized Palette palette() {
		if (palette == null) {
			palette = build();
			Billboard.LOGGER.info("Billboard pictures use {} blocks", palette.size());
		}
		return palette;
	}

	/** Forgets the palette, so changed tags are picked up. */
	public static synchronized void invalidate() {
		palette = null;
	}

	/**
	 * Whether a block may be a picture pixel: an opaque full cube without block entity that
	 * does not fall, tick, glow or send redstone power, so it looks the same for as long as it is shown.
	 */
	public static boolean usable(BlockState state) {
		Block block = state.getBlock();
		return !(block instanceof EntityBlock)
				&& !(block instanceof Fallable)
				&& !state.isRandomlyTicking()
				&& state.getLightEmission() == 0
				&& !state.isSignalSource()
				&& state.canOcclude()
				&& state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
				&& !state.is(EXCLUDED);
	}

	private static Palette build() {
		List<BlockState> states = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : readTable().entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id == null) {
				continue;
			}
			BuiltInRegistries.BLOCK.getOptional(id).map(Block::defaultBlockState).filter(BlockColors::usable).ifPresent(state -> {
				states.add(state);
				colors.add(Integer.parseInt(entry.getValue().getAsString(), 16));
			});
		}
		return new Palette(states.toArray(BlockState[]::new), colors.stream().mapToInt(Integer::intValue).toArray());
	}

	private static JsonObject readTable() {
		try (InputStream in = BlockColors.class.getResourceAsStream("/billboard/block_colors.json")) {
			if (in == null) {
				throw new IOException("missing");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("colors");
		} catch (IOException | RuntimeException e) {
			Billboard.LOGGER.error("Could not read billboard/block_colors.json, pictures will be empty", e);
			return new JsonObject();
		}
	}

	/** Blocks and their colors, with a nearest-color lookup in OKLab, a color space that follows how people see differences. */
	public static final class Palette {
		/** Colors are looked up with 6 bits per channel; the cache holds the answer for every such color. */
		private static final int BITS = 6;

		private final BlockState[] states;
		private final int[] rgb;
		private final float[] lab;
		private final short[] cache = new short[1 << (3 * BITS)];

		Palette(BlockState[] states, int[] rgb) {
			this.states = states;
			this.rgb = rgb;
			this.lab = new float[rgb.length * 3];
			for (int i = 0; i < rgb.length; i++) {
				toOklab(rgb[i] >> 16 & 0xFF, rgb[i] >> 8 & 0xFF, rgb[i] & 0xFF, lab, i * 3);
			}
			Arrays.fill(cache, (short) -1);
		}

		public int size() {
			return states.length;
		}

		public BlockState state(int index) {
			return states[index];
		}

		public int rgb(int index) {
			return rgb[index];
		}

		/** Index of the block whose color comes closest; -1 if the palette is empty. */
		public int nearest(int r, int g, int b) {
			if (states.length == 0) {
				return -1;
			}
			int shift = 8 - BITS;
			int key = (r >> shift) << (2 * BITS) | (g >> shift) << BITS | (b >> shift);
			int cached = cache[key];
			if (cached >= 0) {
				return cached;
			}
			// Search with the center of the cache cell, so every color in it gets the same answer.
			int half = 1 << (shift - 1);
			float[] target = new float[3];
			toOklab((r >> shift << shift) + half, (g >> shift << shift) + half, (b >> shift << shift) + half, target, 0);
			int best = 0;
			float bestDistance = Float.MAX_VALUE;
			for (int i = 0; i < states.length; i++) {
				float dl = lab[i * 3] - target[0];
				float da = lab[i * 3 + 1] - target[1];
				float db = lab[i * 3 + 2] - target[2];
				float distance = dl * dl + da * da + db * db;
				if (distance < bestDistance) {
					bestDistance = distance;
					best = i;
				}
			}
			cache[key] = (short) best;
			return best;
		}

		private static void toOklab(int r, int g, int b, float[] out, int at) {
			double lr = linear(r);
			double lg = linear(g);
			double lb = linear(b);
			double l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
			double m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
			double s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
			out[at] = (float) (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s);
			out[at + 1] = (float) (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s);
			out[at + 2] = (float) (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
		}

		private static double linear(int channel) {
			double c = channel / 255.0;
			return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
		}
	}
}
