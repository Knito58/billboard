package de.knito.billboard.block;

import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import de.knito.billboard.wall.BillboardFont;
import de.knito.billboard.wall.Picture;
import de.knito.billboard.wall.Wall;

/**
 * The engine functions for programs, shared by the Script Box's Lua and the CC: Tweaked peripheral so both
 * behave the same. Results that can fail are Lua style: {@code true}, or {@code false} plus a short reason
 * such as "need_wand". Changing calls fail while another controller holds the engine. Main thread only.
 */
public final class EngineControl {
	/** A wrong argument or a claim held by someone else; becomes a Lua error. */
	public static final class Failure extends Exception {
		public Failure(String message) {
			super(message);
		}
	}

	private final BillboardEngineBlockEntity engine;
	private final EngineController caller;

	/** caller may be null for a program that cannot claim; it can then only change an unclaimed engine. */
	public EngineControl(BillboardEngineBlockEntity engine, EngineController caller) {
		this.engine = engine;
		this.caller = caller;
	}

	private void checkClaim() throws Failure {
		EngineController holder = engine.controller();
		if (holder != null && !holder.equals(caller)) {
			throw new Failure("engine is claimed by " + holder.describe());
		}
	}

	public String getText() {
		return engine.text();
	}

	/** Sets the text for the next go(); the wall keeps showing the old text until then. */
	public void setText(String text) throws Failure {
		checkClaim();
		if (text.length() > BillboardEngineBlockEntity.MAX_TEXT_LENGTH) {
			throw new Failure("Text is longer than " + BillboardEngineBlockEntity.MAX_TEXT_LENGTH + " characters");
		}
		engine.applySettings(text, engine.speed(), engine.size());
	}

	public int getSpeed() {
		return engine.speed();
	}

	/** Takes effect at once, also while running. */
	public void setSpeed(int speed) throws Failure {
		checkClaim();
		if (speed < BillboardEngineBlockEntity.MIN_SPEED || speed > BillboardEngineBlockEntity.MAX_SPEED) {
			throw new Failure("Speed must be " + BillboardEngineBlockEntity.MIN_SPEED + " to " + BillboardEngineBlockEntity.MAX_SPEED);
		}
		engine.applySettings(engine.text(), speed, engine.size());
	}

	public int getSize() {
		return engine.size();
	}

	/** Letter size 1 to 9 (1 = 5x7 blocks per letter). Takes effect at once, also on a stopped text. */
	public void setSize(int size) throws Failure {
		checkClaim();
		if (size < BillboardFont.MIN_SIZE || size > BillboardFont.MAX_SIZE) {
			throw new Failure("Size must be " + BillboardFont.MIN_SIZE + " to " + BillboardFont.MAX_SIZE);
		}
		engine.applySettings(engine.text(), engine.speed(), size);
	}

	/** The pixel block's id, such as "minecraft:gold_block", or null if none is set. */
	public String getPixel() {
		ItemStack sample = engine.pixelSample();
		return sample.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
	}

	/** Sets the pixel block for the next go(), like the sample slot. Only the id is stored; no item is needed. */
	public void setPixel(String id) throws Failure {
		checkClaim();
		Identifier identifier = Identifier.tryParse(id);
		ItemStack stack = identifier == null ? ItemStack.EMPTY
				: BuiltInRegistries.ITEM.getOptional(identifier).map(ItemStack::new).orElse(ItemStack.EMPTY);
		if (!BillboardEngineBlockEntity.isValidPixel(stack)) {
			throw new Failure(id + " is not a full block that can be a pixel");
		}
		engine.setPixelSample(stack);
	}

	public boolean isRunning() {
		return engine.isRunning();
	}

	/** True while the wall shows text (running or stopped), false once it is restored. */
	public boolean isShowing() {
		return engine.isShowingText();
	}

	/** Draws the text at the left edge, or with a column number starting at that column (0 = left edge). */
	public Object[] go(Integer column) throws Failure {
		checkClaim();
		return result(column != null ? engine.goAt(column) : engine.go(), "started");
	}

