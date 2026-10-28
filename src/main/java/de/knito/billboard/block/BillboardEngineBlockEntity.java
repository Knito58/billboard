package de.knito.billboard.block;

import java.util.Optional;
import java.util.function.ToIntFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

import de.knito.billboard.ModBlockEntities;
import de.knito.billboard.ModComponents;
import de.knito.billboard.ModItems;
import de.knito.billboard.item.WandCorners;
import de.knito.billboard.menu.BillboardEngineMenu;
import de.knito.billboard.wall.BackupData;
import de.knito.billboard.wall.BillboardFont;
import de.knito.billboard.wall.BillboardPainter;
import de.knito.billboard.wall.BlockColors;
import de.knito.billboard.wall.Picture;
import de.knito.billboard.wall.PicturePlayer;
import de.knito.billboard.wall.Wall;

public class BillboardEngineBlockEntity extends BlockEntity implements ExtendedMenuProvider<BillboardEngineMenu.OpenData> {
	/** Menu slot numbers. */
	public static final int SLOT_WAND = 0;
	public static final int SLOT_PIXEL = 1;
	public static final int MAX_TEXT_LENGTH = 64;
	public static final int MIN_SPEED = 1;
	public static final int MAX_SPEED = 10;

	/** Holds the wand, a real item. */
	private final SimpleContainer items = new EngineContainer();
	/** Holds a one-item sample of the pixel block. It is only a setting: never taken from or given to a player. */
	private final SimpleContainer pixelSample = new EngineContainer();
	/** A real pixel stack from a world saved before the sample slot existed; handed back on the next tick. */
	private ItemStack legacyPixel = ItemStack.EMPTY;

	private class EngineContainer extends SimpleContainer {
		EngineContainer() {
			super(1);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			BillboardEngineBlockEntity.this.setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(BillboardEngineBlockEntity.this, player);
		}
	}

	private String text = "";
	private int speed = 5;
	/** Letter size: 1 = 5x7 blocks per letter, n = every font dot drawn as n x n blocks. */
	private int size = BillboardFont.MIN_SIZE;
	private boolean running;
	/** Run-off mode: the text scrolls until it has left the wall once, then the wall is restored. */
	private boolean once;
	/** What the wall shows, which may differ from the text box until Go is pressed again. */
	private String shownText = "";
	/** Letter size the wall shows. */
	private int shownSize = BillboardFont.MIN_SIZE;
	private BlockState pixel;
	private int offset;
	private int ticks;
	private boolean[][] bitmap;
	/** Whether the engine had a redstone signal at the last neighbour update, so only a rising edge counts. */
	private boolean powered;
	/** Text of the attached sign when it was last read; a running engine follows changes to it. */
	private String signText = "";
	/** Whether the wall shows a picture instead of text. Saved, so text commands leave such a wall alone after a restart. */
	private boolean pictureShown;
	/** Paints and plays the picture; not saved, the wall keeps its last picture after a restart. */
	private PicturePlayer picture;
	/** The Lua Box or CC computer that claimed the engine; not saved, programs claim again after a restart. */
	private EngineController controller;

