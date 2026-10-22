package de.knito.billboard.compat;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralLookup;

import de.knito.billboard.ModBlockEntities;
import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.block.EngineControl;
import de.knito.billboard.block.EngineController;
import de.knito.billboard.wall.Picture;

/**
 * Makes the engine a CC: Tweaked peripheral. Only loaded when CC: Tweaked is installed. The functions are the
 * same as the Script Box's {@code engine} table; both go through {@link EngineControl}.
 */
public final class ComputerCraftCompat {
	private ComputerCraftCompat() {
	}

	public static void initialize() {
		PeripheralLookup.get().registerForBlockEntity((engine, direction) -> new EnginePeripheral(engine), ModBlockEntities.BILLBOARD_ENGINE);
	}

	/** A computer as claim holder. Its claim ends when the computer shuts down or the engine is detached. */
	private record Computer(IComputerAccess access) implements EngineController {
		@Override
		public String describe() {
			return "computer #" + access.getID();
		}

		@Override
		public boolean isActive() {
			return true;
		}
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

		@Override
		public void detach(IComputerAccess computer) {
			engine.release(new Computer(computer));
		}

		private EngineControl control(IComputerAccess computer) {
			return new EngineControl(engine, new Computer(computer));
		}

		@LuaFunction(mainThread = true)
		public final String getText(IComputerAccess computer) {
			return control(computer).getText();
		}

		/** Sets the text for the next go(); the wall keeps showing the old text until then. */
		@LuaFunction(mainThread = true)
		public final void setText(IComputerAccess computer, String text) throws LuaException {
			try {
				control(computer).setText(text);
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		@LuaFunction(mainThread = true)
		public final int getSpeed(IComputerAccess computer) {
			return control(computer).getSpeed();
		}

		@LuaFunction(mainThread = true)
		public final void setSpeed(IComputerAccess computer, int speed) throws LuaException {
			try {
				control(computer).setSpeed(speed);
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		@LuaFunction(mainThread = true)
		public final int getSize(IComputerAccess computer) {
			return control(computer).getSize();
		}

		@LuaFunction(mainThread = true)
		public final void setSize(IComputerAccess computer, int size) throws LuaException {
			try {
				control(computer).setSize(size);
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		/** The pixel block's id, such as "minecraft:gold_block", or nil if none is set. */
		@LuaFunction(mainThread = true)
		public final MethodResult getPixel(IComputerAccess computer) {
			String id = control(computer).getPixel();
			return id == null ? MethodResult.of() : MethodResult.of(id);
		}

		@LuaFunction(mainThread = true)
		public final void setPixel(IComputerAccess computer, String id) throws LuaException {
			try {
				control(computer).setPixel(id);
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		@LuaFunction(mainThread = true)
		public final boolean isRunning(IComputerAccess computer) {
			return control(computer).isRunning();
		}

		@LuaFunction(mainThread = true)
		public final boolean isShowing(IComputerAccess computer) {
			return control(computer).isShowing();
		}

		@LuaFunction(mainThread = true)
		public final MethodResult go(IComputerAccess computer, Optional<Integer> column) throws LuaException {
			try {
				return MethodResult.of(control(computer).go(column.orElse(null)));
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		/** setText, go and stop in one: the text stands still at the left edge. Unchanged text is not redrawn. */
		@LuaFunction(mainThread = true)
		public final MethodResult show(IComputerAccess computer, String text) throws LuaException {
			try {
				return MethodResult.of(control(computer).show(text));
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		@LuaFunction(mainThread = true)
		public final MethodResult runOff(IComputerAccess computer) throws LuaException {
			try {
				return MethodResult.of(control(computer).runOff());
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		@LuaFunction(mainThread = true)
		public final boolean isRunningOff(IComputerAccess computer) {
			return control(computer).isRunningOff();
		}

		@LuaFunction(mainThread = true)
		public final MethodResult stop(IComputerAccess computer) throws LuaException {
			try {
				return MethodResult.of(control(computer).stop());
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		@LuaFunction(mainThread = true)
		public final MethodResult restore(IComputerAccess computer) throws LuaException {
			try {
				return MethodResult.of(control(computer).restore());
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		/**
		 * Shows an image file on the wall: {@code drawImage(data [, options])}. data is the file's content
		 * (PNG, JPEG, GIF or BMP) as a string; an animated GIF plays by itself. Options:
		 * {@code fit = "fit" | "fill" | "stretch"}, {@code dither = true}, {@code loop = false}.
		 * The file is read on the computer's own thread; only the wall work happens in the game tick.
		 */
		@LuaFunction
		public final MethodResult drawImage(IComputerAccess computer, ILuaContext context, IArguments args) throws LuaException {
			ByteBuffer buffer = args.getBytes(0);
			byte[] data = new byte[buffer.remaining()];
			buffer.get(data);
			Map<?, ?> options = args.optTable(1, Map.of());
			boolean dither = Boolean.TRUE.equals(options.get("dither"));
			boolean loop = !Boolean.FALSE.equals(options.get("loop"));
			Picture.Fit fit;
			Picture.Source source;
			try {
				fit = EngineControl.fit(options.get("fit"));
				source = Picture.decode(data);
			} catch (EngineControl.Failure | Picture.PictureException e) {
				throw new LuaException(e.getMessage());
			}
			return context.executeMainThreadTask(() -> {
				try {
					return control(computer).showPicture(source, fit, dither, loop);
				} catch (EngineControl.Failure e) {
					throw new LuaException(e.getMessage());
				}
			});
		}

		/**
		 * Draws pixels made by the program: {@code drawPixels(x, y, width, height, data)}, x and y from 1 at the top left.
		 * data holds 3 bytes per pixel (red, green, blue), or 4 with alpha (below 128 keeps the wall's own block), row by row.
		 */
		@LuaFunction(mainThread = true)
		public final MethodResult drawPixels(IComputerAccess computer, int x, int y, int width, int height, ByteBuffer data) throws LuaException {
			byte[] bytes = new byte[data.remaining()];
			data.get(bytes);
			try {
				return MethodResult.of(control(computer).drawPixels(x, y, width, height, bytes));
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		@LuaFunction(mainThread = true)
		public final MethodResult getWallSize(IComputerAccess computer) {
			return MethodResult.of(control(computer).getWallSize());
		}

		@LuaFunction(mainThread = true)
		public final boolean isPainting(IComputerAccess computer) {
			return control(computer).isPainting();
		}

		@LuaFunction(mainThread = true)
		public final boolean isShowingPicture(IComputerAccess computer) {
			return control(computer).isShowingPicture();
		}

		@LuaFunction(mainThread = true)
		public final boolean play(IComputerAccess computer) throws LuaException {
			try {
				return control(computer).play();
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		/** Takes the engine for this computer alone: true, or false plus who holds it. Ends when the computer shuts down. */
		@LuaFunction(mainThread = true)
		public final MethodResult claim(IComputerAccess computer) throws LuaException {
			try {
				return MethodResult.of(control(computer).claim());
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		}

		@LuaFunction(mainThread = true)
		public final boolean release(IComputerAccess computer) {
			return control(computer).release();
		}

		@LuaFunction(mainThread = true)
		public final MethodResult isClaimed(IComputerAccess computer) {
			return MethodResult.of(control(computer).isClaimed());
		}
	}
}