	/** Lets the text run off the wall once, then restores the wall. Starts from the right edge if nothing is shown. */
	public Object[] runOff() throws Failure {
		checkClaim();
		return result(engine.runOff(), "running_off");
	}

	/** True while a runOff() is still on its way; false once the wall is back. */
	public boolean isRunningOff() {
		return engine.isRunningOff();
	}

	public Object[] stop() throws Failure {
		checkClaim();
		return result(engine.stop(), "stopped");
	}

	public Object[] restore() throws Failure {
		checkClaim();
		return result(engine.restore(), "restored");
	}

	/** Shows a decoded image file; an animated GIF plays by itself. */
	public Object[] showPicture(Picture.Source source, Picture.Fit fit, boolean dither, boolean loop) throws Failure {
		checkClaim();
		try {
			return result(engine.showPicture(source, fit, dither, loop), "picture_shown");
		} catch (Picture.PictureException e) {
			throw new Failure(e.getMessage());
		}
	}

	/** The fit option of drawImage: "fit" (default), "fill" or "stretch". */
	public static Picture.Fit fit(Object value) throws Failure {
		if (value == null) {
			return Picture.Fit.FIT;
		}
		try {
			return Picture.Fit.valueOf(value.toString().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new Failure("fit must be \"fit\", \"fill\" or \"stretch\"");
		}
	}

	/**
	 * Draws pixels made by the program, x and y from 1 at the top left. data holds 3 bytes per pixel (red, green, blue),
	 * or 4 with alpha (below 128 keeps the wall's own block), row by row. Starts a picture on the marked wall if none is shown.
	 */
	public Object[] drawPixels(int x, int y, int width, int height, byte[] data) throws Failure {
		checkClaim();
		if (width < 1 || height < 1 || width > Picture.MAX_SIDE || height > Picture.MAX_SIDE) {
			throw new Failure("Width and height must be 1 to " + Picture.MAX_SIDE);
		}
		int pixels = width * height;
		if (data.length != pixels * 3 && data.length != pixels * 4) {
			throw new Failure("Expected " + pixels * 3 + " bytes (RGB) or " + pixels * 4 + " bytes (RGBA), got " + data.length);
		}
		return result(engine.drawPixels(x - 1, y - 1, width, height, data, data.length / pixels), "drawn");
	}

	/** Width and height of the wall shown now, else of the one the wand marks; or null plus a reason such as "need_wand". */
	public Object[] getWallSize() {
		Wall.Result wall = engine.pictureWall();
		return wall.isOk() ? new Object[]{wall.wall().width(), wall.wall().height()} : new Object[]{null, reason(wall.error())};
	}

	/** True until the picture, or the first frame of an animation, is completely on the wall. */
	public boolean isPainting() {
		return engine.isPainting();
	}

	/** True while the wall shows a picture rather than text. */
	public boolean isShowingPicture() {
		return engine.isShowingPicture();
	}

	/** Lets an animation stopped with stop() run again. */
	public boolean play() throws Failure {
		checkClaim();
		return engine.play();
	}

	/** Takes the engine for this program alone: true, or false plus who holds it. Never waits. */
	public Object[] claim() throws Failure {
		if (caller == null) {
			throw new Failure("this program cannot claim the engine");
		}
		EngineController holder = engine.controller();
		if (engine.claim(caller)) {
			return new Object[]{true};
		}
		return new Object[]{false, holder.describe()};
	}

	/** Gives the engine free again. False if this program did not hold it. */
	public boolean release() {
		return caller != null && engine.release(caller);
	}

	/** Whether anyone holds the engine, plus who. */
	public Object[] isClaimed() {
		EngineController holder = engine.controller();
		return holder == null ? new Object[]{false} : new Object[]{true, holder.describe()};
	}

	private static Object[] result(Component message, String success) {
		String reason = reason(message);
		return reason.equals(success) ? new Object[]{true} : new Object[]{false, reason};
	}

	/** The short reason for a message: its translation key without the "message.billboard." part. */
	public static String reason(Component message) {
		return message.getContents() instanceof TranslatableContents translatable
				? translatable.getKey().replace("message.billboard.", "")
				: message.getString();
	}
}
