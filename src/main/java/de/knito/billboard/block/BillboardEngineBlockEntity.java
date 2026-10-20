package de.knito.billboard.block;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
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
import de.knito.billboard.wall.Wall;

public class BillboardEngineBlockEntity extends BlockEntity implements ExtendedMenuProvider<BillboardEngineMenu.OpenData> {
	public static final int SLOT_WAND = 0;
	public static final int SLOT_PIXEL = 1;
	public static final int MAX_TEXT_LENGTH = 64;
	public static final int MIN_SPEED = 1;
	public static final int MAX_SPEED = 10;

	private final SimpleContainer items = new SimpleContainer(2) {
		@Override
		public void setChanged() {
			super.setChanged();
			BillboardEngineBlockEntity.this.setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(BillboardEngineBlockEntity.this, player);
		}
	};

	private String text = "";
	private int speed = 5;
	private boolean running;
	/** What the wall shows, which may differ from the text box until Go is pressed again. */
	private String shownText = "";
	private BlockState pixel;
	private int offset;
	private int ticks;
	private boolean[][] bitmap;
	/** Whether the engine had a redstone signal at the last neighbour update, so only a rising edge counts. */
	private boolean powered;
	/** Text of the attached sign when it was last read; a running engine follows changes to it. */
	private String signText = "";

	/** Synced to the open screen: 0 = speed, 1 = 1 while the wall shows text, 2 = 1 while running. */
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> speed;
				case 1 -> isShowing() ? 1 : 0;
				case 2 -> running ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return 3;
		}
	};

	public BillboardEngineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BILLBOARD_ENGINE, pos, state);
	}

	public static boolean isValidWand(ItemStack stack) {
		return stack.is(ModItems.SELECTOR_WAND);
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

	public void applySettings(String newText, int newSpeed) {
		text = cleanText(newText);
		speed = Math.clamp(newSpeed, MIN_SPEED, MAX_SPEED);
		setChanged();
	}

	/** Checks everything, backs up the wall and draws the text. Returns the message for the player. */
	public Component go() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return Component.empty();
		}

		ItemStack wand = items.getItem(SLOT_WAND);
		WandCorners corners = wand.getOrDefault(ModComponents.WAND_CORNERS, WandCorners.EMPTY);
		if (!isValidWand(wand) || !corners.isComplete()) {
			return Component.translatable("message.billboard.need_wand");
		}
		ItemStack pixelStack = items.getItem(SLOT_PIXEL);
		if (!isValidPixel(pixelStack)) {
			return Component.translatable("message.billboard.need_pixel");
		}
		if (text.isBlank()) {
			return Component.translatable("message.billboard.need_text");
		}

		GlobalPos a = corners.first().orElseThrow();
		GlobalPos b = corners.second().orElseThrow();
		if (!a.dimension().equals(serverLevel.dimension()) || !b.dimension().equals(serverLevel.dimension())) {
			return Component.translatable("message.billboard.other_dimension");
		}

		Wall.Result result = Wall.of(a.pos(), b.pos(), worldPosition);
		if (!result.isOk()) {
			return result.error();
		}
		Wall wall = result.wall();

		BackupData backups = BackupData.get(serverLevel);
		if (backups.overlapsOther(worldPosition, wall)) {
			return Component.translatable("message.billboard.overlap");
		}
		Optional<Component> blocked = checkWallBlocks(serverLevel, wall);
		if (blocked.isPresent()) {
			return blocked.get();
		}

		// Put back an older billboard of this engine first, so the backup always holds the real wall.
		backups.restore(serverLevel, worldPosition);
		backups.backup(serverLevel, worldPosition, wall);
		shownText = text;
		bitmap = BillboardFont.render(shownText);
		pixel = ((BlockItem) pixelStack.getItem()).getBlock().defaultBlockState();
		offset = BillboardPainter.startOffset(wall);
		ticks = 0;
		BillboardPainter.paint(serverLevel, wall, bitmap, offset, pixel);
		running = true;
		setChanged();
		return Component.translatable("message.billboard.started", wall.width(), wall.height());
	}

	/** Ticks between two scroll steps: speed 1 = one column per second, speed 10 = ten per second. */
	private int interval() {
		return 22 - 2 * speed;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BillboardEngineBlockEntity engine) {
		if (engine.running && level instanceof ServerLevel serverLevel) {
			if (level.getGameTime() % 20 == 0) {
				engine.followSign();
			}
			if (engine.running) {
				engine.scroll(serverLevel);
			}
		}
	}

	/** Called by the block on neighbour updates. A rising redstone edge reads the sign and starts the billboard. */
	public void setPowered(boolean newPowered) {
		if (newPowered == powered) {
			return;
		}
		powered = newPowered;
		setChanged();
		if (!powered) {
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
			bitmap = BillboardFont.render(shownText);
		}
		int next = (offset + 1) % BillboardPainter.period(wall, bitmap);
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

	public int speed() {
		return speed;
	}

	public boolean isRunning() {
		return running;
	}

	public boolean isShowingText() {
		return isShowing();
	}

	public Component stop() {
		if (!running) {
			return Component.translatable("message.billboard.not_running");
		}
		running = false;
		setChanged();
		return Component.translatable("message.billboard.stopped");
	}

	public Component restore() {
		running = false;
		setChanged();
		if (level instanceof ServerLevel serverLevel && BackupData.get(serverLevel).restore(serverLevel, worldPosition)) {
			return Component.translatable("message.billboard.restored");
		}
		return Component.translatable("message.billboard.nothing_to_restore");
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// The engine is going away: give the wall back and drop the wand and pixel block.
		if (level instanceof ServerLevel serverLevel) {
			BackupData.get(serverLevel).restore(serverLevel, pos);
			Containers.dropContents(serverLevel, pos, items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clearContent();
		ContainerHelper.loadAllItems(input, items.getItems());
		text = cleanText(input.getStringOr("text", ""));
		speed = Math.clamp(input.getIntOr("speed", 5), MIN_SPEED, MAX_SPEED);
		running = input.getBooleanOr("running", false);
		shownText = cleanText(input.getStringOr("shown_text", ""));
		pixel = input.read("pixel", BlockState.CODEC).orElse(null);
		offset = Math.max(0, input.getIntOr("offset", 0));
		powered = input.getBooleanOr("powered", false);
		signText = input.getStringOr("sign_text", "");
		bitmap = null;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items.getItems());
		output.putString("text", text);
		output.putInt("speed", speed);
		output.putBoolean("running", running);
		output.putString("shown_text", shownText);
		if (pixel != null) {
			output.store("pixel", BlockState.CODEC, pixel);
		}
		output.putInt("offset", offset);
		output.putBoolean("powered", powered);
		output.putString("sign_text", signText);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.billboard.billboard_engine");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new BillboardEngineMenu(containerId, inventory, items, data, this);
	}

	@Override
	public BillboardEngineMenu.OpenData getScreenOpeningData(ServerPlayer player) {
		return new BillboardEngineMenu.OpenData(worldPosition, text);
	}
}
