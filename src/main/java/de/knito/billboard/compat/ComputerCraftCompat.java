package de.knito.billboard.compat;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralLookup;

import de.knito.billboard.ModBlockEntities;
import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.wall.BillboardFont;
import de.knito.billboard.wall.Picture;
import de.knito.billboard.wall.Wall;

/** Makes the engine a CC: Tweaked peripheral. Only loaded when CC: Tweaked is installed. */
public final class ComputerCraftCompat {
	private ComputerCraftCompat() {
	}

	public static void initialize() {
		PeripheralLookup.get().registerForBlockEntity((engine, direction) -> new EnginePeripheral(engine), ModBlockEntities.BILLBOARD_ENGINE);
	}

	public static final class EnginePeripheral implements IPeripheral {
		private final BillboardEngineBlockEntity engine;

		EnginePeripheral(BillboardEngineBlockEntity engine) {
			this.engine = engine;
		}

		@Override
		public String getType() {
			return "billboard_engine";
		}

		@Override
		public boolean equals(IPeripheral other) {
			return other instanceof EnginePeripheral peripheral && peripheral.engine == engine;
		}

		@Override
		public Object getTarget() {
			return engine;
		}

		@LuaFunction(mainThread = true)
		public final String getText() {
			return engine.text();
		}

		/** Sets the text for the next go(); the wall keeps showing the old text until then. */
		@LuaFunction(mainThread = true)
		public final void setText(String text) throws LuaException {
			if (text.length() > BillboardEngineBlockEntity.MAX_TEXT_LENGTH) {
				throw new LuaException("Text is longer than " + BillboardEngineBlockEntity.MAX_TEXT_LENGTH + " characters");
			}
			engine.applySettings(text, engine.speed(), engine.size());
		}

		@LuaFunction(mainThread = true)
		public final int getSpeed() {
			return engine.speed();
		}

		/** Takes effect at once, also while running. */
		@LuaFunction(mainThread = true)
		public final void setSpeed(int speed) throws LuaException {
			if (speed < BillboardEngineBlockEntity.MIN_SPEED || speed > BillboardEngineBlockEntity.MAX_SPEED) {
				throw new LuaException("Speed must be " + BillboardEngineBlockEntity.MIN_SPEED + " to " + BillboardEngineBlockEntity.MAX_SPEED);
			}
			engine.applySettings(engine.text(), speed, engine.size());
		}

		@LuaFunction(mainThread = true)
		public final int getSize() {
			return engine.size();
		}

		/** Letter size 1 to 9 (1 = 5x7 blocks per letter). Takes effect at once, also on a stopped text. */
		@LuaFunction(mainThread = true)
		public final void setSize(int size) throws LuaException {
			if (size < BillboardFont.MIN_SIZE || size > BillboardFont.MAX_SIZE) {
				throw new LuaException("Size must be " + BillboardFont.MIN_SIZE + " to " + BillboardFont.MAX_SIZE);
			}
			engine.applySettings(engine.text(), engine.speed(), size);
		}

		/** The pixel block's id, such as "minecraft:gold_block", or nil if none is set. */
		@LuaFunction(mainThread = true)
		public final MethodResult getPixel() {
			ItemStack sample = engine.pixelSample();
			return sample.isEmpty() ? MethodResult.of() : MethodResult.of(BuiltInRegistries.ITEM.getKey(sample.getItem()).toString());
		}

		/** Sets the pixel block for the next go(), like the sample slot. Only the id is stored; no item is needed. */
		@LuaFunction(mainThread = true)
		public final void setPixel(String id) throws LuaException {
			Identifier identifier = Identifier.tryParse(id);
			ItemStack stack = identifier == null ? ItemStack.EMPTY
					: BuiltInRegistries.ITEM.getOptional(identifier).map(ItemStack::new).orElse(ItemStack.EMPTY);
			if (!BillboardEngineBlockEntity.isValidPixel(stack)) {
				throw new LuaException(id + " is not a full block that can be a pixel");
			}
			engine.setPixelSample(stack);
		}

		@LuaFunction(mainThread = true)
		public final boolean isRunning() {
			return engine.isRunning();
		}

		/** True while the wall shows text (running or stopped), false once it is restored. */
		@LuaFunction(mainThread = true)
		public final boolean isShowing() {
			return engine.isShowingText();
		}

		@LuaFunction(mainThread = true)
		public final MethodResult go() {
			return result(engine.go(), "started");
		}

		/** Lets the text run off the wall once, then restores the wall. Starts from the right edge if nothing is shown. */
		@LuaFunction(mainThread = true)
		public final MethodResult runOff() {
			return result(engine.runOff(), "running_off");
		}

