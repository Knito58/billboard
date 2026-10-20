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
	public static final int INVENTORY_Y = 108;

	private final Container container;
	private final ContainerData data;
	private final String initialText;
	/** Only set on the server. */
	private final BillboardEngineBlockEntity engine;

	/** Client side. */
	public BillboardEngineMenu(int containerId, Inventory inventory, OpenData openData) {
		this(containerId, inventory, new SimpleContainer(2), new SimpleContainerData(3), null, openData.text());
	}

	/** Server side. */
	public BillboardEngineMenu(int containerId, Inventory inventory, Container container, ContainerData data, BillboardEngineBlockEntity engine) {
		this(containerId, inventory, container, data, engine, "");
	}

	private BillboardEngineMenu(int containerId, Inventory inventory, Container container, ContainerData data, BillboardEngineBlockEntity engine, String initialText) {
		super(ModMenus.BILLBOARD_ENGINE, containerId);
		checkContainerSize(container, 2);
		checkContainerDataCount(data, 3);
		this.container = container;
		this.data = data;
		this.engine = engine;
		this.initialText = initialText;

		addSlot(new Slot(container, BillboardEngineBlockEntity.SLOT_WAND, WAND_X, SLOTS_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return BillboardEngineBlockEntity.isValidWand(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new Slot(container, BillboardEngineBlockEntity.SLOT_PIXEL, PIXEL_X, SLOTS_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return BillboardEngineBlockEntity.isValidPixel(stack);
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

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if (slotIndex < 2) {
				if (!moveItemStackTo(stack, 2, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (BillboardEngineBlockEntity.isValidWand(stack)) {
				if (!moveItemStackTo(stack, 0, 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (BillboardEngineBlockEntity.isValidPixel(stack)) {
				if (!moveItemStackTo(stack, 1, 2, false)) {
					return ItemStack.EMPTY;
				}
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
