package de.knito.billboard.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralLookup;

import de.knito.billboard.ModBlockEntities;
import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.wall.BillboardFont;

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

		@LuaFunction(mainThread = true)
		public final MethodResult stop() {
			return result(engine.stop(), "stopped");
		}

		@LuaFunction(mainThread = true)
		public final MethodResult restore() {
			return result(engine.restore(), "restored");
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