	/** Synced to the open screen: 0 = speed, 1 = 1 while the wall shows text, 2 = 1 while running, 3 = letter size, 4 = 1 while claimed. */
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> speed;
				case 1 -> isShowing() ? 1 : 0;
				case 2 -> isRunning() ? 1 : 0;
				case 3 -> size;
				case 4 -> controller() != null ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return 5;
		}
	};

	public BillboardEngineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BILLBOARD_ENGINE, pos, state);
	}

	public static boolean isValidWand(ItemStack stack) {
		return stack.is(ModItems.SELECTOR_WAND);
	}

	/**
	 * Why this wand can't go into this engine, or null if it fits: it needs both corners,
	 * and they must mark a wall in this dimension that the engine touches.
	 */
	public static Component wandProblem(ItemStack wand, Level level, BlockPos engine) {
		WandCorners corners = wand.getOrDefault(ModComponents.WAND_CORNERS, WandCorners.EMPTY);
		if (!corners.isComplete()) {
			return Component.translatable("message.billboard.need_wand");
		}
		GlobalPos a = corners.first().orElseThrow();
		GlobalPos b = corners.second().orElseThrow();
		if (!a.dimension().equals(level.dimension()) || !b.dimension().equals(level.dimension())) {
			return Component.translatable("message.billboard.other_dimension");
		}
		return Wall.isNextTo(a.pos(), b.pos(), engine) ? null : Component.translatable("message.billboard.not_attached");
	}

	/** Pixel blocks must be plain full blocks: no block entity, nothing that falls. */
	public static boolean isValidPixel(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		Block block = blockItem.getBlock();
		return !(block instanceof EntityBlock)
				&& !(block instanceof Fallable)
				&& block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** A one-item copy of a valid pixel block, or empty for anything else. */
	public static ItemStack sampleOf(ItemStack stack) {
		return isValidPixel(stack) ? stack.copyWithCount(1) : ItemStack.EMPTY;
	}

	public static String cleanText(String text) {
		StringBuilder sb = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (sb.length() >= MAX_TEXT_LENGTH) {
				break;
			}
			sb.append(BillboardFont.supports(c) ? c : '?');
		}
		return sb.toString();
	}

	private boolean isShowing() {
		return level instanceof ServerLevel serverLevel && BackupData.get(serverLevel).has(worldPosition);
	}

	/** Text and size are used by the next Go; speed and a size change on a shown wall take effect at once. */
	public void applySettings(String newText, int newSpeed, int newSize) {
		text = cleanText(newText);
		speed = Math.clamp(newSpeed, MIN_SPEED, MAX_SPEED);
		size = Math.clamp(newSize, BillboardFont.MIN_SIZE, BillboardFont.MAX_SIZE);
		setChanged();
		if (size != shownSize) {
			resizeShown();
		}
	}

	/**
	 * Redraws the shown text in the new size, keeping the scroll position (and a stopped text) where it is,
	 * so a computer can let the letters grow or shrink. Waits for the next Go if the wall is not loaded.
	 */
	private void resizeShown() {
		if (!(level instanceof ServerLevel serverLevel) || pixel == null || pictureShown) {
			return;
		}
		BackupData backups = BackupData.get(serverLevel);
		Wall wall = backups.wallOf(worldPosition);
		if (wall == null || !isLoaded(serverLevel, wall)) {
			return;
		}
		boolean[][] oldBitmap = bitmap != null ? bitmap : BillboardFont.render(shownText, shownSize);
		boolean[][] newBitmap = BillboardFont.render(shownText, size);
		// Offset counts text columns from the wall's right edge; scale the part that already crossed it.
		int start = BillboardPainter.startOffset(wall);
		int newOffset = offset <= start ? offset : start + (offset - start) * size / shownSize;
		newOffset %= BillboardPainter.period(wall, newBitmap);
		BillboardPainter.step(serverLevel, worldPosition, backups, wall, oldBitmap, offset, newBitmap, newOffset, pixel);
		bitmap = newBitmap;
		shownSize = size;
		offset = newOffset;
		setChanged();
	}

	/** Checks everything, backs up the wall and draws the text at the wall's left edge. Returns the message for the player. */
	public Component go() {
		return start(BillboardPainter::startOffset);
	}

	/** Like {@link #go()}, but the text starts at the given wall column (0 = left edge), e.g. to stand at the right end. */
	public Component goAt(int column) {
		return start(wall -> wall.width() - Math.clamp(column, 0, wall.width()));
	}

	/**
	 * Lets the text run off the wall once, then gives the wall back. A shown text (running or stopped)
	 * continues from where it is; on an idle wall the text first comes in from the right.
	 */
	public Component runOff() {
		if (pictureShown || !isShowing()) {
			Component message = start(wall -> 0);
			if (!running) {
				return message;
			}
		}
		running = true;
		once = true;
		setChanged();
		return Component.translatable("message.billboard.running_off");
	}

	/** The wall marked by the wand in the engine, or the reason why there is none. */
	public Wall.Result wandWall() {
		ItemStack wand = items.getItem(0);
		if (!isValidWand(wand) || level == null) {
			return Wall.Result.fail(Component.translatable("message.billboard.need_wand"));
		}
		Component problem = wandProblem(wand, level, worldPosition);
		if (problem != null) {
			return Wall.Result.fail(problem);
		}
		WandCorners corners = wand.getOrDefault(ModComponents.WAND_CORNERS, WandCorners.EMPTY);
		return Wall.of(corners.first().orElseThrow().pos(), corners.second().orElseThrow().pos(), worldPosition);
	}

	/** @param firstOffset the scroll offset of the first frame for the given wall */
	private Component start(ToIntFunction<Wall> firstOffset) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return Component.empty();
		}

		Wall.Result result = checkWall(serverLevel);
		if (!result.isOk()) {
			return result.error();
		}
		ItemStack pixelStack = pixelSample.getItem(0);
		if (!isValidPixel(pixelStack)) {
			return Component.translatable("message.billboard.need_pixel");
		}
		if (text.isBlank()) {
			return Component.translatable("message.billboard.need_text");
		}
		Wall wall = result.wall();

		// Put back an older billboard of this engine first, so the backup always holds the real wall.
		BackupData backups = BackupData.get(serverLevel);
		backups.restore(serverLevel, worldPosition);
		backups.backup(serverLevel, worldPosition, wall);
		pictureShown = false;
		picture = null;
		shownText = text;
		shownSize = size;
		bitmap = BillboardFont.render(shownText, shownSize);
		pixel = ((BlockItem) pixelStack.getItem()).getBlock().defaultBlockState();
		offset = firstOffset.applyAsInt(wall);
		ticks = 0;
		once = false;
		BillboardPainter.paint(serverLevel, wall, bitmap, offset, pixel);
		running = true;
		setChanged();
		return Component.translatable("message.billboard.started", wall.width(), wall.height());
	}

	/** The wall the wand marks, if it can be used: right size, touched by the engine, no other billboard, no block entities. */
	private Wall.Result checkWall(ServerLevel serverLevel) {
		Wall.Result result = wandWall();
		if (!result.isOk()) {
			return result;
		}
		Wall wall = result.wall();
		if (BackupData.get(serverLevel).overlapsOther(worldPosition, wall)) {
			return Wall.Result.fail(Component.translatable("message.billboard.overlap"));
		}
		Optional<Component> blocked = checkWallBlocks(serverLevel, wall);
		return blocked.map(error -> Wall.Result.fail(error)).orElse(result);
	}

	/** Size of the wall a picture would get: the one shown now, otherwise the one the wand marks. */
	public Wall.Result pictureWall() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return Wall.Result.fail(Component.empty());
		}
		Wall shown = BackupData.get(serverLevel).wallOf(worldPosition);
		return shown != null ? new Wall.Result(shown, null) : wandWall();
	}

	/**
	 * Shows an image (an animated one plays by itself). The wall the wand marks is used; if it is the wall
	 * shown now, only the blocks that change are set, so one picture can follow another without flicker.
	 */
	public Component showPicture(Picture.Source source, Picture.Fit fit, boolean dither, boolean loop) throws Picture.PictureException {
		if (!(level instanceof ServerLevel serverLevel)) {
			return Component.empty();
		}
		Wall.Result result = checkWall(serverLevel);
		if (!result.isOk()) {
			return result.error();
		}
		Wall wall = result.wall();
		Picture rendered = Picture.render(source, wall.width(), wall.height(), fit, dither, BlockColors.palette());
		install(serverLevel, wall, rendered, loop);
		return Component.translatable("message.billboard.picture_shown", wall.width(), wall.height());
	}

	/**
	 * Draws raw pixels (3 or 4 bytes each, row by row) at a position of the shown picture; x and y count from 0
	 * at the top left. Starts a picture on the marked wall if none is shown; an animation stops on its current frame.
	 */
	public Component drawPixels(int x, int y, int w, int h, byte[] data, int bytesPerPixel) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return Component.empty();
		}
		if (picture == null) {
			Wall.Result result = checkWall(serverLevel);
			if (!result.isOk()) {
				return result.error();
			}
			Wall wall = result.wall();
			install(serverLevel, wall, Picture.blank(wall.width(), wall.height(), BlockColors.palette()), false);
		}
		picture = picture.still();
		picture.picture().blit(x, y, w, h, data, bytesPerPixel);
		picture.changed();
		return Component.translatable("message.billboard.drawn");
	}

	private void install(ServerLevel serverLevel, Wall wall, Picture rendered, boolean loop) {
		BackupData backups = BackupData.get(serverLevel);
		PicturePlayer previous = null;
		if (wall.equals(backups.wallOf(worldPosition))) {
			// Same wall: keep the backup and let the new picture overwrite what is there.
			previous = picture;
		} else {
			backups.restore(serverLevel, worldPosition);
			backups.backup(serverLevel, worldPosition, wall);
		}
		picture = new PicturePlayer(rendered, previous, loop);
		pictureShown = true;
		running = false;
		once = false;
		setChanged();
	}

	public boolean isPainting() {
		return picture != null && picture.isPainting();
	}

	/** Lets a stopped animation run again. */
	public boolean play() {
		return picture != null && picture.play();
	}

	/** Ticks between two scroll steps: speed 1 = one column per second, speed 10 = ten per second. */
	private int interval() {
		return 22 - 2 * speed;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BillboardEngineBlockEntity engine) {
		if (!engine.legacyPixel.isEmpty()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, engine.legacyPixel);
			engine.legacyPixel = ItemStack.EMPTY;
			engine.setChanged();
		}
		if (engine.picture != null && level instanceof ServerLevel serverLevel) {
			engine.tickPicture(serverLevel);
		} else if (engine.running && level instanceof ServerLevel serverLevel) {
			if (level.getGameTime() % 20 == 0) {
				engine.followSign();
			}
			if (engine.running) {
				engine.scroll(serverLevel);
			}
		}
	}

	/**
	 * Redstone power from any side except the one touching the wall. A redstone block pixel next to
	 * the engine would otherwise start it again and again.
	 */
	public boolean hasSignal() {
		if (level == null) {
			return false;
		}
		Direction wallSide = wallSide();
		for (Direction direction : Direction.values()) {
			if (direction != wallSide && level.hasSignal(worldPosition.relative(direction), direction)) {
				return true;
			}
		}
		return false;
	}

	/** Direction from the engine to the wall it touches: the shown wall, else the one the wand marks. Null if none. */
	private Direction wallSide() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		Wall shown = BackupData.get(serverLevel).wallOf(worldPosition);
		if (shown != null) {
			return shown.isTouchedBy(worldPosition) ? shown.facing() : null;
		}
		Wall.Result result = wandWall();
		return result.isOk() ? result.wall().facing() : null;
	}

	/** Called by the block on neighbour updates. A rising redstone edge reads the sign and starts the billboard. */
	public void setPowered(boolean newPowered) {
		if (newPowered == powered) {
			return;
		}
		powered = newPowered;
		setChanged();
		// A claimed engine listens only to its program.
		if (!powered || controller() != null) {
			return;
		}
		String sign = readSign();
		if (sign != null) {
			signText = sign;
			if (!sign.isBlank()) {
				text = cleanText(sign);
			}
		}
		// A pulse while the same text already runs changes nothing; this also stops redstone pixels next to the engine from restarting it.
		if (!(running && text.equals(shownText))) {
			go();
		}
	}

	/** While running, a changed sign (edited by a player or a Create Display Link) restarts the billboard with the new text. */
	private void followSign() {
		if (controller() != null) {
			return;
		}
		String sign = readSign();
		if (sign == null || sign.equals(signText)) {
			return;
		}
		signText = sign;
		setChanged();
		if (!sign.isBlank()) {
			text = cleanText(sign);
			go();
		}
	}

	/** Text of the first sign touching the engine, lines joined with spaces; front side, or back side if the front is empty. Null if there is no sign. */
	private String readSign() {
		if (level == null) {
			return null;
		}
		for (Direction direction : Direction.values()) {
			if (level.getBlockEntity(worldPosition.relative(direction)) instanceof SignBlockEntity sign) {
				String front = joinLines(sign.getText(SignTextSlot.FRONT));
				return front.isEmpty() ? joinLines(sign.getText(SignTextSlot.BACK)) : front;
			}
		}
		return null;
	}

	private static String joinLines(SignText signText) {
		StringBuilder sb = new StringBuilder();
		for (Component line : signText.getMessages(false)) {
			String part = line.getString().strip();
			if (!part.isEmpty()) {
				if (!sb.isEmpty()) {
					sb.append(' ');
				}
				sb.append(part);
			}
		}
		return sb.toString();
	}

	private void tickPicture(ServerLevel serverLevel) {
		BackupData backups = BackupData.get(serverLevel);
		Wall wall = backups.wallOf(worldPosition);
		if (wall == null) {
			picture = null;
			pictureShown = false;
			setChanged();
			return;
		}
		if (isLoaded(serverLevel, wall)) {
			picture.tick(serverLevel, worldPosition, backups, wall);
		}
	}

	private void scroll(ServerLevel serverLevel) {
		if (++ticks < interval()) {
			return;
		}
		ticks = 0;

		BackupData backups = BackupData.get(serverLevel);
		Wall wall = backups.wallOf(worldPosition);
		if (wall == null || pixel == null) {
			running = false;
			setChanged();
			return;
		}
		if (!isLoaded(serverLevel, wall)) {
			// Wait until the player comes back; loading chunks just to scroll would keep them alive forever.
			return;
		}
		if (bitmap == null) {
			bitmap = BillboardFont.render(shownText, shownSize);
		}
		int period = BillboardPainter.period(wall, bitmap);
		if (once && offset + 1 >= period) {
			// The last column is leaving the wall: nothing would be shown any more, so hand the wall back.
			restore();
			return;
		}
		int next = (offset + 1) % period;
		BillboardPainter.step(serverLevel, worldPosition, backups, wall, bitmap, offset, next, pixel);
		offset = next;
		setChanged();
	}

	private static boolean isLoaded(ServerLevel serverLevel, Wall wall) {
		for (int column = 0; column < wall.width(); column += 16) {
			if (!serverLevel.isLoaded(wall.pixel(column, 0))) {
				return false;
			}
		}
		return serverLevel.isLoaded(wall.pixel(wall.width() - 1, 0));
	}

	private Optional<Component> checkWallBlocks(ServerLevel serverLevel, Wall wall) {
		for (BlockPos pos : BlockPos.betweenClosed(wall.min(), wall.max())) {
			if (!serverLevel.isLoaded(pos)) {
				return Optional.of(Component.translatable("message.billboard.not_loaded"));
			}
			if (serverLevel.getBlockEntity(pos) != null) {
				return Optional.of(Component.translatable("message.billboard.block_entity",
						serverLevel.getBlockState(pos).getBlock().getName(), pos.getX(), pos.getY(), pos.getZ()));
			}
		}
		return Optional.empty();
	}

	public String text() {
		return text;
	}

	/** True if the wall already shows exactly this text standing still where go() puts it, in the current size and pixel block. */
	public boolean showsStill(String newText) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		Wall shown = BackupData.get(serverLevel).wallOf(worldPosition);
		ItemStack sample = pixelSample();
		return shown != null && offset == BillboardPainter.startOffset(shown)
				&& !running && !pictureShown && isShowing() && pixel != null
				&& shownText.equals(cleanText(newText)) && shownSize == size
				&& sample.getItem() instanceof BlockItem item && item.getBlock().defaultBlockState().equals(pixel);
	}

	public int speed() {
		return speed;
	}

	public int size() {
		return size;
	}

	/** Text scrolling, or an animation playing. */
	public boolean isRunning() {
		return picture != null ? picture.isPlaying() : running;
	}

	public boolean isShowingPicture() {
		return pictureShown && isShowing();
	}

	public boolean isRunningOff() {
		return running && once;
	}

	/** Who holds the engine now, or null. A claim whose program has ended is dropped here. */
	public EngineController controller() {
		if (controller != null && !controller.isActive()) {
			controller = null;
		}
		return controller;
	}

	/** Gives the engine to this controller alone. False if another one holds it. */
	public boolean claim(EngineController claimant) {
		EngineController holder = controller();
		if (holder != null && !holder.equals(claimant)) {
			return false;
		}
		controller = claimant;
		return true;
	}

	/** Frees the engine if this controller holds it. */
	public boolean release(EngineController claimant) {
		if (controller != null && controller.equals(claimant)) {
			controller = null;
			return true;
		}
		return false;
	}

	public ItemStack pixelSample() {
		return pixelSample.getItem(0);
	}

	/** Sets the pixel block for the next go(). Anything that is not a valid pixel block clears it. */
	public void setPixelSample(ItemStack stack) {
		pixelSample.setItem(0, sampleOf(stack));
		pixelSample.setChanged();
	}

	public boolean isShowingText() {
		return isShowing();
	}

	public Component stop() {
		if (picture != null && picture.isPlaying()) {
			picture.stop();
			return Component.translatable("message.billboard.stopped");
		}
		if (!running) {
			return Component.translatable("message.billboard.not_running");
		}
		running = false;
		once = false;
		setChanged();
		return Component.translatable("message.billboard.stopped");
	}

	public Component restore() {
		running = false;
		once = false;
		picture = null;
		pictureShown = false;
		setChanged();
		if (level instanceof ServerLevel serverLevel && BackupData.get(serverLevel).restore(serverLevel, worldPosition)) {
			return Component.translatable("message.billboard.restored");
		}
		return Component.translatable("message.billboard.nothing_to_restore");
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// The engine is going away: give the wall back and drop the wand. The pixel sample is only a setting and drops nothing.
		if (level instanceof ServerLevel serverLevel) {
			BackupData.get(serverLevel).restore(serverLevel, pos);
			Containers.dropContents(serverLevel, pos, items);
			Containers.dropItemStack(serverLevel, pos.getX(), pos.getY(), pos.getZ(), legacyPixel);
			legacyPixel = ItemStack.EMPTY;
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// Up to 1.0.1, slot 1 held a real pixel stack: it becomes the sample, and the items go back to the player.
		NonNullList<ItemStack> saved = NonNullList.withSize(2, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, saved);
		items.setItem(0, saved.get(0));
		legacyPixel = input.read("legacy_pixel", ItemStack.CODEC).orElse(saved.get(1));
		pixelSample.setItem(0, sampleOf(input.read("pixel_sample", ItemStack.CODEC).orElse(legacyPixel)));
		text = cleanText(input.getStringOr("text", ""));
		speed = Math.clamp(input.getIntOr("speed", 5), MIN_SPEED, MAX_SPEED);
		size = Math.clamp(input.getIntOr("size", BillboardFont.MIN_SIZE), BillboardFont.MIN_SIZE, BillboardFont.MAX_SIZE);
		shownSize = Math.clamp(input.getIntOr("shown_size", BillboardFont.MIN_SIZE), BillboardFont.MIN_SIZE, BillboardFont.MAX_SIZE);
		running = input.getBooleanOr("running", false);
		once = input.getBooleanOr("once", false);
		shownText = cleanText(input.getStringOr("shown_text", ""));
		pixel = input.read("pixel", BlockState.CODEC).orElse(null);
		offset = Math.max(0, input.getIntOr("offset", 0));
		powered = input.getBooleanOr("powered", false);
		signText = input.getStringOr("sign_text", "");
		pictureShown = input.getBooleanOr("picture", false);
		bitmap = null;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items.getItems());
		if (!pixelSample.isEmpty()) {
			output.store("pixel_sample", ItemStack.CODEC, pixelSample.getItem(0));
		}
		if (!legacyPixel.isEmpty()) {
			output.store("legacy_pixel", ItemStack.CODEC, legacyPixel);
		}
		output.putString("text", text);
		output.putInt("speed", speed);
		output.putInt("size", size);
		output.putInt("shown_size", shownSize);
		output.putBoolean("running", running);
		output.putBoolean("once", once);
		output.putString("shown_text", shownText);
		if (pixel != null) {
			output.store("pixel", BlockState.CODEC, pixel);
		}
		output.putInt("offset", offset);
		output.putBoolean("powered", powered);
		output.putString("sign_text", signText);
		output.putBoolean("picture", pictureShown);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.billboard.billboard_engine");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new BillboardEngineMenu(containerId, inventory, items, pixelSample, data, this);
	}

	@Override
	public BillboardEngineMenu.OpenData getScreenOpeningData(ServerPlayer player) {
		return new BillboardEngineMenu.OpenData(worldPosition, text);
	}
}
