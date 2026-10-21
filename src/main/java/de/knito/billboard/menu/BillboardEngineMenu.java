package de.knito.billboard.menu;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import de.knito.billboard.ModMenus;
import de.knito.billboard.block.BillboardEngineBlockEntity;

public class BillboardEngineMenu extends AbstractContainerMenu {
	/** Sent to the client when the screen opens. */
	public record OpenData(BlockPos pos, String text) {
		public static final StreamCodec<ByteBuf, OpenData> STREAM_CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenData::pos,
				ByteBufCodecs.stringUtf8(BillboardEngineBlockEntity.MAX_TEXT_LENGTH * 4), OpenData::text,
				OpenData::new
		);
	}

	public static final int WAND_X = 8;
	public static final int PIXEL_X = 30;
	public static final int SLOTS_Y = 42;
	public static final int INVENTORY_Y = 130;

	private final Container container;
	private final Container pixelSample;
	private final ContainerData data;
	private final String initialText;
	/** Only set on the server. */
	private final BillboardEngineBlockEntity engine;

	/** Client side. */
	public BillboardEngineMenu(int containerId, Inventory inventory, OpenData openData) {
		this(containerId, inventory, new SimpleContainer(1), new SimpleContainer(1), new SimpleContainerData(4), null, openData.text());
	}

	/** Server side. */
	public BillboardEngineMenu(int containerId, Inventory inventory, Container container, Container pixelSample, ContainerData data, BillboardEngineBlockEntity engine) {
		this(containerId, inventory, container, pixelSample, data, engine, "");
	}

	private BillboardEngineMenu(int containerId, Inventory inventory, Container container, Container pixelSample, ContainerData data, BillboardEngineBlockEntity engine, String initialText) {
		super(ModMenus.BILLBOARD_ENGINE, containerId);
		checkContainerSize(container, 1);
		checkContainerSize(pixelSample, 1);
		checkContainerDataCount(data, 4);
		this.container = container;
		this.pixelSample = pixelSample;
		this.data = data;
		this.engine = engine;
		this.initialText = initialText;

		addSlot(new Slot(container, 0, WAND_X, SLOTS_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return BillboardEngineBlockEntity.isValidWand(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		// A sample slot: clicking with a block copies it in, the block stays with the player. See clicked().
		addSlot(new Slot(pixelSample, 0, PIXEL_X, SLOTS_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public boolean mayPickup(Player player) {
				return false;
			}
		});
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		addDataSlots(data);
	}

	public BillboardEngineBlockEntity engine() {
		return engine;
	}

	public String initialText() {
		return initialText;
	}

	public int speed() {
		return data.get(0);
	}

	public int size() {
		return data.get(3);
	}

	public boolean isShowing() {
		return data.get(1) != 0;
	}

	public boolean isRunning() {
		return data.get(2) != 0;
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	/** Sets the pixel block sample. Anything that is not a valid pixel block clears it. */
	public void setPixelSample(ItemStack stack) {
		pixelSample.setItem(0, BillboardEngineBlockEntity.sampleOf(stack));
		pixelSample.setChanged();
	}

	@Override
	public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
		if (slotIndex != BillboardEngineBlockEntity.SLOT_PIXEL) {
			super.clicked(slotIndex, button, input, player);
			return;
		}
		// Runs on both sides, so the client shows the change right away. No item ever moves.
		switch (input) {
			case PICKUP -> {
				ItemStack carried = getCarried();
				if (carried.isEmpty() || BillboardEngineBlockEntity.isValidPixel(carried)) {
					setPixelSample(carried);
				}
			}
			case QUICK_MOVE -> setPixelSample(ItemStack.EMPTY);
			case SWAP -> {
				ItemStack hotbar = player.getInventory().getItem(button);
				if (BillboardEngineBlockEntity.isValidPixel(hotbar)) {
					setPixelSample(hotbar);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return slot.index != BillboardEngineBlockEntity.SLOT_PIXEL && super.canDragTo(slot);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if (slotIndex == BillboardEngineBlockEntity.SLOT_WAND) {
				if (!moveItemStackTo(stack, 2, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (BillboardEngineBlockEntity.isValidWand(stack)) {
				if (!moveItemStackTo(stack, 0, 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (BillboardEngineBlockEntity.isValidPixel(stack)) {
				// Shift-click on a block copies it into the sample slot; the stack stays where it is.
				setPixelSample(stack);
				return ItemStack.EMPTY;
			} else {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return clicked;
	}
}