		/** True while a runOff() is still on its way; false once the wall is back. */
		@LuaFunction(mainThread = true)
		public final boolean isRunningOff() {
			return engine.isRunningOff();
		}

		@LuaFunction(mainThread = true)
		public final MethodResult stop() {
			return result(engine.stop(), "stopped");
		}

		@LuaFunction(mainThread = true)
		public final MethodResult restore() {
			return result(engine.restore(), "restored");
		}

		/**
		 * Shows an image file on the wall: {@code drawImage(data [, options])}. data is the file's content
		 * (PNG, JPEG, GIF or BMP) as a string; an animated GIF plays by itself. Options:
		 * {@code fit = "fit" | "fill" | "stretch"}, {@code dither = true}, {@code loop = false}.
		 * The file is read on the computer's own thread; only the wall work happens in the game tick.
		 */
		@LuaFunction
		public final MethodResult drawImage(ILuaContext context, IArguments args) throws LuaException {
			ByteBuffer buffer = args.getBytes(0);
			byte[] data = new byte[buffer.remaining()];
			buffer.get(data);
			Map<?, ?> options = args.optTable(1, Map.of());
			Picture.Fit fit = fit(options.get("fit"));
			boolean dither = Boolean.TRUE.equals(options.get("dither"));
			boolean loop = !Boolean.FALSE.equals(options.get("loop"));
			Picture.Source source;
			try {
				source = Picture.decode(data);
			} catch (Picture.PictureException e) {
				throw new LuaException(e.getMessage());
			}
			return context.executeMainThreadTask(() -> {
				try {
					return result(engine.showPicture(source, fit, dither, loop), "picture_shown").getResult();
				} catch (Picture.PictureException e) {
					throw new LuaException(e.getMessage());
				}
			});
		}

		private static Picture.Fit fit(Object value) throws LuaException {
			if (value == null) {
				return Picture.Fit.FIT;
			}
			try {
				return Picture.Fit.valueOf(value.toString().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new LuaException("fit must be \"fit\", \"fill\" or \"stretch\"");
			}
		}

		/**
		 * Draws pixels made by the program: {@code drawPixels(x, y, width, height, data)}, x and y from 1 at the top left.
		 * data holds 3 bytes per pixel (red, green, blue), or 4 with alpha (below 128 keeps the wall's own block), row by row.
		 * Starts a picture on the marked wall if none is shown.
		 */
		@LuaFunction(mainThread = true)
		public final MethodResult drawPixels(int x, int y, int width, int height, ByteBuffer data) throws LuaException {
			if (width < 1 || height < 1 || width > Picture.MAX_SIDE || height > Picture.MAX_SIDE) {
				throw new LuaException("Width and height must be 1 to " + Picture.MAX_SIDE);
			}
			int pixels = width * height;
			int length = data.remaining();
			if (length != pixels * 3 && length != pixels * 4) {
				throw new LuaException("Expected " + pixels * 3 + " bytes (RGB) or " + pixels * 4 + " bytes (RGBA), got " + length);
			}
			byte[] bytes = new byte[length];
			data.get(bytes);
			return result(engine.drawPixels(x - 1, y - 1, width, height, bytes, length / pixels), "drawn");
		}

		/** Width and height of the wall a picture goes on (the shown one, else the one the wand marks), or false plus a reason. */
		@LuaFunction(mainThread = true)
		public final MethodResult getWallSize() {
			Wall.Result wall = engine.pictureWall();
			return wall.isOk() ? MethodResult.of(wall.wall().width(), wall.wall().height()) : result(wall.error(), "");
		}

		/** True while blocks of the current picture or frame are still being set. */
		@LuaFunction(mainThread = true)
		public final boolean isPainting() {
			return engine.isPainting();
		}

		/** True while the wall shows a picture rather than text. */
		@LuaFunction(mainThread = true)
		public final boolean isShowingPicture() {
			return engine.isShowingPicture();
		}

		/** Lets an animation stopped with stop() run again. */
		@LuaFunction(mainThread = true)
		public final boolean play() {
			return engine.play();
		}

		/** Lua style: true on success, otherwise false plus a short reason such as "need_wand" or "overlap". */
		private static MethodResult result(Component message, String success) {
			String reason = message.getContents() instanceof TranslatableContents translatable
					? translatable.getKey().replace("message.billboard.", "")
					: message.getString();
			return reason.equals(success) ? MethodResult.of(true) : MethodResult.of(false, reason);
		}
	}
}
